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
- **Anonim TTL etkinlikle kayıyor**; metin "son etkinliğinden iki saat sonra"
  demeli (§ 9), sahibi frontend.
- **§ 44.3'ün sıkılaştıracağı limiter hâlâ yok** — 3.3 dilim 4'ün
  `RateLimiter`'ı genel ama **girişe** bağlı; § 44.3 ağır kullanıcının
  **üretim** hakkını kısmak istiyor, yeri `QuotaService`.

## Aşama 1'den taşınan kısıtlar — hâlâ açık

| Eksik | Ne zaman | Neden şimdi değil |
|---|---|---|
| ATS metin çıkarma (§ 23.2) yok | Aşama 3 | Engeli kalktı: PDFBox 3.4 dilim 1'de geldi. `FitReport` `F-008`'de indi — kalan yarısı üretilen PDF'i geri okumak |
| `UserScopedRepository`'de `findAll` yok | — | § 41.2 parçacığı `findByUserId` çağırıyor, o da `JpaRepository`'de yok. Alt sınıflar kendi bulucularını ekler |

## Kapanış denetimi (2026-08-28) — sekiz dilimin yedisi indi

**Tam kayıt `kapanis-denetimi.md`'de**; kalıcı olanlar `spec/`'e işlendi
(§ 47, § 57.4, § 3.2, § 51.7, atomsuz entry'nin § 20.2'si). Hâlâ canlı ikisi:

- **Sığmayan bir başlık-adayı için `RejectedAtom` üretilmiyor** — o liste
  kullanıcıya atom atom gösteriliyor ve hiçbir atoma çözülmeyen bir entry
  id'si sessizlikten kötü. Golden'daki "her atom ya seçilir ya bir sebep alır"
  sayımının başlık-adaylarını dışlamasının sebebi bu.
- **`SelectionPhase.openEntries` `LinkedHashSet` olmak zorunda.** `HashSet` iken
  `upgradeFirstEntryOf` "ilk ulaştığını" ücretlendiriyor ve iterasyon JVM başına
  tuzlandığı için bir kaldırmanın iadesi koşudan koşuya değişiyordu.
  **İterasyon sırası bir sayıya dönüşüyorsa `Linked*` gerekiyor.**

**Geliştiricide:** yeni model seçilince fiyat tablosu — **o güne kadar günlük
bütçe freni çalışmaz**, çünkü fiyatı olmayan model sıfır ediyor ve toplam hep
sıfır kalıyor. Ayrıca VPS kurulumu ve restore testi.

## Aşama 3 · dilim 9-13 — `F-017`-`F-024` (2026-08-29/30)

Kayıtları `archive/stage-3-handoff-answers.md`'de, kalıcı kararları orada
adlandırılan spec bölümlerinde. Burada yalnız **canlı** olanlar:

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

## Aşama 3 · dilim 14 — `F-027`, `F-025`, `F-026` (2026-09-02)

Tam kayıt `archive/stage-3-slice-14.md`'de; kalıcı kararlar
`spec/05-pipeline-a-c.md` § 18.4.1 ve `spec/07-subsystems.md` § 34.4.2'de.
Burada yalnız **canlı** olanlar:

**Tamir etmeye kalkma:**
- **Kayıtlı beş `cover_letter` fixture'ının üçü sentetik girdiyle koşulmuş**
  (`synthetic-631`); gerçek olan iki tanesi `6b34bdf1ae6e` ve `a57ecb1d54d1`.
- **Yazıyla yazılmış sayıyı hiçbir muhafız görmüyor** (§ 34.4.2). Bilerek açık.
- **`job_analysis` ve `cover_letter` prompt'ları eski cümleyi taşıyor** — ikisi
  de `v2` bekliyor, `v2` model seçimini bekliyor.

**Geçici — `build.gradle.kts`'te iki BOM geçersizleştirmesi var.**
`postgresql.version` 42.7.12 ve `netty.version` 4.1.136.Final; ikisi de
Trivy'nin düşürdüğü HIGH CVE'ler için, ve Boot'un BOM'u yetişince
**kaldırılmalı**.

**Ders — reddedilen bir cevap da kaydediliyor, ve ölçüm orada duruyor.**
`F-026`'nın dört taslağını yeniden üretmek gerekmedi: `local-record` onları
diske yazmıştı. Bir muhafızın yanlış pozitifini aramanın en ucuz yeri
`fixtures/llm/`.

