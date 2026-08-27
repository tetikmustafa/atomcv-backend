/**
 * Text rules that more than one module has to apply identically.
 *
 * <p>Nothing here is a service and nothing here holds state. What lands in this
 * package is a rule where two callers reaching different answers would be a
 * bug — a skill name canonicalised one way at ingestion and another way at
 * scoring stops matching itself.
 */
package com.mustafatetik.atomcv.shared.text;
