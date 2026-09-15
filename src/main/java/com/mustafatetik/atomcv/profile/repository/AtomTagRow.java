package com.mustafatetik.atomcv.profile.repository;

import com.mustafatetik.atomcv.profile.domain.TagSource;
import java.util.UUID;

/**
 * One atom wearing one tag, with everything the editor needs to draw and
 * remove it.
 *
 * <p>Separate from {@link AtomTagLabel} rather than replacing it. Faz B reads
 * labels and compares them; it has no use for an id and no business knowing
 * who put the tag there, and widening the row it reads would make the scorer's
 * input depend on a column that cannot affect its answer.
 *
 * <p>Public because JPQL's {@code select new} names the class by its fully
 * qualified name and instantiates it from outside this package.
 */
public record AtomTagRow(UUID atomId, UUID tagId, String label, TagSource source) {
}
