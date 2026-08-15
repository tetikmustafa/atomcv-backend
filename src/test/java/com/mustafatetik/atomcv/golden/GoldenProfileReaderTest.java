package com.mustafatetik.atomcv.golden;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.profile.seed.GoldenProfile;
import com.mustafatetik.atomcv.profile.seed.GoldenProfileReader;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The fixtures themselves (Bolum 51.3).
 *
 * <p>Everything else in the golden set asserts on what the algorithms do with
 * these profiles. This asserts that the profiles are what they claim to be —
 * a fixture that quietly failed to parse would make every test below it pass
 * for the wrong reason.
 */
class GoldenProfileReaderTest {

    private static final UUID OWNER = UUID.randomUUID();

    static java.util.stream.Stream<String> names() {
        return GoldenProfileReader.NAMES.stream();
    }

    @ParameterizedTest
    @MethodSource("names")
    void everyFixtureParsesIntoATreeThatHangsTogether(String name) {
        GoldenProfile golden = GoldenProfileReader.read(name, OWNER);

        assertThat(golden.name()).isEqualTo(name);
        assertThat(golden.description()).as("a fixture says what it is for").isNotBlank();
        assertThat(golden.sections()).isNotEmpty();
        assertThat(golden.atoms()).isNotEmpty();
        assertThat(golden.variants()).hasSameSizeAs(golden.atoms());
        assertThat(golden.tree().atomCount()).isEqualTo(golden.atoms().size());

        // The invariant the composite foreign keys enforce in the database.
        UUID profileId = golden.profile().getId();
        assertThat(golden.sections()).allSatisfy(section ->
                assertThat(section.getProfileId()).isEqualTo(profileId));
        assertThat(golden.atoms()).allSatisfy(atom ->
                assertThat(atom.getProfileId()).isEqualTo(profileId));
    }

    @Test
    void theSetCoversTheCasesItIsSupposedTo() {
        var senior = GoldenProfileReader.read("senior_backend_tr", OWNER);
        var junior = GoldenProfileReader.read("junior_frontend_en", OWNER);
        var academic = GoldenProfileReader.read("academic_long", OWNER);
        var edge = GoldenProfileReader.read("minimal_edge", OWNER);
        var changer = GoldenProfileReader.read("career_changer", OWNER);

        assertThat(senior.profile().getSourceLanguage()).as("a Turkish profile").isEqualTo("tr");
        assertThat(junior.atoms().size()).as("thin enough to leave room")
                .isLessThan(senior.atoms().size());
        assertThat(academic.atoms().size()).as("far more than two pages hold")
                .isGreaterThan(30);
        assertThat(academic.profile().getPreferences().defaults().maxPages()).isEqualTo(2);
        assertThat(edge.atoms()).anySatisfy(atom -> assertThat(atom.isActive()).isFalse());
        assertThat(edge.sections()).anySatisfy(section ->
                assertThat(section.isActive()).isFalse());
        assertThat(changer.entries()).anySatisfy(entry ->
                assertThat(entry.isAlwaysInclude()).isTrue());
        assertThat(changer.atoms()).anySatisfy(atom ->
                assertThat(atom.isAlwaysInclude()).isTrue());
    }

    @Test
    void anEntryCanAskForMoreBulletsThanItHas() {
        // minimal_edge sets minAtoms above the number of atoms on purpose:
        // the entry has to be dropped whole rather than printed short.
        var edge = GoldenProfileReader.read("minimal_edge", OWNER);

        assertThat(edge.tree().sections()).anySatisfy(section ->
                assertThat(section.entries()).anySatisfy(entry ->
                        assertThat(entry.entry().getMinAtoms())
                                .isGreaterThan((short) entry.atoms().size())));
    }
}
