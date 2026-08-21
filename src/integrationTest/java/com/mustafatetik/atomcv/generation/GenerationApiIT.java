package com.mustafatetik.atomcv.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mustafatetik.atomcv.AbstractIntegrationTest;
import com.mustafatetik.atomcv.compilation.CompilationException;
import com.mustafatetik.atomcv.compilation.CompiledDocument;
import com.mustafatetik.atomcv.compilation.LatexCompilerClient;
import com.mustafatetik.atomcv.profile.domain.Atom;
import com.mustafatetik.atomcv.profile.domain.AtomKind;
import com.mustafatetik.atomcv.profile.domain.AtomVariant;
import com.mustafatetik.atomcv.profile.domain.Entry;
import com.mustafatetik.atomcv.profile.domain.Profile;
import com.mustafatetik.atomcv.profile.domain.Section;
import com.mustafatetik.atomcv.profile.domain.SectionKind;
import com.mustafatetik.atomcv.profile.domain.content.RichContent;
import com.mustafatetik.atomcv.shared.error.CompilationFailureKind;
import com.mustafatetik.atomcv.shared.security.LocalDevCurrentUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The endpoint that finally hands over a PDF (XI-A.3 Adim 1.8).
 *
 * <p>The compiler is stubbed here so the whole chain — resolver, measurement,
 * scoring, selection, rendering, error presentation — can be exercised without
 * a two-gigabyte image. {@code GeneralCvIT} runs the same path through the
 * real one.
 */
@AutoConfigureMockMvc
class GenerationApiIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private LocalDevCurrentUser localUser;

    @Autowired
    private TransactionTemplate tx;

    @PersistenceContext
    private EntityManager em;

    @MockitoBean
    private LatexCompilerClient compiler;

    @BeforeEach
    void startFromAnEmptyProfile() {
        localUser.ensureUserExists();
        jdbc.update("DELETE FROM profiles WHERE user_id = ?", LocalDevCurrentUser.DEV_USER_ID);
    }

    @Test
    void aProfileComesBackAsAPdfAttachment() throws Exception {
        seedCareer();
        when(compiler.compile(anyString())).thenReturn(pdf(1));

        byte[] body = mvc.perform(post("/api/v1/generations/general"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PDF_VALUE))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.startsWith("attachment; filename=\"atomcv-cv-")))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(new String(body, StandardCharsets.ISO_8859_1)).startsWith("%PDF-");
    }

    @Test
    void theRequestBodyIsOptionalAndOverridesThePageLimit() throws Exception {
        seedCareer();
        when(compiler.compile(anyString())).thenReturn(pdf(2));

        // Two pages is a failure against the profile's default of one...
        mvc.perform(post("/api/v1/generations/general"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PAGE_LIMIT_EXCEEDED"));

        // ...and exactly what was asked for when the request says so.
        mvc.perform(post("/api/v1/generations/general")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maxPages\":2}"))
                .andExpect(status().isOk());
    }

    /** Design principle 5: an empty profile costs no compilation at all. */
    @Test
    void anEmptyProfileIsRefusedBeforeAnythingIsCompiled() throws Exception {
        mvc.perform(post("/api/v1/generations/general"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_PROFILE"))
                .andExpect(jsonPath("$.params.missing[0]").value("atoms"))
                .andExpect(jsonPath("$.params.completeness").isNumber())
                .andExpect(jsonPath("$.resolutions[0].action").value("complete_profile"));

        verify(compiler, never()).compile(anyString());
        verify(compiler, never()).measure(anyString());
    }

    @Test
    void aDocumentThatStaysTooLongExplainsWhatWouldHaveWorked() throws Exception {
        seedCareer();
        when(compiler.compile(anyString())).thenReturn(pdf(3));

        mvc.perform(post("/api/v1/generations/general"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PAGE_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.params.actual").value(3))
                .andExpect(jsonPath("$.params.limit").value(1))
                .andExpect(jsonPath("$.resolutions[0].action").value("increase_page_limit"))
                .andExpect(jsonPath("$.resolutions[0].params.maxPages").value(3));
    }

    @Test
    void aCompilerThatIsDownIsAnOutageNotABadProfile() throws Exception {
        seedCareer();
        when(compiler.compile(anyString())).thenThrow(new CompilationException(
                CompilationFailureKind.UNAVAILABLE, "down", "", null));

        mvc.perform(post("/api/v1/generations/general"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("COMPILATION_FAILED"))
                .andExpect(jsonPath("$.params.detail").value("unavailable"))
                .andExpect(jsonPath("$.resolutions[0].action").value("retry"));
    }

    @Test
    void anImpossiblePageLimitIsRejectedBeforeTheProfileIsEvenRead() throws Exception {
        mvc.perform(post("/api/v1/generations/general")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maxPages\":99}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void nothingIsWrittenToTheGenerationsTable() throws Exception {
        // Stage 1 hands over the bytes and keeps nothing (EK D.8.8). A row
        // appearing here means the Stage 2 work landed without its retention
        // rules.
        seedCareer();
        when(compiler.compile(anyString())).thenReturn(pdf(1));

        mvc.perform(post("/api/v1/generations/general")).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM generations", Integer.class))
                .isZero();
    }

    /** Three bullets under one job, already measured so no compiler is needed. */
    private void seedCareer() {
        tx.executeWithoutResult(status -> {
            var profile = new Profile(LocalDevCurrentUser.DEV_USER_ID);
            em.persist(profile);
            UUID profileId = profile.getId();

            var section = new Section(profileId, SectionKind.EXPERIENCE, "Experience", (short) 0);
            em.persist(section);
            var entry = new Entry(profileId, section.getId(), "Backend Engineer", (short) 0);
            entry.setOrganization("Acme");
            entry.setStartDate(java.time.LocalDate.of(2021, 3, 1));
            em.persist(entry);

            for (int index = 0; index < 3; index++) {
                var atom = new Atom(profileId, section.getId(), entry.getId(),
                        AtomKind.BULLET, (short) index);
                em.persist(atom);
                var variant = new AtomVariant(profileId, atom.getId(), "en",
                        RichContent.plain("Built ETL pipelines processing 300K+ rows " + index));
                variant.setPrimary(true);
                variant.recordRenderCost("classic:v1", 25.0, Instant.now());
                em.persist(variant);
            }
        });
    }

    private static CompiledDocument pdf(int pages) {
        return new CompiledDocument("%PDF-1.7 pretend".getBytes(StandardCharsets.UTF_8), pages);
    }
}
