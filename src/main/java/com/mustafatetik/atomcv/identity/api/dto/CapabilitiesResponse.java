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
