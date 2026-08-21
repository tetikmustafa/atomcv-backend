package com.mustafatetik.atomcv.llm.fake;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where the recorded answers live (Bolum 54.2).
 *
 * <p>A filesystem directory rather than a classpath resource, because
 * {@code local-record} <em>writes</em> here while the application is running
 * and nothing can write into a packaged resource. Reading and writing have to
 * name the same place or a recorded fixture would never be replayed.
 *
 * <p>The default points into the test source tree, which is where Bolum 54.2
 * puts them so that the golden set can read the same files. Only the
 * {@code local-*} profiles activate this, so nothing here ships.
 *
 * @param fixtureDir  the directory, relative to the working directory
 * @param synthesize  whether a miss falls back to a schema-shaped answer. Off
 *                    means a missing fixture fails loudly, which is what
 *                    recording a complete set wants.
 */
@ConfigurationProperties(prefix = "atomcv.llm.fake")
public record FakeLlmProperties(Path fixtureDir, boolean synthesize) {

    public FakeLlmProperties {
        fixtureDir = fixtureDir == null
                ? Path.of("src", "test", "resources", "fixtures", "llm")
                : fixtureDir;
    }
}
