# İnşa Notları — Aktif

> Kural: bu dosya **200 satırı geçmez**. Aşama bitince `archive/`'a taşınır, boş başlanır.
> Kayıt tipleri: **Sapma** (doküman başka diyor) · **Ekleme** (doküman sessiz) · **Düzeltme** (doküman yanlış).
>
> Bir sapma kalıcıysa `spec/`'e işlenir ve buradan silinir.

**Aktif aşama:** Aşama 3 — hesap ve MVP. **Plan:** `spec/14-build-guide.md`
§ XI-A.6; gerekçesi § 55. Aşama 1-2: `archive/stage-1.md`, `archive/stage-2.md`.

---

## Aşama 2'den taşınan açık kutular

- **Axiom'da loglar görünüyor** — dataset açık, `.env` dolu (2026-08-26); telde
  doğrulanması üretim dağıtımını bekliyor.
- **Anonim TTL etkinlikle kayıyor**; metin "son etkinliğinden iki saat sonra"
  demeli (§ 9) — frontend'in işi.
- **§ 44.3'ün limiter'ı hâlâ yok** — 3.3'ünki **girişe** bağlı; § 44.3 ağır
  kullanıcının **üretim** hakkını kısmak istiyor, yeri `QuotaService`.

## Aşama 1'den taşınan kısıtlar — hâlâ açık

| Eksik | Ne zaman | Neden şimdi değil |
|---|---|---|
| ATS metin çıkarma (§ 23.2) yok | Aşama 3 | Engeli kalktı (PDFBox 3.4). `FitReport` `F-008`'de indi; kalanı üretilen PDF'i geri okumak |
| `UserScopedRepository`'de `findAll` yok | — | § 41.2 `findByUserId` çağırıyor, `JpaRepository`'de yok. Alt sınıflar kendi bulucularını ekler |

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

**Geliştiricide:** fiyat tablosu (**o güne kadar günlük bütçe freni ölü** —
fiyatsız model sıfır ediyor), VPS kurulumu, restore testi.

## Aşama 3 · dilim 9-13 — `F-017`-`F-024` (2026-08-29/30)

Kayıtları `archive/stage-3-handoff-answers.md`'de, kalıcı kararları orada
adlandırılan spec bölümlerinde. Burada yalnız **canlı** olanlar:

**Tamir etmeye kalkma — üçü de bilinçli:**
- **"Kritik uyarı" diye bir şey yok, `critical` bayrağı da yok.**
  `ExtractionWarningCode` kapalı ve altı değeri de düzeltilebilir bir alanı
  tarif ediyor; § 31.6'nın üçüncü kuralı **silindi**. Yedincisi gerçekten
  engelleyici olursa karar § 31.6.4'te.
- **`ImportWarning.code` `String`, şeması enum** — değer JSONB'den geri
  okunuyor, enum yapmak adı değişmiş satırı düşürür ve `warningCount`'u bozar.
  **`OpenApiSchemaIT`'in okuduğu altı değer de elle yazılı**: `values()`'tan
  türetilirse yedinciye de "evet" der.

**`shared.wire` bir sonraki kapalı sözlüğün yeri** — iki modülün yayımladığı,
**ret olmayan** sözlükler oraya; `shared.error` retlerin.

## Aşama 3 · dilim 14 — `F-027`, `F-025`, `F-026` (2026-09-02)

Tam kayıt `archive/stage-3-slice-14.md`'de; kalıcı kararlar
`spec/05-pipeline-a-c.md` § 18.4.1 ve `spec/07-subsystems.md` § 34.4.2'de.
Burada yalnız **canlı** olanlar:

**Tamir etmeye kalkma:**
- **Kayıtlı beş `cover_letter` fixture'ının üçü sentetik girdiyle koşulmuş**
  (`synthetic-631`); gerçek olan iki tanesi `6b34bdf1ae6e` ve `a57ecb1d54d1`.
