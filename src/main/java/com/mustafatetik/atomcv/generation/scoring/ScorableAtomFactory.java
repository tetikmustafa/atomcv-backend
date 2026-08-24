package com.mustafatetik.atomcv.generation.scoring;

import com.mustafatetik.atomcv.profile.domain.Atom;
import com.mustafatetik.atomcv.profile.domain.AtomVariant;
import com.mustafatetik.atomcv.profile.domain.Entry;
import com.mustafatetik.atomcv.profile.domain.ProfileTree;
import com.mustafatetik.atomcv.profile.domain.ProfileTree.AtomNode;
import com.mustafatetik.atomcv.profile.domain.ProfileTree.EntryNode;
import com.mustafatetik.atomcv.profile.domain.ProfileTree.SectionNode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * A profile tree, as the values Faz B scores (Bolum 19.2).
 *
 * <p>{@link RelevanceScorer} is a pure function of {@link ScorableAtom}s and
 * this is the piece that knows where those come from — the tree for the atoms
 * and their wordings, a separate query for the tags. Keeping the two apart is
 * what lets the scorer be tested without a database and this be tested without
 * a scorer.
 *
 * <p>Pure and deterministic: no clock, no session, and the tree's own order is
 * preserved (Bolum 19.6). It also computes each atom's general-mode score
 * (Bolum 19.4) — the thing that decides between two atoms whose relevance is
 * indistinguishable — because that needs a date and the scorer must not have
 * one.
 */
public final class ScorableAtomFactory {

    /**
     * Bolum 28.2: every vector is computed from the English variant, so every
     * text compared against one is read from the English variant too.
     */
    private static final String EMBEDDING_LANGUAGE = "en";

    private ScorableAtomFactory() {
    }

    /**
     * @param tagsByAtom what {@code TagRepository.labelsByAtom} returned; an
     *                   atom missing from the map has no tags
     * @return one entry per <em>active</em> atom. Bolum 19.5 does not score
     *         inactive ones — selection rejects them as {@code INACTIVE}
     *         before a score would matter, and scoring them would spend a
     *         cosine per atom the user has switched off.
     */
    public static List<ScorableAtom> from(
            ProfileTree tree, Map<UUID, Set<String>> tagsByAtom, LocalDate today) {

        List<ScorableAtom> atoms = new ArrayList<>();
        for (SectionNode section : tree.sections()) {
            for (EntryNode entry : section.entries()) {
                for (AtomNode node : entry.atoms()) {
                    add(atoms, node, entry.entry(), tagsByAtom, today);
                }
            }
            for (AtomNode node : section.atoms()) {
                add(atoms, node, null, tagsByAtom, today);
            }
        }
        return List.copyOf(atoms);
    }

    private static void add(
            List<ScorableAtom> atoms, AtomNode node, Entry entry,
            Map<UUID, Set<String>> tagsByAtom, LocalDate today) {

        Atom atom = node.atom();
        if (!atom.isActive()) {
            return;
        }
        atoms.add(new ScorableAtom(
                atom.getId(),
                atom.getEmbedding(),
                tagsByAtom.getOrDefault(atom.getId(), Set.of()),
                canonicalSkills(atom),
                contentTokens(node, entry),
                atom.getImportance(),
                // Bolum 19.4, computed here because it needs a date and the
                // scorer must not have one. Today is a parameter for the same
                // reason it is in GeneralModeScorer: a factory that read the
                // clock could not be tested for the same-input-same-output
                // property Bolum 51.2 requires.
                GeneralModeScorer.score(atom, entry, today)));
    }

    /**
     * The words the keyword component matches the posting's phrases against:
     * what the atom says, and the heading it is printed under.
     *
     * <p>The heading counts because it is on the page with the bullet and it
     * is what makes one job's bullets more relevant than another's — every
     * atom under "Senior Backend Engineer" carries those words, and a posting
     * that asks for a backend engineer should rank them above a bullet from an
     * unrelated role that happens to use the same verb.
     *
     * <p>Read from the English wording when there is one, because the
     * posting's keywords are always English (Bolum 18.2) and a Turkish
     * sentence would match none of them. When there is not, the primary
     * wording is used anyway rather than nothing: technology names and proper
     * nouns are spelled the same in both languages, and they are most of what
     * a keyword list contains.
     */
    private static List<String> contentTokens(AtomNode node, Entry entry) {
        Optional<AtomVariant> wording = node.variantIn(EMBEDDING_LANGUAGE)
                .or(node::primaryVariant);

        Set<String> tokens = new LinkedHashSet<>();
        wording.map(AtomVariant::getPlainText)
                .ifPresent(text -> tokens.addAll(RelevanceScorer.tokensOf(text)));
        if (entry != null) {
            tokens.addAll(RelevanceScorer.tokensOf(entry.getTitle()));
            tokens.addAll(RelevanceScorer.tokensOf(entry.getOrganization()));
        }
        return List.copyOf(tokens);
    }

    /**
     * Absolute rule 7. The column holds whatever normalization produced, and a
     * Turkish locale here would turn "SQL" into "sqı" — the atom would stop
     * matching the posting and the score would drop for every user on a
     * Turkish server, with nothing looking broken.
     *
     * <p>Through {@link RelevanceScorer#canonicalSkill} rather than inline, so
     * the atom side and the posting side cannot drift: Faz F reports on the
     * same keys Faz B matched on.
     */
    public static Set<String> canonicalSkills(Atom atom) {
        return atom.getSkills().stream()
                .map(RelevanceScorer::canonicalSkill)
                .filter(skill -> !skill.isBlank())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
