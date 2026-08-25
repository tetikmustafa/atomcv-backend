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
| ATS metin çıkarma (§ 23.2) yok | Aşama 3 | PDF metin çıkarımı istiyor. `FitReport` `F-008`'de indi — bu satırın kalan yarısı |
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

## Aşama 3 kayıtları

**Düzeltme — § 18.4'ün kod parçacığı `requiredSkills` diyordu, kod
`allSkills()` kullanıyor.** Kod doğruydu: enjekte edilmiş bir talimatın
`preferredSkills`'e düşmesini engelleyen bir şey yok. Kalıcı olduğu için
`spec/05-pipeline-a-c.md` § 18.4'e işlendi, burada yalnız izi duruyor.

**`suspicious_output` telde hiç görülmedi — ve bu bir eksik değil.** Frontend
gerçek uca karşı üç ilanla denedi, üçünde de model uzun beceri adlarını
normalleştirdi. Kapı bir enjeksiyon tripwire'ı; uslu bir modelle açılmaması
beklenen davranış. `PlausibilityGateTest` onu kurgulanmış analizle sınıyor.
**Bunu "çalışmıyor" diye tamir etmeye kalkma.**


`F-008`…`F-016` kapandı ve kayıtları
`archive/stage-3-frontend-findings.md`'ye indi (2026-08-25) — dosya sınırı.
Kalıcı kararlar `spec/`'te; arşiv yalnız nasıl bulunduklarını taşıyor.
