package com.mustafatetik.atomcv.ingestion.extraction;

import com.mustafatetik.atomcv.shared.error.ApiException;
import com.mustafatetik.atomcv.shared.error.ErrorCode;
import com.mustafatetik.atomcv.shared.error.UserFacingError;

/**
 * The two refusals that carry a parameter, built in one place.
 *
 * <p>{@code UNSUPPORTED_DOCUMENT} is raised from four rungs of Bolum 31.2's
 * ladder and every one of them has to publish the same accepted list. Built at
 * each site, the day a format is added is the day three of the four go stale.
 */
final class ExtractionRefusal {

    private ExtractionRefusal() {
    }

    static ApiException unsupported() {
        return new ApiException(UserFacingError.with(ErrorCode.UNSUPPORTED_DOCUMENT)
                .param("accepted", DocumentFormat.allExtensions())
                .build());
    }

    static ApiException tooLarge(int limitBytes) {
        return new ApiException(UserFacingError.with(ErrorCode.DOCUMENT_TOO_LARGE)
                .param("limitBytes", limitBytes)
                .build());
    }
}
