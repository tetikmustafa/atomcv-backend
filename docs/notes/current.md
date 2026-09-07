# İnşa Notları — Aktif

> Kural: bu dosya **200 satırı geçmez**. Aşama bitince `archive/`'a taşınır, boş başlanır.
> Kayıt tipleri: **Sapma** (doküman başka diyor) · **Ekleme** (doküman sessiz) · **Düzeltme** (doküman yanlış).
>
> Bir sapma kalıcıysa `spec/`'e işlenir ve buradan silinir.

**Aktif aşama:** Aşama 3 — hesap ve MVP. **Plan:** `spec/14-build-guide.md`
§ XI-A.6; gerekçesi § 55. Aşama 1-2: `archive/stage-1.md`, `archive/stage-2.md`.

---

## Aşama 2'den taşınan açık kutular

- **Axiom'da loglar görünüyor** — dataset açık (2026-08-26); telde doğrulanması
  üretim dağıtımını bekliyor.
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
  bir kaldırmanın iadesi koşudan koşuya değişiyordu. **İterasyon sırası bir
  sayıya dönüşüyorsa `Linked*` gerekiyor.**

**Geliştiricide:** fiyat tablosu (**o güne kadar günlük bütçe freni ölü** —
fiyatsız model sıfır ediyor), VPS kurulumu, restore testi.

## Aşama 3 · dilim 9-13 — `F-017`-`F-024` (2026-08-29/30)

Kayıtları `archive/stage-3-handoff-answers.md`'de. Canlı olanlar:
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

Tam kayıt `archive/stage-3-slice-14.md`'de. Canlı olanlar:
- **Kayıtlı beş `cover_letter` fixture'ının üçü sentetik girdiyle koşulmuş**
  (`synthetic-631`); gerçek olan iki tanesi `6b34bdf1ae6e` ve `a57ecb1d54d1`.
