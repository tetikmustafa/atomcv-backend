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

### Adım 3.3 · dilim 1 — oturum omurgası

**Sapma — `LocalDevCurrentUser` "kimlik gelince bütünüyle silinecek" diyordu;
silinmedi, ikiye bölündü.** Oturumlar, oturum *başlatmanın* herhangi bir
yolundan önce indi: OAuth bir sonraki dilim. Sınıfı silmek `make dev`'e
kullanıcısı olmayan bir veritabanı ve kimse olamayan bir tarayıcı bırakırdı.
Kalanı `LocalDevUser` (yalnız satırı basan tohum) ve
`identity.service.LocalDevSessions` (yalnız `sid` çerezi **hiç yokken**
cevaplayan yedek). Gerçek çerez her zaman kazanır, ikisi de `@Profile("local")`.
Gerçek giriş inince ikisi de gider.

**Ekleme — `AUTHENTICATION_REQUIRED` (401).** Katalogda oturumsuz isteğin
karşılığı yoktu. Kalıcı olduğu için `spec/08b-api-contract.md` § EK D.6'ya
işlendi; frontend'e `B-045`.

**Ekleme — hesabın yetenek kümesi.** § 35.7 yalnız anonim gövdeyi yazmıştı.
Karar `spec/08-api.md` § 35.7'ye işlendi; frontend'e `B-046`.

**Ekleme — oturum deposu Spring Session değil, elde yazıldı.** Kayan TTL
(§ D.6.6) ve Adım 3.6'nın anonim→hesap devri `SessionRepository`'nin etrafından
dolaşmayı gerektirirdi. Spring Security yalnız filtre zinciri ve CSRF için
duruyor; kimliği o taşımıyor. `SessionStore` kullanıcı başına ikinci bir anahtar
tutuyor (`sess:user:{id}`) — Bölüm 40.1'in JWT'ye karşı öne sürdüğü "iptal
anında" iddiasının tek dayanağı o, ve sonradan eklenirse o ana kadarki her
oturum iptal edilemez kalırdı.

**Ekleme — `sid` çerezinde `secure` yalnız `local`'de kapalı.** Safari
`http://localhost` üzerinde `Secure` çerezi reddediyor, Chrome ve Firefox
kabul ediyor. `application.yml` açık tutuyor, `application-local.yml`
kapatıyor, üretim hiçbir şeyi geçersiz kılmıyor. `SessionProperties` noktayla
başlayan bir `domain`'i **açılışta reddediyor** — Adım 3.3'ün uyarısı yazı
değil kural.

**Testteki iki tuzak `CLAUDE.md`'ye yazıldı** (iç içe `@TestConfiguration`
miras alınan taban sınıfta bulunmaz; `@WebMvcTest` kendi zincirini kurar).
Burada ikinci kopyası durmasın.

**Redis artık entegrasyon paketinde gerçek.** Taban sınıfa bir konteyner
eklendi. Yoksa `SessionStore` her aramada başarısız oluyor — ve tam olarak
tasarlandığı gibi, "kimse giriş yapmamış" diye — yani oturum testi bir kesintiye
karşı iddiada bulunurdu. Bölüm 18.6'nın analiz önbelleği de aynı yokluğu fark
edilmeden hoş görüyordu.

**Açık kalan:** giriş yolu yok (OAuth · dilim 2), magic link (dilim 3), üç
katmanlı rate limit ve Turnstile (dilim 4). `revokeAllFor` yazıldı ve sınandı
ama henüz çağıran yok — rol değişimi ve hesap silme onu isteyecek.

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

### Adım 3.3 · dilim 2 — OAuth

Kalıcı kararların tamamı `spec/10-security.md` § 40.6.1'e yazıldı; burada
yalnız izleri ve **koddan öğrenilenler** duruyor.

**Düzeltme — `citext` kolonu `varchar` parametreyle aranırsa büyük/küçük harf
duyarlı arar.** JPA'nın türettiği `findByEmail`, JDBC parametresini `varchar`
bağlıyor; Postgres `citext = varchar`'ı citext'i `text`'e indirerek çözüyor.
Sonuç iki yarının çelişmesi: **UNIQUE index duyarsız koruyor, sorgu duyarlı
arıyor** — var olan hesap bulunamıyor, giriş "yeni kişi" diyor, insert
`users_email_key`'de ölüyor ve kullanıcı 500 görüyor. Çözüm parametreyi
`CAST(:email AS citext)` ile yukarı çevirmek; aynı index'i de kullanıyor.
`OAuthApiIT` yalnız harf büyüklüğü farkeden bir adresle iki kez giriyor — bunu
o buldu. **Aynı tuzak `users.email`'e dokunan her yeni sorguda var.**

**Düzeltme — çok kuruculu bir record'u Spring Boot bağlamıyor.**
`Registration`'a kolaylık olsun diye eklenen ikinci kurucu, her sağlayıcıyı
sessizce yapılandırılmamış bıraktı; belirti, hiç görünmeyen bir giriş
düğmesiydi. Kolaylık artık statik `of(...)` fabrikası.

**Ekleme — `SignInAccounts` kullanıcı kapsamlı değil.** Mutlak kural 3'ün tek
istisnası: giriş, *kullanıcının kim olduğunu hesaplama* eylemi ve hesaplamakta
olduğu cevapla kapsanamaz. Yerine geçen şey cephenin darlığı — id alan bulucu
yok, listeleyen yok. ArchUnit'e modülün satırı eklendi ve **kasıtlı ihlalle
düştüğü görüldü**. Metot eklenecekse ölçüt: saldırganın cevabını istediği bir
soruyu yanıtlıyorsa kapsamlı bir repository'nin arkasına ait.

**Ekleme — `UserAccount.email` `@Column(columnDefinition = "citext")` şart;**
onsuz Hibernate `validate` açılışta düşüyor (`citext (Types#OTHER)` vs
`varchar`).

**Ekleme — yerel giriş kısayolu kapatılabilir** (`LOCAL_DEV_SESSION=false`).
Açıkken çıkış yapmak dev kullanıcısına düşürüyor ve tarayıcı tekrar girişli
görünüyor — logout'un çalıştığını izlemeye çalışırken kafa karıştırıcı.

**Açık kalan:** magic link (dilim 3), üç katmanlı rate limit + Turnstile
(dilim 4). `LocalDevUser` ve `LocalDevSessions` hâlâ duruyor — entegrasyon
paketinin 27 sınıfı çerezsiz istek atıyor; onları gerçek oturuma taşımak ayrı
bir test-altyapısı dilimi.
