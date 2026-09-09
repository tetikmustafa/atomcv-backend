package com.mustafatetik.atomcv.shared.error;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/**
 * What {@code FEATURE_REQUIRES_ACCOUNT} names in its {@code feature} parameter
 * (§ 35.7, EK D.6).
 *
 * <p><strong>A closed set, and it was a bare {@code String} until F-030.</strong>
 * The frontend asked what the whole vocabulary was, could not find it written
 * anywhere, and invented a token that happened to be the one we were about to
 * need — but the catalogue said {@code feature: string}, so a guess and a
 * contract looked identical from the outside. The same reasoning as
 * {@link UnreadablePostingReason} and {@link ResolutionAction}: the client
 * writes a sentence per value, so a value the server can emit and the client
 * has never seen renders as a raw key to a user.
 *
 * <p>Every value here is a capability {@code CapabilitiesResponse} also
 * publishes as a boolean. That is the point of the pairing — the block says
 * what will be refused before it is asked for, and this says which of them was
 * asked for anyway. A value added here without its counterpart in the block is
 * a refusal the client had no way to prevent.
 */
public enum AccountFeature {

    /**
     * The four fields {@code spec/04-data-model.md} labels
     * {@code -- kullanıcı kontrolleri}: {@code importance}, {@code active},
     * {@code always_include}, {@code verbatim}. Paired with
     * {@code canEditAtomControls}.
     */
    ATOM_CONTROLS,

    /** A second wording of one atom. Paired with {@code canAddAlternatives}. */
    ALTERNATIVES,

    /**
     * A covering letter, asked for with the CV or regenerated afterwards
     * (Bolum 34). Paired with {@code canWriteCoverLetter}.
     */
    COVER_LETTER,

    /**
     * A verdict on a generation, and the support grant that can ride with it
     * (Bolum 34.6). Paired with {@code canSaveHistory}: feedback is a row keyed
     * to a user, so it needs the same thing history does.
     */
    FEEDBACK;

    /** Lowercase on the wire, as the catalogue publishes it. */
    @JsonValue
    public String wireValue() {
        // Locale.ROOT: absolute rule 7.
        return name().toLowerCase(Locale.ROOT);
    }
}
