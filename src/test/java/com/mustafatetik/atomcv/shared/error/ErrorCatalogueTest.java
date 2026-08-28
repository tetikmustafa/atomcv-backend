package com.mustafatetik.atomcv.shared.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The catalogue is a contract with another repository: the frontend writes an
 * ICU message per code and cannot write one without the parameter names and
 * their types. These tests hold the catalogue to that.
 */
class ErrorCatalogueTest {

    @Test
    void everyPipelineErrorFromTheDocumentHasACode() {
        // Bolum 25.2 names ten; a code disappearing here means a pipeline
        // failure that reaches the user with no message at all.
        assertThat(names()).contains(
                "INSUFFICIENT_PROFILE", "UNPARSEABLE_JOB_DESCRIPTION", "CONFLICTING_PREFERENCES",
                "FEATURE_REQUIRES_ACCOUNT", "QUOTA_EXCEEDED", "ALL_PROVIDERS_UNAVAILABLE",
                "COMPILATION_FAILED", "PAGE_LIMIT_EXCEEDED", "REWRITE_VALIDATION_FAILED",
                "EMBEDDING_UNAVAILABLE");
    }

    @Test
    void theDocumentedStatusMappingHolds() {
        // Bolum 35.5, verbatim.
        assertThat(ErrorCode.INSUFFICIENT_PROFILE.httpStatus()).isEqualTo(422);
        assertThat(ErrorCode.UNPARSEABLE_JOB_DESCRIPTION.httpStatus()).isEqualTo(422);
        assertThat(ErrorCode.CONFLICTING_PREFERENCES.httpStatus()).isEqualTo(409);
        assertThat(ErrorCode.FEATURE_REQUIRES_ACCOUNT.httpStatus()).isEqualTo(403);
        assertThat(ErrorCode.QUOTA_EXCEEDED.httpStatus()).isEqualTo(429);
        assertThat(ErrorCode.ALL_PROVIDERS_UNAVAILABLE.httpStatus()).isEqualTo(503);
        assertThat(ErrorCode.COMPILATION_FAILED.httpStatus()).isEqualTo(502);
        assertThat(ErrorCode.EMBEDDING_UNAVAILABLE.httpStatus()).isEqualTo(503);
        assertThat(ErrorCode.PAGE_LIMIT_EXCEEDED.httpStatus()).isEqualTo(422);
        assertThat(ErrorCode.REWRITE_VALIDATION_FAILED.httpStatus()).isEqualTo(500);
    }

    @Test
    void everyCodeCarriesAFailingStatus() {
        for (ErrorCode code : ErrorCode.values()) {
            assertThat(code.httpStatus())
                    .as("%s", code)
                    .isBetween(400, 599);
        }
    }

    @Test
    void parameterNamesAreUniqueWithinACode() {
        for (ErrorCode code : ErrorCode.values()) {
            assertThat(code.params()).extracting(ErrorCode.Param::name)
                    .as("%s", code)
                    .doesNotHaveDuplicates();
        }
    }

    @Test
    void parameterNamesAreCamelCaseSoIcuPlaceholdersCanUseThemVerbatim() {
        for (ErrorCode code : ErrorCode.values()) {
            for (ErrorCode.Param param : code.params()) {
                assertThat(param.name())
                        .as("%s.%s", code, param.name())
                        .matches("[a-z][a-zA-Z0-9]*");
            }
        }
    }

    @Test
    void codesAreScreamingSnakeCaseOnTheWire() {
        for (String name : names()) {
            assertThat(name).matches("[A-Z][A-Z0-9_]*");
        }
    }

    // ─── the parameter contract is enforced, not merely documented ───

    @Test
    void theDocumentedConflictExampleIsAccepted() {
        // Bolum 35.4's example body, built through the catalogue.
        var error = UserFacingError.with(ErrorCode.CONFLICTING_PREFERENCES)
                .param("pinnedPages", 2.3)
                .param("maxPages", 1)
                .resolution(Resolution.of(ResolutionAction.INCREASE_PAGE_LIMIT, "maxPages", 3))
                .resolution(ResolutionAction.REVIEW_PINS)
                .resolution(Resolution.of(ResolutionAction.KEEP_TOP_PINNED, "keep", 3))
                .build();

        assertThat(error.httpStatus()).isEqualTo(409);
        assertThat(error.params()).containsExactly(
                Map.entry("pinnedPages", 2.3), Map.entry("maxPages", 1));
        assertThat(error.resolutions()).extracting(Resolution::action).containsExactly(
                ResolutionAction.INCREASE_PAGE_LIMIT,
                ResolutionAction.REVIEW_PINS,
                ResolutionAction.KEEP_TOP_PINNED);
    }

