package com.mustafatetik.atomcv.llm.prompts;

import com.mustafatetik.atomcv.llm.gateway.JsonSchema;

/**
 * One prompt at one version, with the schema its answer must match
 * (Bolum 53.1).
 *
 * <p>The text and the schema travel together because they change together:
 * Bolum 53.1 keeps prompts in files rather than in the database for exactly
 * that reason — a prompt and the code that parses its answer drift apart the
 * moment they are stored apart.
 *
 * @param id      the directory name under {@code resources/prompts}
 * @param version e.g. {@code v1}; the file is {@code {version}.md}
 * @param text    the prompt body, never logged (absolute rule 4 applies to
 *                what gets interpolated into it)
 * @param schema  the shape the answer must take; one per prompt id, shared by
 *                every version of it
 */
public record Prompt(String id, String version, String text, JsonSchema schema) {

    public Prompt {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("A prompt needs an id");
        }
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("Prompt " + id + " needs a version");
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Prompt " + id + ":" + version + " is empty");
        }
        if (schema == null) {
            throw new IllegalArgumentException("Prompt " + id + " needs a schema");
        }
    }

    /** How the pair is named in telemetry and in fixture keys. */
    public String ref() {
        return id + ":" + version;
    }
}
