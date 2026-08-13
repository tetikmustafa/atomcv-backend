package com.mustafatetik.atomcv.profile.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mustafatetik.atomcv.profile.domain.content.Mark;
import com.mustafatetik.atomcv.profile.domain.content.RichContent;
import com.mustafatetik.atomcv.profile.domain.content.Run;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AtomVariantTest {

    private static final UUID PROFILE_ID = UUID.randomUUID();
    private static final UUID ATOM_ID = UUID.randomUUID();

    private static AtomVariant variantOf(String text) {
        return new AtomVariant(PROFILE_ID, ATOM_ID, "en", RichContent.plain(text));
    }

    @Test
    void plainTextAndHashAreDerivedFromTheContent() {
        var content = RichContent.of(Run.of("Reduced latency by "), Run.of("40%", Mark.METRIC));
        var variant = new AtomVariant(PROFILE_ID, ATOM_ID, "en", content);

        assertThat(variant.getPlainText()).isEqualTo("Reduced latency by 40%");
        assertThat(variant.getContentHash()).isEqualTo(content.contentHash());
    }

    @Test
    void newContentInvalidatesTheMeasuredCosts() {
        var variant = variantOf("Reduced latency by 40%");
        variant.recordRenderCost("classic:v1", 27.7, Instant.now());

        variant.setContent(RichContent.plain("Reduced latency by 45% across three services"));

        assertThat(variant.getRenderCosts()).isEmpty();
        assertThat(variant.getCostMeasuredAt()).isNull();
        assertThat(variant.getPlainText()).isEqualTo("Reduced latency by 45% across three services");
    }

    @Test
    void remarkingTheSameSentenceKeepsTheMeasuredCosts() {
        var variant = variantOf("Reduced latency by 40%");
        var measuredAt = Instant.now();
        variant.recordRenderCost("classic:v1", 27.7, measuredAt);

        // Same words, one of them now marked: the rendered width is unchanged,
        // so re-measuring would cost a compilation for nothing.
        variant.setContent(RichContent.of(Run.of("Reduced latency by "), Run.of("40%", Mark.METRIC)));

        assertThat(variant.getRenderCosts()).containsEntry("classic:v1", 27.7);
        assertThat(variant.getCostMeasuredAt()).isEqualTo(measuredAt);
    }

    @Test
    void derivationRecordsTheSourceHashItWasBuiltFrom() {
        var source = variantOf("Reduced latency by 40%");
        var translation = new AtomVariant(PROFILE_ID, ATOM_ID, "tr",
                RichContent.plain("Gecikmeyi %40 azalttim"));

        translation.markDerivedFrom(source);

        assertThat(translation.getDerivedFromVariantId()).isEqualTo(source.getId());
        assertThat(translation.getSourceHash()).isEqualTo(source.getContentHash());
        assertThat(translation.isStale()).isFalse();
    }

    @Test
    void renderCostsCannotBeMutatedThroughTheGetter() {
        var variant = variantOf("Reduced latency by 40%");
        variant.recordRenderCost("classic:v1", 27.7, Instant.now());

        assertThatThrownBy(() -> variant.getRenderCosts().put("classic:v2", 1.0))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void toStringCarriesShapeAndHashButNotContent() {
        var variant = variantOf("Reduced latency by 40%");

        assertThat(variant.toString())
                .doesNotContain("Reduced")
                .contains("chars=22")
                .contains("hash=" + variant.getContentHash().substring(0, 8));
    }

    @Test
    void aSectionLevelAtomHasNoEntry() {
        var atom = new Atom(PROFILE_ID, UUID.randomUUID(), null, AtomKind.SKILL, (short) 0);

        assertThat(atom.isSectionLevel()).isTrue();
        assertThat(atom.getSource()).isEqualTo(AtomSource.MANUAL);
    }

    @Test
    void importanceStaysInsideTheRangeTheSchemaChecks() {
        var atom = new Atom(PROFILE_ID, UUID.randomUUID(), null, AtomKind.BULLET, (short) 0);

        assertThatThrownBy(() -> atom.setImportance(1.5f)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> atom.setImportance(-0.1f)).isInstanceOf(IllegalArgumentException.class);
        atom.setImportance(1.0f);
        assertThat(atom.getImportance()).isEqualTo(1.0f);
    }
}
