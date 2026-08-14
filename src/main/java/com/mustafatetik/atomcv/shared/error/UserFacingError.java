package com.mustafatetik.atomcv.shared.error;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * An error on its way to a user: a code, its published parameters, and the ways
 * out (Bolum 35.4).
 *
 * <p>The parameters are checked against {@link ErrorCode}'s declaration as the
 * object is built. A missing parameter is not a small thing — the frontend's
 * ICU message interpolates it, so the user would read
 * "Your pinned content takes {pinnedPages} pages". That is the silently bad
 * result design principle 4 exists to prevent, and it is cheaper to fail here
 * than to discover it in a screenshot.
 */
public record UserFacingError(
        ErrorCode code, Map<String, Object> params, List<Resolution> resolutions) {

    public UserFacingError {
        Objects.requireNonNull(code, "code");
        // LinkedHashMap, not Map.copyOf: the immutable maps of the JDK iterate
        // in an order that is salted per JVM run, so the same error would
        // serialise differently between runs. Nothing may vary run to run here.
        params = params == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(params));
        resolutions = resolutions == null ? List.of() : List.copyOf(resolutions);
        validate(code, params);
    }

    public static UserFacingError of(ErrorCode code, Resolution... resolutions) {
        return new UserFacingError(code, Map.of(), List.of(resolutions));
    }

    public static Builder with(ErrorCode code) {
        return new Builder(code);
    }

    public int httpStatus() {
        return code.httpStatus();
    }

    /**
     * Collects parameters by name so a caller cannot silently pass them
     * positionally in the wrong order.
     */
    public static final class Builder {

        private final ErrorCode code;
        private final Map<String, Object> params = new LinkedHashMap<>();
        private final List<Resolution> resolutions = new ArrayList<>();

        private Builder(ErrorCode code) {
            this.code = Objects.requireNonNull(code, "code");
        }

        public Builder param(String name, Object value) {
            params.put(Objects.requireNonNull(name, "name"), value);
            return this;
        }

        public Builder resolution(Resolution resolution) {
            resolutions.add(Objects.requireNonNull(resolution, "resolution"));
            return this;
        }

        public Builder resolution(ResolutionAction action) {
            return resolution(Resolution.of(action));
        }

        public UserFacingError build() {
            return new UserFacingError(code, params, resolutions);
        }
    }

    private static void validate(ErrorCode code, Map<String, Object> params) {
        for (ErrorCode.Param declared : code.params()) {
            Object value = params.get(declared.name());
            if (value == null) {
                throw new IllegalArgumentException(
                        code + " must publish " + declared.name());
            }
            if (!declared.type().accepts(value)) {
                throw new IllegalArgumentException(
                        code + "." + declared.name() + " must be " + declared.type()
                                + ", was " + value.getClass().getSimpleName());
            }
        }
        for (String name : params.keySet()) {
            if (code.param(name).isEmpty()) {
                throw new IllegalArgumentException(
                        code + " does not declare " + name
                                + "; add it to the catalogue before sending it");
            }
        }
    }
}
