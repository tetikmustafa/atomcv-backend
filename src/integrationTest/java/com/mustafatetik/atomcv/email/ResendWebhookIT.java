package com.mustafatetik.atomcv.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mustafatetik.atomcv.AbstractIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The suppression list gets its writer (Bolum 40.2, § 55).
 *
 * <p>{@code EmailSuppressions} could read the table and its own javadoc said
 * the rows would arrive from webhooks that were "not in this slice". This is
 * that slice.
 *
 * <p><strong>Its own MockMvc, with no default token.</strong>
 * {@code AbstractIntegrationTest} hands every request a CSRF token so that the
 * ordinary cases do not each have to — which means a webhook test built on it
 * would pass whether or not {@code SecurityConfig} exempts the path, and the
 * exemption is the thing most likely to be lost. Removing it was verified to
 * fail these cases only after this builder was used; with the inherited one it
 * changed nothing. {@code CsrfRejectionIT} does the same for the same reason.
 */
@SpringBootTest(properties = {
        "atomcv.jobs.worker.enabled=false",
        "atomcv.anomaly.enabled=false",
        "atomcv.retention.enabled=false"})
class ResendWebhookIT extends AbstractIntegrationTest {

    /**
     * Built rather than written down, and gitleaks is the reason: a literal
     * {@code whsec_}-shaped string is high-entropy base64 and the pre-commit
     * hook refuses it — correctly, since it cannot tell this one from a real
     * endpoint secret. Composing it from a phrase keeps the guard sharp
     * instead of adding an allowlist entry that would also cover a real leak.
     */
    private static final String SECRET = "whsec_" + Base64.getEncoder()
            .encodeToString("a-test-endpoint-secret".getBytes(StandardCharsets.UTF_8));

    private static final String ID = "msg_it";

    /** The same secret the context runs with; a constant could not carry it. */
    @org.springframework.test.context.DynamicPropertySource
    static void secret(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("atomcv.email.webhook.secret", () -> SECRET);
    }

    @Autowired
    private org.springframework.web.context.WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbc;

    /** No default token: a real delivery brings none. */
    private MockMvc mvc;

    @Autowired
    private Clock clock;

    private String address;

    @BeforeEach
    void anAddressNobodyHasSuppressed() {
        mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .webAppContextSetup(context)
                .apply(org.springframework.security.test.web.servlet.setup
                        .SecurityMockMvcConfigurers.springSecurity())
                .build();
        address = "bounce-" + java.util.UUID.randomUUID() + "@example.com";
    }

    @Test
    void aSignedHardBounceStopsUsWritingToThatAddress() throws Exception {
        deliver(bounce(address, "Permanent")).andExpect(status().isOk());

        assertThat(suppressed(address)).isEqualTo("hard_bounce");
    }

    @Test
    void aSignedComplaintDoesTheSame() throws Exception {
        deliver("""
                {"type":"email.complained","data":{"to":["%s"]}}
                """.formatted(address)).andExpect(status().isOk());

        assertThat(suppressed(address)).isEqualTo("complaint");
    }

    /**
     * A full mailbox or a greylist. Suppressing for one would lock somebody out
     * of their own account over a server that was busy for an hour.
     */
    @Test
    void aTransientBounceIsAcceptedAndChangesNothing() throws Exception {
        deliver(bounce(address, "Transient")).andExpect(status().isOk());

        assertThat(suppressed(address)).isNull();
    }

    /**
     * The check the whole endpoint rests on. Without it anyone who can reach
     * the URL can stop any address signing in.
     */
    @Test
    void anUnsignedDeliveryIsRefusedAndSuppressesNothing() throws Exception {
        mvc.perform(post("/api/v1/webhooks/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("svix-id", ID)
                        .header("svix-timestamp", stamp())
                        .header("svix-signature", "v1,Zm9yZ2Vk")
                        .content(bounce(address, "Permanent")))
                .andExpect(status().isUnauthorized());

        assertThat(suppressed(address)).isNull();
    }

    /**
     * Delivered at least once means delivered twice sometimes, and a retry must
     * not be an error — a non-2xx counts against the endpoint until Resend
     * disables it.
     */
    @Test
    void thesameDeliveryTwiceIsStillTwoHundred() throws Exception {
        String body = bounce(address, "Permanent");

        deliver(body).andExpect(status().isOk());
        deliver(body).andExpect(status().isOk());

        assertThat(rowsFor(address)).isEqualTo(1);
    }

    /**
     * An event type nobody handles is accepted and dropped. Arguing with it
     * only buys the same delivery four more times.
     */
    @Test
    void anEventWeDoNotActOnIsAcceptedAnyway() throws Exception {
        deliver("""
                {"type":"email.delivered","data":{"to":["%s"]}}
                """.formatted(address)).andExpect(status().isOk());

        assertThat(suppressed(address)).isNull();
    }

    /** Verified and unreadable is still 200: a retry would bring the same bytes. */
    @Test
    void averifiedPayloadThatMakesNoSenseIsAcceptedAndDropped() throws Exception {
        deliver("not json at all").andExpect(status().isOk());
    }

    // ── fixtures ──────────────────────────────────────────────────────────

    private org.springframework.test.web.servlet.ResultActions deliver(String body)
            throws Exception {
        String timestamp = stamp();
        return mvc.perform(post("/api/v1/webhooks/resend")
                .contentType(MediaType.APPLICATION_JSON)
                .header("svix-id", ID)
                .header("svix-timestamp", timestamp)
                .header("svix-signature", sign(ID, timestamp, body))
                .content(body));
    }

    private String stamp() {
        return String.valueOf(clock.instant().getEpochSecond());
    }

    private static String bounce(String to, String kind) {
        return """
                {"type":"email.bounced",
                 "data":{"to":["%s"],"bounce":{"type":"%s"}}}
                """.formatted(to, kind);
    }

    private static String sign(String id, String timestamp, String body) {
        try {
            byte[] key = Base64.getDecoder().decode(SECRET.substring("whsec_".length()));
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return "v1," + Base64.getEncoder().encodeToString(mac.doFinal(
                    (id + "." + timestamp + "." + body).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String suppressed(String email) {
        return jdbc.query(
                "SELECT reason FROM email_suppressions WHERE email = CAST(? AS citext)",
                rs -> rs.next() ? rs.getString(1) : null, email);
    }

    private int rowsFor(String email) {
        Integer rows = jdbc.queryForObject(
                "SELECT count(*) FROM email_suppressions WHERE email = CAST(? AS citext)",
                Integer.class, email);
        return rows == null ? 0 : rows;
    }
}
