package com.mustafatetik.atomcv.generation.support;

import com.mustafatetik.atomcv.generation.domain.Generation;
import com.mustafatetik.atomcv.generation.domain.SupportGrant;
import com.mustafatetik.atomcv.generation.repository.GenerationRepository;
import com.mustafatetik.atomcv.generation.repository.SupportGrantLookup;
import com.mustafatetik.atomcv.shared.security.UserContext;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Reading one generation's content under the permission its owner gave
 * (Bolum 48.4).
 *
 * <p><strong>A command, not an endpoint, and that is the whole design.</strong>
 * Absolute rule 3 leaves no way for one user's request to read another's rows,
 * and adding one would mean a support role, an authorisation branch and an
 * exception to the guard ArchUnit enforces per module — a permanent hole in the
 * defence, for something that happens by hand a few times a year. This runs
 * with the application, on the server, started deliberately:
 *
 * <pre>
 *   --spring.profiles.active=prod,support --support.generation=&lt;uuid&gt;
 * </pre>
 *
 * <p><strong>The grant is the credential.</strong> {@link SupportGrantLookup} is
 * the one unscoped read in the flow, because the grant is what says whose CV
 * this is; everything after it goes through the ordinary scoped repository,
 * acting as the owner the grant names. A closed grant reads nothing at all —
 * revoked and expired are refusals here, not warnings, because the consent is
 * what makes the read lawful and it has ended.
 *
 * <p><strong>Why {@code System.out} and never the logger.</strong> Absolute rule
 * 4 keeps user content out of logs, and a log line goes to Axiom, to Sentry's
 * breadcrumbs and to a file somebody else can read. This prints to the terminal
 * of the person the grant was given to, once, and writes down that it did.
 */
@Component
@Profile("support")
public class SupportRead implements ApplicationRunner {

    /** {@code --support.generation=<uuid>}. */
    static final String ARGUMENT = "support.generation";

    private final SupportGrantLookup grants;
    private final GenerationRepository generations;
    private final Clock clock;

    SupportRead(SupportGrantLookup grants, GenerationRepository generations, Clock clock) {
        this.grants = grants;
        this.generations = generations;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        var requested = arguments.getOptionValues(ARGUMENT);
        if (requested == null || requested.isEmpty()) {
            print("Nothing to read. Pass --" + ARGUMENT + "=<generation id>.");
            return;
        }
        read(UUID.fromString(requested.get(0)));
    }

    /** Package-private so the flow can be asserted without booting anything. */
    void read(UUID generationId) {
        Instant now = clock.instant();
        Optional<SupportGrant> found = grants.newestFor(generationId);
        if (found.isEmpty()) {
            print("No support grant on " + generationId
                    + ". Nobody has given permission to read it, so nothing is read.");
            return;
        }

        SupportGrant grant = found.get();
        if (!grant.isOpenAt(now)) {
            print("The support grant on " + generationId + " is closed — "
                    + (grant.getRevokedAt() != null
                            ? "withdrawn at " + grant.getRevokedAt()
                            : "it ran out at " + grant.getExpiresAt())
                    + ". Nothing is read: the permission is what made it lawful.");
            return;
        }

        // As the owner, which is exactly what the grant permits. A role would
        // not have helped: ownership is settled in the repository layer.
        var owner = UserContext.of(grant.getOwnerId());
        Optional<Generation> generation = generations.findById(owner, generationId);
        if (generation.isEmpty()) {
            print("The grant on " + generationId + " is open but the generation is gone.");
            return;
        }

        if (grant.markAccessed(now)) {
            grants.save(grant);
        }
        // Stamped before the content is printed. If printing fails halfway the
        // person is still told it was read, which is the direction an audit
        // trail has to fail in.
        print(report(generation.get(), grant));
    }

    /**
     * What a diagnosis needs, and no more of the person than that.
     *
     * <p>The generation row is the record of what was produced — the posting it
     * was written against, the shape selection chose, the document that came out
     * and the engine that made it. The profile behind it is not opened: the
     * grant is on this generation.
     */
    private static String report(Generation generation, SupportGrant grant) {
        var out = new StringBuilder();
        out.append("Generation ").append(generation.getId())
                .append("  status=").append(generation.getStatus())
                .append("  pages=").append(generation.getPageCount())
                .append("  granted=").append(grant.getGrantedAt())
                .append("  expires=").append(grant.getExpiresAt())
                .append("  read=").append(grant.getAccessedAt())
                .append(System.lineSeparator());
        out.append("engine: ").append(generation.getEngineVersion()).append(System.lineSeparator());
        out.append("fit: ").append(generation.getFitReport()).append(System.lineSeparator());
        out.append("selection: ").append(generation.getSelectionState()).append(System.lineSeparator());
        out.append("posting: ").append(generation.getJobDescription()).append(System.lineSeparator());
        out.append("document: ").append(generation.getContentSnapshot()).append(System.lineSeparator());
        out.append("cover letter: ").append(generation.getCoverLetter());
        return out.toString();
    }

    @SuppressWarnings("java:S106")
    private static void print(String line) {
        System.out.println(line);
    }
}
