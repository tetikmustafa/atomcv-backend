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

---

## ACK — frontend karşıladı (2026-10-23)

`B-120`…`B-123` geldikleri gün indi; gövdeleri, ne yapıldığıyla birlikte,
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

`B-100`…`B-119` önceki turlarda indi; kayıtları `resolved/to-frontend-2026-09.md`'de.

## Dağıtım bekleyen doğrulamalar

Üçü bir dağıtım bekliyor ve hiçbiri kod işi değil: OAuth sıçraması (`B-048`),
sihirli bağlantının Turnstile'ı (`B-050`), `B-083`'ün challenge'ı. `B-100`
üçünün önündeki kapıyı açtı. `B-076`'dan kalan tek şey yayımlanan sağlayıcı
sayfasını `ProcessorAudit`'in açılış satırına karşı okumak. Sıra
`notes/current.md` § *Dağıtım günü*'nde.

Kalıcı kuralların `spec/`'e işlendiği yerler: `resolved/to-frontend-2026-08.md`.
