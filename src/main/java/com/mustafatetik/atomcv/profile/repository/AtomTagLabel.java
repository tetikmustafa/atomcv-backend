package com.mustafatetik.atomcv.profile.repository;

import java.util.UUID;

/**
 * One atom wearing one label, as the join returns it.
 *
 * <p>Public because JPQL's {@code select new} names the class by its fully
 * qualified name and instantiates it from outside this package.
 */
public record AtomTagLabel(UUID atomId, String label) {
}
