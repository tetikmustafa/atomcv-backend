package com.mustafatetik.atomcv.shared.text;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Bolum 31.5's skill dictionary, and the properties it has to keep.
 *
 * <p>The cases about the dictionary's <em>shape</em> matter more than any
 * individual alias. A dictionary that needed applying twice, or one whose
 * canonical values were themselves aliases, would give different answers
 * depending on where it was called from — and it is called from both sides of
 * every comparison.
 */
class SkillNamesTest {

    private final Locale original = Locale.getDefault();

    @AfterEach
    void restoreLocale() {
        Locale.setDefault(original);
    }

    // -- spelling ----------------------------------------------------------

    @Test
    void caseAndSpacingAreMechanicalAndComeFirst() {
        assertThat(SkillNames.canonical("  Machine Learning ")).isEqualTo("machine-learning");
        assertThat(SkillNames.canonical("PostgreSQL")).isEqualTo("postgres");
        assertThat(SkillNames.canonical("micro_service")).isEqualTo("micro-service");
    }

    @Test
    void punctuationALeavesBehindIsTrimmed() {
        assertThat(SkillNames.canonical("- Go")).isEqualTo("go");
        assertThat(SkillNames.canonical("Kubernetes,")).isEqualTo("kubernetes");
    }

    /**
     * A leading dot is part of the name and survives. Trimming it would turn
     * {@code .NET} into {@code net}, which is a different technology and a
     * different alias entry.
     */
    @Test
    void aLeadingDotIsPartOfTheNameAndSurvives() {
        assertThat(SkillNames.canonical(".NET")).isEqualTo("dotnet");
    }

    @Test
    void aDotInsideANameSurvivesLongEnoughToBeLookedUp() {
        assertThat(SkillNames.canonical("Node.js")).isEqualTo("node");
        assertThat(SkillNames.canonical("React.js")).isEqualTo("react");
    }

    @Test
    void nothingAtAllIsEmptyRatherThanNull() {
        assertThat(SkillNames.canonical(null)).isEmpty();
        assertThat(SkillNames.canonical("   ")).isEmpty();
    }

    // -- absolute rule 7 ---------------------------------------------------

    /**
     * The one that has bitten this project before. A Turkish default locale
     * lowercases {@code SQL} to {@code sqı}, and an atom normalised on a
     * Turkish machine would never match a posting normalised on the runner.
     */
    @Test
    void aTurkishDefaultLocaleDoesNotChangeTheAnswer() {
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));

        assertThat(SkillNames.canonical("SQL")).isEqualTo("sql");
        assertThat(SkillNames.canonical("SQLITE")).isEqualTo("sqlite");
        assertThat(SkillNames.canonical("Istanbul")).isEqualTo("istanbul");
    }

    // -- the dictionary's shape --------------------------------------------

    /**
     * One hop, always. If a canonical value were itself an alias, the answer
     * would depend on how many times the function had been applied — and it is
     * applied once on the CV's side and once on the posting's.
     */
    @Test
    void noCanonicalNameIsItselfAnAlias() {
        Map<String, String> aliases = SkillNames.aliases();

        assertThat(aliases.values()).allSatisfy(canonical ->
                assertThat(aliases).as("%s is both a canonical name and an alias", canonical)
                        .doesNotContainKey(canonical));
    }

    /** Applying it twice has to be applying it once. */
    @Test
    void canonicalisationIsIdempotent() {
        for (String alias : SkillNames.aliases().keySet()) {
            String once = SkillNames.canonical(alias);

            assertThat(SkillNames.canonical(once)).as("%s", alias).isEqualTo(once);
        }
    }

    /**
     * Every alias key has to be reachable.
     *
     * <p>The lookup happens <em>after</em> the spelling rules, so a key
     * written "React JS" or "React_JS" would sit in the map forever without
     * ever matching: by the time the lookup runs, the input has become
     * "react-js". The condition is therefore on the keys themselves — already
     * lowercase, already hyphenated, no leading or trailing punctuation.
     */
    @Test
    void everyAliasKeyIsInTheFormTheSpellingRulesProduce() {
        for (String alias : SkillNames.aliases().keySet()) {
            assertThat(alias).as("%s is unreachable: nothing normalises to it", alias)
                    .isEqualTo(alias.toLowerCase(Locale.ROOT))
                    .doesNotContainAnyWhitespaces()
                    .doesNotContain("_")
                    .doesNotEndWith("-").doesNotEndWith(".").doesNotEndWith(",")
                    .doesNotStartWith("-").doesNotStartWith(",");
        }
    }

    @Test
    void theDictionaryIsActuallyLoaded() {
        assertThat(SkillNames.aliases()).hasSizeGreaterThan(20);
        assertThat(SkillNames.aliases()).containsEntry("k8s", "kubernetes");
    }
}
