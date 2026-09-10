package com.mustafatetik.atomcv.generation.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mustafatetik.atomcv.generation.domain.EngineVersion;
import com.mustafatetik.atomcv.generation.domain.Generation;
import com.mustafatetik.atomcv.generation.domain.StoredSelection;
import com.mustafatetik.atomcv.generation.domain.SupportGrant;
import com.mustafatetik.atomcv.generation.repository.GenerationRepository;
import com.mustafatetik.atomcv.generation.repository.SupportGrantLookup;
import com.mustafatetik.atomcv.generation.selection.SelectionState;
import com.mustafatetik.atomcv.rendering.template.TemplateCustomization;
import com.mustafatetik.atomcv.shared.security.UserContext;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * The offline support reader (Bolum 48.4), and what it refuses.
 *
 * <p>The promise this closes is the one {@code accessedAt} makes: a consent
 * nobody can check the use of is not a consent. So the assertions are about the
 * stamp as much as about the read — and about the two cases where nothing is
 * read at all, because the permission is what makes the read lawful.
 */
class SupportReadTest {

    private static final Instant NOW = Instant.parse("2026-09-09T12:00:00Z");
    private static final UUID GENERATION = UUID.randomUUID();
    private static final UUID OWNER = UUID.randomUUID();

    private final SupportGrantLookup grants = mock(SupportGrantLookup.class);
    private final GenerationRepository generations = mock(GenerationRepository.class);
    private final SupportRead reader = new SupportRead(grants, generations,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void anopenGrantIsReadAsItsOwnerAndStamped() {
        var grant = openGrant();
        when(grants.newestFor(GENERATION)).thenReturn(Optional.of(grant));
        when(generations.findById(any(), eq(GENERATION))).thenReturn(Optional.of(generation()));

        reader.read(GENERATION);

        assertThat(grant.getAccessedAt())
                .as("the person is shown when it was read")
                .isEqualTo(NOW);
        verify(grants).save(grant);
    }

    /**
     * As the owner, not as somebody with a role. {@code ProfileRefTest} asserts
     * a role buys no reach — the grant is what buys it, for this one generation
     * and for forty-eight hours.
     */
    @Test
    void thereadActsAsTheOwnerTheGrantNames() {
        when(grants.newestFor(GENERATION)).thenReturn(Optional.of(openGrant()));
        when(generations.findById(any(), eq(GENERATION))).thenReturn(Optional.of(generation()));

        reader.read(GENERATION);

        ArgumentCaptor<UserContext> acting = ArgumentCaptor.forClass(UserContext.class);
        verify(generations).findById(acting.capture(), eq(GENERATION));
        assertThat(acting.getValue().userId()).isEqualTo(OWNER);
        assertThat(acting.getValue().isAdmin())
                .as("nothing here needs a role, and asking for one would be a hole")
                .isFalse();
    }

    @Test
    void awithdrawnGrantReadsNothingAndStampsNothing() {
        var grant = openGrant();
        grant.revoke(NOW.minus(Duration.ofHours(1)));
        when(grants.newestFor(GENERATION)).thenReturn(Optional.of(grant));

        reader.read(GENERATION);

        assertThat(grant.getAccessedAt()).isNull();
        verify(generations, never()).findById(any(), any());
        verify(grants, never()).save(any());
    }

    /** Run out is a different event from withdrawn, and both refuse. */
    @Test
    void anexpiredGrantReadsNothingEither() {
        var expired = new SupportGrant(OWNER, GENERATION,
                NOW.minus(SupportGrant.LIFETIME).minus(Duration.ofMinutes(1)));
        when(grants.newestFor(GENERATION)).thenReturn(Optional.of(expired));

        reader.read(GENERATION);

        assertThat(expired.getAccessedAt()).isNull();
        verify(generations, never()).findById(any(), any());
    }

    /**
     * And no grant at all is the commonest case: a generation id somebody has
     * is not permission to read it.
     */
    @Test
    void withoutAGrantNothingIsRead() {
        when(grants.newestFor(GENERATION)).thenReturn(Optional.empty());

        reader.read(GENERATION);

        verify(generations, never()).findById(any(), any());
        verify(grants, never()).save(any());
    }

    /**
     * A second read does not move the stamp. The column holds one instant, so it
     * answers "was this looked at, and from when" — a trail that moved every time
     * would answer a question nobody asked with a worse version of the one they
     * did.
     */
    @Test
    void asecondReadLeavesTheFirstStampAlone() {
        var grant = openGrant();
        Instant first = NOW.minus(Duration.ofHours(2));
        grant.markAccessed(first);
        when(grants.newestFor(GENERATION)).thenReturn(Optional.of(grant));
        when(generations.findById(any(), eq(GENERATION))).thenReturn(Optional.of(generation()));

        reader.read(GENERATION);

        assertThat(grant.getAccessedAt()).isEqualTo(first);
        verify(grants, never()).save(any());
    }

    /**
     * A real row rather than a mock: the reader's report reads eight getters off
     * it, and a mock would assert that they were called rather than that the
     * report can be built from a generation.
     */
    private static Generation generation() {
        return new Generation(OWNER, UUID.randomUUID(), Map.of(),
                new StoredSelection("en", TemplateCustomization.CLASSIC,
                        new SelectionState.BudgetBreakdown(648.0, 68.4, 579.6, 0.0),
                        List.of(), List.of()),
                new EngineVersion(null, "without-embedding", "classic:v5",
                        Map.of("job_analysis", "v2")));
    }

    private static SupportGrant openGrant() {
        return new SupportGrant(OWNER, GENERATION, NOW.minus(Duration.ofHours(1)));
    }
}