    @Test
    void aMissingParameterIsRefused() {
        assertThatThrownBy(() -> UserFacingError.with(ErrorCode.CONFLICTING_PREFERENCES)
                .param("pinnedPages", 2.3)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxPages");
    }

    @Test
    void anUndeclaredParameterIsRefused() {
        assertThatThrownBy(() -> UserFacingError.with(ErrorCode.EMBEDDING_UNAVAILABLE)
                .param("provider", "bge-m3")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not declare");
    }

    @Test
    void aWronglyTypedParameterIsRefused() {
        assertThatThrownBy(() -> UserFacingError.with(ErrorCode.PAGE_LIMIT_EXCEEDED)
                .param("actual", "two")
                .param("limit", 1)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("INTEGER");
    }

    @Test
    void aCodeWithNoParametersNeedsNone() {
        var error = UserFacingError.of(ErrorCode.ANONYMOUS_SESSION_EXPIRED,
                Resolution.of(ResolutionAction.SIGN_UP));

        assertThat(error.params()).isEmpty();
        assertThat(error.httpStatus()).isEqualTo(401);
        assertThat(error.resolutions()).containsExactly(Resolution.of(ResolutionAction.SIGN_UP));
    }

    @Test
    void everyDeclaredTypeAcceptsWhatItPromises() {
        assertThat(ParamType.STRING.accepts("classic")).isTrue();
        assertThat(ParamType.INTEGER.accepts(1)).isTrue();
        assertThat(ParamType.INTEGER.accepts(1.5)).isFalse();
        assertThat(ParamType.NUMBER.accepts(1)).isTrue();
        assertThat(ParamType.NUMBER.accepts(2.3)).isTrue();
        assertThat(ParamType.BOOLEAN.accepts(true)).isTrue();
        assertThat(ParamType.TIMESTAMP.accepts(Instant.now())).isTrue();
        assertThat(ParamType.UUID_VALUE.accepts(UUID.randomUUID())).isTrue();
        assertThat(ParamType.STRING_ARRAY.accepts(List.of("go", "sql"))).isTrue();
        assertThat(ParamType.STRING_ARRAY.accepts(List.of(1, 2))).isFalse();
        assertThat(ParamType.STRING.accepts(null)).isFalse();
    }

    @Test
    void publishedValuesAreImmutable() {
        var error = UserFacingError.with(ErrorCode.LANGUAGE_UNDETECTED)
                .param("detectedCandidates", List.of("tr", "en"))
                .build();

        assertThat(error.params()).isUnmodifiable();
        assertThat(error.resolutions()).isUnmodifiable();
    }

    // ─── the action vocabulary (EK D.6) ───

    @Test
    void theActionVocabularyIsTheAgreedTwelve() {
        // EK D.6.1's closed set. It is closed against the frontend, which
        // writes one ICU message per action: a value added here without a
        // handoff item renders as a raw key to a user.
        assertThat(Arrays.stream(ResolutionAction.values()).map(ResolutionAction::wireValue))
                .containsExactlyInAnyOrder(
                        "increase_page_limit", "review_pins", "keep_top_pinned", "sign_up",
                        "paste_full_posting", "continue_as_general_cv", "switch_to_manual_form",
                        "complete_profile",
                        // Adim 2.3: Bolum 18.1 offers three ways past a
                        // preflight refusal and only two of them had a name
                        // (handoff B-037).
                        "continue_anyway",
                        "retry",
                        // Bolum 08b: a second CV is refused and the answer is
                        // replace or keep. Never a merge -- that is atom-level
                        // de-duplication and Stage 4 work (handoff B-060).
                        "replace_profile", "keep_existing_profile");
    }

    @Test
    void actionsRoundTripThroughTheirWireValue() {
        for (ResolutionAction action : ResolutionAction.values()) {
            assertThat(ResolutionAction.fromWireValue(action.wireValue())).isEqualTo(action);
        }
    }

    @Test
    void actionWireValuesDoNotDependOnTheDefaultLocale() {
        var previous = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("tr", "TR"));
            assertThat(ResolutionAction.SIGN_UP.wireValue()).isEqualTo("sign_up");
            assertThat(ResolutionAction.fromWireValue("switch_to_manual_form"))
                    .isEqualTo(ResolutionAction.SWITCH_TO_MANUAL_FORM);
        } finally {
            Locale.setDefault(previous);
        }
    }

    // ─── the wire shape is the contract (Bolum 35.4) ───

    @Test
    void theBodySerialisesTheWayTheDocumentShowsIt() throws Exception {
        var error = UserFacingError.with(ErrorCode.CONFLICTING_PREFERENCES)
                .param("pinnedPages", 2.3)
                .param("maxPages", 1)
                .resolution(Resolution.of(ResolutionAction.INCREASE_PAGE_LIMIT, "maxPages", 3))
                .resolution(ResolutionAction.REVIEW_PINS)
                .build();

        var json = new ObjectMapper().writeValueAsString(error);

        assertThat(json).isEqualTo("""
                {"code":"CONFLICTING_PREFERENCES",\
                "params":{"pinnedPages":2.3,"maxPages":1},\
                "resolutions":[\
                {"action":"increase_page_limit","params":{"maxPages":3}},\
                {"action":"review_pins"}]}""");
    }

    private static List<String> names() {
        return Arrays.stream(ErrorCode.values()).map(Enum::name).toList();
    }
}
