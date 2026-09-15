package com.mustafatetik.atomcv.ingestion.github;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The filter: which repositories are worth putting on a CV.
 *
 * <p>The question it answers is not "is this good code". It is "has anybody
 * done anything with this" — a star, a description, a subject label — because
 * the alternative is offering somebody the forty repositories they made while
 * following a tutorial.
 */
class GitHubRepositoryTest {

    private final Locale original = Locale.getDefault();

    @AfterEach
    void restoreTheLocale() {
        Locale.setDefault(original);
    }

    @Test
    void arealProjectIsOffered() {
        assertThat(repository("order-management-system")
                .described("Microservice order pipeline").isSignificant()).isTrue();
    }

    /** Somebody else's work, and work the person has closed. */
    @Test
    void aforkAndAnarchiveAreNot() {
        assertThat(repository("linux").described("kernel").asFork().isSignificant()).isFalse();
        assertThat(repository("old-api").described("retired").archived().isSignificant())
                .isFalse();
    }

    /** The size floor: below it a repository is a file. */
    @Test
    void asingleFileIsNot() {
        assertThat(repository("gist").described("a snippet").sized(4).isSignificant()).isFalse();
    }

    /**
     * The shapes are named: hello-world, test, learning-*. A repository called
     * after a lesson is a lesson.
     */
    @Test
    void alessonIsNot() {
        assertThat(repository("hello-world").described("first commit").isSignificant()).isFalse();
        assertThat(repository("react-tutorial").described("following along").isSignificant())
                .isFalse();
        assertThat(repository("learning-go").described("notes").isSignificant()).isFalse();
    }

    /**
     * <strong>Absolute rule 7, and it bites here.</strong> On a Turkish default
     * locale {@code "TEST".toLowerCase()} is "tesT" -- the dotted capital I
     * lowercases to "i" with a dot, which does not equal the ASCII "i" the
     * list holds. A filter written to catch a repository called TEST would let
     * it through, on that machine only.
     */
    @Test
    void aturkishDefaultLocaleDoesNotLetAlessonThrough() {
        Locale.setDefault(Locale.forLanguageTag("tr"));

        assertThat(repository("TUTORIAL").described("x").isSignificant()).isFalse();
        assertThat(repository("PRACTICE").described("x").isSignificant()).isFalse();
    }

    /**
     * Nothing has been done with it: no star, no description, no topic. That
     * is the one the commit count and README were there to catch, and the
     * listing answers the same question for free.
     */
    @Test
    void arepositoryNobodyHasTouchedIsNot() {
        assertThat(repository("scratchpad-2").isSignificant()).isFalse();
    }

    @Test
    void astarIsEnoughOnItsOwn() {
        assertThat(repository("tiny-lib").starred(3).isSignificant()).isTrue();
    }

    private static Builder repository(String name) {
        return new Builder(name);
    }

    /** A builder, because most of these differ in one field out of ten. */
    private static final class Builder {

        private final String name;
        private String description;
        private boolean fork;
        private boolean archived;
        private int sizeKb = 800;
        private int stars;
        private List<String> topics = List.of();

        private Builder(String name) {
            this.name = name;
        }

        Builder described(String text) {
            this.description = text;
            return this;
        }

        Builder asFork() {
            this.fork = true;
            return this;
        }

        Builder archived() {
            this.archived = true;
            return this;
        }

        Builder sized(int kb) {
            this.sizeKb = kb;
            return this;
        }

        Builder starred(int count) {
            this.stars = count;
            return this;
        }

        boolean isSignificant() {
            return new GitHubRepository(name, description, "https://example.com/" + name,
                    fork, archived, sizeKb, stars, "Java", topics, List.of())
                    .isSignificant();
        }
    }
}
