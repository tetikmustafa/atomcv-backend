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

_(açık madde yok.)_

---

## ACK — frontend karşıladı (2026-10-23 / 10-24)

`B-120`…`B-125` geldikleri gün indi; gövdeleri, ne yapıldığıyla birlikte,
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
- **`B-125`** — sonuç ekranında uygunluk raporunun altında "Makine
  okunabilirliği" kartı: temizse tek cümle, değilse kusuru **şablonun** sayan
  cümle; sayılar çift olarak, yüzde yok; alan yoksa kart yok. Mock temiz döner,
  testler kusuru ve yokluğu ayrıca çiziyor.

`B-100`…`B-119` önceki turlarda indi; kayıtları `resolved/to-frontend-2026-09.md`'de.

## Dağıtım bekleyen doğrulamalar

Üçü bir dağıtım bekliyor ve hiçbiri kod işi değil: OAuth sıçraması (`B-048`),
sihirli bağlantının Turnstile'ı (`B-050`), `B-083`'ün challenge'ı. `B-100`
üçünün önündeki kapıyı açtı. `B-076`'dan kalan tek şey yayımlanan sağlayıcı
sayfasını `ProcessorAudit`'in açılış satırına karşı okumak. Sıra
`notes/current.md` § *Dağıtım günü*'nde.

Kalıcı kuralların `spec/`'e işlendiği yerler: `resolved/to-frontend-2026-08.md`.
