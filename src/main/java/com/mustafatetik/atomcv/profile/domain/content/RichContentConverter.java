package com.mustafatetik.atomcv.profile.domain.content;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Maps {@link RichContent} to the {@code atom_variants.content} JSONB column
 * through the {@link ContentMigrator}, so the version stamp and the lazy
 * upgrade apply to every read and write without a caller remembering them.
 *
 * <p>Not a Spring bean: {@code ContentMigrator} has no dependencies, and a
 * converter that works without a container is easier to test.
 */
@Converter
public class RichContentConverter implements AttributeConverter<RichContent, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ContentMigrator migrator = new ContentMigrator();

    @Override
    public String convertToDatabaseColumn(RichContent content) {
        if (content == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(migrator.write(content));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Atom content could not be serialised", e);
        }
    }

    @Override
    public RichContent convertToEntityAttribute(String stored) {
        if (stored == null) {
            return null;
        }
        try {
            return migrator.read(MAPPER.readTree(stored));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Stored atom content is not valid JSON", e);
        }
    }
}
