package com.mustafatetik.atomcv.ingestion.structuring;

import com.mustafatetik.atomcv.profile.domain.SectionKind;
import java.util.List;
import java.util.Optional;

/**
 * Bolum 43.1's third layer, on the answer that came back.
 *
 * <p>A CV is the one document in this system an attacker controls end to end,
 * so it is the likeliest carrier of an injected instruction. The first two
 * layers do most of the work — the answer must fit a schema, and the prompt
 * fences the text as data — but neither stops a model from writing an
 * instruction it followed into a field. Length is what catches that: an
 * injected payload is long and a CV field is short.
 *
 * <p>The thresholds are deliberately generous. This is a tripwire and not a
 * quality filter; a rambling bullet is the user's business and refusing it
 * would be the system deciding how they should write. Only a value that could
 * not be a CV field at all trips it.
 *
 * <p><strong>What a refusal is called matters.</strong> Bolum 43.2: a special
 * message tells an attacker their attempt was noticed. The caller answers with
 * the same {@code EXTRACTION_EMPTY} it would give a page of nonsense, and the
 * fact that this is what refused reaches the operator through a log line
 * instead.
 */
final class StructuringAudit {

    /**
     * A bullet is a sentence, and a long one is still a sentence. Six hundred
     * characters is about four lines of a rendered CV — past anything that
     * survives Bolum 20's page budget, and far past anything a person writes
     * in one bullet.
     *
     * <p>Measured against every recorded extraction: the longest bullet any
     * real CV has produced here is 219 characters, so this keeps most of a
     * threefold margin.
     */
    private static final int MAX_ATOM_TEXT = 600;

    /**
     * An About paragraph is not a bullet, and the reasoning above does not
     * transfer to it.
     *
     * <p>It cost a silent failure to find out. A four-page CV extracted
     * cleanly into 84 atoms and every one of them was thrown away because a
     * professional summary came to 607 characters — seven over a ceiling
     * argued from what "a person writes in one bullet". The three next longest
     * fields in that profile were 541, 519 and 506, and all four were About:
     * this was a systematic near-miss on real writing, not a freak.
     *
     * <p>Fifteen hundred is about 250 words, or twenty rendered lines. It
     * keeps the original test — a value that could not be a CV field at all —
     * for a field whose shape is a paragraph rather than a line.
     */
    private static final int MAX_ABOUT_TEXT = 1500;

    /** Bolum 43.1 uses sixty for a skill name against a posting; a CV is no different. */
    private static final int MAX_SKILL = 60;

    /** A job title, an employer, a degree. Long ones exist; this long ones do not. */
    private static final int MAX_LABEL = 200;

    private StructuringAudit() {
    }

    /**
     * @return which kind of field was abnormal, for a log line, or empty when
     *         nothing was. Never the value itself — absolute rule 4 holds
     *         especially here, where the suspect string is the thing an
     *         attacker wants echoed somewhere
     */
    static Optional<String> abnormalField(ExtractedProfile profile) {
        for (var section : profile.sections()) {
            if (tooLong(section.title(), MAX_LABEL)) {
                return Optional.of("section.title");
            }
            for (var entry : section.entries()) {
                Optional<String> onEntry = abnormalEntryField(entry, section.kind());
                if (onEntry.isPresent()) {
                    return onEntry;
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<String> abnormalEntryField(
            ExtractedProfile.ExtractedEntry entry, SectionKind kind) {
        if (tooLong(entry.title(), MAX_LABEL)) {
            return Optional.of("entry.title");
        }
        if (tooLong(entry.organization(), MAX_LABEL)) {
            return Optional.of("entry.organization");
        }
        int maxText = maxTextFor(kind);
        for (var atom : entry.atoms()) {
            if (tooLong(atom.textSource(), maxText)) {
                return Optional.of("atom.textSource");
            }
            if (tooLong(atom.textEn(), maxText)) {
                return Optional.of("atom.textEn");
            }
            if (anyTooLong(atom.skills(), MAX_SKILL)) {
                return Optional.of("atom.skills");
            }
            if (anyTooLong(atom.properNouns(), MAX_LABEL)) {
                return Optional.of("atom.properNouns");
            }
        }
        return Optional.empty();
    }

    /** A paragraph gets a paragraph's ceiling; everything else is a line. */
    private static int maxTextFor(SectionKind kind) {
        return kind == SectionKind.ABOUT ? MAX_ABOUT_TEXT : MAX_ATOM_TEXT;
    }

    private static boolean tooLong(String value, int limit) {
        return value != null && value.length() > limit;
    }

    private static boolean anyTooLong(List<String> values, int limit) {
        return values.stream().anyMatch(value -> tooLong(value, limit));
    }
}
