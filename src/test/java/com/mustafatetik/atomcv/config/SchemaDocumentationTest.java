package com.mustafatetik.atomcv.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Every applied migration is named in the chapter that documents the schema.
 *
 * <p><strong>Written because the chapter had stopped following.</strong>
 * Section 13 calls itself the complete schema and its block is
 * {@code V1__initial_schema.sql}; everything after it went in as a new file,
 * because an applied migration is never edited, and six of those files reached
 * the database without reaching the chapter. A reader who stopped at the block
 * came away believing {@code profiles.user_id} is {@code NOT NULL}, that
 * {@code sections.layout} takes four values, and that no table called
 * {@code template_capacities} exists. All three were wrong and nothing said
 * so.
 *
 * <p><strong>The rule is in the chapter and this is what checks it.</strong>
 * "A new migration adds a row here, in the same commit" is a sentence, and a
 * sentence nothing enforces is how the last six were missed. The check is the
 * file name and not the content on purpose: a name is exact, it never changes
 * once applied, and it is the thing a reader searches for. What the migration
 * did still has to be written by a person — but it cannot be forgotten
 * silently.
 */
class SchemaDocumentationTest {

    private static final Path MIGRATIONS = Path.of("src/main/resources/db/migration");
    private static final Path CHAPTER = Path.of("docs/spec/04-data-model.md");

    @Test
    void everyMigrationIsNamedInTheDataModelChapter() {
        String chapter = read(CHAPTER);

        assertThat(migrations())
                .as("section 13.2 carries a row per migration; add one for the new file")
                .allSatisfy(migration -> assertThat(chapter).contains(migration));
    }

    /**
     * And the chapter names no file that is not there, which is the failure
     * that put {@code V2__add_template_customizations} and
     * {@code V3__add_content_version} in section 16.1 for a stage: neither ever
     * existed, and both sent a reader looking for a file to open.
     *
     * <p><strong>Except inside a blockquote</strong>, where this chapter keeps
     * its corrections — and a correction has to be able to quote the name it is
     * correcting. Naming the mistake is how the next reader recognises it;
     * a rule that forbade that would force the record to describe the wrong
     * name without writing it, which is how a correction stops being findable.
     */
    @Test
    void theChapterInventsNoMigration() {
        List<String> real = migrations();

        assertThat(read(CHAPTER).lines()
                .filter(line -> !line.stripLeading().startsWith(">"))
                .flatMap(line -> Stream.of(line.split("[^A-Za-z0-9_]+")))
                .filter(word -> word.matches("V[0-9]+__[a-z0-9_]+"))
                .distinct())
                .as("a name written here is a file somebody can open")
                .allSatisfy(named -> assertThat(real)
                        .anySatisfy(file -> assertThat(file).startsWith(named)));
    }

    /** The assertion that keeps the two above from passing on an empty read. */
    @Test
    void bothSidesWereActuallyRead() {
        assertThat(migrations()).hasSizeGreaterThan(10);
        assertThat(read(CHAPTER)).hasSizeGreaterThan(1000);
    }

    /** File names without the extension: {@code V12__a_measured_capacity...}. */
    private static List<String> migrations() {
        try (Stream<Path> files = Files.list(MIGRATIONS)) {
            return files.map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".sql"))
                    .map(name -> name.substring(0, name.length() - ".sql".length()))
                    .sorted()
                    .toList();
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }
}
