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

    /**
     * The anonymous flow's subject (Bolum 44.1, Adim 3.6).
     *
     * <p><strong>The address and not the session</strong>, and Bolum 44.1 says
     * so for the reason that matters: a session is a cookie, and a cookie is
     * something anybody can throw away and ask for another. Counting by
     * session would give an unlimited allowance to whoever clears theirs, and
     * the whole point of these counters is that this product is free and the
     * calls behind it are not.
     *
     * <p>Not forgeable by the caller: the address comes from the connection,
     * and what the caller may claim in a header is only trusted where a proxy
     * is the one setting it (§ 46.5's {@code forward-headers-strategy}).
     */
    public static QuotaSubject ofAddress(String clientAddress) {
        Objects.requireNonNull(clientAddress, "clientAddress");
        if (clientAddress.isBlank()) {
            throw new IllegalArgumentException("A caller has an address");
        }
        return new QuotaSubject(Type.IP, clientAddress);
    }
}
