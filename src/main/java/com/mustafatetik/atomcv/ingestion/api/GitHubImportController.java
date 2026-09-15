package com.mustafatetik.atomcv.ingestion.api;

import com.mustafatetik.atomcv.ingestion.github.GitHubImportService;
import com.mustafatetik.atomcv.ingestion.github.GitHubLogin;
import com.mustafatetik.atomcv.ingestion.github.GitHubSuggestion;
import com.mustafatetik.atomcv.profile.service.CallerProfiles;
import com.mustafatetik.atomcv.profile.service.ProfileResolver;
import com.mustafatetik.atomcv.shared.error.ApiErrorResponse;
import com.mustafatetik.atomcv.shared.error.ApiException;
import com.mustafatetik.atomcv.shared.error.ErrorCode;
import com.mustafatetik.atomcv.shared.error.UserFacingError;
import com.mustafatetik.atomcv.shared.ratelimit.RateLimitDecision;
import com.mustafatetik.atomcv.shared.ratelimit.RateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The GitHub half of ingestion, in two steps: look, then choose.
 *
 * <p><strong>The path is {@code /profile/github}, not {@code
 * /ingestion/github}.</strong> The resource map says the latter and the CV
 * import already moved: F-029 settled that the upload is {@code POST
 * /profile/import}, because what these endpoints do is write to a profile. Two
 * ingestion paths under two prefixes would be worse than one deviation, and
 * this is the one recorded.
 *
 * <p><strong>No OAuth, no stored token, no new permission.</strong> Only
 * public data is read, so there is nothing to connect and nothing to keep: a
 * login is enough, and the profile usually already carries one.
 */
@RestController
@RequestMapping("/api/v1/profile/github")
@Tag(name = "GitHub", description = "What a public GitHub account says about somebody's projects")
public class GitHubImportController {

    /**
     * Each call spends up to twelve requests against GitHub's hourly budget,
     * which one deployment shares (the client). Five an hour is more than
     * anyone needs to look at their own repositories twice.
     */
    private static final int LOOKUPS_PER_HOUR = 5;

    private final CallerProfiles callers;
    private final GitHubImportService github;
    private final RateLimiter rateLimiter;

    GitHubImportController(CallerProfiles callers, GitHubImportService github,
            RateLimiter rateLimiter) {
        this.callers = callers;
        this.github = github;
        this.rateLimiter = rateLimiter;
    }

    @Operation(
            operationId = "suggestFromGitHub",
            summary = "What a public GitHub account has that this profile does not",
            description = """
                    Reads the public repositories of one account and offers the                     significant ones. Nothing is written.

                    **No permission is asked for and no token is kept.** Only                     public data is read, which is why this needs neither --                     no provider token is stored anywhere.

                    `username` is optional: without it the account named in the                     profile's own contact block is read, which is the one the                     CV shows an employer.

                    A suggestion carrying `matchedEntryId` is a merge onto a                     project already written about. Applying it adds the                     repository's languages to what those bullets claim and                     puts the link on the entry -- **the sentences are never                     touched**, because the person wrote them about what the                     work was for and GitHub knows what it was written in.

                    One carrying no match would be written as a new project,                     with GitHub's own description as its first line.

                    An account that does not exist, a GitHub that will not                     answer and one with nothing significant in it are the same                     empty list: none of them is something a person can act on.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "What is on offer",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            array = @ArraySchema(
                                    schema = @Schema(implementation = GitHubSuggestion.class)))),
            @ApiResponse(responseCode = "400",
                    description = "VALIDATION_FAILED — `params.fields` is `username`: the "
                            + "request named no account and the profile names none either",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "429",
                    description = "RATE_LIMITED — five an hour, because GitHub's own budget "
                            + "is shared by the whole deployment",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping(path = "/suggestions", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<GitHubSuggestion> suggestions(
            @Valid @RequestBody(required = false) GitHubImportRequest request) {

        ProfileResolver.OwnedProfile owned = callers.owned();
        GitHubLogin login = loginFor(owned, request);
        limit(owned);
        return github.suggest(owned.ref(), login);
    }

    @Operation(
            operationId = "applyGitHubSuggestions",
            summary = "Add the repositories a person picked",
            description = """
                    The rule is "offered, never added automatically", and                     this is the second half of that sentence: nothing is                     written until a request names it.

                    A repository this account no longer has is skipped rather                     than refused -- the list is a moment old and a repository                     can be renamed.

                    One transaction. Half an import is not a smaller import, it                     is a profile somebody has to work out the state of.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "How many were written or merged"),
            @ApiResponse(responseCode = "400",
                    description = "VALIDATION_FAILED — no account to read",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "RATE_LIMITED",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping(path = "/apply", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public GitHubImportResult apply(@Valid @RequestBody GitHubApplyRequest request) {
        ProfileResolver.OwnedProfile owned = callers.owned();
        GitHubLogin login = loginFor(owned, new GitHubImportRequest(request.username()));
        limit(owned);

        Set<String> chosen = new LinkedHashSet<>(request.repositories());
        return new GitHubImportResult(github.apply(owned.ref(), login, chosen));
    }

    /**
     * The account named in the request, or the one the CV carries.
     *
     * <p>Neither is a 404: an account nobody named is a field the client can
     * fill in, which is what {@code VALIDATION_FAILED} says and what the screen
     * can act on.
     */
    private GitHubLogin loginFor(
            ProfileResolver.OwnedProfile owned, GitHubImportRequest request) {

        if (request != null && request.username() != null && !request.username().isBlank()) {
            return GitHubLogin.parse(request.username()).orElseThrow(this::noAccount);
        }
        return github.loginOf(owned.profile()).orElseThrow(this::noAccount);
    }

    /**
     * Per profile rather than per address: the budget being protected is
     * GitHub's, and it is spent by whoever asks — an anonymous session has a
     * profile here just as an account does.
     */
    private void limit(ProfileResolver.OwnedProfile owned) {
        RateLimitDecision allowed = rateLimiter.check(
                "github", owned.ref().id().toString(), LOOKUPS_PER_HOUR, Duration.ofHours(1));
        if (!allowed.allowed()) {
            throw new ApiException(UserFacingError.with(ErrorCode.RATE_LIMITED)
                    .param("resetsAt", allowed.resetsAt())
                    .build());
        }
    }

    private ApiException noAccount() {
        return new ApiException(UserFacingError.with(ErrorCode.VALIDATION_FAILED)
                .param("fields", List.of("username"))
                .build());
    }
}
