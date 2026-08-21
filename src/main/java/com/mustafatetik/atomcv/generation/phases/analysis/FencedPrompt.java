package com.mustafatetik.atomcv.generation.phases.analysis;

import com.mustafatetik.atomcv.llm.prompts.Prompt;

/**
 * Bolum 18.3's prompt, split where the instructions end and the data begins.
 *
 * <p>Two reasons for the split rather than sending one interpolated document.
 * Bolum 27.4 discounts a cached prefix, and a prefix is only constant if the
 * posting is not in it. And Bolum 43's defence is that the model is told the
 * fenced region is data — a distinction that reads better when the fence is
 * literally the boundary between the two messages.
 *
 * <p>The boundary is the {@code <job_description>} tag <em>on its own line</em>.
 * The tag name also appears inside the instructions above it, which is why the
 * newlines are part of the marker and not decoration.
 */
record FencedPrompt(String system, String userTemplate) {

    private static final String FENCE_OPEN = "\n<job_description>\n";
    private static final String PLACEHOLDER = "{{jobDescription}}";

    static FencedPrompt of(Prompt prompt) {
        var text = prompt.text().replace("\r\n", "\n");
        int fence = text.indexOf(FENCE_OPEN);
        if (fence < 0) {
            throw new IllegalStateException(
                    "Prompt " + prompt.ref() + " has no <job_description> fence on its own line");
        }
        if (!text.contains(PLACEHOLDER)) {
            throw new IllegalStateException(
                    "Prompt " + prompt.ref() + " has no " + PLACEHOLDER + " placeholder");
        }
        return new FencedPrompt(text.substring(0, fence).strip(), text.substring(fence + 1));
    }

    /**
     * The posting, inside the fence the prompt declared.
     *
     * <p>No escaping of the posting's own text: a posting containing
     * {@code </job_description>} would close the fence early, and the answer to
     * that is not a quoting scheme the model may or may not respect. It is that
     * the answer has to fit a schema and pass Bolum 18.4's gate — including the
     * field-length audit, which is what catches an answer that started
     * following the posting instead of describing it.
     */
    String userPromptFor(String jobDescription) {
        return userTemplate.replace(PLACEHOLDER, jobDescription);
    }
}
