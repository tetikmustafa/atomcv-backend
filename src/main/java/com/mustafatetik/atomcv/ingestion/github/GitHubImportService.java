package com.mustafatetik.atomcv.ingestion.github;

import com.mustafatetik.atomcv.profile.domain.Atom;
import com.mustafatetik.atomcv.profile.domain.AtomKind;
import com.mustafatetik.atomcv.profile.domain.Entry;
import com.mustafatetik.atomcv.profile.domain.Profile;
import com.mustafatetik.atomcv.profile.domain.Section;
import com.mustafatetik.atomcv.profile.domain.SectionKind;
import com.mustafatetik.atomcv.profile.domain.SectionLayout;
import com.mustafatetik.atomcv.profile.domain.content.RichContent;
import com.mustafatetik.atomcv.profile.repository.AtomRepository;
import com.mustafatetik.atomcv.profile.repository.EntryRepository;
import com.mustafatetik.atomcv.profile.repository.SectionRepository;
import com.mustafatetik.atomcv.profile.service.AtomDraft;
import com.mustafatetik.atomcv.profile.service.AtomService;
import com.mustafatetik.atomcv.profile.service.EntryDraft;
import com.mustafatetik.atomcv.profile.service.EntryService;
import com.mustafatetik.atomcv.profile.service.SectionDraft;
import com.mustafatetik.atomcv.profile.service.SectionService;
import com.mustafatetik.atomcv.shared.security.ProfileRef;
import com.mustafatetik.atomcv.shared.text.SkillNames;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What GitHub knows about somebody's projects, offered to their profile
 * (Bolum 31.8).
 *
 * <p><strong>Public data, no stored token, no LLM.</strong> Three things this
 * is not, and each is why it can exist at all: Bolum 40.6.1 deliberately does
 * not store a provider token and says one would arrive with key management;
 * Bolum 31.8 asks for no private repository scope; and nothing here writes
 * prose, so there is no fabrication surface and no bill.
 *
 * <p><strong>The narrative stays the person's.</strong> For a repository that
 * matches a project they have already written about, what travels is the
 * skills and the link — Bolum 31.8 is explicit that the description in a CV
 * comes from the CV. For one that matches nothing, the atom written is
 * GitHub's own description, which they also wrote. Nothing in this class
 * composes a sentence.
 *
 * <p><strong>Suggested, never applied.</strong> The two steps are two requests
 * because Bolum 31.8 ends on that sentence.
 */
@Service
public class GitHubImportService {

    private static final Logger log = LoggerFactory.getLogger(GitHubImportService.class);

    /**
     * How alike two names have to be before this offers a merge rather than a
     * new project.
     *
     * <p><strong>Measured rather than guessed, and the measurement moved
     * it.</strong> An earlier 0.86 was picked by eye and the real pairs said
     * no: {@code payments-api} against "Payments API Gateway" -- different
     * work by the same person -- scores 0.92, because Jaro-Winkler pays a
     * prefix bonus and those share their whole prefix. A true match, once both
     * names are normalised, is usually 1.0: "order-management-system" and
     * "Order Management System" are the same string with different
     * punctuation.
     *
     * <p><strong>What this cannot separate, and does not pretend to.</strong>
     * {@code order-mgmt-system} against "Order Management System" is a true
     * match and scores 0.90, under the line and under the near miss above it.
     * No single string distance splits those two, which is why Bolum 31.8
     * names an embedding as well -- and one round trip per repository is a
     * cost this screen does not carry, on a service Bolum 28.4 lets be down.
     *
     * <p>So the line sits where a merge is nearly certain and everything else
     * is offered as a new project, which the person can decline. That
     * asymmetry is the decision: a missed merge shows somebody two entries for
     * one project and they delete one, while a wrong merge puts a link on
     * their description of different work and says nothing.
     */
    static final double MERGE_THRESHOLD = 0.95;

    /** More projects than a one-page CV has room for, and a ceiling on the work. */
    private static final int MAX_SUGGESTIONS = 10;

    private final GitHubRepositories github;
    private final SectionRepository sections;
    private final EntryRepository entries;
    private final AtomRepository atomRows;
    private final SectionService sectionService;
    private final EntryService entryService;
    private final AtomService atoms;

    GitHubImportService(GitHubRepositories github, SectionRepository sections,
            EntryRepository entries, AtomRepository atomRows,
            SectionService sectionService, EntryService entryService, AtomService atoms) {

        this.github = github;
        this.sections = sections;
        this.entries = entries;
        this.atomRows = atomRows;
        this.sectionService = sectionService;
        this.entryService = entryService;
        this.atoms = atoms;
    }

    /**
     * Which account to read, when the request did not name one.
     *
     * <p>The CV is what is asked. {@code profiles.contact.github} is what the
     * extraction read off the page a person actually sends to employers, so it
     * is the account they mean; an OAuth identity is how they signed in, which
     * is a different question with usually the same answer and sometimes not.
     */
    public Optional<GitHubLogin> loginOf(Profile head) {
        return head.getContact() == null
                ? Optional.empty()
                : GitHubLogin.fromProfileUrl(head.getContact().github());
    }

    @Transactional(readOnly = true)
    public List<GitHubSuggestion> suggest(ProfileRef profile, GitHubLogin login) {
        List<Entry> existing = projectEntriesOf(profile);

        var suggestions = new ArrayList<GitHubSuggestion>();
        for (GitHubRepository repository : github.repositoriesOf(login)) {
            if (suggestions.size() == MAX_SUGGESTIONS) {
                break;
            }
            if (repository.isSignificant()) {
                suggestions.add(suggestionOf(repository, existing));
            }
        }
        return List.copyOf(suggestions);
    }

