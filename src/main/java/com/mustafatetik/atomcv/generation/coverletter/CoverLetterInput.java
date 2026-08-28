package com.mustafatetik.atomcv.generation.coverletter;

import java.util.List;

/**
 * Everything a covering letter may be written from (Bolum 34.2).
 *
 * <p><strong>The CV's protection was selection; this has none.</strong> A CV
 * is assembled from atoms a person wrote, so there is nothing in it to
 * fabricate. A covering letter is free text, written in the first person, and
 * the claims in it are the ones an interviewer opens with. So the answer of
 * Bolum 34.2 is to feed it the same atoms: the letter becomes the narrative
 * version of the page, and consistency comes for free.
 *
 * @param applicantName  who is writing, from the profile's contact block
 * @param roleTitle      the job, from Faz A
 * @param companyName    the employer, from Faz A. May be blank — a posting
 *                       that never named one is ordinary
 * @param evidence       the two or three highest-scoring atoms on the page,
 *                       which Bolum 34.3's body is built from
 * @param allowedSkills  every skill on the page. The letter may name nothing
 *                       else
 * @param allowedMetrics every number on the page, as written
 * @param ownEmployers   the organisations on this person's own CV. Not
 *                       permission to claim anything — the closed set the
 *                       greeting is checked against, so that a letter cannot
 *                       be addressed to the employer it just read about
 * @param profileYears   how long this person has actually been working,
 *                       computed from the entries' dates. Bolum 34.4 calls a
 *                       claim about this the most common fabrication
 * @param companyNote    what the person themselves knows about this employer
 *                       (Bolum 34.5). User content, and the only source of
 *                       personalisation — the alternative is a model inventing
 *                       admiration for a company it has never heard of
 * @param language       the language to write in
 * @param tone           how the profile asked to sound
 */
public record CoverLetterInput(
        String applicantName,
        String roleTitle,
        String companyName,
        List<Evidence> evidence,
        List<String> allowedSkills,
        List<String> allowedMetrics,
        List<String> ownEmployers,
        int profileYears,
        String companyNote,
        String language,
        String tone) {

    /**
     * One thing the person actually did, and what it is allowed to say about
     * it.
     *
     * @param text  the wording that is on the page, so the letter and the CV
     *              cannot describe the same work differently
     */
    public record Evidence(String text, List<String> skills, List<String> metrics) {

        public Evidence {
            skills = List.copyOf(skills);
            metrics = List.copyOf(metrics);
        }

        /** Shape only: the sentence is the user's (absolute rule 4). */
        @Override
        public String toString() {
            return "Evidence[skills=" + skills.size() + ", metrics=" + metrics.size() + "]";
        }
    }

    public CoverLetterInput {
        evidence = List.copyOf(evidence);
        allowedSkills = List.copyOf(allowedSkills);
        allowedMetrics = List.copyOf(allowedMetrics);
        ownEmployers = List.copyOf(ownEmployers);
        applicantName = applicantName == null ? "" : applicantName;
        roleTitle = roleTitle == null ? "" : roleTitle;
        companyName = companyName == null ? "" : companyName;
        companyNote = companyNote == null ? "" : companyNote;
    }

    /** Counts and lengths, never a line of the CV (absolute rule 4). */
    @Override
    public String toString() {
        return "CoverLetterInput[evidence=" + evidence.size()
                + ", skills=" + allowedSkills.size()
                + ", metrics=" + allowedMetrics.size()
                + ", years=" + profileYears
                + ", companyNote=" + (companyNote.isBlank() ? "none" : "set")
                + ", style=" + language + "/" + tone + "]";
    }
}