- **Yazıyla yazılmış sayıyı hiçbir muhafız görmüyor** (§ 34.4.2). Bilerek açık.
- **`cover_letter` prompt'u eski cümleyi taşıyor** — `v2` model seçimini
  bekliyor. (`job_analysis` v2 dilim H'de indi.)

**Geçici — `build.gradle.kts`'te üç BOM geçersizleştirmesi var:**
`postgresql` 42.7.12, `netty` 4.1.136.Final, `tomcat` 10.1.59. Hepsi Trivy'nin
düşürdüğü CVE'ler için; Boot'un BOM'u yetişince **kaldırılmalı**.

**Ders — bir muhafızın yanlış pozitifini aramanın en ucuz yeri `fixtures/llm/`.**

## Aşama 3 kapanışından sonra · uçtan uca ölçüm (2026-09-03/07)

Dört bulgu, **yedi** ayrı kusur; dilim A-G'nin tam kaydı
`archive/stage-3-post-closure-e2e.md`'de. Bulgular kusurlarla eşleşmedi:
"eksik Tech Stack" render sanılıyordu Faz C çıktı; iki uydurma cümle Faz D
sanılıyordu, ikisi de çıkarımdan geliyordu. Dilim F Klasik şablonunu indirdi
ve drift'i kapatan **`ITEM_LINE`'ın bir ölçüm artefaktı olduğunu görmek** oldu;
dilim G ölçümün kendisini indirdi (`job_id`, TEI parçalama, `RewriteTally`,
`local-record`'un kaynak dosyası).

**Geliştiricide:** fiyat tablosu (**o güne kadar günlük bütçe freni ölü**),
VPS kurulumu, restore testi.

## Kapanış sonrası · dilim I-J-H — sayfanın şekli (2026-09-07)

Tek sayfada altı bölüm, ilana göre doldurulmuş. Üçü de ölçüme dayalı.

**Sapma — § 20 seçimi saf bütçe yarışı diye tarif ediyor, o bir CV üretmiyor.**
Ölçülen koşu sayfaya yirmi atom koydu, yirmisi de Projects'ten: deneyim yok,
yetenek yok, özet yok — her biri "hangi atom puan başına daha değerli"nin doğru
cevabıydı. **`SectionFloor`** her türe önce taban ayırıyor (About 1 paragraf ·
Education 1 · Experience 2×2 · Projects 2×3 · Tech Stack 3 · Languages 2 =
**576/708pt**), kalan **132pt** ilana göre yarışıyor. Sıra sabit, seçimde ve
sayfada aynı. **Taban bir tavandır, talep değil:** profilde yoksa basılmaz,
eksikse ne varsa girer. About'un ayrıca *tavanı* var — bir CV bir özet.

**Düzeltme — `inline_list` maliyeti.** Renderer Tech Stack'i tek blokta düz
satır basarken Faz C her satıra entry mobilyası yazıyordu: **35.43pt**, beş
satırda **169.55pt**, sayfanın dörtte biri. Prob hiç yoktu; artık
`LatexCalibrationIT` ölçüyor ve cevap "kendi sabiti gerekmiyor".

**Düzeltme — § 18.4'ün `no_responsibilities`'i kalktı.** Gerçek ilanların çoğu
başlıksız nitelik listesi; ölçülen ilan 0.92 güven ve 20 yetenekle reddedildi.
`job_analysis` **v2** sorumlulukları metinde ne varsa ondan türetiyor; yetenek
kuralı sıkı kaldı (`postingSkills` § 21.6'nın sözlüğü). İş tarif etmeyen metin
zaten `LOW_CONFIDENCE`; ikisi aynı ilanı iki kez reddediyordu. `B-072`.

**Ekleme — üç import kararı ikinci yazıcıyla paylaşıldı:** `layoutFor`
(Languages da `INLINE_LIST`, `V6`), `reachableMinimumFor` (About entry'si 1,
`V7`), `hangsOffItsSection` (özet bölüme asılır, `V8` — uydurulan "Professional
Summary" başlığı gitti). `EphemeralProfileWriter` üçünü de kaçırmıştı.

**Tamir etmeye kalkma — testlerin öğrettiği üçü:**
- **`improveBySwapping()` tabanı yerleştirir yerleştirmez takas ediyordu.**
  `reservedByFloor` ayrı bir küme: `forcedByLock` "kullanıcı seçti" demek ve
  `pinnedCostPt` onu sayar; taban kullanıcının seçimi değil.
- **Golden, tabanın bir entry'yi 3'lük asgarisinin 2'siyle açtığını yakaladı**
  (§ 20.3). Entry artık açılmadan önce fiyatlanıyor.
- **Taban "nerede" değil "ne kadar" demeli.** İlk hali About ve Languages'ı
  serbest atom sanıyordu; gerçek profil ikisini de entry'de tutuyor.

**Canlı kalanlar:**
- **Aynı 4-sayfa belge iki kez faturalandı**, tek `profile_extract` satırı var;
  `job_id` indiği için bir sonraki tekrar kendini gösterir.
- **Faz D eşikleri ölçüldü, ikisi de doğru çıktı:** bir profilde en yüksek skor
  0.3577 (Faz D koşmadı), diğerinde 0.4313 (koştu). § 21.2'nin sayıları profile
  göre kenarda; **ayarlamak yeni ölçüm ister**, karar spec'in.
- **Altı bölümün *sert* tabanı sığmazsa sayfa limiti kazanır**, en alttaki
  düşer. Ölçülen sayfada olmuyor (386/708pt); kendi testi var.

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
- **`suspicious_output` telde hiç görülmedi** — bir enjeksiyon tripwire'ı, uslu bir modelle açılmaması doğru sonuç.
- **`bullet_rewrite` / `about_synthesis` fixture'ı olmayan girdide anlamsız.**
  `SyntheticAnswer` şema şeklinde bir cümle üretir, doğrulayıcı reddeder,
  orijinal basılır. İkisi de artık kayıtlı ve `.gitignore`'da.

**Test yazarken:**
- **`MagicLinkApiIT` her testten önce `ratelimit:*`'ı siliyor.** Silmeyen bir
  kimlik testi dördüncüde ilgisiz bir 429'da düşer, flake gibi okunur.
- **`profiles` entegrasyon paketinde hiç boş değil** (`DevSeeder`) — anonim
  gizlilik iddiası "satır sayısı değişmedi" diye kuruluyor.
- **`local` profilinde LLM sağlayıcısı yok** — entegrasyon lane'inde her çağrı
  `ALL_PROVIDERS_UNAVAILABLE` alır; `CoverLetterApiIT` kasten öyle kuruyor.
- **`AccountDeletionIT` tablo listesini `information_schema`'dan okuyor** —
  elle yazılmış liste sonradan eklenen tabloda sonsuza kadar geçerdi.

**Sınırlar ve açıklar:**
- **Faz D sekize kadar eşzamanlı çağrı yapıyor**, her biri `REQUIRES_NEW` ile
  bağlantı alıyor. Havuz 10, işçi eşzamanlılığı 2 → tepede 16 kısa ödünç.
  **Havuz büyütülmeden işçi eşzamanlılığı artırılmamalı.**
- **`support_grants.accessed_at`'i hiçbir şey yazmıyor** (operatör arayüzü bu
  repoda yok). **R2'deki PDF'ler** § 57.4'ün silme listesinde, R2 istemcisi ise
  hiç yok (7. karar: MVP'ye girmiyor).
- **Anonim işler yükseltmede taşınmıyor** (`anon_session_id` kapsıyor); anonim
  *üretim* inince değişir. **Anonim oturumun kullanıcı indeksi yok.**
- **`ExtractedContact`, `Contact` ve şema aynı şekli üç yerde taşıyor**
  (§ 31.4.1). **`SkillNames.canonical` dört çağıranın ortak kuralı** — alias
  dosyasında sol taraf insanların yazdığı gibi olmalı. **`Contact.linkedin`**
  CV alanı, LinkedIn *girişinin* kaldırılmasıyla ilgisiz — karıştırılmasın.
- **İzlenecek:** CI bir kez `PGVectorTypeContributor`'da `NoClassDefFoundError`
  verdi, aynı ağaç tekrar koşuşta geçti (2026-08-27, run 33091345512).
  **Tekrarlarsa ilk bakılacak yer bağlam sayısı, kod değil.**

**Ders (3.4, dilim 1'de ikinci kez):** *bir javadoc ne zaman çalıştığını
söylüyorsa çağıranı da ara.* **(3.5):** *doğru davranan kod, korunan değildir.*

---

Sekiz dilimin kaydı **`kapanis-denetimi.md` § 6**'da. Oradan çıkan tek kural
§ 51.7'de: *bir muhafızın düştüğünü görmeden yazıldı sayma.* Dilim 14, kapanış
sonrası A, G ve J'de yine gerekti — J'de üç kez.
