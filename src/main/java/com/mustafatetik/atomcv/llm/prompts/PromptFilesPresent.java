package com.mustafatetik.atomcv.llm.prompts;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Reads every configured prompt at startup, so a missing file is a boot
 * failure rather than one user's failed generation.
 *
 * <p>{@link PromptRegistry#validateConfiguredPrompts()} was written for this
 * and nothing called it. The registry's own comment explains why it is not
 * done in its constructor — the fake provider's tests configure prompt ids no
 * file backs, and a registry that validated itself could not be built for them
 * — and that reasoning is right; what was missing is the caller. A separate
 * bean is the caller, and it is absent from exactly those tests, because they
 * build the registry by hand instead of asking Spring for one.
 *
 * <p><strong>{@code @PostConstruct} and not {@code ApplicationReadyEvent}.</strong>
 * By the time the application is ready the port is open, and an instance that
 * has already served a request is not one that failed to start. A prompt file
 * that did not survive a build is a deployment that should not come up.
 */
@Component
class PromptFilesPresent {

    private static final Logger log = LoggerFactory.getLogger(PromptFilesPresent.class);

    private final PromptRegistry registry;
    private final PromptProperties properties;

    PromptFilesPresent(PromptRegistry registry, PromptProperties properties) {
        this.registry = registry;
        this.properties = properties;
    }

    @PostConstruct
    void check() {
        registry.validateConfiguredPrompts();
        // The ids and versions, which are configuration — never a prompt's
        // text, which carries the instructions and, once interpolated, the
        // user's own document (absolute rule 4).
        log.info("Prompts loaded: {}", properties.active());
    }
}
