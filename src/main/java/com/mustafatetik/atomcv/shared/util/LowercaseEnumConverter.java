package com.mustafatetik.atomcv.shared.util;

import jakarta.persistence.AttributeConverter;
import java.util.Locale;

/**
 * Maps an enum constant to the lowercase form the schema stores
 * ({@code BULLET_LIST} to {@code bullet_list}) and back.
 *
 * <p>{@code EnumType.STRING} would store the constant name verbatim, which
 * would mean naming the constants in lowercase to match the schema. Subclass
 * this instead, one nested class per enum.
 *
 * <p>An unknown stored value fails loudly rather than becoming null: the
 * vocabularies are closed and owned by a migration, so an unknown value means
 * the row is wrong, not that the reader is old.
 */
public abstract class LowercaseEnumConverter<E extends Enum<E>> implements AttributeConverter<E, String> {

    private final Class<E> type;

    protected LowercaseEnumConverter(Class<E> type) {
        this.type = type;
    }

    @Override
    public String convertToDatabaseColumn(E value) {
        // Locale.ROOT: absolute rule 7. Under a Turkish locale "INLINE_LIST"
        // would lowercase to "ınline_list" and no row would ever match.
        return value == null ? null : value.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public E convertToEntityAttribute(String value) {
        return value == null ? null : Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
    }
}
