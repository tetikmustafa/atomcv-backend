package com.mustafatetik.atomcv.ingestion.extraction;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * A LaTeX source read as the prose inside it (Bolum 31.3).
 *
 * <p><strong>Nothing here compiles anything.</strong> A {@code .tex} upload is
 * text we simplify with regular expressions; handing it to a compiler would be
 * handing an arbitrary program to a machine, which is what absolute rule 8 and
 * the isolated container exist to bound in the one place we do it on purpose.
 *
 * <p>The simplification keeps arguments and drops the commands around them:
 * {@code \textbf{Ada Lovelace}} is a name, and a stripper that dropped the
 * braces along with the command would lose exactly the words that matter. What
 * it does drop whole is the preamble — configuration, which would otherwise
 * reach the model as a page of package declarations.
 */
@Component
class TexTextExtractor implements TextExtractor {

    /** Everything before it is configuration, not content. */
    private static final Pattern DOCUMENT_START =
            Pattern.compile("\\\\begin\\s*\\{document\\}");

    /** An unescaped percent sign to the end of its line. */
    private static final Pattern COMMENT = Pattern.compile("(?<!\\\\)%.*");

    /**
     * Commands whose argument is not prose, removed along with it.
     *
     * <p>A package declaration and a cross-reference label are the shape of
     * the problem: keeping the argument would put "amsmath" in a CV.
     */
    private static final Pattern COMMAND_WITH_DISCARDED_ARGUMENT = Pattern.compile(
            "\\\\(usepackage|documentclass|label|ref|cite|includegraphics|input|include"
                    + "|hypersetup|geometry|definecolor|newcommand|renewcommand|bibliography"
                    + "|bibliographystyle|pagestyle|vspace|hspace|setlength)"
                    + "\\s*(\\[[^\\]]*\\])?\\s*(\\{[^{}]*\\})*");

    /** An environment's opening and closing marks, which are structure, not words. */
    private static final Pattern ENVIRONMENT_MARKER =
            Pattern.compile("\\\\(begin|end)\\s*\\{[^{}]*\\}\\s*(\\[[^\\]]*\\])?");

    /** Any remaining command, keeping whatever it wrapped. */
    private static final Pattern COMMAND_KEEPING_ARGUMENT =
            Pattern.compile("\\\\[a-zA-Z@]+\\s*(\\[[^\\]]*\\])?\\s*\\{([^{}]*)\\}");

    /** A command with no argument at all, a line break among them. */
    private static final Pattern BARE_COMMAND = Pattern.compile("\\\\\\\\|\\\\[a-zA-Z@]+\\*?");

    /** What LaTeX escapes, put back as the character a reader sees. */
    private static final Pattern ESCAPED_CHARACTER = Pattern.compile("\\\\([%$&#_{}])");

    private static final Pattern BLANK_LINES = Pattern.compile("\n{3,}");

    private static final Pattern TRAILING_SPACE = Pattern.compile("[ \t]+(?=\n)");

    @Override
    public List<DocumentFormat> formats() {
        return List.of(DocumentFormat.TEX);
    }

    @Override
    public String extract(byte[] bytes) {
        String source = new String(bytes, StandardCharsets.UTF_8);
        Matcher start = DOCUMENT_START.matcher(source);
        if (start.find()) {
            source = source.substring(start.end());
        }
        String text = COMMENT.matcher(source).replaceAll("");
        text = COMMAND_WITH_DISCARDED_ARGUMENT.matcher(text).replaceAll("");
        text = ENVIRONMENT_MARKER.matcher(text).replaceAll("");
        // Twice, because a command may wrap a command: a bold emphasised name
        // is one pass away from being a name after the inner one is unwrapped.
        // Two covers the nesting CVs actually use; looping to a fixed point
        // would spend the rest of the passes on documents that do not exist.
        text = COMMAND_KEEPING_ARGUMENT.matcher(text).replaceAll("$2");
        text = COMMAND_KEEPING_ARGUMENT.matcher(text).replaceAll("$2");
        text = BARE_COMMAND.matcher(text).replaceAll("\n");
        text = ESCAPED_CHARACTER.matcher(text).replaceAll("$1");
        text = text.replace("{", "").replace("}", "").replace("~", " ");
        text = TRAILING_SPACE.matcher(text).replaceAll("");
        return BLANK_LINES.matcher(text).replaceAll("\n\n").strip();
    }
}
