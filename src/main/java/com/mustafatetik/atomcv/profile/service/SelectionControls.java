package com.mustafatetik.atomcv.profile.service;

import com.mustafatetik.atomcv.shared.error.AccountFeature;
import com.mustafatetik.atomcv.shared.security.ProfileRef;

/**
 * The account gate on an entry's and a section's own controls.
 *
 * <p>§ 9 gives an anonymous session the content and not the controls —
 * importance, the locks, availability — and {@code AtomService} enforced that
 * for atoms only. An entry's {@code importance}, {@code alwaysInclude},
 * {@code verbatim}, {@code active} and {@code minAtoms}, and a section's three
 * switches, were writable by anybody: the frontend hid them, the server did
 * not refuse them (B-124).
 *
 * <p>Asked by the controllers rather than by the services, because only the
 * controller knows what the request <em>said</em>. A draft carries defaults
 * filled in, and the GitHub import writes entries with {@code minAtoms = 1}
 * through the same service for an anonymous session on purpose — a control
 * the server chose is not one the caller asked for.
 */
public final class SelectionControls {

    private SelectionControls() {
    }

    /**
     * @param asked whether the request named any control at all; a field left
     *              out is not a request to change it
     */
    public static void requireAccountIfAsked(ProfileRef profile, boolean asked) {
        if (asked) {
            AnonymousLimits.requireAccountFor(profile, AccountFeature.ATOM_CONTROLS);
        }
    }
}
