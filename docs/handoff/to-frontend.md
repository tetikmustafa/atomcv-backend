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

*(açık madde yok.)*

## ACK — frontend karşıladı

**`B-097`, `B-098`, `B-099` karşılandı (2026-09-12), geldikleri gün.**

- **`B-099`** · `npm run gen:api` koşuldu. 26 operasyon adı değişti ve
  bağlamalar yeni adlara taşındı; numaralı id'ler için yol üzerinden bağlayan
  `ReturnsAt`/`AcceptsAt` **silindi** — tek varlık sebepleri oydu, ve muhafız
  artık sizdeki test. `Appearance` okunup doğrudan geri yazılıyor, eleme kodu
  kalktı.
- **`B-098`** · `JobStatus` iki alanı da tipli taşıyor. Mock'ta terminal yük
  **tek yerde** üretiliyor artık: akış ile `GET /jobs/{id}` aynı nesneyi
  yayıyor, yani alanın birinde olup diğerinde olmaması bir daha yazılamaz.
  `matchLevel` genel modda iki taşıyıcıda da yok.
- **`B-097`** · Elle aç/kapa arayüzü indi. Liste kapalı başlıyor (uç ikinci
  bir istek), sunucunun sırasıyla çiziliyor, **yalnız yeri değişen** satırlar
  gönderiliyor, her satırın durumu switch'in yanında sözle de yazıyor ve
  hareket `aria-live`'a düşüyor. Emekli üretim halefine bağlantı veriyor.

Üçünün de testleri negatif kontrolden geçti. `B-088`…`B-094` ve `B-096`
`resolved/to-frontend-2026-09.md`'de (2026-09-11).

---

## Dağıtım bekleyen doğrulamalar

*(`B-085`…`B-087` de `resolved/to-frontend-2026-09.md`'ye indi 2026-09-09'da.
Aşağıdaki ikisi bir maddenin kapanışı değil, **bir dağıtım bekleyen doğrulama** —
o yüzden arşive inmiyorlar.)*

**Üç yerde bir doğrulama eksik ve söylenmesi gerekiyor:** ne OAuth sıçraması
(`B-048`), ne sihirli bağlantının Turnstile'ı (`B-050`), ne de `B-083`'ün
üretim/içe aktarım challenge'ı gerçek uca karşı denendi — üçü de kendi
anahtarları yapılandırılmış bir dağıtım istiyor.

**EK C.1'in sağlayıcı listesi yazıldı** (`B-076`). Yayın öncesi kontrol
listesinde kalan tek şey, yayımlanan sayfayı `ProcessorAudit`'in açılış
satırına karşı okumak — dağıtım işi, kod işi değil.

---

## Kalıcı kurallar

Eski maddelerin `spec/`'e işlendiği yerlerin tablosu
`resolved/to-frontend-2026-08.md`'ye taşındı (2026-08-24) — dosya sınırı.
