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

*(boş)*
