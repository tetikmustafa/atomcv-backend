# → Frontend

> **Kanal kuralları**
>
> - Backend yazar, frontend okur ve `OPEN` → `ACK` taşır.
> - Her madde bir ID taşır (`B-nnn`), numaralar tekrar kullanılmaz.
> - **Dosya 100 satırı geçerse arşivleme gecikmiştir.** `ACK` maddeleri `resolved/`'a taşınır.
> - API _şekli_ için otorite OpenAPI şemasıdır. Burası **neden değişti + ne yapman lazım** taşır.
> - Kalıcı kural niteliğindeki maddeler `spec/`'e işlenir ve buradan silinir.

---

## OPEN

### B-040 · Üç şema düzeltmesi — `gen:api` çalıştırın
**Since:** `06d78d3`…`0955bcb` · **Spec:** `spec/08-api.md` § 35.3, `spec/07-subsystems.md` § 30.6
**Kapatır:** `F-009`, `F-010`, `F-011`, `F-012`

Üçü de sizin ölçümlerinizden çıktı ve üçü de tipi değiştiriyor.

**1 · `generalMode` şemadan düştü** (`F-009`). Hiç yazılmamıştı: bir record'daki
`isGeneralMode()` Jackson ve springdoc için bir getter ve şemaya sızmıştı.
Genel modu isteyen tek şey `jobDescription`'ın yokluğu — sizin de tahmininiz
buydu. `GenerationRequest` artık **tam dört** özellik: `jobDescription?`,
`acknowledgePreflight`, `maxPages?`, `language?`. § 35.3'ün `directives`/`options`
gösteren örneği de düzeltildi.

**2 · İlerleme alanları opsiyonel oldu** (`F-010`). `phase`, `label` ve `detail`
**boşken gönderilmiyor** — akışta da, `GET /jobs/{id}`'de de, çünkü ikisi tek
bir shape'i serialize ediyor. `pct` sıfırken de geliyor. İstediğiniz buydu;
`generation.phase.` diye bir anahtarı çevirmeye kalkan istemci artık mümkün
değil. Tipiniz bu alanları `string | undefined` görecek.

**3 · `usage` iki sayı taşıyor** (`F-012`). Sayaç **denemeleri** sayıyor ve bu
kasıtlı — reddedilen istek birimini geri almıyor, yoksa sınırını aşmış bir
kullanıcı ucu döverdi. Yani sayı yanlış değildi, adı yanlıştı:

```
used       harcanan, asla limit'ten büyük değil  →  "20 of 20" basılabilir
attempted  birim alan her istek, reddedilenler dahil (26)
remaining  limit - used, asla negatif
```

**Aksiyonunuz:** `gen:api`, sonra kota ekranındaki yara bandını kaldırın —
`used`/`limit` çifti artık olduğu gibi basılabilir. Kaç kez denendiğini
göstermek isterseniz `attempted` orada.

**`F-011` için aksiyon yok:** ölçümünüz § 30.6'ya, `proxy_buffering off`
satırının yanına yazıldı. Doğru yere işaret ettiniz.

---

### B-041 · Uygunluk raporu yayımlandı — sıra 8 yazılabilir
**Since:** `d96e2df`…`075e023` · **Spec:** `spec/06-pipeline-d-g.md` § 23.3, `spec/08-api.md` § 35.3
**Kapatır:** `F-008`

Üç yerden okunuyor:

```
GET /generations/{id}   { generationId, status, pageCount, createdAt, fitReport? }
completed olayı         { generationId, pageCount, matchLevel }
GET /jobs/{id}          pageCount kazandı
```

`fitReport`: `requiredCovered/requiredTotal`, `preferredCovered/preferredTotal`,
`coveredSkills`, `missingRequired`, `missingPreferred`, `level`.
`level` ∈ `WEAK | MODERATE | GOOD | STRONG` — **kapalı sözlük**, şemada enum.

**Yüzde yok ve olmayacak.** § 23.3 onu adıyla yasaklıyor: ölçüm beceri adlarını
karşılaştırıyor, virgülden sonrası olan bir sayı işe alınma olasılığı gibi
okunur. Ekranda sayılar gösterilsin — "4/4 zorunlu", "2/3 tercih edilen".

**İki davranış:** (1) rapor **sayfaya çıkanla** ölçülüyor, sıralananla değil —
belgede yer bulamamış beceri kapsanmış sayılmıyor; (2) **genel modda `fitReport`
alanı hiç gelmiyor** (ilan yok, her sayı sıfır olurdu).

`missingRequired` **ilanın kendi sözcüklerini** taşıyor (ilan Türkçeyse
"mikroservis"), eşleştirme İngilizce anahtar üzerinden — kullanıcı ilanda
okuduğu sözcüğü arar. § 23.3'ün "Terraform deneyimin varsa…" önerisini bu
listeden yazabilirsiniz.

**Aksiyonunuz:** `gen:api` (bu da `B-040` ile aynı tur), sonra sonuç ekranı.

---

## ACK — frontend tamamladı, backend arşivleyebilir

_(boş — `B-037`…`B-039` `resolved/to-frontend-2026-08.md`'de)_

---

## Kalıcı kurallar

Eski maddelerin `spec/`'e işlendiği yerlerin tablosu
`resolved/to-frontend-2026-08.md`'ye taşındı (2026-08-24) — dosya sınırı.
