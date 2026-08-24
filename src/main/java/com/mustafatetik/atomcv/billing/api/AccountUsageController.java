package com.mustafatetik.atomcv.billing.api;

import com.mustafatetik.atomcv.billing.QuotaMetric;
import com.mustafatetik.atomcv.billing.QuotaService;
import com.mustafatetik.atomcv.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * What today has cost the user so far (Bolum 44.1, § 35.7).
 *
 * <p>Published so the limit is visible <em>before</em> it is hit. A quota that
 * only ever appears as a 429 is a quota the user meets by being stopped
 * mid-task, which is the kind of surprise Bolum 4's principles exist to
 * prevent.
 *
 * <p>Both metrics, always, even the untouched one — a client rendering
 * "3 of 20" should not have to guess that a missing entry means zero.
 */
@RestController
@RequestMapping("/api/v1/account")
@Tag(name = "Account", description = "What the acting user has used today")
public class AccountUsageController {

    private final CurrentUser currentUser;
    private final QuotaService quotas;

    AccountUsageController(CurrentUser currentUser, QuotaService quotas) {
        this.currentUser = currentUser;
        this.quotas = quotas;
    }

    @Operation(
            summary = "Today's usage against today's limits",
            description = """
                    `resetsAt` is an absolute instant, not an hour: the day \
                    boundary is UTC and the client writes the sentence in the \
                    user's own locale. Counters roll over at UTC midnight, \
                    which is 03:00 in Turkey.""")
    @GetMapping("/usage")
    public ResponseEntity<List<QuotaService.Usage>> usage() {
        var user = currentUser.require();
        return ResponseEntity.ok()
                // It changes with every generation; a cached copy is a limit
                // the user believes they still have.
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(List.of(
                        quotas.usage(user, QuotaMetric.GENERATION),
                        quotas.usage(user, QuotaMetric.PROFILE_EXTRACT)));
    }
}
