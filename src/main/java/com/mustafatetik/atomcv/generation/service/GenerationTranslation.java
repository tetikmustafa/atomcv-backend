package com.mustafatetik.atomcv.generation.service;

import com.mustafatetik.atomcv.generation.scoring.ScoredAtom;
import com.mustafatetik.atomcv.profile.domain.Atom;
import com.mustafatetik.atomcv.profile.domain.AtomVariant;
import com.mustafatetik.atomcv.profile.domain.ProfileTree;
import com.mustafatetik.atomcv.profile.service.VariantTranslationService;
import com.mustafatetik.atomcv.shared.error.Result;
import com.mustafatetik.atomcv.shared.security.ProfileRef;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Bolum 21.8's second step, at the moment a generation needs it.
 *
 * <p><strong>What this closes.</strong> Bolum 21.8 lists four steps and only
 * the first was written: use the wording that exists. Without the second —
 * translate the one that does not — {@code cvLanguage: auto} could only follow
 * the posting when the profile already had a wording for every atom in its
 * language, which F-013 records as the reason a Turkish profile applying to an
 * English posting got a Turkish CV. {@link ProfileTree#canBeWrittenIn} was the
 * guard, and the guard existed because the phase did not.
 *
 * <p><strong>It runs between Faz B and Faz C, and the order is Bolum 32.3's
 * rule.</strong> That section is explicit: choose the language, then optimise
 * against <em>that</em> language's costs. Translating after selection would be
 * the mistake it names — a page packed with English heights and printed in
 * Turkish. Faz B can run first because scoring is language-independent: the
 * vector comes from the English wording and skills are canonical.
 *
 * <p><strong>Bounded, because this is the one place a generation could make a
 * call per atom.</strong> Only the highest-scoring {@value #TRANSLATION_BUDGET}
 * are offered, which is more than a page holds — anything below that is
 * competing for room it will not get, and a wording nobody prints is a wording
 * nobody should pay for. The rest keep the fallback Bolum 20.4 already
 * describes.
 *
 * <p><strong>All or nothing, and that is F-013's rule kept rather than
 * dropped.</strong> A document is written in one language. If any atom in the
 * budget cannot be translated — the provider is down, or the audit refused an
 * answer that dropped a number — the caller is told, and it writes the CV in
 * the language the profile already holds. Half a translation is the mixed CV
 * this was built to stop.
 */
@Service
public class GenerationTranslation {

    private static final Logger log = LoggerFactory.getLogger(GenerationTranslation.class);

    /**
     * More atoms than a two-page CV prints, and far fewer than a profile
     * holds. Faz D's ceiling is eight for the same kind of reason.
     */
    static final int TRANSLATION_BUDGET = 60;

    private final VariantTranslationService translations;

    GenerationTranslation(VariantTranslationService translations) {
        this.translations = translations;
    }

    /**
     * Fills in the wordings a document in {@code language} would need.
     *
     * @param ranked what Faz B produced, best first
     * @return whether the profile can now be written in that language. False
     *         means the caller falls back to the profile's own language --
     *         every atom of it, not the ones that failed
     */
    public boolean ensureWordingsIn(ProfileRef profile, ProfileTree tree, String language,
            List<ScoredAtom> ranked, String bucketKey, UUID userId) {

        Map<UUID, ProfileTree.AtomNode> missing = missingWordings(tree, language, ranked);
        if (missing.isEmpty()) {
            // Bolum 21.8's first step answered everything: cost zero, which is
            // what the profile editor's investment buys.
            return true;
        }

        // A virtual thread per task, as Faz D does and for the reason
        // Bolum 21.5.1 gives: one failure is one failure, not a reason to
        // cancel the answers already paid for.
        int translated = 0;
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var running = new LinkedHashMap<UUID, Future<Boolean>>();
            for (Map.Entry<UUID, ProfileTree.AtomNode> entry : missing.entrySet()) {
                running.put(entry.getKey(), workers.submit(
                        translateOne(profile, entry.getValue(), language, bucketKey, userId)));
            }
            for (Future<Boolean> answer : running.values()) {
                translated += Boolean.TRUE.equals(get(answer)) ? 1 : 0;
            }
        }

        boolean complete = translated == missing.size();
        // Counts, never content (absolute rule 4). Which language and how many
        // is the whole of what a diagnosis needs here.
        log.info("Translated {} of {} wordings into {}; document language {}",
                translated, missing.size(), language,
                complete ? "follows the posting" : "falls back to the profile");
        return complete;
    }

    private Callable<Boolean> translateOne(ProfileRef profile, ProfileTree.AtomNode node,
            String language, String bucketKey, UUID userId) {

        return () -> {
            Atom atom = node.atom();
            AtomVariant source = node.primaryVariant().orElse(null);
            if (source == null || source.getPlainText().isBlank()) {
                // Nothing to translate from is not a failure of translation,
                // and it is not something a retry fixes. Selection already
                // counts an atom with no wording.
                return false;
            }
            return translations.translateInto(
                    profile, atom, source, language, bucketKey, userId) instanceof Result.Ok;
        };
    }

    /**
     * The atoms a document in this language would print and does not have a
     * wording for, best first and capped.
     *
     * <p>Inactive atoms and atoms with no wording at all are skipped for the
     * reason {@code canBeWrittenIn} skips them: neither is a candidate, and
     * neither should decide what language a CV comes out in.
     */
    private static Map<UUID, ProfileTree.AtomNode> missingWordings(
            ProfileTree tree, String language, List<ScoredAtom> ranked) {

        Map<UUID, ProfileTree.AtomNode> byId = new LinkedHashMap<>();
        tree.sections().forEach(section -> {
            section.atoms().forEach(node -> byId.put(node.atom().getId(), node));
            section.entries().forEach(entry ->
                    entry.atoms().forEach(node -> byId.put(node.atom().getId(), node)));
        });

        var missing = new LinkedHashMap<UUID, ProfileTree.AtomNode>();
        for (ScoredAtom scored : ranked) {
            if (missing.size() == TRANSLATION_BUDGET) {
                break;
            }
            ProfileTree.AtomNode node = byId.get(scored.atomId());
            if (node == null || !node.atom().isActive() || node.variants().isEmpty()) {
                continue;
            }
            if (node.variantIn(language).isEmpty()) {
                missing.put(scored.atomId(), node);
            }
        }
        return missing;
    }

    private static Boolean get(Future<Boolean> answer) {
        try {
            return answer.get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException failed) {
            // The class and nothing else: a translation carries the person's
            // own sentence, and so does the exception a provider wraps.
            log.warn("A translation failed: {}",
                    failed.getCause() == null
                            ? failed.getClass().getSimpleName()
                            : failed.getCause().getClass().getSimpleName());
            return false;
        }
    }
}
