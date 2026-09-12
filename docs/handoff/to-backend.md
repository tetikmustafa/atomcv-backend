# → Backend

> **Kanal kuralları**
> - Frontend yazar, backend okur ve `OPEN` → `ACK` taşır.
> - Her madde bir ID taşır (`F-nnn`), numaralar tekrar kullanılmaz.
> - **Dosya 100 satırı geçerse arşivleme gecikmiştir.**
> - Bir spec değişikliği gerekiyorsa burada iste — `spec/`'i frontend reposunda düzenleme,
>   bir sonraki senkronda kaybolur.

---

## OPEN

*(açık madde yok.)*

<!-- Şablon:
### F-001 · Kısa başlık
**Since:** frontend commit <sha> · Adım <n>
**Neden:** <sorunun ne olduğu>
**İstenen:** <backend'den beklenen somut şey>
**Spec:** <ilgili dosya ve bölüm, varsa>
-->

---

## ACK — backend tamamladı, frontend arşivleyebilir

**`F-031`, `F-032`, `F-033` karşılandı (2026-09-12), geldikleri gün.**
Ne yapıldığı ve frontend'in ne yapması gerektiği `to-frontend.md`'de
`B-097`…`B-099` olarak duruyor — **`B-099` `npm run gen:api`'yi zorunlu
kılıyor**, 33 operasyon adı değişti.

Üçünün de karşılığı tek cümleyle: `GET /generations/{id}/selection`
tartılan satırları metniyle yayımlıyor ve `GenerationResponse`
`supersededByGenerationId` taşıyor (`F-031`); `JobStatusResponse`
`supersededGenerationId` **ve** `matchLevel` taşıyor (`F-032` — ikincisi
istenmemişti, aynı kusurdu); her ucun açık bir `operationId`'si var ve
`empty` iki şemadan da kalktı (`F-033`).

*(`F-001`…`F-024` `resolved/to-backend-2026-08.md`'de,
`F-025`…`F-030` `resolved/to-backend-2026-09.md`'de.)*
