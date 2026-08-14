package com.mustafatetik.atomcv.generation.pipeline;

import java.util.function.Function;

/**
 * Either a value or a reason there is none (Bolum 25.1).
 *
 * <p>The pipeline's expected failures are not exceptional: a profile too thin
 * to generate from, pinned content that cannot fit, a provider that is down.
 * Each of them ends with something to tell the user, and a return type carries
 * that better than a throw — the compiler makes the caller look at it.
 *
 * <p>No library for this. The language has sealed interfaces and pattern
 * matching, which is the whole of what a Result needs.
 */
public sealed interface Result<T> permits Result.Ok, Result.Err {

    record Ok<T>(T value) implements Result<T> {
    }

    record Err<T>(PipelineError error) implements Result<T> {
    }

    static <T> Result<T> ok(T value) {
        return new Ok<>(value);
    }

    static <T> Result<T> err(PipelineError error) {
        return new Err<>(error);
    }

    default boolean isErr() {
        return this instanceof Err<T>;
    }

    default <R> Result<R> map(Function<T, R> mapper) {
        return switch (this) {
            case Ok<T> ok -> Result.ok(mapper.apply(ok.value()));
            case Err<T> err -> Result.err(err.error());
        };
    }

    default <R> Result<R> flatMap(Function<T, Result<R>> mapper) {
        return switch (this) {
            case Ok<T> ok -> mapper.apply(ok.value());
            case Err<T> err -> Result.err(err.error());
        };
    }

    /** @throws IllegalStateException when there is no value — for tests and for callers that checked */
    default T orElseThrow() {
        return switch (this) {
            case Ok<T> ok -> ok.value();
            case Err<T> err -> throw new IllegalStateException(
                    "No value: " + err.error().getClass().getSimpleName());
        };
    }
}
