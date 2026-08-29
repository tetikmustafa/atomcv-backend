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
                                "switch_to_manual_form", "complete_profile",
                                // Adim 2.3, handoff B-037: Bolum 18.1 offers
                                // three ways past a preflight refusal and the
                                // vocabulary named only two of them.
                                "continue_anyway", "retry",
                        "replace_profile", "keep_existing_profile")));
    }

    /**
     * F-019: an integer enum has to be published as integers.
     *
     * <p>The column allows two values and springdoc was publishing them as the
     * strings {@code "1"} and {@code "-1"}, beside a {@code format: int32} on
     * the same schema and a {@code number} coming back in the response.
     * openapi-typescript believes the enum, so the generated request type said
     * the field was a string literal while the client — correctly — sent a
     * number, and the frontend was carrying an {@code Omit} to paper over the
     * difference.
     *
     * <p>Asserted as numbers rather than as anything: {@code value(1)} against
     * a JSON string fails, which is the whole point of the case.
     */
    @Test
    void anIntegerEnumIsPublishedAsIntegersAndNotAsStrings() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.components.schemas.FeedbackRequest"
                        + ".properties.rating.type").value("integer"))
                .andExpect(jsonPath("$.components.schemas.FeedbackRequest"
                        + ".properties.rating.enum")
                        .value(Matchers.containsInAnyOrder(1, -1)));
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
    void theHeadReplacementPublishesItsTwoRequiredFields() throws Exception {
        // A PUT replaces, so the fields that cannot be cleared have to be the
        // fields a client cannot leave out — and the generated type is where
        // the frontend learns that (F-004, handoff B-035). `contains` is
        // exact: a third required field here would be a contract change
        // nobody announced.
        mvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.components.schemas.ProfileUpdate.required")
                        .value(Matchers.containsInAnyOrder("sourceLanguage", "enabledLanguages")));
    }

    @Test
    void theGenerationRequestPublishesNoSecondWayToAskForGeneralMode() throws Exception {
        // F-009. `isGeneralMode()` is a derived method, but an isX() on a
        // record is a getter to Jackson and to springdoc, so the schema grew a
        // `generalMode` boolean — a second way to ask for the thing the
        // absence of `jobDescription` already decides. The frontend found it
        // and asked what it was for.
        //
        // Same defect as Stage 2's RichContent, on the other side of the wire:
        // a getter-shaped method on a record is a field somebody will find.
        mvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.components.schemas.GenerationRequest.properties.generalMode")
                        .doesNotExist())
                .andExpect(jsonPath("$.components.schemas.GenerationRequest.properties")
                        .value(Matchers.aMapWithSize(5)))
                .andExpect(jsonPath("$.components.schemas.GenerationRequest"
                        + ".properties.jobDescription").exists())
                .andExpect(jsonPath("$.components.schemas.GenerationRequest"
                        + ".properties.acknowledgePreflight").exists())
                .andExpect(jsonPath("$.components.schemas.GenerationRequest"
                        + ".properties.maxPages").exists())
                .andExpect(jsonPath("$.components.schemas.GenerationRequest"
                        + ".properties.language").exists())
                // Bolum 34, opt-in. The count above is what makes this a
                // guard: a fifth property nobody meant to publish fails here.
                .andExpect(jsonPath("$.components.schemas.GenerationRequest"
                        + ".properties.coverLetter").exists())
                .andExpect(jsonPath("$.components.schemas.GenerationRequest"
                        + ".properties.wantsCoverLetter").doesNotExist());
    }

    @Test
    void theFitReportIsPublishedAsCountsAndAClosedVocabulary() throws Exception {
        // F-008. The frontend cannot draw a result screen from a report that
        // is not in the schema, and Bolum 23.3 forbids a percentage by name —
        // so what is published has to be the counts and a closed set of
        // levels, not a number the client is tempted to render as a bar.
        mvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/api/v1/generations/{generationId}'].get")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/generations/{generationId}'].get"
                        + ".responses.200.content['application/json'].schema.$ref")
                        .value("#/components/schemas/GenerationResponse"))
                .andExpect(jsonPath("$.components.schemas.GenerationResponse"
                        + ".properties.fitReport.$ref").value("#/components/schemas/FitReport"))
                .andExpect(jsonPath("$.components.schemas.GenerationResponse"
                        + ".properties.pageCount").exists())
                .andExpect(jsonPath("$.components.schemas.FitReport.properties.requiredCovered")
                        .exists())
                .andExpect(jsonPath("$.components.schemas.FitReport.properties.missingRequired")
                        .exists())
                .andExpect(jsonPath("$.components.schemas.FitReport.properties.level.enum")
                        .value(Matchers.containsInAnyOrder(
                                "WEAK", "MODERATE", "GOOD", "STRONG")))
                // Absolute rule 4: no path publishes the posting back.
                .andExpect(jsonPath("$.components.schemas.GenerationResponse"
                        + ".properties.jobDescription").doesNotExist());
    }

    @Test
    void aGenerationSaysWhichLanguageItCameOutIn() throws Exception {
        // F-013. The posting's language is followed only as far as the
        // profile's own wordings reach, so the two can differ and the screen
        // has to be able to say so. Two facts rather than a flag: the sentence
        // the user reads names both languages.
        mvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.components.schemas.GenerationResponse"
                        + ".properties.contentLanguage.type").value("string"))
                .andExpect(jsonPath("$.components.schemas.GenerationResponse"
                        + ".properties.postingLanguage.type").value("string"));
    }

    @Test
    void aPolledJobCanReachTheSameResultTheStreamCarried() throws Exception {
        // F-008: polling is the documented fallback for a stream that closed
        // without a terminal event, and it was reaching the generation but not
        // the page count beside it.
        mvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.components.schemas.JobStatusResponse"
                        + ".properties.pageCount").exists())
                .andExpect(jsonPath("$.components.schemas.JobStatusResponse"
                        + ".properties.generationId").exists());
    }

    @Test
    void usageSeparatesWhatWasSpentFromWhatWasAttempted() throws Exception {
        // F-012. The counter counts attempts, so a single `used` ran past
        // `limit` and the screen read "26 of 20". Two fields, because there
        // are two facts — clamping alone would have been the server
        // misreporting itself.
        mvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.components.schemas.Usage.properties.used").exists())
                .andExpect(jsonPath("$.components.schemas.Usage.properties.attempted").exists())
                .andExpect(jsonPath("$.components.schemas.Usage.properties.remaining").exists())
                .andExpect(jsonPath("$.components.schemas.Usage.properties.limit").exists())
                .andExpect(jsonPath("$.components.schemas.Usage.properties.resetsAt").exists());
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
