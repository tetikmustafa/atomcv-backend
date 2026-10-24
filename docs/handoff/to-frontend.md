# → Frontend

> **Kanal kuralları**
>
> - Backend yazar, frontend okur ve `OPEN` → `ACK` taşır.
> - Her madde bir ID taşır (`B-nnn`); numaralar tekrar kullanılmaz.
> - **Dosya 100 satırı geçerse arşivleme gecikmiştir.** `ACK` maddeleri `resolved/`'a taşınır.
> - API _şekli_ için otorite OpenAPI şemasıdır. Burası **neden değişti + ne yapman lazım** taşır.
> - Kalıcı kural niteliğindeki maddeler `spec/`'e işlenir ve buradan silinir.

---

## OPEN

### B-125 · `GenerationResponse.atsCheck` — PDF'in geri okunuşu artık görünür

**Since:** `feat/ats-check-visible`, V18 · konsept § 10.1 ("ATS uyumluluk doğrulaması") · spec 04 § 13.2

`AtsCheck` Aşama 2'den beri her PDF'i metin olarak geri okuyordu (başlıklar,
maddeler, iletişim satırı, sıra); sonuç yalnız bir sayaca gidiyordu. Artık
generation satırında saklanıyor ve `GET /generations/{id}` yanıtında
`atsCheck: AtsCheckResult` olarak geliyor: `readable` (hepsi temiz),
`headingsFound/headingsExpected`, `bulletsFound/bulletsExpected`,
`contactReadable`, `orderPreserved`. **Yalnız sayılar ve bayraklar** — başlık
metni yok. V18'den önceki üretimlerde alan **yok** (tahmin edilmez).

**Action:** `gen:api`; sonuç ekranında uygunluk raporunun yanında küçük bir
"ATS okunabilirliği" kartı (temiz → tek satır onay; değilse hangi sayının
eksik kaldığı, yüzde değil). `readable: false` kişinin CV'sinin kusuru değil,
**bizim şablonumuzun**: metin suçlamasın, belgeyi geri almasın. Alan yoksa
kart çizilmez. Mock `atsCheck` döndürsün.

---

## ACK — frontend karşıladı (2026-10-23)

`B-120`…`B-124` geldikleri gün indi; gövdeleri, ne yapıldığıyla birlikte,
`resolved/to-frontend-2026-10.md`'de.

- **`B-120`** — `FONT_FAMILIES` şemanın birliğinden türetiliyor (`satisfies` +
  kapsamlılık kontrolü: beşinci bir değer typecheck hatası olur); `book`
  seçicide; mock tek yazımla geri veriyor.
- **`B-121`** — sonuç ekranının mektup panelinde dil seçici
  (`allowedLanguages`, boş = profilin varsayılanı); mock `400 language`'ı üretiyor.
- **`B-122`** — içe aktarma çubuğu `import.phase.*` anahtarlarını çiziyor
  (en/tr); mock aynı üç fazı aynı yüzdelerle gönderiyor.
- **`B-123`** — `keep_top_pinned` çiziliyor ve aynı isteği `keepPinned` ile
  yeniden gönderiyor.
- **`B-124`** — sonuç ekranının başlığı rol ve şirket; "başvurulara ekle"
  kartı onlarla ön dolu ve `generationId` gönderiyor. Giriş/bölüm kontrolleri
  zaten `canEditAtomControls` arkasındaydı.

`B-100`…`B-119` önceki turlarda indi; kayıtları `resolved/to-frontend-2026-09.md`'de.

## Dağıtım bekleyen doğrulamalar

Üçü bir dağıtım bekliyor ve hiçbiri kod işi değil: OAuth sıçraması (`B-048`),
sihirli bağlantının Turnstile'ı (`B-050`), `B-083`'ün challenge'ı. `B-100`
üçünün önündeki kapıyı açtı. `B-076`'dan kalan tek şey yayımlanan sağlayıcı
sayfasını `ProcessorAudit`'in açılış satırına karşı okumak. Sıra
`notes/current.md` § *Dağıtım günü*'nde.

Kalıcı kuralların `spec/`'e işlendiği yerler: `resolved/to-frontend-2026-08.md`.
