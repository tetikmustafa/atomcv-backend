package com.mustafatetik.atomcv.generation.phases.analysis;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * What Faz A extracts from a posting (Bolum 18.2).
 *
 * <p>Three fields are <strong>always English</strong> whatever language the
 * posting was written in: {@code responsibilities}, {@code keywords} and every
 * skill's {@code canonical}. An atom's embedding is computed from its English
 * variant, and a similarity between a Turkish sentence and an English one
 * measures the languages rather than the match. {@code jdLanguage} is kept
 * anyway, because the cover letter should be offered in the posting's language.
 *
 * <p>Unknown fields are ignored rather than refused: a model that adds one is
 * not a failure, and the plausibility gate in Bolum 18.4 judges the fields
 * that matter.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record JobAnalysis(
        Role role,
        Company company,
        List<Skill> requiredSkills,
        List<Skill> preferredSkills,
        List<String> responsibilities,
        List<String> keywords,
        ExperienceYears experienceYears,
        List<String> languageRequirements,
        String companyTone,
        String jdLanguage,
        double confidence,
        List<String> extractionNotes) {

    public JobAnalysis {
        requiredSkills = copyOf(requiredSkills);
        preferredSkills = copyOf(preferredSkills);
        responsibilities = copyOf(responsibilities);
        keywords = copyOf(keywords);
        languageRequirements = copyOf(languageRequirements);
        extractionNotes = copyOf(extractionNotes);
        companyTone = companyTone == null ? "" : companyTone;
        jdLanguage = jdLanguage == null ? "" : jdLanguage;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Role(
            String title, Seniority seniority, String domain,
            EmploymentType employmentType, WorkMode workMode) {

        public Role {
            title = title == null ? "" : title;
            domain = domain == null ? "" : domain;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Company(String name, SizeHint sizeHint) {

        public Company {
            name = name == null ? "" : name;
        }
    }

    /**
     * @param name      as the posting wrote it, in the posting's language
     * @param canonical the matching key, always English and lowercase — it is
     *                  compared against atom tags, and absolute rule 7 is why
     *                  the lowercasing happens with an explicit locale
     * @param importance absent on a preferred skill
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Skill(String name, String canonical, Importance importance) {

        public Skill {
            name = name == null ? "" : name;
            canonical = canonical == null ? "" : canonical;
        }
    }

    /** @param max null when the posting names a floor and no ceiling */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ExperienceYears(Integer min, Integer max) {
    }

    // ── The closed vocabularies of Bolum 18.2 ────────────────────────────
    //
    // Lowercase on the wire, and an unknown value reads as null rather than
    // failing the parse. With `strict: true` the provider enforces the enum
    // (Bolum 27.2), so an unknown one only reaches here in the weaker
    // json_object mode — and turning a model that answered "staff" into a
    // total failure would buy a whole retry for a field Bolum 18.4's gate does
    // not read.

    public enum Seniority {
        JUNIOR, MID, SENIOR, LEAD, PRINCIPAL;

        @JsonValue
        public String wireValue() {
            return lower(name());
        }

        @JsonCreator
        public static Seniority fromWireValue(String value) {
            return parse(Seniority.class, value);
        }
    }

    public enum EmploymentType {
        FULL_TIME, PART_TIME, CONTRACT, INTERNSHIP;

        @JsonValue
        public String wireValue() {
            return lower(name());
        }

        @JsonCreator
        public static EmploymentType fromWireValue(String value) {
            return parse(EmploymentType.class, value);
        }
    }

    public enum WorkMode {
        ONSITE, HYBRID, REMOTE;

        @JsonValue
        public String wireValue() {
            return lower(name());
        }

        @JsonCreator
        public static WorkMode fromWireValue(String value) {
            return parse(WorkMode.class, value);
        }
    }

    public enum SizeHint {
        STARTUP, SCALEUP, ENTERPRISE;

        @JsonValue
        public String wireValue() {
            return lower(name());
        }

        @JsonCreator
        public static SizeHint fromWireValue(String value) {
            return parse(SizeHint.class, value);
        }
    }

    /** How much a required skill matters. Bolum 19 weighs the score by it. */
    public enum Importance {
        CRITICAL, HIGH, MEDIUM;

        @JsonValue
        public String wireValue() {
            return lower(name());
        }

        @JsonCreator
        public static Importance fromWireValue(String value) {
            return parse(Importance.class, value);
        }
    }

    /** Every skill the posting named, required first. */
    public Stream<Skill> allSkills() {
        return Stream.concat(requiredSkills.stream(), preferredSkills.stream());
    }

    private static <T> List<T> copyOf(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    /** Locale.ROOT: absolute rule 7. A Turkish locale writes "mıd" here. */
    private static String lower(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException outsideTheVocabulary) {
            return null;
        }
    }
}
