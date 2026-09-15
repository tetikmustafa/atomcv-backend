/**
 * Counting what a subject did in a window, and nothing about who they are.
 *
 * <p>It lived under {@code identity} because sign-in was the first thing to
 * need it, and that placement held until the tightening needed the same
 * counter for generations: {@code billing} reaching into {@code identity}
 * closed a cycle, and the ArchUnit rule refused it. The rule was right —
 * nothing here knows what a session is. The layer name, the subject, the limit
 * and the window are the whole vocabulary.
 *
 * <p>What stayed behind in {@code identity.ratelimit} is the part that really
 * is about signing in: the three layers and their numbers.
 */
package com.mustafatetik.atomcv.shared.ratelimit;
