package com.mustafatetik.atomcv.rendering.latex;

import java.util.Map;

/**
 * The one place text becomes LaTeX-safe (Bolum 22.3).
 *
 * <p>In the previous generation this was a rule in a prompt. It is code now,
 * which means a model cannot get it wrong: every character of user text passes
 * through here on its way into a document, and the ten characters that mean
 * something to TeX come out meaning themselves.
 */
public final class LatexEscaper {

    private static final Map<Character, String> ESCAPES = Map.ofEntries(
            Map.entry('\\', "\\textbackslash{}"),
            Map.entry('&', "\\&"),
            Map.entry('%', "\\%"),
            Map.entry('$', "\\$"),
            Map.entry('#', "\\#"),
            Map.entry('_', "\\_"),
            Map.entry('{', "\\{"),
            Map.entry('}', "\\}"),
            Map.entry('~', "\\textasciitilde{}"),
            Map.entry('^', "\\textasciicircum{}"));

    private LatexEscaper() {
    }

    public static String escape(String text) {
        if (text == null) {
            return "";
        }
        var out = new StringBuilder(text.length() + 8);
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            String replacement = ESCAPES.get(character);
            out.append(replacement == null ? character : replacement);
        }
        return out.toString();
    }

    /**
     * A URL for {@code \href}, which is stricter: a backslash or a brace there
     * ends the argument early, and percent starts a comment. Anything that is
     * not a plausible URL character is dropped rather than escaped — a broken
     * link is better than a document that will not compile.
     */
    public static String escapeUrl(String url) {
        if (url == null) {
            return "";
        }
        var out = new StringBuilder(url.length());
        for (int index = 0; index < url.length(); index++) {
            char character = url.charAt(index);
            if (character == '%' || character == '#' || character == '&') {
                out.append('\\').append(character);
            } else if (character > 32 && character < 127
                    && character != '\\' && character != '{' && character != '}') {
                out.append(character);
            }
        }
        return out.toString();
    }
}
