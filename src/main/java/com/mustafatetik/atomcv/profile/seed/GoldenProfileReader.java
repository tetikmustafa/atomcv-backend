package com.mustafatetik.atomcv.profile.seed;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mustafatetik.atomcv.profile.domain.Atom;
import com.mustafatetik.atomcv.profile.domain.AtomKind;
import com.mustafatetik.atomcv.profile.domain.AtomVariant;
import com.mustafatetik.atomcv.profile.domain.Contact;
import com.mustafatetik.atomcv.profile.domain.Entry;
import com.mustafatetik.atomcv.profile.domain.Preferences;
import com.mustafatetik.atomcv.profile.domain.Profile;
import com.mustafatetik.atomcv.profile.domain.Section;
import com.mustafatetik.atomcv.profile.domain.Tone;
import com.mustafatetik.atomcv.profile.domain.content.RichContent;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Golden fixtures, as rows in memory (Bolum 51.3).
 *
 * <p>Read by both consumers of the fixtures: the tests that guard the page
 * limit without a database, and the local seeder that puts something in one.
 * Keeping the reader in main rather than in the tests is what stops those two
 * from drifting into different fixture formats.
 *
 * <p>Costs are applied by content hash rather than by variant id, because the
 * ids are minted here and differ every run while the hash is the content's own
 * (EK D.8.9).
 */
public final class GoldenProfileReader {

    /** The five profiles of Bolum 51.3, in the order they are listed there. */
    public static final List<String> NAMES = List.of(
            "senior_backend_tr",
            "junior_frontend_en",
            "career_changer",
            "academic_long",
            "minimal_edge");

    private static final String PROFILE_PATH = "golden/profiles/%s.json";
    private static final String COSTS_PATH = "golden/profiles/%s.costs.json";

    private static final ObjectMapper JSON = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private GoldenProfileReader() {
    }

    /** Every fixture, with its stored costs applied. */
    public static List<GoldenProfile> all(UUID ownerId) {
        return NAMES.stream().map(name -> read(name, ownerId)).toList();
    }

    public static GoldenProfile read(String name, UUID ownerId) {
        GoldenProfileDocument document = load(String.format(PROFILE_PATH, name),
                GoldenProfileDocument.class);
        GoldenProfile profile = materialise(document, ownerId);
        applyCosts(profile, costsOf(name));
        return profile;
    }

