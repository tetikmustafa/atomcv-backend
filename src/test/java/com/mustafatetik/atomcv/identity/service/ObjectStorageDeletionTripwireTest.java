package com.mustafatetik.atomcv.identity.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.generation.domain.Generation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The reminder Bolum 57.4 asks a paragraph to be.
 *
 * <p><strong>Object storage is deliberately not in the MVP</strong> (the
 * decision of 2026-08-28, recorded in Bolum 57.4 and EK D.6.3): nothing stores
 * bytes, a download re-renders from {@code content_snapshot}, and
 * {@code generations.pdf_key} is null on every row. So there is no PDF to
 * delete, and the deletion list is not wrong — it is unbacked.
 *
 * <p>Bolum 57.4 then says the thing this test exists for:
 *
 * <blockquote>Depolama indigi gun silme yolunun oradan da gecmesi gerekir, ve
 * bunu hatirlatacak tek sey bu paragraftir: {@code AccountDeletionIT} tablolari
 * {@code information_schema}'dan okuyor, yani veritabanina eklenen her tabloyu
 * kendisi yakalar — ama veritabaninda olmayan bir nesne deposunu
 * goremez.</blockquote>
 *
 * <p>A paragraph is a poor reminder and the section says so. This is the same
 * reminder with teeth: it fails on the day something can write a {@code pdfKey},
 * which is the day a bucket starts holding somebody's CV, and its failure
 * message is the instruction. <strong>It is not a rule against storage</strong>
 * — it is a rule against storage arriving without the deletion path, which is
 * the one order those two must never come in.
 */
class ObjectStorageDeletionTripwireTest {

    /**
     * What {@code AccountDeletionService} would have to learn to do. Named as
     * strings rather than as calls because none of them exists yet: this test
     * is about a future that has not been written, and it has to keep
     * compiling until it is.
     */
    private static final List<String> WHAT_DELETION_WOULD_NEED = List.of(
            "read every pdf_key this user has, before the cascade drops the rows",
            "delete those objects from the bucket, and fail the deletion if the "
                    + "bucket refuses -- a row gone with its object left behind is "
                    + "the worst of both",
            "cover the anonymous sweep too (Bolum 51.6.1), which deletes profiles "
                    + "nobody is signed in to and would otherwise leak every "
                    + "artifact they made",
            "say so in the privacy policy, which Bolum 57.4 names in the same "
                    + "breath as the code");

    @Test
    void nothingCanWriteApdfKeyYet() {
        List<String> writers = Arrays.stream(Generation.class.getMethods())
                .filter(method -> method.getName().toLowerCase(java.util.Locale.ROOT)
                        .contains("pdfkey"))
                .filter(method -> method.getParameterCount() > 0)
                .map(Method::getName)
                .toList();

        assertThat(writers)
                .as("""
                        `%s` can now set a pdf_key, so bytes are about to live in a \
                        bucket -- and Bolum 57.4's deletion path does not go through \
                        it. Before this ships, account deletion has to: %s. \
                        Then delete this test and add the real one to \
                        AccountDeletionIT, which cannot see a bucket on its own.\
                        """.formatted(Generation.class.getSimpleName(),
                        String.join("; ", WHAT_DELETION_WOULD_NEED)))
                .isEmpty();
    }

    /**
     * The other half of the same day. A client is how bytes get to a bucket,
     * and one appearing is the same signal from the other direction — the
     * column could be written by something that never touches {@link
     * Generation}.
     */
    @Test
    void thereIsNoObjectStoreClient() {
        assertThat(onTheClasspath("software.amazon.awssdk.services.s3.S3Client"))
                .as("An S3/R2 client is on the classpath. Bolum 57.4's deletion "
                        + "path has to go through it before anything stores a "
                        + "user's PDF: %s", String.join("; ", WHAT_DELETION_WOULD_NEED))
                .isFalse();
    }

    private static boolean onTheClasspath(String className) {
        try {
            Class.forName(className, false,
                    ObjectStorageDeletionTripwireTest.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException absent) {
            return false;
        }
    }
}
