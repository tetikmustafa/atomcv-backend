package com.mustafatetik.atomcv.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Every meter this system publishes, against the row of Bolum 48.3 it answers.
 *
 * <p><strong>Why a catalogue and not a count.</strong> Bolum 48.3 is a table of
 * seven categories and it is prose — nothing can be generated from it, and
 * nothing fails when a row quietly stops being answered. A metric is also the
 * easiest thing in a codebase to lose: renaming one breaks no build, deleting
 * one breaks no test, and the loss surfaces months later as a dashboard panel
 * that has been flat since a refactor nobody connected to it.
 *
 * <p>So this reads the meter names out of the source and holds them against a
 * list with a reason beside each. Adding a meter fails until it is written
 * down, which is the point: the line one has to add is the line that says which
 * question it answers. Deleting one fails too, and that is the failure worth
 * having.
 *
 * <p>The scan reads the argument list of every {@code counter(}, {@code timer(},
 * {@code summary(} or {@code gauge(} call and takes the dotted lowercase
 * literals in it, plus any constant declared in the same file whose value has
 * that shape. Dotted is what separates a meter name from a tag name, and
 * reading the whole argument list rather than only its first token is what
 * finds the two real spellings here: a ternary choosing between two names, and
 * a private helper being handed one. <strong>A name assembled at runtime would
 * be missed</strong>, and that is a thing not to do rather than a hole to widen
 * this for — a name nobody can grep for is a name nobody can find in a
 * dashboard either.
 */
class MetricCatalogueTest {

    /**
     * The meter, and the row of Bolum 48.3 it answers. Two meters may answer
     * one row: a rate needs a numerator and a denominator, and those are
     * separate series.
     */
    private static final Map<String, String> CATALOGUE = new TreeMap<>(Map.ofEntries(
            // Pipeline -- "Faz bazinda p50/p95 gecikme, basari orani"
            Map.entry("job.phase", "per-phase latency, with p50/p95 set in application.yml"),
            Map.entry("job.run", "the success rate: the outcome tag is the ratio"),
            Map.entry("generation.compile.attempts",
                    "how often the compile loop had to go round again"),

            // Secim -- "Butce doluluk orani, sayfa sapma orani, tahmin kullanim orani"
            Map.entry("generation.budget.overshoot", "a selection that did not fit the page"),
            Map.entry("generation.pages.drift",
                    "predicted pages against printed pages (Bolum 23)"),
            Map.entry("generation.scoring.weights", "which weight set Faz B ran with"),
            Map.entry("generation.ats.clean", "a document that passed the ATS check"),
            Map.entry("generation.ats.defect", "and one that did not"),

            // LLM -- "Saglayici fallback orani, sema hata orani, token maliyeti/gun"
            Map.entry("llm.chain.answers",
                    "the fallback rate: the position tag is the ratio (Bolum 27.3)"),
            Map.entry("llm.chain.exhausted", "walks that ran out of providers"),
            Map.entry("llm.calls", "the schema error rate: the outcome tag is the ratio"),
            Map.entry("llm.unpriced_calls",
                    "calls whose model the price table does not know, which is what makes "
                            + "a cost report quietly low"),

            // Dogrulama -- "Yeniden yazim red orani, red nedenleri dagilimi"
            Map.entry("rewrite.attempts", "the denominator of the rejection rate"),
            Map.entry("rewrite.refusals", "the numerator, and the issue tag is the distribution"),
            Map.entry("rewrite.unreachable",
                    "attempts that never reached a model, counted apart from the refusals"),

            // Kullanici -- "Manuel duzenleme orani"
            Map.entry("selection.manual_include", "an atom the person put back (Bolum 24)"),
            Map.entry("selection.manual_exclude", "and one they took out"),

            // Sistem -- "kuyruk bekleme suresi". CPU, RAM and disk come from
            // Micrometer's own binders and are not declared here.
            Map.entry("job.queue.wait", "how long work waited before it ran (Bolum 50.4)"),

            // E-posta -- "Teslimat orani, bounce orani"
            Map.entry("email.sent", "whether the provider accepted the message"),
            Map.entry("email.events",
                    "what actually happened to it, which only the webhook knows"),

            // Bolum 44.3's brake. Not one of 48.3's rows, and watched for its
            // own reasons.
            Map.entry("anomaly.budget_exceeded", "Bolum 44.3: the daily budget went over"),
            Map.entry("anomaly.heavy_user", "Bolum 44.3: one account ran away with it"),
            Map.entry("anomaly.signup_burst", "Bolum 44.3: sign-ups arriving too fast")));

    /** Where an argument list starts. Unqualified too: a private helper is one. */
    private static final Pattern CALL =
            Pattern.compile("\\b(?:counter|timer|summary|gauge)\\s*\\(");

    /** Dotted and lowercase, which a meter name is and a tag name is not. */
    private static final Pattern NAME =
            Pattern.compile("\"([a-z][a-z0-9_]*(?:\\.[a-z][a-z0-9_]*)+)\"");

    private static final Pattern IDENTIFIER = Pattern.compile("\\b([A-Z][A-Z0-9_]{2,})\\b");

    private static final Pattern CONSTANT = Pattern.compile(
            "static final String ([A-Z][A-Z0-9_]*)\\s*=\\s*\"([a-z][a-z0-9._]*)\"");

    @Test
    void everyMeterInTheCodeIsInTheCatalogue() {
        assertThat(published())
                .as("""
                        A meter is published that nothing wrote down. Add it to \
                        CATALOGUE with the row of Bolum 48.3 it answers -- and if it \
                        answers none of them, that is worth knowing before it reaches \
                        a dashboard.\
                        """)
                .isSubsetOf(CATALOGUE.keySet());
    }

    @Test
    void everyMeterInTheCatalogueIsStillPublished() {
        assertThat(CATALOGUE.keySet())
                .as("""
                        A catalogued meter is no longer published anywhere. Renaming \
                        one breaks no build and deleting one breaks no test, so this \
                        is the only place it fails -- either put it back or take the \
                        line out on purpose.\
                        """)
                .isSubsetOf(published());
    }

    /** No two names may differ only in how they were spelled. */
    @Test
    void thenamesAreOneNamingConvention() {
        assertThat(CATALOGUE.keySet())
                .allSatisfy(name -> assertThat(name)
                        .as("dots between words, underscores inside one")
                        .matches("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+"));
    }

    private static Set<String> published() {
        var found = new TreeSet<String>();
        for (Path file : sources()) {
            String source = read(file);
            var constants = new TreeMap<String, String>();
            Matcher declared = CONSTANT.matcher(source);
            while (declared.find()) {
                constants.put(declared.group(1), declared.group(2));
            }

            Matcher call = CALL.matcher(source);
            while (call.find()) {
                String arguments = argumentsAt(source, call.end() - 1);
                Matcher literal = NAME.matcher(arguments);
                while (literal.find()) {
                    found.add(literal.group(1));
                }
                Matcher named = IDENTIFIER.matcher(arguments);
                while (named.find()) {
                    String value = constants.get(named.group(1));
                    if (value != null && value.contains(".")) {
                        found.add(value);
                    }
                }
            }
        }
        return found;
    }

    /**
     * The text between one {@code (} and the {@code )} that closes it.
     *
     * <p>Balanced rather than up to the next {@code )}: a call whose tag is
     * itself a call — {@code status.name().toLowerCase(ROOT)} is one — would
     * otherwise be cut in half and the name after it lost.
     */
    private static String argumentsAt(String source, int openParen) {
        int depth = 0;
        for (int at = openParen; at < source.length(); at++) {
            char character = source.charAt(at);
            if (character == '(') {
                depth++;
            } else if (character == ')') {
                depth--;
                if (depth == 0) {
                    return source.substring(openParen + 1, at);
                }
            }
        }
        return "";
    }

    private static List<Path> sources() {
        try (Stream<Path> tree = Files.walk(Path.of("src", "main", "java"))) {
            return tree.filter(path -> path.toString().endsWith(".java")).toList();
        } catch (IOException unreadable) {
            throw new AssertionError("Could not walk the main sources", unreadable);
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            throw new AssertionError("Could not read " + file, unreadable);
        }
    }
}