    /**
     * What each wording was measured at, under the template key it was measured
     * against. Empty when the file is absent, which is what a newly written
     * fixture looks like until the measuring test has run.
     *
     * <p><strong>The file names its own template.</strong> Reading the key from
     * {@code TemplateCustomization} instead would put this package on
     * {@code rendering}, which already depends on it — and writing the key here
     * as a literal is what went wrong before: it said {@code classic:v1} through
     * a version bump, every lookup missed, selection fell through to the
     * estimate and its 8% margin, and the drift test reported a 30% error in the
     * cost model that was really a fixture reading the wrong shelf.
     *
     * <p>A stale file is now loud rather than quiet. Its costs land under the
     * old key, the running template looks under the new one and finds nothing,
     * and {@code MeasurementDriftIT} says so — which is the failure that led
     * here in the first place, and is worth keeping.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Map<String, Double>> costsOf(String name) {
        String path = String.format(COSTS_PATH, name);
        try (InputStream in = open(path)) {
            return in == null ? Map.of() : JSON.readValue(in, Map.class);
        } catch (IOException malformed) {
            throw new UncheckedIOException("Unreadable costs file: " + path, malformed);
        }
    }

    public static void applyCosts(
            GoldenProfile profile, Map<String, Map<String, Double>> byTemplate) {

        Instant measuredAt = Instant.EPOCH;
        byTemplate.forEach((costKey, costs) -> {
            for (AtomVariant variant : profile.variants()) {
                Double cost = costs.get(variant.getContentHash());
                if (cost != null) {
                    variant.recordRenderCost(costKey, cost, measuredAt);
                }
            }
        });
    }

    private static GoldenProfile materialise(GoldenProfileDocument document, UUID ownerId) {
        var profile = new Profile(ownerId);
        profile.setHeadline(document.headline());
        if (document.sourceLanguage() != null) {
            profile.setSourceLanguage(document.sourceLanguage());
        }
        if (document.enabledLanguages() != null) {
            profile.setEnabledLanguages(document.enabledLanguages());
        }
        if (document.contact() != null) {
            var contact = document.contact();
            profile.setContact(new Contact(contact.name(), contact.email(), contact.phone(),
                    contact.linkedin(), contact.github(), contact.website(), contact.location()));
        }
        if (document.maxPages() != null) {
            var defaults = Preferences.DEFAULTS.defaults();
            profile.setPreferences(new Preferences(Preferences.DEFAULTS.writingStyle(),
                    new Preferences.Defaults(document.maxPages(), defaults.templateId(),
                            defaults.cvLanguage(), defaults.coverLetterLanguage())));
        }

        UUID profileId = profile.getId();
        String language = profile.getSourceLanguage();
        var sections = new ArrayList<Section>();
        var entries = new ArrayList<Entry>();
        var atoms = new ArrayList<Atom>();
        var variants = new ArrayList<AtomVariant>();

        short sectionOrder = 0;
        for (GoldenProfileDocument.Section source : orEmpty(document.sections())) {
            var section = new Section(profileId, source.kind(), source.title(), sectionOrder++);
            if (source.layout() != null) {
                section.setLayout(source.layout());
            }
            section.setAlwaysInclude(Boolean.TRUE.equals(source.alwaysInclude()));
            section.setVerbatim(Boolean.TRUE.equals(source.verbatim()));
            section.setActive(source.active() == null || source.active());
            sections.add(section);

            short atomOrder = 0;
            for (GoldenProfileDocument.Atom loose : orEmpty(source.atoms())) {
                atomOrder = addAtom(loose, profileId, section, null, atomOrder,
                        language, atoms, variants);
            }

            short entryOrder = 0;
            for (GoldenProfileDocument.Entry sourceEntry : orEmpty(source.entries())) {
                var entry = new Entry(profileId, section.getId(),
                        sourceEntry.title(), entryOrder++);
                entry.setOrganization(sourceEntry.organization());
                entry.setLocation(sourceEntry.location());
                entry.setStartDate(sourceEntry.startDate());
                entry.setEndDate(sourceEntry.endDate());
                entry.setUrl(sourceEntry.url());
                if (sourceEntry.importance() != null) {
                    entry.setImportance(sourceEntry.importance());
                }
                if (sourceEntry.minAtoms() != null) {
                    entry.setMinAtoms(sourceEntry.minAtoms());
                }
                entry.setAlwaysInclude(Boolean.TRUE.equals(sourceEntry.alwaysInclude()));
                entry.setVerbatim(Boolean.TRUE.equals(sourceEntry.verbatim()));
                entry.setActive(sourceEntry.active() == null || sourceEntry.active());
                entries.add(entry);

                short bulletOrder = 0;
                for (GoldenProfileDocument.Atom bullet : orEmpty(sourceEntry.atoms())) {
                    bulletOrder = addAtom(bullet, profileId, section, entry, bulletOrder,
                            language, atoms, variants);
                }
            }
        }

        return new GoldenProfile(document.name(), document.description(), profile,
                sections, entries, atoms, variants,
                com.mustafatetik.atomcv.profile.service.ProfileAssembler.assemble(
                        profileId, sections, entries, atoms, variants));
    }

    private static short addAtom(
            GoldenProfileDocument.Atom source,
            UUID profileId,
            Section section,
            Entry entry,
            short displayOrder,
            String profileLanguage,
            List<Atom> atoms,
            List<AtomVariant> variants) {

        AtomKind kind = source.kind() != null
                ? source.kind()
                : (entry == null ? AtomKind.SKILL : AtomKind.BULLET);

        var atom = new Atom(profileId, section.getId(),
                entry == null ? null : entry.getId(), kind, displayOrder);
        if (source.importance() != null) {
            atom.setImportance(source.importance());
        }
        atom.setActive(source.active() == null || source.active());
        atom.setAlwaysInclude(Boolean.TRUE.equals(source.alwaysInclude()));
        atom.setVerbatim(Boolean.TRUE.equals(source.verbatim()));
        atom.setVerified(Boolean.TRUE.equals(source.verified()));
        atom.setSkills(orEmpty(source.skills()));
        atom.setMetrics(orEmpty(source.metrics()));
        atom.setProperNouns(orEmpty(source.properNouns()));
        atoms.add(atom);

        String primaryLanguage = source.language() == null ? profileLanguage : source.language();
        var variant = new AtomVariant(profileId, atom.getId(), primaryLanguage,
                RichContent.plain(source.text()));
        variant.setPrimary(true);
        variants.add(variant);

        // A slot is one language-and-tone pair, and the database allows one
        // wording per slot. Catching a clash here names the fixture that has
        // it; letting it through means the seeder dies on a constraint at
        // startup, which says nothing about which file is wrong.
        var taken = new java.util.HashSet<String>();
        taken.add(slot(primaryLanguage, null));
        for (GoldenProfileDocument.Wording alternative : orEmpty(source.alternatives())) {
            String language = alternative.language() == null
                    ? primaryLanguage
                    : alternative.language();
            if (!taken.add(slot(language, alternative.tone()))) {
                throw new IllegalStateException(
                        "Two wordings of the same atom claim " + slot(language, alternative.tone()));
            }
            var other = new AtomVariant(profileId, atom.getId(), language,
                    RichContent.plain(alternative.text()));
            other.setTone(alternative.tone());
            variants.add(other);
        }

        return (short) (displayOrder + 1);
    }

    private static String slot(String language, Tone tone) {
        return language + "/" + (tone == null ? "neutral" : tone.name());
    }

    private static <T> T load(String path, Class<T> type) {
        try (InputStream in = open(path)) {
            if (in == null) {
                throw new IllegalStateException("No such golden fixture: " + path);
            }
            return JSON.readValue(in, type);
        } catch (IOException malformed) {
            throw new UncheckedIOException("Unreadable golden fixture: " + path, malformed);
        }
    }

    private static InputStream open(String path) {
        return GoldenProfileReader.class.getClassLoader().getResourceAsStream(path);
    }

    private static <T> List<T> orEmpty(List<T> values) {
        return values == null ? List.of() : values;
    }
}
