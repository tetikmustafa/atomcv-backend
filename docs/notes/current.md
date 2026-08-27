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

**Sıkılaştırılacak rate limiter hâlâ yok — ama artık bir limiter var.**
Dilim 4'ün getirdiği `RateLimiter` genel: katman adı, konu, sınır ve pencere
alıyor. § 44.3'ün istediği şey ağır kullanıcının **üretim** hakkını kısmak, ve
onu bağlayacak yer `QuotaService`. Anomali sinyalleri hâlâ yalnız raporluyor.

## Aşama 1'den taşınan kısıtlar — hâlâ açık

| Eksik | Ne zaman | Neden şimdi değil |
|---|---|---|
| `ProfileRef.Scope` yalnız `PERSISTENT` | Aşama 3 | `EPHEMERAL`'ı anonim akıştan önce eklemek, üretmenin denetimli yolu yokken sahiplik kontrolünün etrafından dolaşmanın yolu olurdu |
| ATS metin çıkarma (§ 23.2) yok | Aşama 3 | Engeli kalktı: PDFBox 3.4 dilim 1'de geldi. `FitReport` `F-008`'de indi — kalan yarısı üretilen PDF'i geri okumak |
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

Beş kararın beşi de kalıcı çıktı ve `spec/10-security.md` § 40.4.1'e işlendi:
hesabın istek anında doğması, tek kullanımın koşullu `UPDATE` ile olması,
girişin bekleyen diğer bağlantıları harcaması, `EmailSender`'ın üç dallı
olması, ve bastırılmış adrese gönderilmemesi. Burada yalnız izleri duruyor.
**"Bu uç üretime açılmamalı" kısıtı kalktı** — freni dilim 4'te indi.

**Tuzak (canlı) — doğrulama sayfası önce bir `GET` yapmalı.** `POST
/auth/verify` CSRF tokenı istiyor, token da `XSRF-TOKEN` çerezinden okunuyor;
e-postadan gelen kullanıcının tarayıcısında o çerez henüz yok. Sayfa
`/auth/session`'ı çağırsın, sonra POST etsin. `B-049`'da yazılı.

### Dilim 4 — rate limit ve Turnstile

Altı kararın altısı da kalıcı çıktı ve `spec/10-security.md` § 40.5.1'e
işlendi: katmanların sırası ve challenge'ın neden aralarında durduğu, adres
katmanının serviste yaşaması, Bucket4j yerine kayan pencere (§ 02'nin tablosuna
da), Redis erişilemezken reddetme, `remoteip`'in gönderilmemesi, ve `prod`
profilinin secret'sız açılmaması. Ters yöndeki tuzak § 46.5'te.

**Canlı — `MagicLinkApiIT` her testten önce `ratelimit:*` anahtarlarını da
siliyor.** Düzen değil zorunluluk: sınıfın çoğu testi ilk satırında bağlantı
istiyor ve pencere üçte doluyor. Silinmezse dördüncü test ilgisiz bir 429'da
düşer ve flake gibi okunur. **Yeni bir kimlik testi yazan bunu unutmasın.**

**Açık — `/auth/verify` limitsiz.** Verifier 32 rastgele bayt, yani tahmin
edilemez; Nginx'in `auth` zone'u (1r/s) de önünde. Bugün koruduğu bir şey yok.

---

## Adım 3.4 — CV yükleme ve çıkarım

**Plan:** § XI-A.6 Adım 3.4, dokuz madde; dört dilime bölündü —
**1** dosya doğrulama + metin çıkarımı · **2** LLM yapılandırma
(§ 31.4) · **3** normalizasyon (§ 31.5) · **4** uç + `PROFILE_EXTRACT` işi +
arka plan tetikleme.

### Dilim 1 — doğrulama ve çıkarım

Yedi kararın hepsi kalıcı çıktı ve `spec/07-subsystems.md` § 31.3.1'e işlendi:
dosyanın hiçbir yere yazılmaması, merdivenin sırası, NUL baytı, iki yeni hata
kodu, taranmış/boş ayrımı, `orphanWordRatio`'nun okunuşu, ve Markdown ile
LaTeX'in zıt muamele görmesi. Aşağıdakiler yalnız burada yaşıyor.

**Canlı — çok büyük multipart'ın `DOCUMENT_TOO_LARGE`'a eşlenmesi dilim 4'e
kaldı.** Spring `MaxUploadSizeExceededException` fırlatıyor ve
`ProblemDetailAdvice` onu bugün 500'e düşürür. Uç olmadan tetiklenemediği için
yazılmadı — **hiç düşmemiş bir kapı, çalıştığı bilinmeyen kapıdır.** Merdivenin
kendi boyut kontrolü çalışıyor ve test edildi.

**Canlı — `ZipSecureFile.setMinInflateRatio` global durum.** POI'nin statiği;
`DocxTextExtractor`'ın kurucusu onu her açılışta yeniden yazıyor, çünkü başka
bir kütüphane ya da sonraki bir POI sürümü gevşetirse zip-bomb koruması sessizce
kalkardı (§ 42.1).

**Frontend'e söylenecek şey dilim 4'e bırakıldı.** İki yeni kod ve kabul edilen
uzantı listesi onları ilgilendiriyor ama uç inmeden yapabilecekleri iş yarım
kalır; `B-051` yükleme sözleşmesinin tamamını tek maddede taşıyacak.
`to-frontend.md` zaten 145 satır.
