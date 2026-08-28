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

- **Kılavuz "tablo" dediğinde önce `V1`'e bak** — beş kez var olan bir tablo
  için migration istedi.
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
**"Çalışmıyor" diye tamir etmeye kalkma.**

**`citext` kolonu `varchar` parametreyle aranırsa büyük/küçük harf duyarlı
arar.** UNIQUE index duyarsız korur, JPA'nın türettiği sorgu duyarlı arar;
çelişince var olan satır bulunamaz ve insert 500 verir. Çözüm
`CAST(:x AS citext)` — **`users.email` ve `email_suppressions.email`'e dokunan
her yeni sorguda.**

**`MagicLinkApiIT` her testten önce `ratelimit:*` anahtarlarını siliyor.**
Silinmezse dördüncü test ilgisiz bir 429'da düşer ve flake gibi okunur.
**Yeni bir kimlik testi yazan bunu unutmasın.**

**`/auth/verify` limitsiz** — verifier 32 rastgele bayt, Nginx'in `auth`
zone'u önünde. Bugün koruduğu bir şey yok.

## Adım 3.4 — CV yükleme ve çıkarım · 4/4 · kapandı

Otuz yedi kararın hepsi `spec/`'e işlendi (§ 31.3.1, § 31.4.1, § 31.5.1,
§ 31.6.1, § 31.6.2; ayrıca § 43.1 ve § 53.1). İnşa kaydı
`archive/stage-3-ingestion.md`'de.

**Ders:** *bir metodun javadoc'u ne zaman çalıştığını söylüyorsa çağıranı da
ara* — `validateConfiguredPrompts()` "açılışta hata" diyordu, çağıranı yoktu.

### Hâlâ canlı olanlar

**`jobs.payload` kullanıcı içeriği taşıyor ve tamamlanmış işleri budayan bir
şey yok.** `generation` da ilan metnini taşıyor, yani saklama süresi her iş
tipinin sorusu.

**`ProfileWriter` mevcut profile *ekliyor*, değiştirmiyor.** İkinci bir içe
aktarma bölümleri ikinci kez yazar. `PROFILE_ALREADY_EXISTS` (409) katalogda ve
**hiçbir şey onu üretmiyor** — ürünün "ikinci CV yüklenirse ne olur" cevabı yok.

**`ExtractedContact`, `Contact` ve şema aynı şekli üç yerde taşıyor** (§ 31.4.1).

**`SkillNames.canonical` dört çağıranın ortak kuralı.** Alias dosyasında sol
taraf insanların gerçekten yazdığı gibi olmalı, yoksa anahtar eşleşmez.

**İzlenecek — CI bir kez `PGVectorTypeContributor`'da `NoClassDefFoundError`
verdi, aynı ağaç tekrar koşuşta geçti** (2026-08-27, run 33091345512): dört
bağlam birden, yerelde hiç. **Tekrarlarsa ilk bakılacak yer bağlam sayısı,
kod değil.**

**Açık — `local-fake` fixture'ı yok ve uydurulamaz.** Fixture anahtarı istek
metninin özetinden türüyor; elle yazılan bir fixture yalnız tek bir CV'de
ateşlenir. **`make record` geliştiricinin anahtarını istiyor.**

## Adım 3.5 — çok dillilik · kapandı

On iki karar § 32.2.1 ve § 32.3.1'de; inşa kaydı
`archive/stage-3-multilingual.md`'de.

**Ders (bu oturumda iki kez):** *doğru davranan kod, korunan kod değildir.*
§ 32.3'ün sıralaması Aşama 2'den beri doğruydu ve tek bir yanlış satır bütün
paketi geçerdi. Aynı şey `userEdited`'ın önkoşulunda da oldu.

## Adım 3.6 — anonim mod · 6/6 · kapandı

Kararlar `spec/`'te (§ 35.7.1, § 41.3.1, § 41.3.2, § 41.3.3, § 44.1.1,
§ 31.6.3, § 44.2); inşa kaydı `archive/stage-3-anonymous.md`'de.

