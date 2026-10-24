package com.mustafatetik.atomcv.generation.coverletter;

import com.mustafatetik.atomcv.llm.gateway.LlmResponse;
import com.mustafatetik.atomcv.llm.gateway.ModelTier;
import com.mustafatetik.atomcv.llm.gateway.ProviderChain;
import com.mustafatetik.atomcv.llm.gateway.StructuredRequest;
import com.mustafatetik.atomcv.llm.prompts.FencedPrompt;
import com.mustafatetik.atomcv.llm.prompts.Prompt;
import com.mustafatetik.atomcv.llm.prompts.PromptRegistry;
import com.mustafatetik.atomcv.shared.error.PipelineError;
import com.mustafatetik.atomcv.shared.error.Result;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * One covering letter, written from the page.
 *
 * <p><strong>This one can fail, and that is the difference.</strong> Every
 * other phase that asks a model for words has the person's own text to fall
 * back on: a refused rewrite prints the original bullet, a refused summary
 * prints the About they wrote. A letter has no original. So the two outcomes
 * are an honest letter and a reported failure — never a letter that claims
 * something the page does not support, which is the one thing this whole
 * subsystem exists to prevent.
 *
 * <p>Two attempts, like the bullet rewrite, and for the same reason: these
 * failures are largely a model being sloppy once. A third would be paying
 * twice for the same answer.
 */
@Service
public class CoverLetterService {

    public static final String PROMPT_ID = "cover_letter";

    /** The fence: the CV's sentences, the lists, the person's note. */
    private static final String FENCE_TAG = "letter";

    /** Ours, not the CV's, so they are substituted into the instructions. */
    private static final String LANGUAGE = "{{language}}";
    private static final String TONE = "{{tone}}";
    private static final String STYLE = "{{style}}";

    private static final Duration TIMEOUT = Duration.ofSeconds(45);

    static final int ATTEMPTS = 2;

    private static final Logger log = LoggerFactory.getLogger(CoverLetterService.class);

    private final PromptRegistry prompts;
    private final ProviderChain providers;

    CoverLetterService(PromptRegistry prompts, ProviderChain providers) {
        this.prompts = prompts;
        this.providers = providers;
    }

    /** Which version of the prompt this bucket is on. */
    public String promptVersionFor(String bucketKey) {
        return prompts.selectVersion(PROMPT_ID, bucketKey);
    }

