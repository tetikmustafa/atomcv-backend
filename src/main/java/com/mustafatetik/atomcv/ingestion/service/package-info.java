/**
 * The path from an uploaded file to a stored profile.
 *
 * <p>It runs in two halves and the seam is deliberate. Reading the file is
 * synchronous, because every way it can fail is something a person acts on at
 * once (Bolum 31.10); structuring, normalising and writing are a job, because
 * together they are an LLM call over a whole document.
 */
package com.mustafatetik.atomcv.ingestion.service;
