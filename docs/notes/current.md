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

## Kapanış denetimi (2026-08-28) — sekiz dilimin yedisi indi

Aşama 0-3 baştan tarandı, on üç karar alındı ve uygulandı. **Tam kayıt
`kapanis-denetimi.md`'de**: her bulgu, her kararın gerekçesi, ve geliştiriciye
düşenler. Buradaki kural gereği burada yalnız *hâlâ canlı olan* duruyor.

**Kalıcı olanlar `spec/`'e işlendi:** § 47 (migration açılışta kalıyor,
`--spring.flyway.migrate-only` diye bir property yok), § 57.4 (R2 listede var,
kodda yok), § 3.2 (Resend `send.` öneki ve bölge), § 51.7 (testin kendisi
hakkındaki dört kural, CLAUDE.md'den taşındı).

**Açık kalan tek kod maddesi: atomsuz entry.** Sayfa sınırı garantisine
dokunduğu için ayrı bir oturuma bırakıldı —
`sonraki-oturum-atomsuz-entry.md` ne yapılacağını satır satır taşıyor.

**Geliştiricide:** yeni model seçilince fiyat tablosu — **o güne kadar günlük
bütçe freni çalışmaz**, çünkü fiyatı olmayan model sıfır ediyor ve toplam hep
sıfır kalıyor. Ayrıca VPS kurulumu ve restore testi.

## Aşama 2'den öğrenilen, tekrar edecek iki şey

- **Kılavuz "tablo" dediğinde önce `V1`'e bak** — beş kez var olan bir tablo
  için migration istedi.
- **Toplu JPQL `update` `@Version`'ı atlar** ve **okuma, yakalanmak istenen
  bayatlığı onarır** — etag'i **önceki yazmanın yanıtından** al. Aşama 3'ün
  başvuru izlemesi ikisine de çarpacak.

---

## Kapanan adımların arşiv haritası

| Adım | İnşa kaydı | Kalıcı kararlar |
|---|---|---|
| 3.3 kimlik | `archive/stage-3-identity.md` | § 40.4.1, § 40.5.1, § 40.6.1, § 46.5 |
| 3.4 çıkarım | `archive/stage-3-ingestion.md` | § 31.3.1, § 31.4.1, § 31.5.1, § 31.6.1-2, § 43.1, § 53.1 |
| 3.5 çok dillilik | `archive/stage-3-multilingual.md` | § 32.2.1, § 32.3.1 |
| 3.6 anonim | `archive/stage-3-anonymous.md` | § 35.7.1, § 41.3.1-3, § 44.1.1, § 44.2, § 31.6.3 |
| 3.8 Faz D | `archive/stage-3-faz-d.md` | § 21.1 notu, § 21.3.1, § 21.5.1-7.1, § 34.4.1 |
| 3.9 hukuki | — | § 57.4.1, § 48.4.1 |

Frontend aksiyonları: `B-055`-`B-058`.

## Kapanan adımlardan hâlâ canlı olanlar

**Tamir etmeye kalkma — ikisi de beklenen davranış:**
- **`suspicious_output` telde hiç görülmedi.** Bir enjeksiyon tripwire'ı; uslu
  bir modelle açılmaması doğru sonuç.
- **`bullet_rewrite` / `about_synthesis` yerelde anlamsız çalışıyor.**
  Fixture yokken `SyntheticAnswer` şema şeklinde bir cümle üretiyor,
  doğrulayıcı reddedip orijinali bastırıyor. Fixture inince düzelir.

**Test yazarken:**
- **`MagicLinkApiIT` her testten önce `ratelimit:*`'ı siliyor.** Silmeyen bir
  kimlik testi dördüncü sırada ilgisiz bir 429'da düşer, flake gibi okunur.
- **`profiles` entegrasyon paketinde hiç boş değil** (`DevSeeder`) — anonim
  gizlilik iddiası bu yüzden "satır sayısı değişmedi" diye kuruluyor.
- **`local` profilinde yapılandırılmış LLM sağlayıcısı yok**, entegrasyon
  lane'inde her çağrı `ALL_PROVIDERS_UNAVAILABLE` alır. `CoverLetterApiIT`
  bunu kasten öyle kuruyor.
- **`AccountDeletionIT` tablo listesini `information_schema`'dan okuyor** —
  elle yazılmış bir liste, sonradan eklenen tabloda sonsuza kadar geçerdi.

**Sınırlar ve açıklar:**
- **`latexTest` 44/48**, sebebi fixture yokluğu (`job_analysis` → `LOW_CONFIDENCE`
  → `failed`), Faz D değil. `ce9483e`'de de düşüyordu. Düzeltmesi `make record`.
- **Faz D bir üretimde sekize kadar eşzamanlı çağrı yapıyor**, her biri
  `REQUIRES_NEW` ile bağlantı alıyor. Havuz 10, işçi eşzamanlılığı 2 → tepede
  16 kısa ödünç. **Havuz büyütülmeden işçi eşzamanlılığı artırılmamalı.**
- **`support_grants.accessed_at`'i hiçbir şey yazmıyor** — operatör arayüzü bu
  repoda yok. **Erişim aracı gelirse ilk işi o kolonu yazmak olmalı.**
- **R2'deki PDF'ler** § 57.4'ün silme listesinde, R2 istemcisi ise hiç yok
  (7. karar: MVP'ye girmiyor, cümle işaretlenecek).
- **Anonim işler yükseltmede taşınmıyor** (`anon_session_id` ile kapsanıyor);
  bugün zararsız, anonim *üretim* inince değişir. **Anonim oturumun kullanıcı
  indeksi yok** — toplu iptali de yok, onu bitiren TTL'i.
- **`ExtractedContact`, `Contact` ve şema aynı şekli üç yerde taşıyor**
  (§ 31.4.1). **`SkillNames.canonical` dört çağıranın ortak kuralı** — alias
  dosyasında sol taraf insanların yazdığı gibi olmalı, yoksa anahtar eşleşmez.
- **`Contact.linkedin` CV alanı**, LinkedIn *girişinin* kaldırılmasıyla ilgisiz
  ve duruyor — karıştırılmasın.
- **İzlenecek:** CI bir kez `PGVectorTypeContributor`'da `NoClassDefFoundError`
  verdi, aynı ağaç tekrar koşuşta geçti (2026-08-27, run 33091345512).
  **Tekrarlarsa ilk bakılacak yer bağlam sayısı, kod değil.**

**Ders (Adım 3.4, ve dilim 1'de ikinci kez):** *bir metodun javadoc'u ne zaman
çalıştığını söylüyorsa çağıranı da ara.*
**Ders (Adım 3.5):** *doğru davranan kod, korunan kod değildir.*

---

## Dilim kayıtları

Sekiz dilimin hepsinin kaydı **`kapanis-denetimi.md` § 6**'da: ne bulundu, ne
yazıldı, hangi muhafız hangi ihlalle düşürüldü. Buraya kopyalanmadı — ikinci
bir kopya ayrışır.

**Bu oturumda üç kez tekrarlanan ve tekrar edecek olan ders:** *bir muhafızın
düştüğünü görmeden yazıldı sayma.* Üçü de yeşil görünüyordu ve üçü de bir şey
kanıtlamıyordu — `make record`'un hiç kaydetmemesi, `@SpringBootTest`'in taban
sınıfın anahtarlarını düşürmesi, ve CSRF muafiyeti sökülüyken geçen webhook
testi. Sonuncusunu CLAUDE.md zaten yazmıştı ve yine düşüldü; kural artık
§ 51.7'de.