- **Yazıyla yazılmış sayıyı hiçbir muhafız görmüyor** (§ 34.4.2). Bilerek açık.
- **`cover_letter` prompt'u eski cümleyi taşıyor** — `v2` model seçimini
  bekliyor. (`job_analysis` v2 dilim H'de indi.)

**Geçici — `build.gradle.kts`'te üç BOM geçersizleştirmesi:** `postgresql`
42.7.12, `netty` 4.1.136.Final, `tomcat` 10.1.59; Boot'un BOM'u yetişince gider.

## Aşama 3 kapanışından sonra · uçtan uca ölçüm (2026-09-03/07)

Dört bulgu, yedi ayrı kusur; dilim A-G'nin tam kaydı
`archive/stage-3-post-closure-e2e.md`'de, **dilim K'nınki de orada**.

**Geliştiricide:** fiyat tablosu (**o güne kadar günlük bütçe freni ölü** — ve
dilim K bunu telde gördü: `openai/gpt-5.6-sol` fiyatsız, iki gerçek çağrı
`cost_usd = 0` yazdı), VPS kurulumu, restore testi.

## Kapanış sonrası · dilim I-J-H ve K — sayfanın şekli (2026-09-07)

Tam kayıt `archive/stage-3-post-closure-shape.md`'de; kalıcı kararlar
`spec/05-pipeline-a-c.md` § 18.4 ve § 20, `spec/07-subsystems.md` § 31.3.1 ve
§ 33.4.1, `spec/06-pipeline-d-g.md` § 21.2 ve § 22.4.1'de. Burada yalnız
**canlı** olanlar:

**Tamir etmeye kalkma — hepsi bilinçli:**
- **`SectionFloor` bir tavandır, talep değil.** Profilde yoksa basılmaz;
  About'un ayrıca *tavanı* var (bir CV bir özet). Sert taban sığmazsa sayfa
  limiti kazanır ve en alttaki düşer — ölçülen sayfada olmuyor, kendi testi var.
- **`reservedByFloor` `forcedByLock`'tan ayrı bir küme.** Lock kullanıcının
  seçimi ve `pinnedCostPt` onu sayar; taban belgenin şeklini koruması.
- **Bir inline satırın öğeleri **ve** etiketi sade diziliyor**, işaretleri ne
  derse desin: kalın etiket zaten vurgunun kendisi, ve çıkarım her öğesi
  teknoloji olan bir listenin %70'ini işaretliyor. Karar render'da, içerikte
  değil — `contentHash` düz metin üzerinden hesaplandığı için işaretleyerek
  kalın yapmak ölçülmüş maliyeti **geçersizleştirmezdi**.
- **`PARAGRAPH` `INLINE_LIST`'e katlanmadı.** İkisi de etiketsiz liste açıyor,
  ama inline satırın ilk iki noktası kalın diziliyor. Geometri aynı, o yüzden
  şablon sürümü **yükselmedi** ve hiçbir maliyet geçersizleşmedi.
- **Maddelerdeki `emphasis` italik kalıyor.** Referans belge teknolojileri
  **kalın** yazıyor; çıkarım hepsini `EMPHASIS` veriyor (§ 31.5.1) ve § 22.3
  `emphasis → 	extit` diyor. Şablonda çevirmek bütün ölçümleri yeniden ister.
  **Karar spec'in.**

**Canlı kalanlar:**
- **`.env`'in `LLM_CHAIN_*`'i `application-local-fake.yml`'i eziyor.** Ortam
  değişkeni profil YAML'ının üstünde, `make dev` .env'i export ediyor, yani
  `chain: [fake]` hiç uygulanmıyor ve **`make dev` gerçek, ücretli çağrı
  yapıyor**. Ölçüldü: bir `local-fake` içe aktarımı 127 saniyelik bir OpenRouter
  çağrısı koştu. `CLAUDE.md`'ye yazıldı; kalıcı çözüm ya `.env`'den çıkarmak ya
  da fake profilinde o adları okumamak.
- **`headline` içe aktarımın alanı değil**, `PUT /profile`'ın. `mode=replace`
  onu korumakta haklı; ölçülen koşuda İngilizce bir CV'nin üstünde çıkan Türkçe
  unvan `DevSeeder`'dan kalmaydı, elle boşaltıldı. Kusur değil.
- **`.tex` fixture'ı ulaşılamaz oldu.** Bitişik-argüman düzeltmesi çıkarılan
  metni değiştirdi; `v1-75f61110da11` anahtarı bir daha tutmayacak.
- **Faz D eşikleri ölçüldü:** en yüksek atom skoru 0.3577-0.4313, § 21.2'nin
  tabanı 0.40. **Ayarlamak yeni ölçüm ister**, karar spec'in.
- **`to-frontend.md` 100 satırı aşıyor** ve sebebi arşivleme değil: `B-071`,
  `B-072` ve `B-073` ACK bekliyor.
- **`about_synthesis` bu ilanda her seferinde reddediliyor** — iki gerçek
  deneme, ikisi de `UNSUPPORTED_CLAIM`, sonra kişinin kendi paragrafı basılıyor.
  Ölçüldü: dört makul özetin üçü düşüyor. İkisi **yanlış pozitif** —
  `OOP` reddediliyor, oysa sayfanın becerilerinde `object-oriented-programming`
  var (`TDD` yalnız orijinal paragrafta harfleri geçtiği için geçiyor); `Scrum`
  ve `Kanban` reddediliyor, çünkü `RewriteContext.canonical()` ilanın
  `name`'ini ("Agile frameworks (Scrum, Kanban)") atıp yalnız `canonical`'ı
  ("agile methodologies") taşıyor. Üçüncüsü **doğru davranış**: sayfada olmayan
  bir ilan becerisini (`unit testing`) anmak § 21.7'nin yasakladığı şey.
  Kısaltmanın yeri `introducedNames`'in kaynak listesi: metni harfiyen arıyor,
  kanonikleştirmiyor. **Güvenli yönde düşüyor** ama her üretimde iki çağrı boşa
  gidiyor; **P3 muhafızını değiştirmek kendi dilimini istiyor.**

**Ders — golden fixture yazılmaz, okunur.** `master_cv_en` beş elle yazılmış
profilin taşımadığını taşıyor: **ilanla ilgisi olmayan çok fazla içerik.** İlk
koşusunda Faz C'de belirlenimsizlik yakaladı — `SectionFloor` eşit puanlı iki
entry'yi **UUID sırasıyla** seçiyordu, ve on dört tarihsiz proje genel modda tam
olarak eşit puanlı: aynı CV iki kez okununca iki farklı sayfa (İlke 2). Sıra
artık sözcüklemenin özetinden.

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

Sekiz dilimin kaydı **`kapanis-denetimi.md` § 6**'da; oradan çıkan tek kural
§ 51.7'de: *bir muhafızın düştüğünü görmeden yazıldı sayma.* Dilim 14, kapanış
sonrası A, G, J ve K'de yine gerekti.
