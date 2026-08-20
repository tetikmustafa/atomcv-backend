# Kapatılmış — → Frontend · 2026-08

Aşama 1 kapanışında `to-frontend.md`'nin `ACK` bölümünden taşındı. Maddeler
frontend tarafından uygulandı ve teyit edildi; burada yalnız "bu karar ne zaman
ve neden verilmişti?" sorusu için duruyorlar.

---

### B-025 · Media type `application/json`, `If-Match: "7"`
§ 35.6'nın `application/merge-patch+json` yazması hataydı; öyle gönderen istek artık **415**. ETag'de `v` öneki yok. 405/406/400 doğru kodla geliyor.
**Yeni ICU anahtarları:** `METHOD_NOT_ALLOWED`, `NOT_ACCEPTABLE`, `UNSUPPORTED_MEDIA_TYPE`

### B-026 · Değişiklik yapmayan yazma sürümü artırmaz
Aynı değerlerle `PATCH` → 200 + **aynı** sürüm. Autosave için taşıyıcı.

### B-027 · Atom ve varyant sürümleri bağımsız
`PATCH /atoms/{id}` atomun `version`'ını artırır, varyantlarınkine dokunmaz. Editör atom başına **iki** sürüm tutar.

### B-028 · Promote için metni geri gönderme
`PATCH …/variants/{id}` artık `content` istemiyor; `{"primary": true}` yeterli.
**Hata düzeltmesiydi:** metni geri gönderen istek `tone`'u siliyordu. `tone` üç durumlu: atlanırsa korunur, `null` gönderilirse nötr.

### B-029 · Şema artık `200`'leri ve `ETag`'i söylüyor
On operasyon başarı yanıtını, her tekil kaynak yazması `ETag`'i ilan ediyor.
`endpoints/profile.ts`'teki elle beyanlar ve `EntryPatch` null genişletmesi **geri alınabilir**. `ApiError.code`/`.status` zorunlu.

### B-031 · `?format=markdown` şemada
`/profile/export` iki media type ilan ediyor.

---
