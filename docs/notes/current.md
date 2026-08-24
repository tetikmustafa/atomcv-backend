# İnşa Notları — Aktif

> Kural: bu dosya **200 satırı geçmez**. Aşama bitince `archive/`'a taşınır, boş başlanır.
> Kayıt tipleri: **Sapma** (doküman başka diyor) · **Ekleme** (doküman sessiz) · **Düzeltme** (doküman yanlış).
>
> Bir sapma kalıcıysa `spec/`'e işlenir ve buradan silinir.

**Aktif aşama:** Aşama 3 — hesap ve MVP.
**Plan:** `spec/14-build-guide.md` § XI-A.6, Adım 3.1-3.x; gerekçesi
`spec/13-development.md` § 55. Aşama 2'nin tam kaydı `archive/stage-2.md`'de,
Aşama 1 `archive/stage-1.md`'de.

---

## Aşama 2'den taşınan açık kutular

**`Axiom'da loglar görünüyor` kutusu 3.1'e taşındı.** OTLP ihracatçısı bağlı ama
bir URL verilene kadar kapalı; dataset Adım 3.1'de açılıyor. Kutu oraya yazıldı.

**`llm_invocations.user_id` NULL.** Olay kullanıcıyı taşımıyor — zincir,
`UserContext` tutan fazlardan çağrılıyor ama aşağı geçirmiyor. Günlük toplam
(bütçe freni) bunu istemiyor; **kullanıcı bazlı maliyet** istiyor.

**Sıkılaştırılacak rate limiter yok.** § 44.3 anormal kullanıcı için bunu
istiyor; anomali sinyalleri şimdilik yalnız raporluyor.

## Aşama 1'den taşınan kısıtlar — hâlâ açık

| Eksik | Ne zaman | Neden şimdi değil |
|---|---|---|
| `ProfileRef.Scope` yalnız `PERSISTENT` | Aşama 3 | `EPHEMERAL`'ı anonim akıştan önce eklemek, üretmenin denetimli yolu yokken sahiplik kontrolünün etrafından dolaşmanın yolu olurdu |
| ATS raporu ve `FitReport` yok | Aşama 3 | Biri PDF metin çıkarımı istiyor (`spec/06-pipeline-d-g.md` § 23) |
| `UserScopedRepository`'de `findAll` yok | — | § 41.2 parçacığı `findByUserId` çağırıyor, o da `JpaRepository`'de yok. Alt sınıflar kendi bulucularını ekler |

## Isırmadan önce ele alınacak iki bulgu

**Atomsuz entry sayfaya hiç çıkmıyor.** Seçim atom atom çalışıyor; altında madde
olmayan bir diploma satırı aday bile değil. Gerçek çözüm § 20.2'nin modelini
değiştiriyor.

**Eşitlik atom id'siyle bozuluyor, id'ler her içe aktarımda yeniden üretiliyor.**
Aynı skor *ve* maliyetteki iki atom yer değiştiriyor — Aşama 3'ün anonim profil
devralması tam olarak bunu yapacak. İçerikten türetilen bir bozucu düzeltir.

## Aşama 3'e taşınanlar

- **`jobs (user_id, idempotency_key)` anonim istekleri tekilleştirmiyor** —
  `user_id` NULL ve Postgres NULL'ları farklı sayıyor. `COALESCE`'lı migration
  gerekiyor (kayıt EK D.6.5'te).
- **Anonim TTL etkinlikle kayıyor**, metin "son etkinliğinden iki saat sonra"
  demeli. `spec/01-foundations.md` § 9 ve ürün dokümanı güncellenmeli; metnin
  sahibi frontend.

## Devredilen açık kararlar

- **Üretimde migration nasıl çalışacak.** `spec/11-operations.md` § 47 dağıtım
  öncesi `--spring.flyway.migrate-only=true` gösteriyor; bu gerçek bir Spring
  Boot property'si değil. Flyway şu an üretimde de açılışta çalışıyor.
- **CI'a imaj taraması.** Trivy Dockerfile'ı görüyor, derlenen imajı görmüyor.
- **Spotless eklenecek mi.** § 47.1 `spotlessCheck` çalıştırıyor ama
  yapılandırılmış formatlayıcı yok — bugün CI'da biçim kapısı yok.
- **V1 bazı enum benzeri kolonlara `CHECK` koyuyor, bazılarına koymuyor.** § 13'ü
  bilinçli yansıtıyor; eksikleri sonradan eklemek ucuz bir migration.

## Aşama 2'den öğrenilen, tekrar edecek iki şey

- **Kılavuz "tablo" dediğinde önce `V1`'e bak.** Beş kez var olan bir tablo için
  migration istedi (2.4 pgvector, 2.6 `jobs` ve `generations`, 2.7
  `usage_counters` ve `feature_flags`).
- **Toplu JPQL `update` `@Version`'ı atlar** ve **okuma, yakalanmak istenen
  bayatlığı onarır** — etag'i **önceki yazmanın yanıtından** al. Aşama 3'ün
  başvuru izlemesi ikisine de çarpacak.

---

## Aşama 2'nin son iki işi (kapandı 2026-08-24)

**§ 19.4'ün "yakın skor"u bir kova genişliği oldu** (0.02, § 19.6'ya yazıldı).
Buraya not düşülen tek şey **neden epsilon olmadığı**: "bu ikisi yakın mı" diye
soran bir karşılaştırıcı geçişli değil, ve `List.sort` bunu fark edince
`IllegalArgumentException` fırlatıyor — büyük profilde, üretimde, her küçük testi
geçmiş olarak. Altmış skoru sıralayan bir test var.