**Ders — entegrasyon testi işleyiciyi çağırmıyorsa işleyiciyi korumuyor.**
Anonim yükleme testi önce yazıcıyı doğrudan çağırıyordu; dalı kalıcı yazmaya
çevirdiğimde **geçti**. Yazıcı zaten bunu yanlış yapabilecek parça değildi.
*Ekilen ihlalin hangi testi düşürdüğüne bak; hiçbirini düşürmüyorsa test yanlış
yerde duruyor.*

**Taşınan carry-over kendiliğinden kapandı** — yükseltme yeniden içe
aktarmıyor, satırları kendi id'leriyle yazıyor. İçe aktarımlar arası id
kararsızlığı duruyor, yükseltmenin sorunu değil.

### Hâlâ canlı

**Hesabın *boş* profil satırı yükseltmeyi engelliyor.** Kontrol "profil satırı
var mı"; bir kez giriş yapıp uygulamayı açan herkeste boş satır oluşuyor
(`ProfileResolver.own` tembel yaratıyor). O kişi çıkıp anonim çalışırsa
`kept_existing` alıyor ve emeği gidiyor. Dar ama **sessiz** bir durum; boşluk
kontrolü de birleştirme de ürün kararı istiyor.

**Anonim işler yükseltmede taşınmıyor.** İş `anon_session_id` ile kapsanıyor;
giriş sonrası kişi kendi çıkarım işini göremiyor. Bugün zararsız (iş bitmiş
oluyor), anonim *üretim* inince değişir.

**Anonim oturumun kullanıcı indeksi yok**, yani "bütün oturumlarını iptal et"
işlemi de yok; onu bitiren tek şey TTL'i.

**`profiles` tablosu entegrasyon paketinde hiç boş değil** (`DevSeeder`).
Anonim gizlilik iddiası bu yüzden "satır sayısı değişmedi" diye kuruluyor.

## Adım 3.8 — Faz D ve cover letter · 3/5

Dilimler: **1 sözcükleme + eşikler** ✅ · **2 prompt + doğrulayıcı** ✅ ·
**3 paralel yürütme + boru hattı** ✅ · 4 About sentezi · 5 cover letter.

Kararlar `spec/`'te: § 21.3.1, § 21.6.1, § 21.5.1 ve § 21.1'in altındaki not.
**Faz D ilana özel üretimde koşuyor**, genel modda koşmuyor; frontend'in
aksiyonu **`B-055`** (`generation.phase.REWRITING`).

**Ders tekrar etti:** *ekilen ihlal hiçbir testi düşürmüyorsa eksik olan
testtir.* Dilim 3'te dört ihlal ekildi, dördü de doğru testi düşürdü.

### Canlı olanlar

**`latexTest` bu makinede 44/48** ve sebebi Faz D değil:
`src/test/resources/fixtures/llm` **yok**, `job_analysis` sentetik cevap alıyor,
`confidence` kapının altına düşüyor → `LOW_CONFIDENCE` → iş `failed`. Dört
`JobSpecificCvIT` testi **`ce9483e`'de de düşüyor**; STATUS'un "48 · 0 hata"
satırı fixture'lar yerelde varken ölçülmüş. Düzeltmesi `make record`, kod değil.

**Faz D bir üretimde sekize kadar eşzamanlı çağrı yapıyor**, ve her çağrının
sonunda `LlmInvocationRecorder` `REQUIRES_NEW` ile bağlantı alıyor. Havuz 10,
işçi eşzamanlılığı 2 → tepede 16 kısa ödünç. Bugün sorun değil (bağlantı LLM
çağrısından *sonra*, milisaniyeler için), ama **havuz büyütülmeden işçi
eşzamanlılığı artırılmamalı.**

**`bullet_rewrite` için de `local-fake` fixture'ı yok.** `SyntheticAnswer` şema
şeklinde bir cümle üretiyor: yerelde yeniden yazım **çalışıyor ama anlamsız**,
doğrulayıcı onu reddedip orijinali bastırıyor. Doğru davranış; "bozuk" diye
tamir etmeye kalkma.
