package com.mustafatetik.atomcv.profile.service;

import com.mustafatetik.atomcv.profile.domain.AtomKind;
import java.util.List;

/**
 * Controls to change on an atom. Null leaves a field alone; a list that is
 * present replaces the whole list, because "add one skill" and "remove one"
 * are the same request with different contents.
 */
public record AtomPatch(
        AtomKind kind,
        Float importance,
        Boolean active,
        Boolean alwaysInclude,
        Boolean verbatim,
        Boolean verified,
        List<String> skills,
        List<String> metrics,
        List<String> properNouns) {
}
