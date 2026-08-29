/**
 * Closed vocabularies the API publishes and no single module owns.
 *
 * <p>{@code shared.error} holds the ones a refusal carries. These are the
 * others: a value produced in one module and published by another, where
 * putting it in either would make the two depend on each other in a circle.
 */
package com.mustafatetik.atomcv.shared.wire;
