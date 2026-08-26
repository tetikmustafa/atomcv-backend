package com.mustafatetik.atomcv.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mustafatetik.atomcv.AbstractIntegrationTest;
import com.mustafatetik.atomcv.email.EmailMessage;
import com.mustafatetik.atomcv.email.EmailSender;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Bolum 40.2's link, end to end — and Bolum 40.4's silence, asserted rather
 * than assumed.
 *
 * <p>The sender is replaced with one that records, because the assertions that
 * matter are about what was in the email: that the verifier travelled and the
 * stored hash did not, and that requesting a link for an address with an
 * account is indistinguishable from requesting one for an address without.
 */
@AutoConfigureMockMvc
class MagicLinkApiIT extends AbstractIntegrationTest {

    private static final Pattern LINK =
            Pattern.compile("/verify\\?s=([A-Za-z0-9_-]+)&v=([A-Za-z0-9_-]+)");

    /** Nested here, on the class actually being run, which is where it is found. */
    @TestConfiguration
    static class RecordingSender {

        @Bean
        @Primary
        EmailSender recordingEmailSender(List<EmailMessage> sent) {
            return message -> {
                sent.add(message);
                return true;
            };
        }

        @Bean
        List<EmailMessage> sent() {
            return new ArrayList<>();
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private List<EmailMessage> sent;

    @BeforeEach
    void clear() {
        sent.clear();
        jdbc.update("DELETE FROM magic_link_tokens");
        jdbc.update("DELETE FROM users WHERE email LIKE '%@link.test'");
    }

    // ── Bolum 40.4 ────────────────────────────────────────────────────────

    /**
     * The whole of the enumeration defence: byte for byte, an address that has
     * an account and one that does not produce the same answer. If this ever
     * diverges — a different status, a body, an extra header — the endpoint
     * has become a way to ask whether someone is a user here.
     */
    @Test
    void anAddressWithAnAccountAndOneWithoutAnswerIdentically() throws Exception {
        jdbc.update("INSERT INTO users (email, email_verified) VALUES ('known@link.test', true)");

        MvcResult known = requestLinkFor("known@link.test");
        MvcResult unknown = requestLinkFor("stranger@link.test");

        assertThat(known.getResponse().getStatus())
                .isEqualTo(unknown.getResponse().getStatus())
                .isEqualTo(202);
        assertThat(known.getResponse().getContentAsString())
                .isEqualTo(unknown.getResponse().getContentAsString())
                .isEmpty();
        assertThat(known.getResponse().getHeaderNames())
                .isEqualTo(unknown.getResponse().getHeaderNames());
        // And both actually sent something, so the silence is not the silence
        // of one of them doing nothing.
        assertThat(sent).hasSize(2);
    }

    /** An address nobody claimed becomes an account, unverified until the link is opened. */
    @Test
    void anUnknownAddressGetsAnUnverifiedAccount() throws Exception {
        requestLinkFor("stranger@link.test");

        assertThat(jdbc.queryForObject(
                "SELECT email_verified FROM users WHERE email = 'stranger@link.test'",
                Boolean.class)).isFalse();
    }

    // ── Bolum 40.2 ────────────────────────────────────────────────────────

    @Test
    void openingTheLinkSignsInAndVerifiesTheAddress() throws Exception {
        requestLinkFor("ada@link.test");
        String[] halves = halvesOfTheLastLink();

        MvcResult verified = verify(halves[0], halves[1])
                .andExpect(status().isNoContent())
                .andReturn();

        Cookie sid = verified.getResponse().getCookie("sid");
        assertThat(sid).isNotNull();
        mvc.perform(get("/api/v1/auth/session").cookie(new Cookie("sid", sid.getValue())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true));

        assertThat(jdbc.queryForObject(
                "SELECT email_verified FROM users WHERE email = 'ada@link.test'",
                Boolean.class)).isTrue();
    }

    /**
     * The stored half is a hash and the sent half is not, so a stolen dump
     * signs nobody in.
     */
    @Test
    void whatIsInTheDatabaseIsNotWhatIsInTheEmail() throws Exception {
        requestLinkFor("ada@link.test");
        String[] halves = halvesOfTheLastLink();

        String storedHash = jdbc.queryForObject(
                "SELECT verifier_hash FROM magic_link_tokens WHERE selector = ?",
                String.class, halves[0]);

        assertThat(storedHash).isNotEqualTo(halves[1]);
        assertThat(sent.get(0).text()).doesNotContain(storedHash);
    }

    /** The guard, made to fail — and every failure is the same failure. */
    @Test
    void aSecondUseOfTheSameLinkIsRefused() throws Exception {
        requestLinkFor("ada@link.test");
        String[] halves = halvesOfTheLastLink();
        verify(halves[0], halves[1]).andExpect(status().isNoContent());

        verify(halves[0], halves[1])
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MAGIC_LINK_INVALID"))
                // No reason, and that is deliberate: expired, used and wrong
                // must not be tellable apart.
                .andExpect(jsonPath("$.params").doesNotExist());
    }

    @Test
    void aWrongVerifierIsRefusedAndLeavesTheLinkUnspent() throws Exception {
        requestLinkFor("ada@link.test");
        String[] halves = halvesOfTheLastLink();

        verify(halves[0], "not-the-verifier")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MAGIC_LINK_INVALID"));

        // A failed guess must not burn the real link.
        verify(halves[0], halves[1]).andExpect(status().isNoContent());
    }

    @Test
    void anExpiredLinkIsRefused() throws Exception {
        requestLinkFor("ada@link.test");
        String[] halves = halvesOfTheLastLink();
        jdbc.update("UPDATE magic_link_tokens SET expires_at = now() - interval '1 minute' "
                + "WHERE selector = ?", halves[0]);

        verify(halves[0], halves[1])
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MAGIC_LINK_INVALID"));
    }

    @Test
    void aSelectorNobodyIssuedIsTheSameRefusal() throws Exception {
        verify("not-a-selector", "not-a-verifier")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MAGIC_LINK_INVALID"));
    }

    /**
     * Signing in spends every other link outstanding for the account, so one
     * somebody else asked for cannot still be redeemed afterwards.
     */
    @Test
    void signingInSpendsTheOtherLinksThatWereOutstanding() throws Exception {
        requestLinkFor("ada@link.test");
        String[] first = halvesOfTheLastLink();
        requestLinkFor("ada@link.test");
        String[] second = halvesOfTheLastLink();

        verify(second[0], second[1]).andExpect(status().isNoContent());

        verify(first[0], first[1]).andExpect(status().isBadRequest());
    }

    @Test
    void anAddressThatIsNotAnAddressIsAValidationFailureAndNotALink() throws Exception {
        mvc.perform(post("/api/v1/auth/magic-link")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"not-an-address\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        assertThat(sent).isEmpty();
    }

    // ── fixtures ──────────────────────────────────────────────────────────

    private MvcResult requestLinkFor(String email) throws Exception {
        return mvc.perform(post("/api/v1/auth/magic-link")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"" + email + "\"}"))
                .andExpect(status().isAccepted())
                .andReturn();
    }

    private org.springframework.test.web.servlet.ResultActions verify(
            String selector, String verifier) throws Exception {
        return mvc.perform(post("/api/v1/auth/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"selector\": \"" + selector + "\", \"verifier\": \""
                        + verifier + "\"}"));
    }

    private String[] halvesOfTheLastLink() {
        assertThat(sent).isNotEmpty();
        Matcher matcher = LINK.matcher(sent.get(sent.size() - 1).text());
        assertThat(matcher.find()).as("the email carries a link").isTrue();
        return new String[] {matcher.group(1), matcher.group(2)};
    }
}
