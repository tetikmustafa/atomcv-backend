# → Frontend — çözülmüş, 2026-10

`B-120`…`B-123`, backend'den geldikleri haliyle; her birinin sonunda frontend'in
ne yaptığı.

### B-120 · `fontFamily` dört değerli bir enum (`F-041`'in cevabı)

**Since:** `feat/font-family-vocabulary` · § 38.5
**Neden:** Profilin görünüşü `MODERN|SERIF|SANS` kabul ediyordu, özelleştirme
uçları dördünü (`book` dahil). Beş alanın beşi de artık `enum: [modern, serif,
sans, book]`; telde tek yazım **küçük harf**, girdi büyük/küçük harf duyarsız,
eski satırlardaki `SANS` `sans` olarak okunuyor.
**Action:** `npm run gen:api`; `FONT_FAMILIES` sabiti yerine şemanın birliği;
dördüncü değer (`book`, Pagella) seçiciye. Eski büyük harfli değerle
karşılaştıran bir kod varsa küçük harfe.

**Frontend (2026-10-23):** `FONT_FAMILIES` şemadan türetildi, `book` eklendi.

### B-121 · `CoverLetterRequest.language` (`F-045`'in cevabı)

**Since:** `feat/cover-letter-language` · § 34.6
**Neden:** Konsept § 10.1 "CV ve cover letter için ayrı seçim" diyor; letter'ın
dili yalnız `defaults.coverLetterLanguage`'ten geliyordu.
**Action:** `POST …/cover-letter/regenerate` gövdesinde opsiyonel `language`
(ISO 639-1). Liste `capabilities.allowedLanguages` — dışındaki kod
`400 VALIDATION_FAILED`, `fields:["language"]`. Gönderilmezse profilin
varsayılanı. Kapı sırası: `403` (anonim) → `400` (`language`) → `404` → `429`.

**Frontend (2026-10-23):** `CoverLetter`'da dil seçici, `allowedLanguages`'ten.

### B-122 · İçe aktarma fazlarının `label`'ı artık çeviri anahtarı

**Since:** `feat/import-phase-keys` · § 30, § 31.6.1
**Neden:** `PROFILE_EXTRACTION` işi `label`'a İngilizce cümle yazıyordu
("Reading your CV"…); frontend onu bilerek yok sayıp tek bir genel başlık
çiziyordu, yani çubuk hangi adımda olduğunu hiç söylemedi. Üretimin
`generation.phase.*` deseni.
**Action:** `import.phase.STRUCTURING`, `import.phase.NORMALIZING`,
`import.phase.SAVING` — en/tr çeviriler ve `ImportScreen`'in ilerleme satırı.
`phase` alanı değişmedi (`structuring`, `normalizing`, `saving`).

**Frontend (2026-10-23):** `ImportProgress` `usePhaseLabel` ile çiziyor; mock aynı anahtarları gönderiyor.

### B-123 · `keep_top_pinned` artık gönderilebilir: `GenerationRequest.keepPinned`

**Since:** `feat/keep-top-pinned` · § 35 (08-api, `CONFLICTING_PREFERENCES`), 08b
**Neden:** Resolution "daraltılmış kümeyle yeniden gönder" diyordu ve şemada
daraltılmış kümeyi taşıyacak alan yoktu — düğmeyi çizmemeniz doğruydu.
**Action:** `keep_top_pinned` düğmesini çizin; basılınca **aynı isteği**
`keepPinned: params.keep` ile yeniden gönderin (`maxPages`'in
`increase_page_limit`'te gittiği gibi). Genel modda da çalışıyor. Hâlâ
sığmazsa yeni ret daha küçük bir `keep` önerir; hiç sabit yoksa düğme gelmez.
`GenerateScreen` ve `ErrorPanel`'deki düşürme kaldırılabilir.

**Frontend (2026-10-23):** `GenerateScreen` `keep_top_pinned`'i çiziyor, `keepPinned` ile yeniden gönderiyor.

### B-124 · Sonucun rolü ve şirketi; giriş/bölüm kontrolleri hesap ister

**Since:** `feat/result-names-and-guest-gates` · § 35.7.2, `B-070`
**Neden:** (1) `GenerationResponse` geçmiş satırının taşıdığı `roleTitle` ile
`companyName`'i taşımıyordu; sonuç ekranı ilanın ne için olduğunu söyleyemiyor,
bir başvuruyu onunla dolduramıyordu. (2) § 9 misafire içeriği verir, kontrolleri
değil; sunucu bunu yalnız atomda zorluyordu — bir girişin `importance`,
`active`, `alwaysInclude`, `verbatim`, `minAtoms`'u ve bir bölümün üç anahtarı
herkese yazılabiliyordu.
**Action:** `gen:api`; sonuç ekranında başlık ve "başvurulara ekle" ön
dolgusu `roleTitle`/`companyName`'den. Misafir bu alanlardan birini gönderirse
`403 FEATURE_REQUIRES_ACCOUNT`, `params.feature: atom_controls` — arayüz onları
zaten `canEditAtomControls` arkasında çiziyor. `EntryCreate.minAtoms` artık
`0…50` (yama ile aynı).

**Frontend (2026-10-24):** `GenerationResult` başlığı ve `TrackApplication` ön dolgusu; mock `roleTitle`/`companyName`'i sonuçta da veriyor.
