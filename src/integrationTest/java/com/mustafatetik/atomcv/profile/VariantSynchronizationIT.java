package com.mustafatetik.atomcv.profile;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.AbstractIntegrationTest;
import com.mustafatetik.atomcv.profile.domain.Atom;
import com.mustafatetik.atomcv.profile.domain.AtomKind;
import com.mustafatetik.atomcv.profile.domain.AtomVariant;
import com.mustafatetik.atomcv.profile.domain.Section;
import com.mustafatetik.atomcv.profile.domain.SectionKind;
import com.mustafatetik.atomcv.profile.domain.content.RichContent;
import com.mustafatetik.atomcv.profile.repository.AtomRepository;
import com.mustafatetik.atomcv.profile.repository.AtomVariantRepository;
import com.mustafatetik.atomcv.profile.repository.SectionRepository;
import com.mustafatetik.atomcv.profile.service.AtomService;
import com.mustafatetik.atomcv.profile.service.ProfileResolver;
import com.mustafatetik.atomcv.profile.service.VariantPatch;
import com.mustafatetik.atomcv.shared.security.ProfileRef;
import com.mustafatetik.atomcv.shared.security.UserContext;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Bolum 32.2, against a real database and a real queue.
 *
 * <p>The rule has two halves that are easy to collapse into one and must not
 * be. Everything derived from an edited wording goes <strong>stale</strong>,
 * whoever wrote it — the person is entitled to know the two have diverged.
 * Only what the person did <em>not</em> write is <strong>queued</strong> for
 * regeneration, because replacing somebody's own sentence with a machine
 * translation over a typo fix is the product overruling them silently.
 */
class VariantSynchronizationIT extends AbstractIntegrationTest {

    @Autowired
    private AtomService atomService;

    @Autowired
    private ProfileResolver profiles;

    @Autowired
    private SectionRepository sections;

    @Autowired
    private AtomRepository atoms;

    @Autowired
    private AtomVariantRepository variants;

    @Autowired
    private JdbcTemplate jdbc;

    private UserContext user;
    private ProfileRef profile;
    private Atom atom;
    private AtomVariant turkish;

    @BeforeEach
    void aProfileWithTwoWordings() {
        UUID userId = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, email_verified) VALUES (?, ?, true)",
                userId, userId + "@sync.test");
        user = UserContext.of(userId);
        profile = profiles.resolve(user);

        Section section = sections.save(profile,
                new Section(profile.id(), SectionKind.EXPERIENCE, "Deneyim", (short) 0));
        atom = atoms.save(profile,
                new Atom(profile.id(), section.getId(), null, AtomKind.BULLET, (short) 0));

        turkish = variants.save(profile, new AtomVariant(profile.id(), atom.getId(), "tr",
                RichContent.plain("300 bin satır taşıdım")));
        turkish.setPrimary(true);
        turkish = variants.save(profile, turkish);
    }

    @Test
    void anEditMarksTheWordingDerivedFromItStale() {
        AtomVariant english = derived(false);

        edit("300 bin satır taşıdım ve raporladım");

        assertThat(reload(english).isStale()).isTrue();
    }

    @Test
    void andQueuesItForRegeneration() {
        derived(false);

        edit("300 bin satır taşıdım ve raporladım");

        assertThat(queuedTranslations()).isEqualTo(1);
    }

    /**
     * The half that is easy to lose. A wording the person wrote is marked so
     * the screen can say the two have diverged, and is <em>not</em> queued —
     * Bolum 32.2 gives them the choice rather than making it for them.
     */
    @Test
    void awordingThePersonWroteIsMarkedStaleButNeverRegenerated() {
        AtomVariant mine = derived(true);

        edit("300 bin satır taşıdım ve raporladım");

        assertThat(reload(mine).isStale()).isTrue();
        assertThat(queuedTranslations()).isZero();
    }

    /** A promote or a tone change leaves every translation of it still accurate. */
    @Test
    void aneditThatDoesNotTouchTheWordsChangesNothing() {
        AtomVariant english = derived(false);

        atomService.patchVariant(profile, user, atom.getId(), turkish.getId(),
                etagOf(turkish), new VariantPatch(null, null, null, null));

        assertThat(reload(english).isStale()).isFalse();
        assertThat(queuedTranslations()).isZero();
    }

    @Test
    void awordingDerivedFromSomethingElseIsLeftAlone() {
        AtomVariant unrelated = variants.save(profile,
                new AtomVariant(profile.id(), atom.getId(), "de",
                        RichContent.plain("300 Tausend Zeilen")));

        edit("300 bin satır taşıdım ve raporladım");

        assertThat(reload(unrelated).isStale()).isFalse();
    }

    // -- fixtures ----------------------------------------------------------

    private AtomVariant derived(boolean userEdited) {
        AtomVariant english = new AtomVariant(profile.id(), atom.getId(), "en",
                RichContent.plain("Moved 300K rows"));
        english.markDerivedFrom(turkish);
        english.setUserEdited(userEdited);
        return variants.save(profile, english);
    }

    private void edit(String words) {
        atomService.patchVariant(profile, user, atom.getId(), turkish.getId(),
                etagOf(turkish), new VariantPatch(RichContent.plain(words), null, null, null));
    }

    private AtomVariant reload(AtomVariant variant) {
        return variants.findById(profile, variant.getId()).orElseThrow();
    }

    private static String etagOf(AtomVariant variant) {
        return "\"" + (variant.getVersion() == null ? 0 : variant.getVersion()) + "\"";
    }

    private int queuedTranslations() {
        Integer rows = jdbc.queryForObject(
                "SELECT count(*) FROM jobs WHERE type = 'translation' AND user_id = ?",
                Integer.class, user.userId());
        return rows == null ? 0 : rows;
    }
}
