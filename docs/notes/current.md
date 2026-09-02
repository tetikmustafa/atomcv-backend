# İnşa Notları — Aktif

> Kural: bu dosya **200 satırı geçmez**. Aşama bitince `archive/`'a taşınır, boş başlanır.
> Kayıt tipleri: **Sapma** (doküman başka diyor) · **Ekleme** (doküman sessiz) · **Düzeltme** (doküman yanlış).
>
> Bir sapma kalıcıysa `spec/`'e işlenir ve buradan silinir.

**Aktif aşama:** Aşama 3 — hesap ve MVP. **Plan:** `spec/14-build-guide.md`
§ XI-A.6; gerekçesi `spec/13-development.md` § 55. Aşama 1-2'nin tam kayıtları
`archive/stage-1.md` ve `archive/stage-2.md`'de.

---

## Aşama 2'den taşınan açık kutular

- **`Axiom'da loglar görünüyor`** — dataset açık, `.env` dolu (2026-08-26);
  telde doğrulanması üretim dağıtımını bekliyor.
- **`llm_invocations.user_id` NULL.** Zincir `UserContext` tutan fazlardan
  çağrılıyor ama kullanıcıyı aşağı geçirmiyor. Günlük toplam (bütçe freni)
  bunu istemiyor; **kullanıcı bazlı maliyet** istiyor.
- **§ 44.3'ün sıkılaştıracağı limiter hâlâ yok** — 3.3 dilim 4'ün
  `RateLimiter`'ı genel ama **girişe** bağlı; § 44.3 ağır kullanıcının
  **üretim** hakkını kısmak istiyor, yeri `QuotaService`.

## Aşama 1'den taşınan kısıtlar — hâlâ açık

| Eksik | Ne zaman | Neden şimdi değil |
|---|---|---|
| ATS metin çıkarma (§ 23.2) yok | Aşama 3 | Engeli kalktı: PDFBox 3.4 dilim 1'de geldi. `FitReport` `F-008`'de indi — kalan yarısı üretilen PDF'i geri okumak |
| `UserScopedRepository`'de `findAll` yok | — | § 41.2 parçacığı `findByUserId` çağırıyor, o da `JpaRepository`'de yok. Alt sınıflar kendi bulucularını ekler |

## Aşama 3'e taşınanlar

- **Anonim TTL etkinlikle kayıyor**; metin "son etkinliğinden iki saat sonra"
  demeli (§ 9), sahibi frontend.

## Kapanış denetimi (2026-08-28) — sekiz dilimin yedisi indi

Aşama 0-3 baştan tarandı, on üç karar alındı ve uygulandı. **Tam kayıt
`kapanis-denetimi.md`'de**: her bulgu, her kararın gerekçesi, ve geliştiriciye
düşenler. Buradaki kural gereği burada yalnız *hâlâ canlı olan* duruyor.

**Kalıcı olanlar `spec/`'e işlendi ve buradan silindi** (kural gereği): § 47,
§ 57.4, § 3.2, § 51.7, ve atomsuz entry'nin § 20.2'si. Gerekçeleri
`kapanis-denetimi.md`'de.

**Hâlâ canlı olan iki kayıt:**

- **Sığmayan bir başlık-adayı için `RejectedAtom` üretilmiyor** — o liste
  kullanıcıya atom atom gösteriliyor ve hiçbir atoma çözülmeyen bir entry
  id'si sessizlikten kötü. Golden'daki "her atom ya seçilir ya bir sebep alır"
  sayımının başlık-adaylarını dışlamasının sebebi bu.
- **`SelectionPhase.openEntries` `LinkedHashSet` olmak zorunda.** `HashSet`
  iken `upgradeFirstEntryOf` "ilk ulaştığını" ücretlendiriyordu ve iterasyon
  JVM başına tuzlandığı için bir kaldırmanın iadesi koşudan koşuya
  değişiyordu. **İterasyon sırası bir sayıya dönüşüyorsa `Linked*` gerekiyor.**

**Geliştiricide:** yeni model seçilince fiyat tablosu — **o güne kadar günlük
bütçe freni çalışmaz**, çünkü fiyatı olmayan model sıfır ediyor ve toplam hep
sıfır kalıyor. Ayrıca VPS kurulumu ve restore testi.

## Aşama 3 · dilim 9-13 — `F-017`-`F-024` (2026-08-29/30)

Kayıtları `archive/stage-3-handoff-answers.md`'de; kalıcı kararlar EK D.6,
EK D.6.1, EK D.6.9, `spec/07-subsystems.md` § 31.6.4-5, `spec/08-api.md`
§ 35.3, `spec/11-operations.md` § 48.4.2 ve `spec/16-cost-legal.md` § 57.6'da.
Frontend aksiyonları `B-062`-`B-070`, dokuzu da kapandı. Burada yalnız
**canlı** olanlar:

