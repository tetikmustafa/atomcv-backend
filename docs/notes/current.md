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

## Adım 3.3 — kimlik · kapandı

Kayıtlar `archive/stage-3-identity.md`'de; kalıcı kararlar `spec/`'te
(§ 40.4.1, § 40.5.1, § 40.6.1, § 46.5). **`Contact.linkedin` CV alanı
LinkedIn girişinin kaldırılmasıyla ilgisiz ve duruyor** — karıştırılmasın.
Aşağıdakiler hâlâ canlı.

**`suspicious_output` telde hiç görülmedi — ve bu bir eksik değil.** Kapı bir
enjeksiyon tripwire'ı; uslu bir modelle açılmaması beklenen davranış.
`PlausibilityGateTest` onu kurgulanmış analizle sınıyor. **Bunu "çalışmıyor"
diye tamir etmeye kalkma.**

**`citext` kolonu `varchar` parametreyle aranırsa büyük/küçük harf duyarlı
arar.** UNIQUE index duyarsız korur, JPA'nın türettiği sorgu duyarlı arar;
çelişince var olan satır bulunamaz ve insert 500 verir. Çözüm
`CAST(:x AS citext)` — **`users.email` ve `email_suppressions.email`'e dokunan
her yeni sorguda.**

**`LocalDevUser` ve `LocalDevSessions` hâlâ duruyor.** Entegrasyon paketi
çerezsiz istek atıyor; `LOCAL_DEV_SESSION=false` ile kapatılabiliyor.

**`MagicLinkApiIT` her testten önce `ratelimit:*` anahtarlarını siliyor.**
Silinmezse dördüncü test ilgisiz bir 429'da düşer ve flake gibi okunur.
**Yeni bir kimlik testi yazan bunu unutmasın.**

**`/auth/verify` limitsiz.** Verifier 32 rastgele bayt; Nginx'in `auth` zone'u
(1r/s) önünde. Bugün koruduğu bir şey yok.

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

## Adım 3.5 — çok dillilik · kapandı

On iki karar § 32.2.1 ve § 32.3.1'de; inşa kaydı
`archive/stage-3-multilingual.md`'de.

**Ders (bu oturumda iki kez):** *doğru davranan kod, korunan kod değildir.*
§ 32.3'ün sıralaması Aşama 2'den beri doğruydu ve tek bir yanlış satır bütün
paketi geçerdi. Aynı şey `userEdited`'ın önkoşulunda da oldu.

**Canlı — `SkillNames.canonical` dört çağıranın ortak kuralı**, ve `RunMarking`
artık `profile.domain.content`'te (Faz D üçüncü çağıran olacak).

## Adım 3.6 — anonim mod · 1/3

**Dilim 1 (anonim oturum) ve dilim 2 (kalıcı olmayan profil) indi**; kararlar
§ 35.7.1 ve § 41.3.1'de. **Dilim 3 (adres bazlı kota) indi**; kararlar § 44.1.1'de.
**Dilim 4:** kalıcı olmayan profilin ingestion'a bağlanması (anonim yükleme).
**Dilim 5:** yükseltme akışı.

**Bulgu — `AnomalyDetectorIT` § 44.3'ün frenini çekip arkasında bırakıyordu.**
Bayrak `feature_flags`'te tek satır ve ayarlanmamış bayrak açık sayılıyor, yani
fren çekili kalınca paylaşılan bağlamda ondan sonra koşan her üretim 503
alıyordu. **On altı hata, dört ilgisiz sınıfta, ve bu sınıfın kendi testleri
geçiyordu.** `@BeforeEach`'te temizlemek yetmiyor: önemli olan sınıfın
**arkasında bıraktığı** durum. Gizliydi, yeni değil — başka yere test eklemek
sırayı kaydırınca çıktı.

**Aşama 1'den taşınan `EPHEMERAL` kısıtı kalktı** — kontrollü üretim yolu
`AnonymousSessionId`.

**Canlı — kalıcı olmayan profil hâlâ hiçbir yere bağlı değil.** Depo ve adres
kotası hazır; `ProfileImportService` ile `ProfileAssembler` yalnız kalıcı yolu
biliyor. **Anonim kullanıcı oturum alıyor ama CV yükleyemiyor** — dilim 4.

**Canlı — atom id'leri her içe aktarımda yeniden üretiliyor.** Yükseltme akışı
(dilim 5) tam olarak buna çarpacak: id'ler değişirse eşitlik bozulur.

**Canlı — anonim oturumun kullanıcı indeksi yok.** Kullanıcısı olmadığı için
"bu kişinin bütün oturumlarını iptal et" diye bir işlem de yok; onu bitiren tek
şey kendi TTL'i.

**Canlı — testler kimliksiz duruma çözülmeyen bir çerezle ulaşıyor**, ikinci bir
bağlamla değil. `local` altında çerezsiz her istek dev kullanıcısı; bayat bir
çerez ise **bilinçli olarak** local-dev isteği sayılmıyor. Beşinci bir Spring
bağlamı, CI'ın `NoClassDefFoundError`'ı dönerse ilk şüphelenilecek şey.

**Dikkat — `git checkout --` ekilmiş bir ihlali geri alırken commit edilmemiş
gerçek değişikliği de alır.** Bu oturumda bir kez oldu ve fark edilmesi
tesadüftü. **İhlal denemesinden dönerken yedek kopyadan restore et**, git'ten
değil.

