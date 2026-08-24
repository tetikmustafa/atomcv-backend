package com.mustafatetik.atomcv.billing;

import com.mustafatetik.atomcv.shared.security.UserContext;
import java.util.Locale;
import java.util.Objects;

/**
 * Who a counter belongs to (Bolum 44.1).
 *
 * <p>Not a user id, because the anonymous flow is counted by IP and the
 * anonymous session by its own id — three kinds of subject, one table, and the
 * column pair says which is which. Stage 3 adds the other two; the type is
 * here now so that adding them is a new constant rather than a new column.
 *
 * <p><strong>This is where absolute rule 3 lives for this table.</strong>
 * {@code usage_counters} has no {@code user_id} to scope by, so nothing may
 * build a subject out of a path variable — {@link #of(UserContext)} takes the
 * acting user and there is no other public way in.
 */
public record QuotaSubject(Type type, String id) {

    public enum Type {
        USER, IP, ANON_SESSION;

        public String wireValue() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public QuotaSubject {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(id, "id");
    }

    public static QuotaSubject of(UserContext user) {
        return new QuotaSubject(Type.USER, user.userId().toString());
    }
}
