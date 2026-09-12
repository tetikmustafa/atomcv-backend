package com.mustafatetik.atomcv.generation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mustafatetik.atomcv.AbstractIntegrationTest;
import com.mustafatetik.atomcv.generation.domain.EngineVersion;
import com.mustafatetik.atomcv.generation.domain.Generation;
import com.mustafatetik.atomcv.generation.domain.StoredSelection;
import com.mustafatetik.atomcv.generation.repository.GenerationRepository;
import com.mustafatetik.atomcv.generation.selection.SelectionState;
import com.mustafatetik.atomcv.rendering.template.TemplateCustomization;
import com.mustafatetik.atomcv.shared.security.LocalDevUser;
import com.mustafatetik.atomcv.shared.security.UserContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code GET /generations/{id}/selection} — the list the toggle is drawn from
 * (F-031, Bolum 24.4).
 *
 * <p>The edit endpoint landed without it and the screen could not be built:
 * an edit refuses an atom this generation never weighed, so controls drawn
 * from today's profile would have included buttons that answer 400. The
 * binding tested here is that one — <strong>every id this endpoint publishes
 * is an id the edit accepts</strong> — and it is tested by sending them.
 */
@AutoConfigureMockMvc
class SelectionViewApiIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private LocalDevUser localUser;

    @Autowired
    private GenerationRepository generations;

    private UUID onThePage;
    private UUID heldBack;
    private UUID deletedSince;
    private Generation generation;

    @BeforeEach
    void startFromOneGenerationOfTwoRealAtoms() {
        localUser.ensureUserExists();
        jdbc.update("DELETE FROM jobs");
        jdbc.update("DELETE FROM generations");

        UUID section = jdbc.queryForObject(
                "INSERT INTO sections (profile_id, kind, title, display_order)"
                        + " VALUES (?, 'experience', 'Experience', 0) RETURNING id",
                UUID.class, devProfile());

        onThePage = atom(section, "Moved 300K rows with Microsoft Fabric");
        heldBack = atom(section, "Wrote the deployment runbook");
        // Weighed by the generation and gone from the profile since. Nothing
        // can be done about it, so nothing is offered for it.
        deletedSince = UUID.randomUUID();

        generation = generations.save(user(), aGeneration());
    }

    /** The page first, then what did not fit, each with the line it printed. */
    @Test
    void thelinesAreWhatThisGenerationWeighed() throws Exception {
        mvc.perform(get("/api/v1/generations/" + generation.getId() + "/selection"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generationId").value(generation.getId().toString()))
                .andExpect(jsonPath("$.lines.length()").value(2))
                .andExpect(jsonPath("$.lines[0].atomId").value(onThePage.toString()))
                .andExpect(jsonPath("$.lines[0].onPage").value(true))
                .andExpect(jsonPath("$.lines[0].text")
                        .value("Moved 300K rows with Microsoft Fabric"))
                .andExpect(jsonPath("$.lines[1].atomId").value(heldBack.toString()))
                .andExpect(jsonPath("$.lines[1].onPage").value(false))
                .andExpect(jsonPath("$.lines[1].text").value("Wrote the deployment runbook"));
    }

    /**
     * The whole reason the endpoint exists. A screen drawing its toggles from
     * this list cannot produce the 400 that drawing them from the profile
     * would, and that holds in both directions.
     */
    @Test
    void everyLineItPublishesIsOneTheEditAccepts() throws Exception {
        mvc.perform(post("/api/v1/generations/" + generation.getId() + "/selection")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exclude\":[\"" + onThePage + "\"],"
                                + "\"include\":[\"" + heldBack + "\"]}"))
                .andExpect(status().isAccepted());
    }

    /**
     * An atom this generation weighed and the profile no longer has is left
     * out, and the list is shorter than the snapshot by exactly it — the guard
     * is not known to work until it has been seen to drop something (§ 51.7).
     *
     * <p>The edit endpoint still accepts the id, and the two are not in
     * disagreement: it was weighed, so it is answerable, and the answer is a
     * document identical to the one being looked at. What has no meaning is
     * <em>offering</em> it — there is no line to put back and no line to take
     * off, so there is nothing for a person to press.
     */
    @Test
    void anatomTheProfileNoLongerHasIsNotOffered() throws Exception {
        mvc.perform(get("/api/v1/generations/" + generation.getId() + "/selection"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines.length()").value(2))
                .andExpect(jsonPath("$.lines[*].atomId").value(
                        Matchers.not(Matchers.hasItem(deletedSince.toString()))));
    }

    /**
     * And an id from nowhere is refused by the edit, which is what makes the
     * published list worth drawing from rather than guessing at.
     */
    @Test
    void anatomThisGenerationNeverWeighedIsRefusedByTheEdit() throws Exception {
        mvc.perform(post("/api/v1/generations/" + generation.getId() + "/selection")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exclude\":[\"" + UUID.randomUUID() + "\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    /** Absolute rule 3: these lines are somebody's own writing. */
    @Test
    void anotherAccountsGenerationReadsAsAbsent() throws Exception {
        UUID stranger = jdbc.queryForObject(
                "INSERT INTO users (email) VALUES (?) RETURNING id",
                UUID.class, UUID.randomUUID() + "@example.com");
        UUID theirProfile = jdbc.queryForObject(
                "INSERT INTO profiles (user_id) VALUES (?) RETURNING id", UUID.class, stranger);
        var theirs = generations.save(UserContext.of(stranger),
                new Generation(stranger, theirProfile, options(), snapshot(), engineVersion()));

        mvc.perform(get("/api/v1/generations/" + theirs.getId() + "/selection"))
                .andExpect(status().isNotFound());
    }

    // ── fixtures ─────────────────────────────────────────────────────────

    private UUID atom(UUID section, String text) {
        UUID atomId = jdbc.queryForObject(
                "INSERT INTO atoms (profile_id, section_id, kind, display_order)"
                        + " VALUES (?, ?, 'bullet', 0) RETURNING id",
                UUID.class, devProfile(), section);
        jdbc.update("INSERT INTO atom_variants (profile_id, atom_id, language, content,"
                        + " plain_text, content_hash, is_primary)"
                        + " VALUES (?, ?, 'en', ?::jsonb, ?, ?, true)",
                devProfile(), atomId,
                "{\"v\":1,\"runs\":[{\"t\":\"" + text + "\"}]}", text,
                Integer.toHexString(text.hashCode()));
        return atomId;
    }

    private UUID devProfile() {
        return jdbc.queryForObject("SELECT id FROM profiles WHERE user_id = ?",
                UUID.class, LocalDevUser.DEV_USER_ID);
    }

    private static UserContext user() {
        return UserContext.of(LocalDevUser.DEV_USER_ID);
    }

    private Generation aGeneration() {
        var record = new Generation(LocalDevUser.DEV_USER_ID,
                devProfile(), options(), snapshot(), engineVersion());
        record.setPageCount(1);
        return record;
    }

    private static Map<String, Object> options() {
        var options = new LinkedHashMap<String, Object>();
        options.put("templateId", "classic");
        options.put("maxPages", 1);
        options.put("cvLanguage", "en");
        return options;
    }

    private static EngineVersion engineVersion() {
        return new EngineVersion(EngineVersion.PIPELINE, "default", "classic:v6",
                Map.of("job_analysis", "v1"));
    }

    private StoredSelection snapshot() {
        return StoredSelection.of(
                new SelectionState(
                        List.of(new SelectionState.SelectedAtom(
                                onThePage, null, 0.81, 27.7, false)),
                        List.of(
                                new SelectionState.RejectedAtom(
                                        heldBack, 0.42, SelectionState.RejectionReason.BUDGET),
                                new SelectionState.RejectedAtom(
                                        deletedSince, 0.12,
                                        SelectionState.RejectionReason.BUDGET)),
                        new SelectionState.BudgetBreakdown(648.0, 142.0, 506.0, 27.7)),
                "en", TemplateCustomization.CLASSIC);
    }
}
