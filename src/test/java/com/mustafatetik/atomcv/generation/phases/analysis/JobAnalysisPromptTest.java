package com.mustafatetik.atomcv.generation.phases.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mustafatetik.atomcv.llm.prompts.PromptProperties;
import com.mustafatetik.atomcv.llm.prompts.PromptRegistry;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The shipped {@code job_analysis} prompt and the schema beside it
 * (Bolum 18.2, 18.3).
 *
 * <p>These hold two things that are otherwise only true by inspection: that
 * the schema and the record it is parsed into describe the same document, and
 * that the prompt still carries Bolum 18.3's injection defence.
 */
class JobAnalysisPromptTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final PromptRegistry REGISTRY = new PromptRegistry(
            new PromptProperties(Map.of("job_analysis", "v1"), Map.of()), JSON);

    @Test
    void theShippedPromptLoadsAtTheConfiguredVersion() {
        var prompt = REGISTRY.load("job_analysis");

        assertThat(prompt.ref()).isEqualTo("job_analysis:v1");
        assertThat(prompt.schema().name()).isEqualTo("job_analysis");
    }

    /**
     * Bolum 18.3, and Bolum 43's whole approach: the posting is fenced and the
     * model is told the fence contains data. It is not a proof against
     * injection — the structural defence is that the answer must fit a schema
     * and pass Bolum 18.4's gate — but removing the instruction would remove
     * the cheapest layer without anyone noticing.
     */
    @Test
    void thePromptFencesThePostingAndSaysItIsDataNotInstructions() {
        var text = REGISTRY.load("job_analysis").text();

        assertThat(text).contains("<job_description>", "</job_description>");
        assertThat(text).contains("{{job_description}}");
        assertThat(text).containsIgnoringCase("DATA to be analysed");
        assertThat(text).containsIgnoringCase("not instructions");
    }

    /**
     * Bolum 18.2's rule that makes Faz B possible at all: an atom's embedding
     * comes from its English variant, so a similarity against a Turkish
     * sentence would measure the languages rather than the match.
     */
    @Test
    void thePromptDemandsEnglishForTheFieldsThatAreCompared() {
        var text = REGISTRY.load("job_analysis").text();

        assertThat(text).contains("responsibilities", "keywords", "canonical");
        assertThat(text).containsIgnoringCase("ENGLISH");
    }

    /** The placeholder is what the phase substitutes; renaming it breaks silently. */
    @Test
    void theSchemaAndTheRecordDescribeTheSameDocument() throws Exception {
        var schema = REGISTRY.load("job_analysis").schema().node();

        // Every property the schema declares is a component of the record, or
        // the model would answer a field nothing reads.
        var declared = schema.get("properties").properties().stream()
                .map(Map.Entry::getKey).toList();
        var components = java.util.Arrays.stream(JobAnalysis.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName).toList();

        assertThat(declared).containsExactlyInAnyOrderElementsOf(components);
    }

    @Test
    void anAnswerInTheSchemasShapeParsesIntoTheRecord() throws Exception {
        var analysis = JSON.readValue("""
                {
                  "role": {"title":"Senior Backend Engineer","seniority":"senior",
                           "domain":"fintech","employmentType":"full_time","workMode":"remote"},
                  "company": {"name":"Acme Payments","sizeHint":"scaleup"},
                  "requiredSkills": [{"name":"Go","canonical":"go","importance":"critical"}],
                  "preferredSkills": [{"name":"Terraform","canonical":"terraform"}],
                  "responsibilities": ["design and scale payment processing systems"],
                  "keywords": ["distributed systems","high availability"],
                  "experienceYears": {"min":5,"max":null},
                  "languageRequirements": ["en"],
                  "companyTone": "technical, results-oriented",
                  "jdLanguage": "tr",
                  "confidence": 0.94,
                  "extractionNotes": []
                }
                """, JobAnalysis.class);

        assertThat(analysis.role().seniority()).isEqualTo(JobAnalysis.Seniority.SENIOR);
        assertThat(analysis.role().employmentType())
                .isEqualTo(JobAnalysis.EmploymentType.FULL_TIME);
        assertThat(analysis.company().sizeHint()).isEqualTo(JobAnalysis.SizeHint.SCALEUP);
        assertThat(analysis.requiredSkills()).singleElement().satisfies(skill -> {
            assertThat(skill.canonical()).isEqualTo("go");
            assertThat(skill.importance()).isEqualTo(JobAnalysis.Importance.CRITICAL);
        });
        // Bolum 18.2: a floor with no ceiling is normal, not missing data.
        assertThat(analysis.experienceYears().max()).isNull();
        assertThat(analysis.confidence()).isEqualTo(0.94);
    }

    /**
     * With {@code strict: true} the provider enforces the vocabulary
     * (Bolum 27.2), so this only happens in the weaker json_object mode.
     * Failing the whole parse there would buy a full retry for a field
     * Bolum 18.4's gate never reads.
     */
    @Test
    void aValueOutsideAClosedVocabularyReadsAsAbsentRatherThanFailing() throws Exception {
        var analysis = JSON.readValue("""
                {"role":{"title":"Staff Engineer","seniority":"staff","domain":"",
                         "employmentType":null,"workMode":"anywhere"},
                 "company":{"name":"Acme","sizeHint":"huge"},
                 "requiredSkills":[],"preferredSkills":[],"responsibilities":[],
                 "keywords":[],"experienceYears":{"min":null,"max":null},
                 "languageRequirements":[],"companyTone":"","jdLanguage":"en",
                 "confidence":0.6,"extractionNotes":[]}
                """, JobAnalysis.class);

        assertThat(analysis.role().title()).isEqualTo("Staff Engineer");
        assertThat(analysis.role().seniority()).isNull();
        assertThat(analysis.role().workMode()).isNull();
        assertThat(analysis.company().sizeHint()).isNull();
    }

    /** A model that adds a field is not a failure (Bolum 18.4 judges the rest). */
    @Test
    void anUnexpectedFieldIsIgnoredRatherThanRefused() throws Exception {
        var analysis = JSON.readValue("""
                {"role":{"title":"Engineer"},"company":{"name":"Acme"},
                 "requiredSkills":[],"preferredSkills":[],"responsibilities":[],
                 "keywords":[],"languageRequirements":[],"companyTone":"",
                 "jdLanguage":"en","confidence":0.8,"extractionNotes":[],
                 "salaryGuess":"a lot"}
                """, JobAnalysis.class);

        assertThat(analysis.role().title()).isEqualTo("Engineer");
    }

    /** Absent lists are empty, so nothing downstream has to null-check them. */
    @Test
    void absentListsReadAsEmpty() throws Exception {
        var analysis = JSON.readValue("{\"confidence\":0.8}", JobAnalysis.class);

        assertThat(analysis.requiredSkills()).isEmpty();
        assertThat(analysis.keywords()).isEmpty();
        assertThat(analysis.allSkills()).isEmpty();
        assertThat(analysis.jdLanguage()).isEmpty();
    }
}
