package com.mustafatetik.atomcv.ingestion.github;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/**
 * One repository, offered.
 *
 * <p><strong>Offered and never applied.</strong> That is the whole shape of
 * this feature: GitHub knows what was built and the person knows what it was
 * for, so the machine proposes and the person decides. Nothing here is written
 * until a second request names it.
 *
 * <p><strong>What the merge does, and what it does not.</strong> The narrative
 * stays the person's — for a repository that matches something they have
 * already written about, what travels is the skills and the link. The
 * description in a CV comes from the CV.
 *
 * @param matchedEntryId the project this already looks like, or absent. Absent
 *                       means applying it writes a new project rather than
 *                       adding to one
 * @param confidence     how alike the two names are, published because the
 *                       screen has to decide how loudly to suggest a merge.
 *                       Absent with {@code matchedEntryId}
 * @param skills         the repository's languages, canonical, as Faz B would
 *                       compare them. What applying adds
 * @param description    GitHub's own one-line description. The person wrote it,
 *                       which is the only reason it may become an atom
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "A public repository worth putting on a CV")
public record GitHubSuggestion(
        String name,
        String description,
        String url,
        int stars,
        List<String> skills,
        UUID matchedEntryId,
        Double confidence) {

    public GitHubSuggestion {
        skills = skills == null ? List.of() : List.copyOf(skills);
    }

    public boolean isMerge() {
        return matchedEntryId != null;
    }
}
