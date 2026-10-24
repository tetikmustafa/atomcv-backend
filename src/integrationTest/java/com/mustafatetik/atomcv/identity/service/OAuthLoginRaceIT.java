package com.mustafatetik.atomcv.identity.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.AbstractIntegrationTest;
import com.mustafatetik.atomcv.identity.domain.OAuthAccount;
import com.mustafatetik.atomcv.identity.domain.OAuthProvider;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Several first sign-ins for one new account, at once.
 *
 * <p>The recovery for this race was a catch inside one transaction, and it
 * could not work: the insert was flushed at commit, after the catch, and a
 * violation caught inside would have left the transaction rollback-only anyway.
 * Every racer here has to come back signed in to the one account the database
 * let exist.
 */
class OAuthLoginRaceIT extends AbstractIntegrationTest {

    @Autowired
    private OAuthLoginService logins;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM users");
    }

    @Test
    void racingFirstSignInsAllLandInOneAccount() throws Exception {
        var account = new OAuthAccount(OAuthProvider.GITHUB, "gh-" + UUID.randomUUID(),
                "racer@example.com", true, "Racer");
        int racers = 6;
        var start = new CountDownLatch(1);

        var users = new HashSet<UUID>();
        try (var pool = Executors.newFixedThreadPool(racers)) {
            var answers = new ArrayList<Future<SignInOutcome>>();
            for (int i = 0; i < racers; i++) {
                answers.add(pool.submit(() -> {
                    start.await();
                    return logins.signIn(account);
                }));
            }
            start.countDown();
            for (var answer : answers) {
                var outcome = answer.get(30, TimeUnit.SECONDS);
                assertThat(outcome).isInstanceOf(SignInOutcome.SignedIn.class);
                users.add(((SignInOutcome.SignedIn) outcome).session().userId());
            }
        }

        assertThat(users).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE email = ?",
                Integer.class, "racer@example.com")).isEqualTo(1);
    }
}
