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

**`Axiom'da loglar görünüyor`** — dataset açık, `.env` dolu (2026-08-26);
telde doğrulanması üretim dağıtımını bekliyor.

**`llm_invocations.user_id` NULL.** Zincir `UserContext` tutan fazlardan
çağrılıyor ama kullanıcıyı aşağı geçirmiyor. Günlük toplam (bütçe freni) bunu
istemiyor; **kullanıcı bazlı maliyet** istiyor.

**§ 44.3'ün sıkılaştıracağı limiter hâlâ yok** — 3.3 dilim 4'ün `RateLimiter`'ı
genel (katman, konu, sınır, pencere) ama **girişe** bağlı. § 44.3 ağır
kullanıcının **üretim** hakkını kısmak istiyor; yeri `QuotaService`.

## Aşama 1'den taşınan kısıtlar — hâlâ açık

| Eksik | Ne zaman | Neden şimdi değil |
|---|---|---|
| `ProfileRef.Scope` yalnız `PERSISTENT` | Aşama 3 | `EPHEMERAL`'ı anonim akıştan önce eklemek, üretmenin denetimli yolu yokken sahiplik kontrolünün etrafından dolaşmanın yolu olurdu |
| ATS metin çıkarma (§ 23.2) yok | Aşama 3 | Engeli kalktı: PDFBox 3.4 dilim 1'de geldi. `FitReport` `F-008`'de indi — kalan yarısı üretilen PDF'i geri okumak |
| `UserScopedRepository`'de `findAll` yok | — | § 41.2 parçacığı `findByUserId` çağırıyor, o da `JpaRepository`'de yok. Alt sınıflar kendi bulucularını ekler |

## Isırmadan önce ele alınacak iki bulgu

**Atomsuz entry sayfaya hiç çıkmıyor.** Seçim atom atom çalışıyor; altında
madde olmayan bir diploma satırı aday bile değil. Çözümü § 20.2'nin modelini
değiştiriyor.

**Eşitlik atom id'siyle bozuluyor**, id'ler her içe aktarımda yeniden üretiliyor:
aynı skor *ve* maliyetteki iki atom yer değiştiriyor. İçerikten türetilen bir
bozucu düzeltir.

## Aşama 3'e taşınanlar

- **`jobs (user_id, idempotency_key)` anonim istekleri tekilleştirmiyor** —
  `user_id` NULL, Postgres NULL'ları farklı sayıyor; `COALESCE`'lı migration
  gerek (EK D.6.5).
- **Anonim TTL etkinlikle kayıyor**; metin "son etkinliğinden iki saat sonra"
  demeli (§ 9), sahibi frontend.

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

**`suspicious_output` telde hiç görülmedi — ve bu bir eksik değil.** Frontend
gerçek uca karşı üç ilanla denedi, üçünde de model uzun beceri adlarını
normalleştirdi. Kapı bir enjeksiyon tripwire'ı; uslu bir modelle açılmaması
beklenen davranış. `PlausibilityGateTest` onu kurgulanmış analizle sınıyor.
**Bunu "çalışmıyor" diye tamir etmeye kalkma.**

§ 18.4'ün `requiredSkills`/`allSkills()` düzeltmesi `spec/05-pipeline-a-c.md`
§ 18.4'e işlendi. `F-008`…`F-016` kapandı, kayıtları
`archive/stage-3-frontend-findings.md`'de (2026-08-25).

### Adım 3.3 · LinkedIn ve OTLP adlandırması

**Sapma — LinkedIn bir kimlik sağlayıcısı değil artık.** `spec/`'in altı
dosyasına ve `V2`'ye işlendi. **`Contact.linkedin` CV alanı bununla ilgisiz ve
duruyor** — bir sonraki oturum ikisini karıştırmasın.

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

### Dilim 3-4 — magic link, rate limit, Turnstile

Kapandı; kayıtları `archive/stage-3-identity.md`'de, kalıcı kararları
`spec/10-security.md` § 40.4.1 ve § 40.5.1'de. İki şey hâlâ canlı:

**`MagicLinkApiIT` her testten önce `ratelimit:*` anahtarlarını da siliyor.**
Sınıfın çoğu testi ilk satırında bağlantı istiyor ve pencere üçte doluyor;
silinmezse dördüncü test ilgisiz bir 429'da düşer ve flake gibi okunur.
**Yeni bir kimlik testi yazan bunu unutmasın.**

**`/auth/verify` limitsiz.** Verifier 32 rastgele bayt; Nginx'in `auth`
zone'u (1r/s) önünde. Bugün koruduğu bir şey yok.


## Adım 3.4 — CV yükleme ve çıkarım · 4/4 · kapandı

Otuz yedi kararın hepsi `spec/`'e işlendi (§ 31.3.1, § 31.4.1, § 31.5.1,
§ 31.6.1, § 31.6.2; ayrıca § 43.1 ve § 53.1). İnşa kaydı
`archive/stage-3-ingestion.md`'de.

**İki ders.** *Bir metodun javadoc'u ne zaman çalıştığını söylüyorsa, çağıranı
da ara* — `validateConfiguredPrompts()` "açılışta hata" diyordu ve kimse onu
çağırmıyordu. *Düşmeyen bir ihlal denemesi, bir eksik testtir* — arka plan
işlerinin sırasını hiçbir test tutmuyordu.

### Hâlâ canlı olanlar

**`jobs.payload` kullanıcı içeriği taşıyor ve tamamlanmış işleri budayan bir
şey yok.** `generation` da ilan metnini taşıyor, yani saklama süresi her iş
tipinin sorusu.

**`ProfileWriter` mevcut profile *ekliyor*, değiştirmiyor.** İkinci bir içe
aktarma bölümleri ikinci kez yazar. `PROFILE_ALREADY_EXISTS` (409) katalogda ve
**hiçbir şey onu üretmiyor** — ürünün "ikinci CV yüklenirse ne olur" cevabı yok.

**Embedding yalnız İngilizce varyanttan** (§ 31.6.2), ve İngilizce varyantı
olmayan atom atlanıyor. **Adım 3.5 tam olarak buraya dokunuyor.**

**`ExtractedContact`, `Contact` ve şema aynı şekli üç yerde taşıyor**
(§ 31.4.1); alan eklendiğinde üçü birden güncellenmeli.

**`MIN_LANGUAGE_CONFIDENCE = 0.5` prompt'un cümlesiyle eşleşiyor**; birini
değiştiren ötekini de değiştirmeli.

**`SkillNames.canonical` dört çağıranın ortak kuralı** — ingestion, Faz B
skorlayıcı, Faz F raporu, run işaretleme. Alias dosyasında soldaki taraf
insanların gerçekten yazdığı gibi olmalı, yoksa anahtar hiçbir şeyle eşleşmez.

**İzlenecek — CI bir kez `PGVectorTypeContributor`'da `NoClassDefFoundError`
verdi; aynı ağaç tekrar koşuşta geçti** (2026-08-27, run 33091345512).
Paylaşılan dâhil dört bağlam birden düştü, yerelde hiç düşmedi. Tek makul
açıklama **bağlam sayısı**: entegrasyon paketi artık dört Spring bağlamı
kuruyor, her biri kendi `EntityManagerFactory`'siyle. **Tekrarlarsa ilk
bakılacak yer beşinci bir bağlam, kod değil.**

**Açık — `local-fake` fixture'ı yok ve uydurulamaz.** Fixture anahtarı istek
metninin özetinden türüyor; elle yazılan bir fixture yalnız tek bir CV'de
ateşlenir. **`make record` geliştiricinin anahtarını istiyor.**