**`latexTest`'i bu oturumda ilk kez koşturdum ve iki gerçek hata çıktı.**
Bu kayda değer, çünkü indirme dilimini yazarken "`GeneralCvIT` artık uçtan uca
koşuyor" dedim ve **koşturmamıştım**:

1. **`RichContent`/`Mark` türetilmiş alanlarını JSONB'ye yazıyordu.**
   `plainText()`, `contentHash()`, `isEmpty()`, `isKnown()` — dördü de
   getter şeklinde, dördü de serialize ediliyor, hiçbiri deserialize edilemiyor.
   İndirme 500 veriyordu. **İki yönlü düzeltildi:** `@JsonIgnore` yazmayı
   kesiyor, `ignoreUnknown` bozuk build'in yazdığı satırları okunur tutuyor —
   veriyi bozan bir düzeltme düzeltme değildir. Genel ders: **JSONB kolonuna
   düşen bir record'daki her getter şeklindeki metot, kimsenin bildirmediği bir
   saklanan alandır.**
2. **`Tag#setLabel` `final`'dı.** Hibernate lazy proxy kurarken reddediyor —
   uyarı olarak, yani entity çalışmaya devam ediyor ve proxy'nin atlandığını
   kimse söylemiyor.

**Ders:** yavaş hattı ("run it when `docker/latex` changes") **onu değiştirmeyen
ama içinden geçen bir şey değiştiğinde de** koştur. `CLAUDE.md`'ye yazıldı.

---

## Aşama 3 kayıtları

### Frontend'in beş bulgusu — `F-009`…`F-012` kapandı (2026-08-25)

**Düzeltme — `generalMode` diye bir alan hiç yazılmadı.** Şemada göründü,
çünkü `GenerationRequest.isGeneralMode()` türetilmiş bir metot ve **bir
record'da `isX()` Jackson ile springdoc için bir getter'dır.** Aşama 2'de
`RichContent`'te yediğimiz hatanın telin öbür yüzü: orada JSONB kolonuna
yazılan bir alan doğurdu, burada request şemasına. Genel kural artık iki
yönlü — *Jackson'ın dokunduğu bir record'daki her getter şeklindeki metot,
kimsenin bildirmediği bir alandır.* Tüm DTO record'ları tarandı, tek örnek
buydu. Şema testi `GenerationRequest`'in **tam dört** özelliği olduğunu
sabitliyor; `@JsonIgnore`'u kaldıran bir sonda ile düşürüldüğü doğrulandı.

**Ekleme — boş dize göndermek yokluk göndermekten kötüdür.** `JobProgress`
tek bir shape olarak hem `jobs.progress` kolonuna hem `phase` olayına gidiyor,
ve `NONE` boş dizelerle çıkıyordu. `JobStatusResponse` bunları zaten `null`'a
çeviriyordu, yani **akış ile yoklama aynı olayı farklı gönderiyordu** — tek
shape'in engellemesi gereken tam da bu. `@JsonInclude(NON_EMPTY)` shape'in
kendisine kondu; `pct` üzerinde açık bir `ALWAYS` var, çünkü NON_EMPTY'nin
ilkelleri atlaması Jackson'ın kararı, bizim değil.

**Ekleme — `used`, `limit`'i geçebiliyordu ve sayı doğruydu, adı yanlıştı.**
Kota sayacı **denemeleri** sayıyor: reddedilen istek birimini geri almıyor,
yoksa sınırını aşmış bir kullanıcı sayaç tavanda sabitken ucu döverdi. Kırpmak
sunucuyu yanlış aktarmak olurdu; `Usage` iki alan taşıyor — `used` (harcanan,
`0..limit`) ve `attempted` (ham sayaç). Kırpma tek bir fabrikada ve bir
invariant onu orada tutuyor: `used > limit` taşıyan bir `Usage` inşa
edilemiyor. İki alanın anlamı **şemanın kendisine** yazıldı — `@Schema`
açıklamalarıyla, çünkü springdoc javadoc'u okumuyor ve şekil için otorite
OpenAPI.

**`F-011` yalnız belge:** Next'in dev rewrite'ı proxy'lediği yanıtı gzip'liyor
ve gzip tamponluyor, yani lokalde SSE hiç akmıyor. § 30.6'ya `proxy_buffering
off`'un yanına yazıldı. Düzeltme frontend'de.

**Açık kalan: `F-008`** — uygunluk raporu. `generations.fit_report` kolonu ve
setter'ı Aşama 2'den beri duruyor ve **hiçbir çağıran yok**; `validation/`
paketi yalnız `package-info` taşıyor. Sıradaki dilim.
