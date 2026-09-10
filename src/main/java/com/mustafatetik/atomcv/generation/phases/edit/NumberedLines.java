package com.mustafatetik.atomcv.generation.phases.edit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What the person can be talking about, numbered (Bolum 24.2).
 *
 * <p>This is the piece that makes an invented atom id impossible rather than
 * merely detectable. The model is shown numbers and answers with numbers; the
 * ids never leave this object. A number out of range is a number, and a number
 * is checkable in a way a UUID is not — {@code 47} against a list of eleven is
 * obviously wrong, while a well-formed UUID that names nothing looks exactly
 * like one that names something until it is looked up.
 *
 * <p>The page comes first and the held-back lines after it, because that is
 * the order the person is looking at: what they can see, then what they were
 * told did not fit. Numbering restarts at one and runs through both, so a
 * single number identifies a line without the model having to say which list
 * it came from.
 *
 * <p>Order is fixed by the caller and kept, because the same generation has to
 * produce the same prompt twice — a numbering that moved between two reads
 * would make one recorded answer mean two different things (Bolum 53.3).
 */
public final class NumberedLines {

    private final Map<Integer, UUID> idByNumber;
    private final List<String> onThePage;
    private final List<String> heldBack;

    private NumberedLines(
            Map<Integer, UUID> idByNumber, List<String> onThePage, List<String> heldBack) {

        this.idByNumber = idByNumber;
        this.onThePage = onThePage;
        this.heldBack = heldBack;
    }

    /**
     * @param page     the lines printed, in the order they are printed
     * @param withheld the lines that exist and did not fit, best first
     */
    public static NumberedLines of(List<Line> page, List<Line> withheld) {
        var idByNumber = new LinkedHashMap<Integer, UUID>();
        var printed = new ArrayList<String>();
        var held = new ArrayList<String>();

        int number = 1;
        for (Line line : page) {
            idByNumber.put(number, line.atomId());
            printed.add(number + ". " + line.text());
            number++;
        }
        for (Line line : withheld) {
            idByNumber.put(number, line.atomId());
            held.add(number + ". " + line.text());
            number++;
        }
        return new NumberedLines(idByNumber, List.copyOf(printed), List.copyOf(held));
    }

    public boolean has(int number) {
        return idByNumber.containsKey(number);
    }

    public UUID idAt(int number) {
        UUID atomId = idByNumber.get(number);
        if (atomId == null) {
            throw new IllegalArgumentException("Number " + number + " was never offered");
        }
        return atomId;
    }

    public int size() {
        return idByNumber.size();
    }

    /**
     * Everything that goes inside the fence, as one block of text.
     *
     * <p>The person's sentence goes in with the lines rather than in the
     * system half, and that is Bolum 43.1: it is their writing, so it is data.
     * A prompt that put it outside the fence would be one instruction away
     * from being rewritten by whoever typed it.
     */
    public String asPromptData(String instruction) {
        var block = new StringBuilder();
        block.append("request: ").append(instruction).append("\n\n");
        block.append("onThePage:\n");
        block.append(onThePage.isEmpty() ? "(none)\n" : String.join("\n", onThePage) + "\n");
        block.append("\nheldBack:\n");
        block.append(heldBack.isEmpty() ? "(none)\n" : String.join("\n", heldBack) + "\n");
        return block.toString();
    }

    /** Shape only: every line here is the user's own writing (absolute rule 4). */
    @Override
    public String toString() {
        return "NumberedLines[page=" + onThePage.size() + ", heldBack=" + heldBack.size() + "]";
    }

    /**
     * One line, as the model will see it.
     *
     * @param text what it says, already resolved to the wording this CV
     *             printed — not today's, which the person may have edited
     *             since (EK D.6.3)
     */
    public record Line(UUID atomId, String text) {
    }
}
