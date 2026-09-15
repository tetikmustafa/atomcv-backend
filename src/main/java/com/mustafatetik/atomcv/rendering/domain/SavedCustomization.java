package com.mustafatetik.atomcv.rendering.domain;

import com.mustafatetik.atomcv.rendering.template.FontFamily;
import com.mustafatetik.atomcv.rendering.template.HexColor;
import com.mustafatetik.atomcv.rendering.template.TemplateCustomization;
import com.mustafatetik.atomcv.shared.security.ProfileOwned;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One named set of appearance settings, saved (Bolum 13's
 * {@code template_customizations}, Bolum 33.2).
 *
 * <p><strong>The table has existed since V1 and nothing wrote to it.</strong>
 * Layer B's sliders lived — and still live — in
 * {@code profiles.preferences.appearance}, which is right for the one set a
 * person is working with: it is a settings form, one per profile, and it needs
 * no id. What it cannot be is <em>several</em>, and Bolum 33.2 is about a
 * person who keeps a compact set for a dense CV and a roomier one for a short
 * one.
 *
 * <p>So the two are not rivals: the preference is the working set, and these
 * are the ones somebody named and kept. A generation may ask for one by id
 * (Bolum 14.4's {@code options.customizationId}); saying nothing still gets
 * the preference.
 *
 * <p><strong>No {@code version} column, and that is deliberate</strong>
 * (EK D.6.2): ETags cover the six tables V1 gave one to, and this is not among
 * them. A customization is a small whole object a person replaces rather than
 * a row two tabs edit a field of at once.
 *
 * <p><strong>{@code fixed_costs}, {@code page_text_height_pt} and
 * {@code measured_at} stay null.</strong> V12's {@code template_capacities} is
 * where a measured capacity lives now, keyed by the geometry rather than by
 * the row that asked for it — which is the better key, because two people who
 * pick the same font and margin have the same page and should not pay for two
 * compilations. The three columns are kept rather than dropped: a migration
 * that removes them buys nothing and Bolum 13 is not rewritten for it.
 */
@Entity
@Table(name = "template_customizations")
public class SavedCustomization implements ProfileOwned {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(nullable = false, updatable = false)
    private UUID profileId;

    /** What the person called it. User content; unique within the profile. */
    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String baseTemplateId;

    /** The renderer version this was saved against (Bolum 16.3). */
    @Column(nullable = false)
    private short templateVersion;

    /**
     * The half of {@link TemplateCustomization} that is not the template id,
     * in the shape Bolum 13 names: {@code fontFamily}, {@code fontSizePt},
     * {@code marginInches}, {@code lineSpacing}, {@code accentColor}.
     *
     * <p>A {@code Map} rather than a typed record, for the reason the column
     * is JSONB at all: a setting added later has to be readable out of a row
     * written before it existed, and the value object below is what turns it
     * into something typed at the point of use.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> params = new LinkedHashMap<>();

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected SavedCustomization() {
        // JPA
    }

    public SavedCustomization(UUID profileId, String name, TemplateCustomization settings,
            short templateVersion) {

        this.profileId = Objects.requireNonNull(profileId, "profileId");
        this.name = requireName(name);
        this.templateVersion = templateVersion;
        apply(settings);
    }

    /** Replaces the settings wholesale; a customization is a small whole object. */
    public final void apply(TemplateCustomization settings) {
        Objects.requireNonNull(settings, "settings");
        this.baseTemplateId = settings.baseTemplateId();
        // Ordered, because this is a JSONB column and the JDK's immutable maps
        // iterate in an order salted per JVM run (CLAUDE.md).
        var written = new LinkedHashMap<String, Object>();
        written.put("fontFamily", settings.fontFamily().wireValue());
        written.put("fontSizePt", settings.fontSizePt());
        written.put("marginInches", settings.marginInches());
        written.put("lineSpacing", settings.lineSpacing());
        written.put("accentColor", settings.accentColor().value());
        this.params = written;
    }

    /**
     * @return the settings this row holds, with anything it does not carry
     *         taken from the base template's own defaults. A row written
     *         before a setting existed is readable rather than broken
     */
    public TemplateCustomization settings() {
        TemplateCustomization base = com.mustafatetik.atomcv.rendering.template.TemplateRegistry
                .defaultsFor(baseTemplateId);
        return new TemplateCustomization(
                baseTemplateId,
                params.get("fontFamily") instanceof String family
                        ? FontFamily.fromWireValue(family) : base.fontFamily(),
                params.get("fontSizePt") instanceof Number size
                        ? size.doubleValue() : base.fontSizePt(),
                params.get("marginInches") instanceof Number margin
                        ? margin.doubleValue() : base.marginInches(),
                params.get("lineSpacing") instanceof Number spacing
                        ? spacing.doubleValue() : base.lineSpacing(),
                params.get("accentColor") instanceof String colour
                        ? HexColor.of(colour) : base.accentColor());
    }

    public void rename(String newName) {
        this.name = requireName(newName);
    }

    private static String requireName(String value) {
        Objects.requireNonNull(value, "name");
        String trimmed = value.strip();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("A customization has a name");
        }
        return trimmed;
    }

    public UUID getId() {
        return id;
    }

    @Override
    public UUID getProfileId() {
        return profileId;
    }

    public String getName() {
        return name;
    }

    public String getBaseTemplateId() {
        return baseTemplateId;
    }

    public short getTemplateVersion() {
        return templateVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
