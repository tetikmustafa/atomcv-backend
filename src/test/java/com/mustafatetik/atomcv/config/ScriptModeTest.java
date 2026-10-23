package com.mustafatetik.atomcv.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Every script under {@code scripts/} is executable in the repository.
 *
 * <p>Cron runs {@code /opt/atomcv/scripts/backup.sh}, the deploy workflow runs
 * {@code ./scripts/deploy.sh} and the Makefile runs {@code
 * ./scripts/measure-template.sh}, all directly. Eleven of thirteen were mode
 * 100644: nothing here fails on that, because Windows has no executable bit
 * and every lane invokes them through {@code sh}. The server would have
 * answered the first deploy and the first nightly backup with "Permission
 * denied" -- the {@code gradlew} lesson, on the files that run unattended.
 *
 * <p>Reads the index through git, so it sees what a clone gets rather than
 * what this checkout's filesystem says. Skipped where git is not on the path.
 */
class ScriptModeTest {

    @Test
    void everyScriptIsCommittedExecutable() throws IOException, InterruptedException {
        List<String> index = gitIndex("scripts");
        assumeTrue(!index.isEmpty(), "git is not available here");

        assertThat(index)
                .filteredOn(line -> line.endsWith(".sh"))
                .isNotEmpty()
                .allSatisfy(line -> assertThat(line)
                        .as("not executable in a clone").startsWith("100755"));
    }

    private static List<String> gitIndex(String path) throws InterruptedException {
        try {
            Process git = new ProcessBuilder("git", "ls-files", "-s", "--", path)
                    .redirectErrorStream(true).start();
            String out = new String(git.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return git.waitFor() == 0 ? out.lines().toList() : List.of();
        } catch (IOException noGit) {
            return List.of();
        }
    }
}
