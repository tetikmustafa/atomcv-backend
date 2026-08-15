package com.mustafatetik.atomcv.api;

import com.mustafatetik.atomcv.AbstractIntegrationTest;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The published schema is the contract between the two repositories: the
 * frontend generates its types from it. Six of the sixteen contract gaps close
 * by themselves once it exists — but only if it carries the closed vocabularies
 * and the headers, not just happy-path payloads (EK D.6).
 */
@AutoConfigureMockMvc
class OpenApiSchemaIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void theDocumentIsPublishedAndDescribesTheProfileEndpoint() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("AtomCV API"))
                .andExpect(jsonPath("$.paths['/api/v1/profile'].get").exists());
    }

    @Test
    void aSingleResourceResponseDocumentsItsEtagHeader() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath(
                        "$.paths['/api/v1/profile'].get.responses.200.headers.ETag").exists());
    }

    @Test
    void aResponseNamesTheMediaTypeItActuallyProduces() throws Exception {
        // Left unset, springdoc publishes */* and a generated client has to
        // guess what to put in Accept.
        mvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/api/v1/profile'].get.responses.200"
                        + ".content['application/json'].schema.$ref").value("#/components/schemas/Profile"))
                .andExpect(jsonPath("$.paths['/api/v1/profile'].get.responses.500"
                        + ".content['application/problem+json']").exists());
    }

    @Test
    void theResolutionVocabularyIsPublishedAsAnEnum() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.components.schemas.Resolution.properties.action.enum")
                        .value(Matchers.containsInAnyOrder(
                                "increase_page_limit", "review_pins", "keep_top_pinned", "sign_up",
                                "paste_full_posting", "continue_as_general_cv",
                                "switch_to_manual_form", "complete_profile", "retry")));
    }

    @Test
    void theErrorCodeVocabularyIsPublishedAsAnEnum() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.components.schemas.ApiError.properties.code.enum")
                        .value(Matchers.hasItems(
                                "CONFLICTING_PREFERENCES", "INSUFFICIENT_PROFILE", "QUOTA_EXCEEDED",
                                "ANONYMOUS_SESSION_EXPIRED", "VERSION_CONFLICT",
                                "VALIDATION_FAILED", "RESOURCE_NOT_FOUND", "INTERNAL_ERROR")));
    }

    @Test
    void theErrorBodyItselfIsPublished() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.components.schemas.ApiError.properties.params").exists())
                .andExpect(jsonPath("$.components.schemas.ApiError.properties.resolutions").exists())
                .andExpect(jsonPath("$.components.schemas.ApiError.properties.type").exists());
    }

    @Test
    void aClearableFieldIsPublishedAsAValueNotAWrapper() throws Exception {
        // The tri-state lives in Java, not in the contract: on the wire the
        // field is a string that may be null. A generated client must not end
        // up with a { present, value } object to fill in.
        mvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.components.schemas.EntryPatch.properties.organization.type")
                        .value("string"))
                .andExpect(jsonPath("$.components.schemas.EntryPatch.properties.endDate.format")
                        .value("date"));
    }

    @Test
    void theProfileShapeIsPublishedWithoutAnIdentifier() throws Exception {
        // Bolum 35.1: no path carries a profile id, so the schema does not
        // suggest one exists to be sent back.
        mvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.components.schemas.Profile.properties.completeness").exists())
                .andExpect(jsonPath("$.components.schemas.Profile.properties.contact").exists())
                .andExpect(jsonPath("$.components.schemas.Profile.properties.id").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.Profile.properties.version").doesNotExist());
    }
}
