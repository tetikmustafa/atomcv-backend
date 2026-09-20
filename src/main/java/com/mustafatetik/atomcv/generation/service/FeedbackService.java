package com.mustafatetik.atomcv.generation.service;

import com.mustafatetik.atomcv.generation.domain.Generation;
import com.mustafatetik.atomcv.generation.domain.GenerationFeedback;
import com.mustafatetik.atomcv.generation.domain.SupportGrant;
import com.mustafatetik.atomcv.generation.repository.FeedbackRepository;
import com.mustafatetik.atomcv.generation.repository.GenerationRepository;
import com.mustafatetik.atomcv.generation.repository.SupportGrantRepository;
import com.mustafatetik.atomcv.shared.security.UserContext;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A thumb on one generation, and the permission that may come with it.
 *
 * <p><strong>One verdict per person per generation.</strong> Pressing the
 * other thumb changes the answer rather than adding a second one, so this
 * updates the row it finds. The alternative would be a feedback rate that
 * counts clicks.
 *
 * <p><strong>The grant is a separate row on purpose.</strong> The flag on the
 * verdict says what the person agreed to; the grant carries when it runs out,
 * when it was used and when it was taken back — a history the verdict has no
 * business holding, and the only part of the arrangement that makes the
 * consent checkable rather than decorative.
 */
@Service
public class FeedbackService {

    private static final Logger log = LoggerFactory.getLogger(FeedbackService.class);

    /**
     * 48.3's "feedback rate": the verdict tag is the ratio, against
     * {@code job.run{type=generation}} as the denominator.
     *
     * <p>The line below used to say "this is the feedback rate" over a log
     * statement. A rate is a series — a log line answers the question once, to
     * whoever happens to be reading that minute, and cannot be plotted against
     * anything (the sixth audit).
     */
    static final String FEEDBACK = "generation.feedback";

    private final GenerationRepository generations;
    private final FeedbackRepository feedback;
    private final SupportGrantRepository grants;
    private final MeterRegistry meters;
    private final Clock clock;

    FeedbackService(GenerationRepository generations, FeedbackRepository feedback,
            SupportGrantRepository grants, MeterRegistry meters, Clock clock) {

        this.generations = generations;
        this.feedback = feedback;
        this.grants = grants;
        this.meters = meters;
        this.clock = clock;
    }

    /** What was said and what it opened, for the caller to answer with. */
    public record Recorded(GenerationFeedback verdict, SupportGrant grant) {
    }

    /** @return empty when there is no such generation for this user */
    public Optional<Generation> find(UserContext user, UUID generationId) {
        return generations.findById(user, generationId);
    }

    /**
     * What this person already said about this generation (F-019).
     *
     * <p><strong>A verdict that cannot be read back is one the screen
     * forgets.</strong> A second press has to show the standing selection
     * rather than a thank-you, and nothing published the answer: a client
     * could honour that only for as long as the tab stayed open, and a reload
     * offered to collect the same verdict again.
     *
     * <p>The grant comes with it and is the half that matters more. The person
     * is promised they can see when their content was read; the window is
     * forty-eight hours, so the one who most needs to look is the one who
     * comes back the next day.
     *
     * <p>Both reads are scoped, and both take the acting user rather than
     * trusting the generation that was already found — the id reaches a
     * browser, so nothing downstream of it may be read on its authority alone
     * (absolute rule 3).
     *
     * @return empty when they have not judged it; the grant inside may be null
     *         when they judged it without opening the door
     */
    @Transactional(readOnly = true)
    public Optional<Recorded> read(UserContext user, UUID generationId) {
        return feedback.findFor(user, generationId)
                .map(verdict -> new Recorded(
                        verdict, grants.findFor(user, generationId).orElse(null)));
    }

    /**
     * @param rating         {@code 1} or {@code -1}
     * @param category one of the five, or null
     * @param comment        what they wrote, or null. Stored, never logged
     *                       (absolute rule 4)
     * @param contentGranted the box. Ticking it opens forty-eight
     *                       hours; unticking it closes them again, which is
     *                       what makes it a consent rather than a switch that
     *                       only goes one way
     */
    @Transactional
    public Recorded record(UserContext user, UUID generationId, short rating,
            GenerationFeedback.Category category, String comment, boolean contentGranted) {

        Instant now = clock.instant();
        GenerationFeedback verdict = feedback.findFor(user, generationId)
                .orElseGet(() -> new GenerationFeedback(
                        user.userId(), generationId, rating, now));

        verdict.setRating(rating);
        verdict.setCategory(category == null ? null : category.name().toLowerCase(Locale.ROOT));
        verdict.setComment(comment == null || comment.isBlank() ? null : comment.strip());
        verdict.setContentGranted(contentGranted);
        feedback.save(user, verdict);

        SupportGrant grant = grantFor(user, generationId, contentGranted, now);

        // The verdict and whether a door was opened; never the comment
        // (absolute rule 4).
        log.info("Feedback on generation {}: {}", generationId, verdict);
        // Counted only when it is new. A person changing their mind is one
        // opinion still, and counting the second press would make the rate
        // measure clicking rather than feedback -- the same reasoning V4's
        // unique index applies to the row.
        if (verdict.getCreatedAt().equals(now)) {
            meters.counter(FEEDBACK, "verdict", rating > 0 ? "up" : "down").increment();
        }
        return new Recorded(verdict, grant);
    }

    /**
     * The grant this answer implies.
     *
     * <p>An existing open grant is not renewed by a second yes: forty-eight
     * hours starts when the person first agreed, and a form saved twice would
     * otherwise keep pushing the end of it back without them meaning to.
     */
    private SupportGrant grantFor(
            UserContext user, UUID generationId, boolean granted, Instant now) {

        Optional<SupportGrant> existing = grants.findFor(user, generationId);

        if (!granted) {
            existing.filter(grant -> grant.isOpenAt(now)).ifPresent(grant -> {
                grant.revoke(now);
                grants.save(user, grant);
                log.info("A support grant on generation {} was withdrawn", generationId);
            });
            return existing.orElse(null);
        }

        if (existing.filter(grant -> grant.isOpenAt(now)).isPresent()) {
            return existing.get();
        }
        SupportGrant opened = grants.save(user, new SupportGrant(
                user.userId(), generationId, now));
        log.info("A support grant on generation {} runs until {}",
                generationId, opened.getExpiresAt());
        return opened;
    }
}
