package com.mustafatetik.atomcv.ingestion.normalization;

import com.mustafatetik.atomcv.ingestion.structuring.ExtractedProfile;
import com.mustafatetik.atomcv.shared.text.ClaimVocabulary;
import com.mustafatetik.atomcv.shared.wire.ExtractionWarningCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Whether extraction extracted, or wrote (P3, Bolum 31.4).
 *
 * <p>P3 was enforced in one place — Faz D, where the model is asked to
 * <em>rewrite</em> a bullet — and nowhere at all where it is asked to read one.
 * That left the whole ingestion path outside the guarantee, and it did not stay
 * hypothetical. A real CV said:
 *
 * <pre>utilizing \textbf{SQL} queries to model complex business reporting logic</pre>
 *
 * <p>and the atom written from it said "utilizing advanced <b>SQL Server</b>
 * queries, optimizing analytic data layers" — a different and more specific
 * product, with {@code mssql} in the skills array behind it. The same upload
 * produced a summary offering "message queues (Redis, Kafka)" from a document
 * that never mentions Kafka. Both were printed on a CV, and neither was ever
 * checked, because by the time Faz D runs they are the person's own atoms.
 *
 * <p>The question asked here is the one {@link ClaimVocabulary#introducedNames}
 * asks in Faz D, with the document as the only source: does this bullet name
 * something the file does not? A dictionary is not consulted, so a technology
 * nobody has heard of is caught on the same terms as a famous one.
 *
 * <p><strong>A warning, not a refusal.</strong> The atom is otherwise the
 * person's own content, Bolum 31.6's review screen exists to correct precisely
 * this, and throwing away an import over one invented word is a failure this
 * codebase has already had.
 */
final class ExtractionFidelity {

    /**
     * Only the source-language text is compared. {@code textEn} is a
     * translation by construction, so holding it against a Turkish document
     * would report every word in it.
     */
    private ExtractionFidelity() {
    }

    /**
     * @param sourceText what the file said, or {@code null} when the caller has
     *                   no document to check against — an entry typed into the
     *                   editor has no source and invents nothing
     * @return one warning naming this entry, or empty
     */
    static Optional<ExtractedProfile.ExtractionWarning> check(
            ExtractedProfile.ExtractedEntry entry, String sourceText) {

        if (sourceText == null || sourceText.isBlank()) {
            return Optional.empty();
        }
        List<String> source = List.of(sourceText);
        List<String> invented = new ArrayList<>();

        for (var atom : entry.atoms()) {
            Set<String> names = ClaimVocabulary.introducedNames(atom.textSource(), source);
            for (String name : names) {
                if (!invented.contains(name)) {
                    invented.add(name);
                }
            }
        }
        if (invented.isEmpty()) {
            return Optional.empty();
        }
        // The invented word itself, which is the one thing the person needs in
        // order to act on this. It is not their content -- it is precisely what
        // is not (absolute rule 4 protects what the document says, and this is
        // what it does not).
        return Optional.of(new ExtractedProfile.ExtractionWarning(
                ExtractionWarningCode.UNSUPPORTED_BY_SOURCE,
                "not in the uploaded document: " + String.join(", ", invented),
                ""));
    }
}
