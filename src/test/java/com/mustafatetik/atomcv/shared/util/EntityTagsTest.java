package com.mustafatetik.atomcv.shared.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mustafatetik.atomcv.shared.error.ApiException;
import com.mustafatetik.atomcv.shared.error.ErrorCode;
import com.mustafatetik.atomcv.shared.error.ResolutionAction;
import org.junit.jupiter.api.Test;

class EntityTagsTest {

    @Test
    void aTagIsTheQuotedVersion() {
        assertThat(EntityTags.of(0)).isEqualTo("\"0\"");
        assertThat(EntityTags.of(7)).isEqualTo("\"7\"");
    }

    @Test
    void theCurrentVersionMatches() {
        assertThatCode(() -> EntityTags.requireMatch("\"7\"", 7L)).doesNotThrowAnyException();
        assertThatCode(() -> EntityTags.requireMatch(" \"7\" ", 7L)).doesNotThrowAnyException();
    }

    @Test
    void aWeakTagFromAProxyStillMatches() {
        assertThatCode(() -> EntityTags.requireMatch("W/\"7\"", 7L)).doesNotThrowAnyException();
    }

    @Test
    void theWildcardMeansAnyVersion() {
        assertThatCode(() -> EntityTags.requireMatch("*", 7L)).doesNotThrowAnyException();
    }

    @Test
    void anUnsavedRowCountsAsVersionZero() {
        assertThatCode(() -> EntityTags.requireMatch("\"0\"", null)).doesNotThrowAnyException();
    }

    /** P8: a write with no precondition is a silent overwrite waiting to happen. */
    @Test
    void aMissingPreconditionIsRefused() {
        assertThatThrownBy(() -> EntityTags.requireMatch(null, 7L))
                .isInstanceOf(ApiException.class)
                .extracting(failure -> ((ApiException) failure).error().code())
                .isEqualTo(ErrorCode.PRECONDITION_REQUIRED);

        assertThatThrownBy(() -> EntityTags.requireMatch("  ", 7L))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void aStaleTagIsRefusedWithSomethingToDoAboutIt() {
        assertThatThrownBy(() -> EntityTags.requireMatch("\"6\"", 7L))
                .isInstanceOf(ApiException.class)
                .satisfies(failure -> {
                    var error = ((ApiException) failure).error();
                    assertThat(error.code()).isEqualTo(ErrorCode.VERSION_CONFLICT);
                    assertThat(error.httpStatus()).isEqualTo(412);
                    assertThat(error.resolutions()).extracting(resolution -> resolution.action())
                            .containsExactly(ResolutionAction.RETRY);
                });
    }

    @Test
    void anUnquotedVersionIsNotAValidTag() {
        // "7" is the tag; 7 is not. Accepting both would mean two clients could
        // disagree about what the header even is.
        assertThatThrownBy(() -> EntityTags.requireMatch("7", 7L))
                .isInstanceOf(ApiException.class);
    }
}
