# → Backend

> **Kanal kuralları**
> - Frontend yazar, backend okur ve `OPEN` → `ACK` taşır.
> - Her madde bir ID taşır (`F-nnn`), numaralar tekrar kullanılmaz.
> - **Dosya 100 satırı geçerse arşivleme gecikmiştir.**
> - Bir spec değişikliği gerekiyorsa burada iste — `spec/`'i frontend reposunda düzenleme,
>   bir sonraki senkronda kaybolur.

---

## OPEN

_(açık madde yok.)_

**Bir madde yazılıp ölçüldüğü için açılmadı** (2026-09-21): `language`'ın
`400`'ünün kapı sırasındaki yeri — **413 → `400` (`language`) → 409 → 415**,
kayıt frontend `notes/current.md`'de.

<!-- Şablon:
### F-001 · Kısa başlık
**Since:** frontend commit <sha> · Adım <n>
**Neden:** <sorunun ne olduğu>
**İstenen:** <backend'den beklenen somut şey>
**Spec:** <ilgili dosya ve bölüm, varsa>
-->

---

## ACK — backend tamamladı, frontend arşivleyebilir

**`F-041`…`F-045` karşılandı (2026-10-23).** Gövdeleri geldikleri haliyle
`0575900`'da (frontend `5c7701f`); burada her birinin ne olduğu.

- **`F-041` → `B-120`.** Beş `fontFamily` alanının beşi de `enum: [modern,
  serif, sans, book]`; profil `MODERN|SERIF|SANS` kabul ediyordu, yani `book`
  bir yerde kaydedilip öbüründe reddediliyordu — sorunun backend yarısı buydu.
  § 38.5.
- **`F-042`.** `INDEX.md`'nin üç yeri `vps-dagitim-plani.md` ve
  `sync-spec.sh`'in `atomcv-backend`'de olduğunu söylüyor.
- **`F-043`.** § 31.6.5 yedi değer diyor ve yedincinin
  (`unsupported_by_source`, `ExtractionFidelity`) ne olduğunu yazıyor.
- **`F-044`.** EK C.5 `src/lib/seo.ts` → `APP_NAME` diyor.
- **`F-045` → `B-121`.** `CoverLetterRequest.language`; § 34.6.

**`F-031`…`F-040` arşivlendi** (2026-09-21) — hepsi geldikleri gün
karşılanmıştı, ve bu dosya 142 satıra çıkmıştı. Satır satır kayıt
`resolved/to-backend-2026-09.md`'de; `F-001`…`F-024`
`resolved/to-backend-2026-08.md`'de, `F-025`…`F-030` aynı dosyanın başında.
