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
        //
        // "may be null" is half the point and used to be missing: this asserted
        // the type alone, so the schema published a plain string for a field
        // whose documented purpose is to accept null, and a generated client
        // rejected the body that clears an end date (EK D.6.8). In OpenAPI 3.1
        // null is a type, so it belongs in the list.
        mvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.components.schemas.EntryPatch.properties.organization.type")
                        .value(Matchers.contains("string", "null")))
                .andExpect(jsonPath("$.components.schemas.EntryPatch.properties.endDate.type")
                        .value(Matchers.contains("string", "null")))
                .andExpect(jsonPath("$.components.schemas.EntryPatch.properties.endDate.format")
                        .value("date"))
                // Saying the type is not enough on its own: dropping the
                // implementation to get the null in left springdoc free to
                // publish the wrapper as a component and point the field at it,
                // which is the same defect wearing the other hat.
                .andExpect(jsonPath("$.components.schemas.EntryPatch.properties.organization.$ref")
                        .doesNotExist())
                .andExpect(jsonPath("$.components.schemas.JsonNullableString").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.JsonNullableLocalDate").doesNotExist());
    }

    @Test
    void aWordingCanBeReturnedToTheNeutralRegister() throws Exception {
        // Null clears the tone, so the schema has to accept it — and the three
        // registers have to survive the nullability, or the client loses the
        // closed vocabulary it generates from.
        mvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.components.schemas.VariantPatch.properties.tone.type")
                        .value(Matchers.contains("string", "null")))
                .andExpect(jsonPath("$.components.schemas.VariantPatch.properties.tone.enum")
                        .value(Matchers.containsInAnyOrder("formal", "casual", "technical")));
    }

    @Test
    void everyErrorBodyPromisesACodeAndAStatus() throws Exception {
        // EK D.9 · 12: every error carries a code, INTERNAL_ERROR included, so
        // that the client's error path always has something to translate.
        // Published as optional it is a guarantee nobody can rely on.
        mvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.components.schemas.ApiError.required")
                        .value(Matchers.containsInAnyOrder("code", "status")));
    }

    @Test
    void everyCollectionReadAndPartialWriteDocumentsWhatItAnswers() throws Exception {
        // Ten operations declared only their failures. Between them that is
        // every collection read and every partial write — the two things the
        // profile editor does constantly — so a generated client had no
        // response type for any of them (EK D.6.8).
        for (String path : new String[] {
                "/api/v1/profile/sections", "/api/v1/profile/entries", "/api/v1/profile/atoms"}) {
            mvc.perform(get("/v3/api-docs"))
                    .andExpect(jsonPath("$.paths['" + path + "'].get.responses.200"
                            + ".content['application/json'].schema.items.$ref").exists())
                    .andExpect(jsonPath("$.paths['" + path + "/reorder'].post.responses.200"
                            + ".content['application/json'].schema.items.$ref").exists());
        }
        for (String path : new String[] {
                "/api/v1/profile/sections/{id}", "/api/v1/profile/entries/{id}",
                "/api/v1/profile/atoms/{id}",
                "/api/v1/profile/atoms/{id}/variants/{variantId}"}) {
            mvc.perform(get("/v3/api-docs"))
                    .andExpect(jsonPath("$.paths['" + path + "'].patch.responses.200"
                            + ".content['application/json'].schema.$ref").exists());
        }
    }

    @Test
    void everySingleResourceWriteDocumentsTheEtagItSends() throws Exception {
        // A patch answers with both the ETag and the body's version, so
        // autosave never needs a read between saves. Undeclared, that is a
        // guarantee that could be removed without a test going red.
        for (String path : new String[] {
                "/api/v1/profile/sections/{id}", "/api/v1/profile/entries/{id}",
                "/api/v1/profile/atoms/{id}",
                "/api/v1/profile/atoms/{id}/variants/{variantId}"}) {
            mvc.perform(get("/v3/api-docs"))
                    .andExpect(jsonPath("$.paths['" + path + "'].patch.responses.200.headers.ETag")
                            .exists());
        }
        for (String path : new String[] {
                "/api/v1/profile/sections", "/api/v1/profile/entries", "/api/v1/profile/atoms",
                "/api/v1/profile/atoms/{id}/variants"}) {
            mvc.perform(get("/v3/api-docs"))
                    .andExpect(jsonPath("$.paths['" + path + "'].post.responses.201.headers.ETag")
                            .exists());
        }
    }

    @Test
    void aCollectionReadCarriesNoEtagBecauseItCoversManyRows() throws Exception {
        // Deliberate: one tag cannot stand for a list. The per-item `version`
        // is what the editor uses instead (EK D.6.2).
        mvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/api/v1/profile/sections'].get.responses.200.headers")
                        .doesNotExist());
    }

    @Test
    void theExportEndpointDeclaresBothOfTheThingsItReturns() throws Exception {
        // ?format=markdown answers text/markdown. Declaring only the JSON form
        // makes a client parse markdown as JSON and throw on the first
        // character.
        mvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/api/v1/profile/export'].get.responses.200"
                        + ".content['application/json']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/profile/export'].get.responses.200"
                        + ".content['text/markdown']").exists());
    }

    @Test
    void operationIdsSayWhatTheOperationIs() throws Exception {
        // Generators name things from these, and positional ids gave the
        // frontend operations["list_2"] for "read the atoms".
        mvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/api/v1/profile/atoms'].get.operationId")
                        .value("listAtoms"))
                .andExpect(jsonPath("$.paths['/api/v1/profile/entries'].post.operationId")
                        .value("createEntry"))
                .andExpect(jsonPath("$.paths['/api/v1/profile/sections/{id}'].patch.operationId")
                        .value("patchSection"));
    }

    @Test
    void aWordingCanBePromotedWithoutResendingItsText() throws Exception {
        // VariantWrite still requires content on POST — an atom with no
        // wording is a fact nobody can read. VariantPatch requires nothing.
        mvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.components.schemas.VariantWrite.required")
                        .value(Matchers.contains("content")))
                .andExpect(jsonPath("$.components.schemas.VariantPatch.required")
                        .doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/profile/atoms/{id}/variants/{variantId}']"
                        + ".patch.requestBody.content['application/json'].schema.$ref")
                        .value("#/components/schemas/VariantPatch"));
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
