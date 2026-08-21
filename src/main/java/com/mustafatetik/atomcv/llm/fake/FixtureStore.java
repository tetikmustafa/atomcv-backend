package com.mustafatetik.atomcv.llm.fake;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mustafatetik.atomcv.llm.gateway.StructuredRequest;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Recorded provider answers on disk (Bolum 54.2).
 *
 * <p>One file per call, named by the prompt, its version and a hash of the
 * user prompt — so a fixture is found again only for the input that produced
 * it, and changing the input misses rather than replaying something unrelated.
 *
 * <p><strong>The file holds the answer, never the prompt.</strong> The answer
 * is derived from the user's own content, so these files are development
 * artifacts and the hash in the name is what stands in for the input
 * (absolute rule 4 covers what may be logged; this is neither logged nor
 * shipped).
 */
public class FixtureStore {

    private final Path root;
    private final ObjectMapper json;

    public FixtureStore(Path root, ObjectMapper json) {
        this.root = root;
        this.json = json;
    }

    public Optional<JsonNode> find(StructuredRequest<?> request) {
        var file = pathFor(request);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(json.readTree(Files.readString(file, StandardCharsets.UTF_8)));
        } catch (IOException e) {
            throw new UncheckedIOException("Unreadable fixture " + file, e);
        }
    }

    /** Used by {@code local-record}; overwrites, so re-recording is idempotent. */
    public Path save(StructuredRequest<?> request, JsonNode answer) {
        var file = pathFor(request);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file,
                    json.writerWithDefaultPrettyPrinter().writeValueAsString(answer),
                    StandardCharsets.UTF_8);
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not record fixture " + file, e);
        }
    }

    /** {@code {root}/{promptId}/{version}-{hash}.json} */
    Path pathFor(StructuredRequest<?> request) {
        return root.resolve(request.promptId())
                .resolve(request.promptVersion() + "-" + hash(request.userPrompt()) + ".json");
    }

    /**
     * Twelve hex characters of SHA-256. Long enough that two postings do not
     * collide, short enough that the directory stays readable — and a hash
     * rather than the text itself, because the file name would otherwise carry
     * the user's own content.
     */
    private static String hash(String input) {
        try {
            var digest = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 12);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required of every JVM", e);
        }
    }
}
