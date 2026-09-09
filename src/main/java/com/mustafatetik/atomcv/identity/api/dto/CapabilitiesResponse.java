package com.mustafatetik.atomcv.identity.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * What this caller may do, so the client can show it before the server has to
 * refuse it (§ 35.7).
 *
 * <p><strong>The server still checks.</strong> Every field here has a gate
 * behind it; this exists so a user meets a limit as a disabled control rather
 * than as an error, which is design principle 4 applied to permissions.
 *
 * @param allowedLanguages     output languages, Bolum 38.1's third axis
 * @param allowedTemplates     the templates that actually exist, not the ones
 *                             § 35.7's example lists — it names three and the
 *                             registry holds one, and publishing a template
 *                             the renderer cannot produce is a selectable
 *                             option that fails at generation time
 * @param canWriteCoverLetter  whether a covering letter may be asked for, with
 *                             the CV or afterwards (Bolum 34, § 35.7.3).
 *                             <strong>Added for F-030's sake as much as
 *                             F-028's:</strong> the block had no field for it,
 *                             so the frontend closed the control on
 *                             {@code canSaveHistory} — true today and true for
 *                             the wrong reason, because that field means "this
 *                             is an account" and the day the two capabilities
 *                             separate the proxy is silently wrong. Every
 *                             {@link com.mustafatetik.atomcv.shared.error.AccountFeature}
 *                             now has a boolean here to be refused against
 * @param maxAtoms             the anonymous ceiling behind
 *                             {@code ATOM_LIMIT_EXCEEDED}; {@code null} for an
 *                             account, which has none. Absent from the JSON
 *                             rather than sent as a number a client would draw
 *                             a progress bar against
 * @param quotaResetsAt        EK D.6.5: an absolute instant, never an hour
 * @param anonymousExpiresAt   EK D.6.6's sliding two hours. {@code null} until
 *                             Adim 3.6 mints anonymous sessions, and always
 *                             {@code null} for an account
 */
@Schema(description = "What the caller may do; the server still enforces all of it")
public record CapabilitiesResponse(
        List<String> allowedLanguages,
        List<String> allowedTemplates,
        boolean canCustomizeTemplate,
        boolean canEditAtomControls,
        boolean canAddAlternatives,
        boolean canWriteCoverLetter,
        boolean canSaveHistory,
        int dailyGenerationQuota,
        int generationsUsedToday,
        int dailyProfileQuota,
        int profilesUsedToday,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(types = {"integer", "null"}) Integer maxAtoms,
        @Schema(types = {"string", "null"}, format = "date-time") Instant quotaResetsAt,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(types = {"string", "null"}, format = "date-time") Instant anonymousExpiresAt) {
}