    /**
     * @param style which of the three buttons was pressed
     * @param bucketKey who this is for, so a prompt experiment keeps showing
     *  one person one variant
     */
    public Result<CoverLetterDraft> write(
            CoverLetterInput input, CoverLetterStyle style, String bucketKey,
            java.util.UUID userId,
            java.util.UUID jobId) {

        List<CoverLetterIssue> lastIssues = List.of();
        int lastWords = 0;
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            Attempt made = attempt(input, style, correctionFor(lastIssues, lastWords),
                    bucketKey, userId, jobId);
            if (made.outage()) {
                return Result.err(new PipelineError.AllProvidersUnavailable(List.of()));
            }
            if (made.issues().isEmpty()) {
                return Result.ok(made.draft());
            }
            lastIssues = made.issues();
            lastWords = CoverLetterValidator.wordCount(made.draft().plainText());
        }
        // Kinds, never the letter (absolute rule 4). These do travel to the
        // user, because there is nothing to print instead and "it did not
        // work" without a reason is not an answer a person can act on.
        //
        // The word count goes with them: a length refusal alone could not
        // say which side of the band a model fell off, and that decides
        // whether the prompt or the model is the thing to change. A count is
        // not content.
        log.info("A cover letter was refused twice: {} ({} words, band {}-{})", lastIssues,
                lastWords, CoverLetterValidator.MIN_WORDS, CoverLetterValidator.MAX_WORDS);
        return Result.err(new PipelineError.CoverLetterRejected(names(lastIssues)));
    }

    /** One attempt: what came back, and what was wrong with it. */
    private record Attempt(CoverLetterDraft draft, List<CoverLetterIssue> issues, boolean outage) {

        static Attempt unavailable() {
            return new Attempt(null, List.of(), true);
        }
    }

    /**
     * What the second attempt is told about the first, when the first missed
     * the length band.
     *
     * <p>Asking twice with the same words bought the same letter twice: on
     * 2026-10-24 the fallback model wrote 119 words both times against a floor
     * of 120, and the person who asked for a letter got none. The count is
     * ours rather than the user's, so it goes above the fence with the style
     * instruction, and only the length is reported back — the other refusals
     * are about what the letter claims, and naming them would invite the model
     * to argue with the check rather than write a different letter.
     */
    static String correctionFor(List<CoverLetterIssue> issues, int words) {
        if (!issues.contains(CoverLetterIssue.LENGTH_OUT_OF_RANGE)) {
            return "";
        }
        return words < CoverLetterValidator.MIN_WORDS
                ? "The previous draft was " + words + " words, under the "
                        + CoverLetterValidator.MIN_WORDS + "-word minimum. Write each piece of "
                        + "evidence out in full, two or three sentences each."
                : "The previous draft was " + words + " words, over the "
                        + CoverLetterValidator.MAX_WORDS + "-word maximum. Keep the shape and "
                        + "say each piece of evidence once.";
    }

    private Attempt attempt(
            CoverLetterInput input, CoverLetterStyle style, String correction, String bucketKey,
            java.util.UUID userId,
            java.util.UUID jobId) {

        Prompt prompt = prompts.load(PROMPT_ID, prompts.selectVersion(PROMPT_ID, bucketKey));
        FencedPrompt fenced = FencedPrompt.of(prompt, FENCE_TAG);

        String system = fenced.system()
                .replace(LANGUAGE, input.language())
                .replace(TONE, input.tone())
                .replace(STYLE, (instructionFor(style) + " " + correction).strip());

        var answer = providers.call(new StructuredRequest<>(
                PROMPT_ID, prompt.version(), system,
                fenced.userPromptFor(fencedData(input)),
                prompt.schema(), CoverLetterDraft.class, ModelTier.MID, TIMEOUT, userId, jobId));

        if (answer instanceof Result.Err<LlmResponse<CoverLetterDraft>>) {
            return Attempt.unavailable();
        }
        CoverLetterDraft draft = answer.orElseThrow().data();
        return new Attempt(draft, CoverLetterValidator.validate(input, draft), false);
    }

    /**
     * The regeneration buttons, as a sentence added to the instructions. They
     * are ours rather than the user's, so they belong above the fence.
     */
    private static String instructionFor(CoverLetterStyle style) {
        return switch (style) {
            case DEFAULT -> "";
            // Named no number since F-026: it used to say "nearer 250 than
            // 400", and the letters this model writes are nearer 130 — so the
            // button was pushing the one draft that might have passed further
            // under the floor.
            case SHORTER -> "Keep it at the short end of the range: "
                    + "as brief as it can be without dropping a section.";
            case MORE_FORMAL -> "More formal than the tone above would usually be: "
                    + "no contractions, no first-name address, plain and reserved.";
        };
    }

    /**
     * Everything the model is given, inside the fence. All of it is the
     * person's content or the posting's — the line is where the data starts,
     * not which field looks structured.
     */
    private static String fencedData(CoverLetterInput input) {
        var data = new StringBuilder()
                .append("applicant: ").append(input.applicantName())
                .append("\nrole: ").append(input.roleTitle())
                .append("\ncompany: ").append(input.companyName())
                .append("\nskills: ").append(String.join(", ", displayed(input)))
                .append("\nmetrics: ").append(String.join(", ", input.allowedMetrics()))
                .append("\nyearsWorking: ").append(input.profileYears())
                .append("\ncompanyNote: ").append(input.companyNote())
                .append("\nevidence:");
        for (CoverLetterInput.Evidence evidence : input.evidence()) {
            data.append("\n- ").append(evidence.text());
        }
        return data.toString();
    }

    /**
     * The skills as the person writes them, where their own sentences say.
     *
     * <p>{@code allowedSkills} holds the matching keys — {@code spring-boot},
     * {@code restful-apis} — and the model copied them into the letter as they
     * came: "utilizing java, spring-boot, and spring-core", measured on
     * 2026-10-24. The sentences on the page carry the real spelling ("Spring
     * Boot", "RESTful APIs"), so that spelling is looked up there and the key
     * is the fallback, with its hyphens read as spaces. The validator compares
     * canonical forms on both sides, so the spelling changes nothing it checks.
     */
    static List<String> displayed(CoverLetterInput input) {
        StringBuilder sentences = new StringBuilder(input.companyNote());
        input.evidence().forEach(evidence -> sentences.append('\n').append(evidence.text()));
        List<String> shown = new ArrayList<>(input.allowedSkills().size());
        for (String skill : input.allowedSkills()) {
            List<String> parts = new ArrayList<>();
            for (String part : skill.split("-")) {
                parts.add(Pattern.quote(part));
            }
            Matcher written = Pattern.compile(
                    "(?<![\\p{L}\\p{N}])" + String.join("[\\s._-]*", parts) + "(?![\\p{L}\\p{N}])",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(sentences);
            shown.add(written.find() ? written.group() : skill.replace('-', ' '));
        }
        return shown;
    }

    private static List<String> names(List<CoverLetterIssue> issues) {
        List<String> names = new ArrayList<>(issues.size());
        issues.forEach(issue -> names.add(issue.name().toLowerCase(Locale.ROOT)));
        return names;
    }
}
