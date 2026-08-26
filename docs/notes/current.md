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

**`Axiom'da loglar görünüyor` — dataset açıldı ve `.env` dolduruldu
(2026-08-26).** Değişkenler `OTLP_*`; telde doğrulanması üretim dağıtımını
bekliyor.

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

### Adım 3.3 · LinkedIn ve OTLP adlandırması

**Sapma — LinkedIn bir kimlik sağlayıcısı değil artık.** Geliştirici kararı;
`spec/`'in altı dosyasına ve `V2` migration'ına işlendi, yani kalıcı ve burada
yalnız izi duruyor. `AuthMethod` ikiye indi, `oauth_identities.provider`
CHECK'i `('google','github')`. **`Contact.linkedin` CV alanı bununla
ilgisiz ve duruyor** — bir sonraki oturum ikisini karıştırmasın.

**Düzeltme — `spec/11-operations.md` § 46.5 `AXIOM_TOKEN` / `AXIOM_DATASET`
diyordu, kod `OTLP_*` okuyor.** Kod doğruydu ve spec koda uyduruldu: isim
satıcının değil telin adı. İki tuzak § 46.5'e yazıldı — `OTLP_AUTHORIZATION`
kendi `Bearer ` önekini taşır, ve `micrometer-registry-otlp` **metrik**
gönderdiği için URL sağlayıcının *metrics* ucu olmalı; trace ucuna giden
metrik sessizce reddedilir, log'a hiçbir şey düşmez.

**`SESSION_SECRET` `.env.example`'dan silindi.** İmzalanan bir şey yok;
kullanılmayan bir sır yalnızca sızabilecek bir sırdır. Yerine
`SESSION_COOKIE_DOMAIN` ve `SESSION_COOKIE_SECURE` geldi.

## Aşama 3 · kimlik dilimleri

Dilim 1 (oturum+CSRF) ve dilim 2 (OAuth) kapandı; kayıtları
`archive/stage-3-identity.md`'de, kalıcı kararları `spec/`'te. Aşağıdakiler
hâlâ canlı.

**Düzeltme (canlı) — `citext` kolonu `varchar` parametreyle aranırsa
büyük/küçük harf duyarlı arar.** UNIQUE index duyarsız korur, JPA'nın türettiği
sorgu duyarlı arar; ikisi çelişince var olan satır bulunamaz ve insert
`users_email_key`'de 500 verir. Çözüm `CAST(:x AS citext)`. **`users.email` ve
`email_suppressions.email`'e dokunan her yeni sorguda geçerli.**

**Sapma (canlı) — `LocalDevUser` ve `LocalDevSessions` hâlâ duruyor.**
Entegrasyon paketinin sınıfları çerezsiz istek atıyor; gerçek oturuma taşımak
ayrı bir test-altyapısı dilimi. `LOCAL_DEV_SESSION=false` ile kapatılabiliyor.

### Dilim 3 — magic link

**Ekleme — magic link hesap da yaratıyor, ve satır doğrulanmadan önce
doğuyor.** Ürün dokümanı onu hesap oluşturma yolu sayıyor, ve
`magic_link_tokens.user_id` `NOT NULL` olduğu için kullanıcı satırı istek
anında var olmak zorunda. `email_verified = false` ile duruyor; bağlantıyı
açmak kanıt, ve doğrulama o an düşüyor. **Sonucu:** herhangi biri, sahibi
olmadığı bir adres için satır yaratabiliyor. Zararsız (o satıra ait oturum,
profil yok) ama sınırsız — freni Bölüm 40.5'in rate limit'i, yani dilim 4.
**O inene kadar bu uç üretime açılmamalı.**

**Ekleme — tek kullanım koşullu `UPDATE` ile.** `used_at` okuyup yazmak, aynı
anda gelen iki isteğin ikisinin de girmesine izin verirdi. `WHERE used_at IS
NULL` ve etkilenen satır sayısı kararı veritabanına bırakıyor.

**Ekleme — giriş, hesabın bekleyen diğer bağlantılarını da harcıyor.** § 40.2
istemiyor; pencereyi yine de kapatıyor.

**Ekleme — `EmailSender` üç dallı.** Resend anahtarı varsa HTTP API; yoksa
`spring.mail` varsa SMTP (yerelde Mailpit); ikisi de yoksa **açılışta WARN** ve
hiçbir şey gitmiyor. Üçüncü dal `LlmPricingAudit`'in mantığıyla: sessizce sıfır
davranmaktansa adıyla söylensin.

**Tuzak — doğrulama sayfası önce bir `GET` yapmalı.** `POST /auth/verify` CSRF
tokenı istiyor, token da `XSRF-TOKEN` çerezinden okunuyor; e-postadan gelen
kullanıcının tarayıcısında o çerez henüz yok. Sayfa `/auth/session`'ı çağırsın,
sonra POST etsin. `B-049`'da yazılı.