## Aşama 4 · uçtan uca ölçüm (2026-09-03/04)

Dört bulgu, **yedi** ayrı kusur; dilim kayıtları
`archive/stage-4-e2e-findings.md`'de. Bulgular kusurlarla bire bir eşleşmedi:
"eksik Tech Stack" render sanılıyordu, Faz C çıktı; iki uydurma cümle Faz D
sanılıyordu, ikisi de çıkarımdan geliyordu.

| Dilim | Ne indi |
|---|---|
| A · Faz C | iade edilen bütçe yeniden teklif ediliyor; boşalan section başlığı iade ediliyor; `min_atoms` import'ta ulaşılabilir yazılıyor (`V5`) ve `trace.C` bütçesini taşıyor |
| B · çıkarım tripwire | `MAX_ATOM_TEXT` tür başına ayrıldı — About paragrafı 1500, gerisi 600 |
| F · Klasik şablonu | referansın komutları, etiketli iletişim bloğu, `INLINE_LIST`; geometri v2'ye ölçüldü ve **`ITEM_LINE` bir kalibrasyon artefaktı çıktı** |
| E · Faz D sessizliği | `trace.D` yazılıyor; sıfır artık "koştu ve değiştirmedi" diyor. Eşiklere dokunulmadı — § 21.2 verbatim ve yeniden ayarlamak ölçüm ister |
| F · Klasik şablonu | referansın komutları, etiketli iletişim bloğu, `INLINE_LIST`; geometri v2 ve **`ITEM_LINE` bir kalibrasyon artefaktı çıktı** |
| D · çıkarım sadakati | `ExtractionFidelity` — çıkarım belgede olmayan bir ad yazarsa `UNSUPPORTED_BY_SOURCE` (`B-071`); P3 artık çıkarımı da kapsıyor |
| C · P3 muhafızları | `ClaimVocabulary.introducedNames()` — sözlüğün tanımadığı uydurma artık görünüyor; About birleşimi § 21.7'ye getirildi |

**Canlı kalanlar:**
- **`llm_invocations.job_id` 108 satırın 108'inde NULL** (`user_id` 104'ünde).
  FK ve kolon var, `ProviderChain` kullanıcıyı da işi de aşağı geçirmiyor. Bir
  faturalı çağrıyı düşen işe bağlamak 17 ms'lik zaman farkıyla yapıldı.
- **Aynı 4-sayfa belge iki kez faturalandı** (13:58 ve 14:06, ikisi de 4522
  giriş jetonu), tek `profile_extract` satırı var.
- **`cost_usd` her satırda 0.000000** — fiyat tablosu yok, yani **günlük bütçe
  freni ölü**. Zaten geliştirici listesindeydi; artık ölçülmüş hali de var.
- **`EMBEDDING_UNAVAILABLE` Faz D'yi kapatıyor** (`dev-full` ile bile, üç
  denemenin üçü de düştü). Artık sessiz değil — `trace.D` sıfır yazıyor — ama
  **eşikler embedding'siz dağılıma göre ayarlanmadı**: § 21.2 onları verbatim
  veriyor ve yeniden ayarlamak ölçüm ister.
- **`local-record` cevabı saklıyor, girdiyi saklamıyor.** Bir çıkarımın
  sadakati ancak kaynak belgeye karşı ölçülebilir; o belge diskte olmadığı için
  `UNSUPPORTED_BY_SOURCE`'un **yanlış pozitif oranı ölçülemedi**. Bir sonraki
  `make record` kaynak metni de yazmalı.
- **§ 14.6'nın `rejectReasons`'ı hâlâ yok** ve `promptVersions` koşmayan
  `bullet_rewrite`'ı koşmuş gösterebiliyor. İkisi de `RewrittenContent`'in
  yalnız kabul edilenleri taşımasından; `ContentRewriter` cephesini istiyor.

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

Sekiz dilimin hepsinin kaydı **`kapanis-denetimi.md` § 6**'da; ikinci bir kopya
ayrışır. Oradan çıkan tek kural § 51.7'de: *bir muhafızın düştüğünü görmeden
yazıldı sayma.* Dilim 14'te ve Aşama 4 dilim A'da yine gerekti.
