package com.mustafatetik.atomcv.generation.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/**
 * {@code POST /generations/{id}/selection} — Bolum 24.4's manual toggle.
 *
 * <p>Ids and nothing else. The natural-language half of Faz G is a separate
 * endpoint with a separate cost, and this one has to stay the cheap one: what
 * it asks for is answerable by re-running selection, and a sentence would not
 * be.
 *
 * @param include atoms to put on the page whatever they scored
 * @param exclude atoms to take off it whatever they scored
 */
@Schema(description = """
        Which atoms this CV should keep and which it should drop, whatever \
        Faz B thought of them. The generation is re-made from its own \
        selection state — the page limit is re-checked and still holds, \
        however many times it is edited — and a new generation replaces the \
        one that was edited.

        No LLM call and no quota: the answer is deterministic, so it costs \
        nothing but a compilation.""")
public record SelectionEditRequest(List<UUID> include, List<UUID> exclude) {

    public SelectionEditRequest {
        include = include == null ? List.of() : List.copyOf(include);
        exclude = exclude == null ? List.of() : List.copyOf(exclude);
    }

    public boolean isEmpty() {
        return include.isEmpty() && exclude.isEmpty();
    }
}
