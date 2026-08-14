package com.mustafatetik.atomcv.shared.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * One offered way out of an error: an action and whatever the client needs to
 * carry it out.
 *
 * <p>Design principle 4 in one object — an error explains the problem and
 * offers concrete options, and the user decides. An error with no resolution is
 * allowed only where there genuinely is nothing to do.
 *
 * @param action what the client should offer
 * @param params values the action needs, for example {@code maxPages} for
 *               {@link ResolutionAction#INCREASE_PAGE_LIMIT}; omitted from JSON
 *               when empty
 */
public record Resolution(
        ResolutionAction action,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, Object> params) {

    public Resolution {
        Objects.requireNonNull(action, "action");
        params = params == null ? Map.of() : Map.copyOf(params);
    }

    public static Resolution of(ResolutionAction action) {
        return new Resolution(action, Map.of());
    }

    public static Resolution of(ResolutionAction action, String key, Object value) {
        var params = new LinkedHashMap<String, Object>();
        params.put(key, value);
        return new Resolution(action, params);
    }
}
