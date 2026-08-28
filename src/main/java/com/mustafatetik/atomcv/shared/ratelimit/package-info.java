/**
 * Counting what a subject did in a window, and nothing about who they are.
 *
 * <p>It lived under {@code identity} because sign-in was the first thing to
 * need it (Bolum 40.5), and that placement held until Bolum 44.3's tightening
 * needed the same counter for generations: {@code billing} reaching into
 * {@code identity} closed a cycle, and the ArchUnit rule refused it. The rule
 * was right — nothing here knows what a session is. The layer name, the
 * subject, the limit and the window are the whole vocabulary.
 *
 * <p>What stayed behind in {@code identity.ratelimit} is the part that really
 * is about signing in: Bolum 40.5's three layers and their numbers.
 */
package com.mustafatetik.atomcv.shared.ratelimit;
