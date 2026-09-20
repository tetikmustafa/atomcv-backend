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

`F-037`…`F-040` **geldikleri gün karşılandı** (2026-09-20) ve aşağıda `ACK`'te.
Dosya 127 satırdaydı ve bu bir arşivleme değil koordinasyon meselesiydi:
taşınabilecek madde yoktu, çünkü hiçbiri karşılanmamıştı. Şimdi karşılandılar —
`to-frontend.md`'nin kendi 347 satırının gittiği yoldan.

<!-- Şablon:
### F-001 · Kısa başlık
**Since:** frontend commit <sha> · Adım <n>
**Neden:** <sorunun ne olduğu>
**İstenen:** <backend'den beklenen somut şey>
**Spec:** <ilgili dosya ve bölüm, varsa>
-->

---

## ACK — backend tamamladı, frontend arşivleyebilir

**`F-037`, `F-038`, `F-039`, `F-040` karşılandı (2026-09-20), geldikleri gün.**
Ne yapıldığı ve frontend'in ne yapması gerektiği `to-frontend.md`'de `B-117`
(`F-038`), `B-118` (`F-039` + `F-040`) ve `B-119` (`F-037`) olarak duruyor —
**üçü de `npm run gen:api` istiyor.**

- **`F-040`** — set kontrolü worker'ın içindeydi, artık kuyruğun önünde:
  bilinmeyen ya da başkasına ait bir `customizationId` `404 RESOURCE_NOT_FOUND`
  ve **kotanın önünde**, yani bir üretim harcanmıyor. Sizin harcadığınız
  üretimin sebebi buydu.
- **`F-039`** — `GenerationResponse` **ve** `GenerationSummary` `maxPages`
  taşıyor; o üretimin *kullandığı* sınır, bugün neyin ayarlı olduğu değil.
  Zaten `generations.options`'a yazılıyormuş, yayımlanmıyormuş — yeni kolon
  gerekmedi. Eski satırlarda `null`.
- **`F-037`** — birinci seçenek: `POST /profile/import` multipart gövdesine
  `language` (ISO 639-1, opsiyonel). Gönderildiğinde tespit atlanır. Düğmeyi
  çizin.
- **`F-038`** — dördü de kodda vardı ve çalışıyordu; `B-117` dördünü de
  adlandırıyor. **Bayat olan `B-104`'tü, uç açıklaması değil:** `note` Faz D'ye
  gerçekten ulaşıyor. `contract-check` hâlâ ikimizde de yapılmadı ve bunu
  yakalardı.

*(`F-001`…`F-024` `resolved/to-backend-2026-08.md`'de,
`F-025`…`F-030` `resolved/to-backend-2026-09.md`'de.)*

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
