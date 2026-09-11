package com.mustafatetik.atomcv.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mustafatetik.atomcv.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Turning the optional post off from an inbox (Bolum 57.7).
 *
 * <p>The token is the whole credential, so the cases worth having are about
 * what it can and cannot do: it stops this account's email, it says the same
 * thing when it is not a token at all, and it needs no session — which is the
 * only reason it can be acted on from a mail client.
 */
@AutoConfigureMockMvc
class UnsubscribeApiIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID created;

    @AfterEach
    void removeTheAccount() {
        if (created != null) {
            jdbc.update("DELETE FROM users WHERE id = ?", created);
            created = null;
        }
    }

    @Test
    void thetokenFromTheEmailStopsTheEmail() throws Exception {
        UUID token = tokenOfAfreshAccount();

        mvc.perform(post("/api/v1/email/unsubscribe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isNoContent());

        assertThat(wantsEmail()).isFalse();
    }

    /** A second click, or a gateway that opened the page twice, says the same. */
    @Test
    void asecondClickIsTheSameAnswer() throws Exception {
        UUID token = tokenOfAfreshAccount();

        for (int click = 0; click < 2; click++) {
            mvc.perform(post("/api/v1/email/unsubscribe")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"token\":\"" + token + "\"}"))
                    .andExpect(status().isNoContent());
        }

        assertThat(wantsEmail()).isFalse();
    }

    /**
     * <strong>An unknown token answers exactly as a real one does.</strong>
     * Bolum 40.4's reasoning in a smaller place: a different status would turn
     * this into an oracle for which tokens are live.
     */
    @Test
    void atokenThatBelongsToNobodyIsAnsweredTheSameWay() throws Exception {
        UUID token = tokenOfAfreshAccount();

        mvc.perform(post("/api/v1/email/unsubscribe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isNoContent());

        assertThat(wantsEmail())
                .as("and it changed nobody's mind")
                .isTrue();
        assertThat(token).isNotNull();
    }

    /** Every account gets one, and no two get the same. */
    @Test
    void thecolumnIsFilledInForRowsThatAlreadyExisted() {
        Integer missing = jdbc.queryForObject(
                "SELECT count(*) FROM users WHERE unsubscribe_token IS NULL", Integer.class);
        Integer distinct = jdbc.queryForObject(
                "SELECT count(DISTINCT unsubscribe_token) FROM users", Integer.class);
        Integer rows = jdbc.queryForObject("SELECT count(*) FROM users", Integer.class);

        assertThat(missing).isZero();
        assertThat(distinct).isEqualTo(rows);
    }

    private UUID tokenOfAfreshAccount() {
        created = jdbc.queryForObject(
                "INSERT INTO users (email) VALUES (?) RETURNING id",
                UUID.class, UUID.randomUUID() + "@example.com");
        return jdbc.queryForObject("SELECT unsubscribe_token FROM users WHERE id = ?",
                UUID.class, created);
    }

    private boolean wantsEmail() {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT lifecycle_emails FROM users WHERE id = ?", Boolean.class, created));
    }
}