**Tamir etmeye kalkma — üçü de bilinçli:**
- **"Kritik uyarı" diye bir şey yok, `critical` bayrağı da yok.**
  `ExtractionWarningCode` kapalı ve altı değerinin altısı da düzeltilebilir bir
  alanı tarif ediyor; § 31.6'nın üçüncü kuralı **silindi**, sayıya
  indirilmedi. Yedincisi gerçekten engelleyici olursa karar § 31.6.4'te.
- **`ImportWarning.code` alanı `String`, şeması enum.** Değer JSONB'den geri
  okunuyor; tipi enum yapmak, adı değişmiş bir kod taşıyan eski satırı ya
  attırır ya düşürür, ve düşen uyarı `warningCount == warnings.length`'i bozar.
- **`OpenApiSchemaIT`'in okuduğu altı değer elle yazılı.**
  `ExtractionWarningCode.values()`'tan türetilirse yedinci değere de "evet"
  der; oysa yedincisi karşı reponun duyması gereken bir tel değişikliği.

**`shared.wire` yeni bir paket, ve bir sonraki kapalı sözlüğün yeri.** İki
modülün yayımladığı, **ret olmayan** sözlükler oraya; `shared.error` retlerin.

**Ders — bir alanı yayımlamak, onu ilk kez okumaktır.** Frontend uyarıların
*yerini* istedi; yayımlamaya kalkınca yerin iki ayrı biçimde **yanlış** olduğu
çıktı. Dilim 9'un `Retry-After`'ının tam tersi: orada iddia doğruydu ve kanıtı
yoktu, burada alan vardı ve yanlıştı.

## Aşama 3 · dilim 14 — `F-027`, `F-025` (2026-09-02)

Silinmiş bir hesabın oturumu artık `401`. Kontrol `SessionCurrentUser`'ın
oturum çözümünde, uçlarda değil: kusur profile özgü değildi, silinmiş bir
`user_id` adına yapılan **her yazma** yabancı anahtarı ihlal ediyordu — profil
uçlarının `500` dönmesinin sebebi satırı ilk kullanımda yaratmalarıydı, hiçbir
şey yazmayan `GET /generations` ise hesap yerindeymiş gibi `200` dönüyordu.
İstek başına tek birincil anahtar okuması, ve zaten memoize edilmiş.

**Ekleme — `revokeAllFor` artık yutmuyor, fırlatıyor.** `AccountDeletionService`
oturumları satırdan **önce** siliyor ve gerekçesini javadoc'unda yazıyor; ama
Redis hatası `warn` + `0` dönüyordu, ki bu "hiç oturumu yoktu"dan ayırt
edilemez. Hesap o cevabın üstüne siliniyordu. Artık silinmiyor.

**Sapma — `DELETE /account` iki kez basılınca ikincisi `204` değil `401`.**
Uç hâlâ idempotent (§ 57.4); değişen, ikinci basışın uca *ulaşamaması*. Telde
zaten böyleydi — ilk yanıt çerezi siliyor — eski `204`'ü üreten şey dev
stub'ıydı.

**Ders — bir sınıf yıktığını geri koymuyorsa, onu ayakta tutan şey sıradır.**
`AccountDeletionIT` acting user'ı siliyor ve suite tek bağlam paylaşıyor;
`SecondImportIT` üç sınıf sonra `409`'unu **silinmiş bir kullanıcıdan**
alıyordu. Yani o sınıf tam da bu kusur sayesinde geçiyormuş, ve `401` inince
ortaya çıktı. `@AfterEach` artık dev kullanıcısını ve altın profili geri
koyuyor.

**`F-025` — `Ekleme`: işveren, ilanın taşıdığı bir addır ya da hiçbir şeydir.**
`EmployerName.verifiedAgainst` ilanda geçmeyen `company.name`'i siliyor,
Faz A'nın geçidinden sonra ve cache'ten önce. Yer tutucu ifade listesi
**bilerek yazılmadı**: dilden ve modelden bağımsız olan şey, adın ilanda
bulunması. Dört yazımı da tek kural yakalıyor (`""`, `"Unknown"`,
`"not specified"`, `"Belirtilmemiş"`).

**Bilinçli bedeli var:** modelin alıntılamak yerine *yeniden yazdığı* bir ad
(çeviri, "A.Ş." eklemesi) etiketi kaybediyor. Şirketsiz satır işi hâlâ
söylüyor; yanlış şirketli satırı okuyanın ayırt etme yolu yok.

**Taşınan:** aynı şey prompt'ta da yazmalı, ama yazmak yeni bir sürüm demek
(§ 53.2) — üç fixture ve bir haftalık cache geçersiz olur. **`job_analysis`
model seçimiyle birlikte `v2`'ye çıktığında o cümle de girsin.**
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
bir kopya ayrışır. Oradan çıkan tek kural § 51.7'de: *bir muhafızın düştüğünü
görmeden yazıldı sayma.* Dilim 14'te yine gerekti.
