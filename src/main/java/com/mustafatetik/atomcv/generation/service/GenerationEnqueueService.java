package com.mustafatetik.atomcv.generation.service;

import com.mustafatetik.atomcv.billing.FeatureFlags;
import com.mustafatetik.atomcv.billing.QuotaMetric;
import com.mustafatetik.atomcv.billing.QuotaService;
import com.mustafatetik.atomcv.generation.phases.analysis.JobDescriptionPreflight;
import com.mustafatetik.atomcv.jobs.queue.Job;
import com.mustafatetik.atomcv.jobs.queue.JobQueue;
import com.mustafatetik.atomcv.jobs.queue.JobRepository;
import com.mustafatetik.atomcv.jobs.queue.JobType;
import com.mustafatetik.atomcv.profile.domain.Profile;
import com.mustafatetik.atomcv.profile.domain.ProfileTree;
import com.mustafatetik.atomcv.profile.service.ProfileAssembler;
import com.mustafatetik.atomcv.profile.service.ProfileResolver;
import com.mustafatetik.atomcv.shared.error.PipelineError;
import com.mustafatetik.atomcv.shared.error.Result;
import com.mustafatetik.atomcv.shared.security.ProfileRef;
import com.mustafatetik.atomcv.shared.security.UserContext;
import java.time.Clock;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Everything that happens before a generation is queued (Bolum 35.3).
 *
 * <p><strong>The preflights are synchronous and that is the whole point.</strong>
 * Bolum 35.3 says so and the reason is what a user sees: a request that was
 * never going to work should be a 4xx on the spot, not a job that is accepted,
 * watched for half a minute and then fails. Both checks here are free — one
 * reads the profile, the other counts characters — so refusing costs nothing
 * and accepting costs a worker.
 *
 * <p>The quota goes first of all (Bolum 44). It is one statement against one
 * row, and a user over their limit should not have their profile loaded or
 * their posting measured to find that out. It is also the only gate here that
 * <em>writes</em> — which is why it runs after idempotency: answering with a
 * job that already exists must not cost a second unit.
 */
@Service
public class GenerationEnqueueService {

    private static final Logger log = LoggerFactory.getLogger(GenerationEnqueueService.class);

    private final ProfileResolver profiles;
    private final ProfileAssembler assembler;
    private final JobQueue queue;
    private final JobRepository jobs;
    private final QuotaService quotas;
    private final FeatureFlags flags;
    private final Clock clock;

    GenerationEnqueueService(ProfileResolver profiles, ProfileAssembler assembler,
            JobQueue queue, JobRepository jobs, QuotaService quotas, FeatureFlags flags,
            Clock clock) {

        this.quotas = quotas;
        this.flags = flags;
        this.profiles = profiles;
        this.assembler = assembler;
        this.queue = queue;
        this.jobs = jobs;
        this.clock = clock;
    }

    /**
     * @param idempotencyKey the request header, or null. Bolum 30.7: the same
     *                       key from the same user is the same job, so a
     *                       double click produces one generation rather than
     *                       two identical ones a second apart.
     */
    public Result<Job> enqueue(
            UserContext user,
            String jobDescription,
            boolean preflightAcknowledged,
            Integer maxPages,
            String language,
            String idempotencyKey) {

        Optional<Job> already = jobs.findByIdempotencyKey(user, idempotencyKey);
        if (already.isPresent()) {
            // Answered with the job that already exists, not with a conflict:
            // the caller asked for one generation and there is one.
            return Result.ok(already.get());
        }

        if (!flags.isEnabled(FeatureFlags.NEW_GENERATIONS)) {
            // Bolum 44.3, and it goes ahead of the quota: a paused deployment
            // must not spend anyone's allowance on a request it will refuse.
            return Result.err(new PipelineError.GenerationPaused());
        }

        Result<Void> spent = quotas.consume(user, QuotaMetric.GENERATION);
        if (spent.isErr()) {
            return spent.map(ignored -> null);
        }

        Result<Void> refused = preflight(user, jobDescription, preflightAcknowledged);
        if (refused.isErr()) {
            // Bolum 44.2: nothing was generated, so nothing was spent. Without
            // this a user could burn a day's allowance on typos.
            quotas.refund(user, QuotaMetric.GENERATION);
            return refused.map(ignored -> null);
        }

        var job = new Job(JobType.GENERATION, user.userId(),
                new GenerationPayload(jobDescription, preflightAcknowledged, maxPages, language)
                        .toMap(),
                clock.instant());
        job.setIdempotencyKey(idempotencyKey);
        return Result.ok(queue.enqueue(job));
    }

    /**
     * The two gates that cost nothing, cheapest first.
     *
     * <p>The posting check runs before the profile is loaded because it is
     * four string measurements against one query, and the profile check runs
     * before the job is queued because an empty profile fails every attempt
     * the retry budget allows.
     */
    private Result<Void> preflight(
            UserContext user, String jobDescription, boolean preflightAcknowledged) {

        // A blank posting is general CV mode, not a bad request: Bolum 18.1's
        // check accepts it for exactly that reason.
        if (!preflightAcknowledged) {
            var verdict = JobDescriptionPreflight.check(jobDescription);
            if (!verdict.isAccepted()) {
                // The verdict, never the posting (absolute rule 4).
                log.info("Refused a posting before queueing: {}", verdict);
                return Result.err(new PipelineError.UnparseableJobDescription(
                        0, 0, verdict.reason()));
            }
        }

        var owned = profiles.owned(user);
        Profile head = owned.profile();
        ProfileRef profile = owned.ref();
        ProfileTree tree = assembler.load(profile);
        return ProfilePreflight.check(head, tree);
    }
}
