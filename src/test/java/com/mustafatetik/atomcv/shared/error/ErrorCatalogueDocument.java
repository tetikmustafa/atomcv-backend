package com.mustafatetik.atomcv.shared.error;

import java.util.List;
import java.util.StringJoiner;

/**
 * The published error catalogue, rendered from {@link ErrorCode}.
 *
 * <p><strong>Generated, because it used to be transcribed.</strong> The table
 * was maintained by hand in the architecture document and checked against this
 * enum by a test. That caught drift after the fact and only in one direction
 * that mattered: five codes were already missing the first time it ran, and
 * only one of them had been noticed. A table nobody types cannot drift.
 *
 * <p>In test sources rather than main: nothing at runtime reads it, and the
 * one thing that does — {@link ErrorCatalogueDocumentTest} — both writes it and
 * checks it, so the check and the thing that satisfies it stay one piece of
 * code.
 *
 * <p>The shape is the one the table already had, so a person who knew where to
 * look still does: a code in backticks, the status, and the parameters as
 * {@code name: type} pairs. An em dash means the code carries none, and is not
 * a parameter called "—".
 */
final class ErrorCatalogueDocument {

    /** What a code with no parameters prints, rather than an empty cell. */
    static final String NONE = "—";

    private ErrorCatalogueDocument() {
    }

    static String render() {
        var out = new StringBuilder();
        out.append("""
                # Hata Kataloğu

                > **Üretilmiş dosya — elle düzenlenmez.** Kaynağı `ErrorCode`
                > enum'u; yeniden üretmek için `make catalogue`.
                > `ErrorCatalogueDocumentTest` ikisi ayrıştığı anda düşer.

                Sunucu bir kod ve bu parametreleri gönderir, cümle göndermez.
                İstemci `errors.{KOD}` anahtarını kendi dilinde çözer, yani
                katalogda karşılığı olmayan bir kod kullanıcıya ham anahtar
                olarak görünür. Parametrelerin **tipi** de sözleşmenin parçası:
                ICU'da `{pinnedPages, number}` biçimlendirir, `{pinnedPages}`
                yalnızca yerine koyar.

                **Parametreler kullanıcı içeriği taşımaz** (mutlak kural 4) —
                sayılar, sınırlar, tanımlayıcılar ve alan adları taşırlar.

                | Kod | HTTP | `params` |
                |---|---|---|
                """);

        for (ErrorCode code : ErrorCode.values()) {
            out.append("| `").append(code.name()).append("` | ")
                    .append(code.httpStatus()).append(" | ")
                    .append(parameters(code.params())).append(" |\n");
        }
        return out.toString();
    }

    private static String parameters(List<ErrorCode.Param> params) {
        if (params.isEmpty()) {
            return NONE;
        }
        var cell = new StringJoiner(", ");
        for (ErrorCode.Param param : params) {
            cell.add("`" + param.name() + ": " + param.type().wireName() + "`");
        }
        return cell.toString();
    }
}
