package com.mustafatetik.atomcv.llm.prompts;

/**
 * A prompt split where the instructions end and the data begins (Bolum 43.1).
 *
 * <p>Two reasons for the split rather than sending one interpolated document.
 * Bolum 27.4 discounts a cached prefix, and a prefix is only constant if the
 * user's text is not in it. And Bolum 43's second layer is that the model is
 * told the fenced region is data — a distinction that reads better when the
 * fence is literally the boundary between the two messages.
 *
 * <p>The boundary is the opening tag <strong>on its own line</strong>. The tag
 * name also appears inside the instructions above it, telling the model what
 * the fence means, which is why the newlines are part of the marker rather
 * than decoration.
 *
 * <p>In {@code llm.prompts} because two phases fence their input this way and
 * a second copy of a security boundary is the kind of duplication that goes
 * stale on one side. The tag is the caller's, so a prompt says
 * {@code <job_description>} or {@code <cv_text>} and means the same thing by
 * it.
 */
public record FencedPrompt(String system, String userTemplate, String placeholder) {

    /**
     * @param prompt the loaded prompt, whose text must contain both the fence
     *               and the placeholder
     * @param tag    the fence's tag name, e.g. {@code cv_text}. The placeholder
     *               is derived from it, so a prompt cannot declare one and use
     *               the other.
     */
    public static FencedPrompt of(Prompt prompt, String tag) {
        String open = "\n<" + tag + ">\n";
        String placeholder = "{{" + tag + "}}";
        String text = prompt.text().replace("\r\n", "\n");
        int fence = text.indexOf(open);
        if (fence < 0) {
            throw new IllegalStateException(
                    "Prompt " + prompt.ref() + " has no <" + tag + "> fence on its own line");
        }
        if (!text.contains(placeholder)) {
            throw new IllegalStateException(
                    "Prompt " + prompt.ref() + " has no " + placeholder + " placeholder");
        }
        return new FencedPrompt(
                text.substring(0, fence).strip(), text.substring(fence + 1), placeholder);
    }

    /**
     * The user's text, inside the fence the prompt declared.
     *
     * <p><strong>No escaping.</strong> Text containing the closing tag would
     * close the fence early, and the answer to that is not a quoting scheme
     * the model may or may not respect. It is that the answer has to fit a
     * schema (Bolum 43.1's first and strongest layer) and pass an audit of its
     * field lengths (the third) — which is what catches an answer that started
     * following the text instead of describing it.
     */
    public String userPromptFor(String data) {
        return userTemplate.replace(placeholder, data);
    }
}
