package com.mustafatetik.atomcv;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * {@link UserDetailsServiceAutoConfiguration} is excluded because nothing here
 * has a password: sign-in is a magic link or OAuth, and form login and HTTP
 * Basic are both disabled in {@code SecurityConfig}. Left in, it builds an
 * in-memory user called {@code user} and prints its generated password to the
 * log on every start — in production, into the shipped log stream — for an
 * account no endpoint accepts.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class AtomCvApplication {

    public static void main(String[] args) {
        SpringApplication.run(AtomCvApplication.class, args);
    }
}