    /**
     * Writes the ones a person picked.
     *
     * <p>One transaction: half an import is not a smaller import, it is a
     * profile somebody has to work out the state of (Bolum 31.6.1's reasoning).
     *
     * @param chosen repository names from a suggestion list. One this account
     *               does not have is skipped rather than refused — the list is
     *               a moment old and a repository can be renamed
     * @return how many projects were written or merged
     */
    @Transactional
    public int apply(ProfileRef profile, GitHubLogin login, Set<String> chosen) {
        if (chosen.isEmpty()) {
            return 0;
        }
        List<Entry> existing = projectEntriesOf(profile);
        int applied = 0;

        for (GitHubRepository repository : github.repositoriesOf(login)) {
            if (!repository.isSignificant() || !chosen.contains(repository.name())) {
                continue;
            }
            GitHubSuggestion suggestion = suggestionOf(repository, existing);
            if (suggestion.isMerge()) {
                merge(profile, suggestion);
            } else {
                write(profile, suggestion);
            }
            applied++;
        }
        // A count, never a name: a repository name is the person's content
        // (absolute rule 4).
        log.info("Applied {} GitHub suggestions", applied);
        return applied;
    }

    private GitHubSuggestion suggestionOf(GitHubRepository repository, List<Entry> existing) {
        Entry best = null;
        double bestScore = 0;
        for (Entry entry : existing) {
            double score = JaroWinkler.similarity(
                    normalised(repository.name()), normalised(entry.getTitle()));
            if (score > bestScore) {
                bestScore = score;
                best = entry;
            }
        }
        boolean merge = best != null && bestScore >= MERGE_THRESHOLD;
        return new GitHubSuggestion(
                repository.name(),
                repository.description(),
                repository.url(),
                repository.stars(),
                skillsOf(repository),
                merge ? best.getId() : null,
                merge ? Math.round(bestScore * 100) / 100.0 : null);
    }

    /**
     * <strong>The merge of Bolum 31.8, and nothing more.</strong> The skills
     * join what the atoms already claim and the link goes on the entry; the
     * sentences are untouched, because the person wrote them about what the
     * work was for and GitHub knows only what it was written in.
     */
    private void merge(ProfileRef profile, GitHubSuggestion suggestion) {
        Optional<Entry> found = entries.findById(profile, suggestion.matchedEntryId());
        if (found.isEmpty()) {
            return;
        }
        Entry entry = found.get();
        if (entry.getUrl() == null || entry.getUrl().isBlank()) {
            // Only when there is not one already: somebody who linked
            // elsewhere linked there on purpose (Principle 8).
            entry.setUrl(suggestion.url());
            entries.save(profile, entry);
        }

        for (Atom atom : atoms.list(profile, entry.getSectionId(), entry.getId())) {
            var claimed = new LinkedHashSet<>(atom.getSkills());
            if (claimed.addAll(suggestion.skills())) {
                atom.setSkills(List.copyOf(claimed));
                // Bolum 31.8: a language GitHub reports is a fact about the
                // repository rather than a claim somebody typed.
                atom.setVerified(true);
                atomRows.save(profile, atom);
            }
        }
    }

    /** A project nobody has written about yet, in the person's own two lines. */
    private void write(ProfileRef profile, GitHubSuggestion suggestion) {
        Section projects = projectSection(profile);

        Entry entry = entryService.create(profile, new EntryDraft(
                projects.getId(), suggestion.name(), null, null, null, null,
                suggestion.url(), 0.5f, false, false, (short) 1));

        if (suggestion.description() != null && !suggestion.description().isBlank()) {
            // The only text this class writes, and GitHub's description is the
            // person's own sentence about their own repository (Bolum 31.8:
            // the narrative comes from what they wrote).
            atoms.create(profile, new AtomDraft(
                    projects.getId(), entry.getId(), AtomKind.BULLET,
                    RichContent.plain(suggestion.description()),
                    null, 0.5f, false, false,
                    suggestion.skills(), List.of(), List.of()));
        }
    }

    private Section projectSection(ProfileRef profile) {
        return sections.findAll(profile).stream()
                .filter(section -> section.getKind() == SectionKind.PROJECTS)
                .findFirst()
                .orElseGet(() -> sectionService.create(profile, new SectionDraft(
                        SectionKind.PROJECTS, "Projects", SectionLayout.ENTRY_LIST, false, false)));
    }

    private List<Entry> projectEntriesOf(ProfileRef profile) {
        Set<UUID> projectSections = sections.findAll(profile).stream()
                .filter(section -> section.getKind() == SectionKind.PROJECTS)
                .map(Section::getId)
                .collect(Collectors.toSet());
        return entries.findAll(profile).stream()
                .filter(entry -> projectSections.contains(entry.getSectionId()))
                .toList();
    }

    /** Canonical, so that Faz B compares them against a posting's (Bolum 19.2). */
    private static List<String> skillsOf(GitHubRepository repository) {
        var names = new LinkedHashSet<>(repository.languages());
        if (names.isEmpty() && repository.primaryLanguage() != null) {
            names.add(repository.primaryLanguage());
        }
        return SkillNames.canonicalAll(List.copyOf(names));
    }

    /**
     * A repository name and a project title, made comparable: hyphens and
     * underscores separate words in one where spaces do in the other, and
     * nothing else about them differs.
     */
    static String normalised(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[-_\\s]+", " ");
    }
}
