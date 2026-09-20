# Denetim — Altıncı Tur (2026-09-20)

> Beş tur adların **varlığını** ve telde bir **ucu** olup olmadığını sordu. Bu
> turun sorusu başkaydı: **bir belge ikinci bir belgeyi mi anlatıyor, ve
> ertelenmiş bir işin koşulu gerçekleşti mi.**
>
> Kapanmış kararlar `spec/`'te; bu dosya turun kendi anlatısı.

---

## Ekseni nasıl seçtim

Beşinci tur "canlılık" sorusunu tüketmişti: her ad, her uç, her enum değeri,
her ayar, her bütçe sayısı telde aranmıştı. Aynı soruyu altıncı kez sormak aynı
cevabı alırdı.

Bu turda iki yeni soru soruldu ve ikisi de **dokümanın kendisi hakkında**:

1. **İki belge aynı şeyi mi anlatıyor?** Spec on dokuz dosya ve iki arşiv
   taşıyor; bir tablo taşındığında eskisinin silindiğini kontrol eden bir şey
   yok. Üçüncü tur "iki otoriteli hata kataloğu"nu (`B-111`) bulmuştu — soru
   şuydu: **başka kaç tane var.**
2. **Ertelenmiş bir işin tetikleyicisi geçti mi?** Notlarda ve javadoc'larda
   "şu olduğunda yapılacak" diye duran kayıtlar var. Koşulu kontrol eden hiçbir
   şey yok, yani koşul gerçekleştiğinde iş kendiliğinden düşüyor.

Üçüncü bir eksen mekanikti ve beşinci turun kümesini genişletti: **üretilemeyen
sözlük değerleri**. Beşinci tur dördünü bulmuştu; bu tur dördünü daha buldu.

---

## Bulunanlar

### 1. EK D.6'nın iki kopyası, ikisi de "tek kaynak burasıdır" diyor

**En pahalı bulgu, ve en sessiz olanı.** `08b-api-contract.md` 600 satırlık
canlı API sözleşmesini taşıyor; `18-appendix-d.md` **328 satırlık eski bir
kopyasını** taşıyordu, ve ikisinin de giriş paragrafı *"Tek kaynak burasıdır"*
diyordu.

Nasıl oldu: EK D monolitten spec ağacına taşınırken (`6fd03f9`) içindeki D.6 de
geldi. `08b` çoktan ayrılmıştı, yani taşıma ikinci bir kopya yarattı ve kimse
ikisini karşılaştırmadı.

Eski kopyada eksik olanlar: `FEATURE_REQUIRES_ACCOUNT`'ın kapalı kümesi,
`params.reason`'ın yedi değerli tablosu, `COVER_LETTER_REJECTED`'ın sözlüğü,
`F-019`/`F-023`/`F-030`'un kararları, D.6.9'un tamamı. Ve iki somut yanlış:
**kapanmış bir soru açık duruyordu** — kota gününün zaman dilimi, ki `F-007`
UTC diye cevaplamıştı — ve ölü bir yol anılıyordu (`/ingestion/cv`).

**Silindi, yerine işaretçi.** Ders § 35.2.1'in dersinin aynısı, üçüncü kez:
*bir tabloyu taşımak eski kopyayı silmiyor.*

### 2. EK D.7 — Aşama 1'de donmuş bir ilerleme kaydı, "tek adres" diyor

*"Her dilim bittiğinde güncellenir… frontend için tek adres burasıdır."* Son
güncellenişi **Aşama 1'in kapanışıydı.** Aşama 2, 3 ve 4 hiç işlenmedi; test
sayıları 312/132/44; ve "hâlâ açık olan kararlar" tablosundaki üç satırın üçü
de kapanmıştı:

| Satır | Gerçekte |
|---|---|
| Kota gününün zaman dilimi | `F-007`, UTC (EK D.6.5) |
| Anonim akış kuyruğu kullanacak mı | Kullanıyor; `V3` indeksi düzeltti |
| CI imaj taraması | `deploy.yml`'de indi, imajın kendisine karşı |

İki "bulgu"su da kapanmıştı: atomsuz entry § 20.2'de aday oldu,
`FontMetricEstimator` Adım 1.8'de `RenderCostEstimator` olarak indi (D.8.4 hâlâ
"Açık" diyordu, D.8.7 indiğini yazıyordu — **aynı dosyada, iki bölüm arayla**).

**Emekliye ayrıldı, `STATUS.md`'ye işaretçi.** Sebep yapısal: iki repo
arasındaki durum kanalı `STATUS.md`, ve iki ilerleme kaydı tutmak ikisinin
ayrışmasını beklemektir.

### 3. Dört üretilemeyen sözlük değeri — biri **girdi**

Beşinci turun ekseni, dört kopya daha:

- **`GenerationStatus.FAILED`** — enum'un kendi javadoc'u *"Reserved. Nothing
  writes it today"* diyor, şema değeri yayımlıyor. `selection_state` `NOT NULL`,
  yani seçimden önce düşen koşunun yazacak satırı yok.
- **`JobStatus.CANCELLED`** — `Job.cancel`'ın tek çağıranı kendi testiydi.
- **`JobType.EMAIL`** — ne enqueue eden var ne handler'ı; posta commit sonrası
  olayla gidiyor (beşinci tur onu oraya taşımıştı, kuyruk tipi kaldı).
- **`SectionLayout.TWO_COLUMN`** — ve bu **ötekilerden farklı.** Uç kabul
  ediyordu, CHECK izin veriyordu, şema yayımlıyordu, `LatexDocumentRenderer`
  onu **bilerek** entry list'e düşürüyordu ve gerekçesini javadoc'una
  yazmıştı: üç şablon da tek kolon, sebebi ATS. Yani kişi bir düzen seçiyor,
  hiçbir şey söylenmiyor, ve belgesi başkasını basıyordu.

**Ayrım kaydedilmeye değer:** ilk üçü **çıktı** ve bedeli frontend'in boş bir
dal yazması; dördüncüsü **girdi** ve bedeli kullanıcının verdiğini sandığı bir
seçim. `V17` dördünü de kolonlardan düşürdü, `jobs.type`'a ilk CHECK'ini verdi,
ve `sections.layout`'un `two_column` satırlarını `entry_list`'e çevirdi —
zaten basıldıkları şeye.

### 4. Dört çıkarım reddi çözümsüz, ve eylem sözlükte duruyor

`PDF_NOT_TEXT_BASED`, `EXTRACTION_EMPTY`, `PDF_ENCRYPTED`,
`LANGUAGE_UNDETECTED` — dördü de boş bir `resolutions` dizisiyle çıkıyordu.
`switch_to_manual_form` **hiçbir yerde üretilmiyordu**, ve `ErrorPresenter`'ın
o noktadaki yorumu *"çıkış yolu manuel form, ki sözlükte böyle bir eylem yok"*
diyordu.

Yorumun kendisi bulgunun tamamı: **eylem vardı, yazan bilmiyordu.** § 31.10
dördünün de çıkışını yıllardır adlandırıyordu.

İki yeni eylem gerekti, çünkü dördü aynı şeyi istemiyor: şifreli bir PDF'e
"elle doldur" demek elinde açık kopyası olabilecek kişiye işini yeniden
yaptırmak (`upload_another_file`), ve adayları yayımlanmış bir dil sorusunun
cevaplanacak bir yeri olmalı (`choose_language`). Bir tane daha aynı kusurdaydı
ve sözlük istemedi: `EXTRACTION_TIMEOUT` (504) `retry` taşımıyordu — `B-113`
onu 503'ten ayırmıştı ki **zıt** şeyler istesinler, ikisi de boş listeyle
çıkarken aynı hiçbir şeyi istiyorlardı.

**Muhafız yazıldı:** `ErrorCatalogueTest.everyActionIsOfferedSomewhere`, kaynağı
tarayıp üreteni olmayan bir eylem bulursa düşüyor. İlk koşuşunda düştü —
`UPLOAD_ANOTHER_FILE` henüz bağlanmamıştı.

### 5. Hesaplanan, loglanan, atılan sayaç

`trace.C.estimatedAtoms` § 20.4 ve § 26.5'te **iki kez** vaat edilmişti.
`SelectionRequestBuilder` sayıyordu, iki servis INFO'ya basıyordu, trace'e
kimse yazmıyordu.

Yanında § 48.3'ün üç satırının hiç serisi yoktu: **bütçe doluluk oranı**,
**tahmin kullanım oranı**, **geri bildirim oranı**. Sonuncusunun kodda izi
vardı — bir log satırının üstünde *"This is the feedback rate"* yazıyordu, ki
bir oran değil bir cümledir.

**Asıl bulgu muhafızdaydı, ve tanıdık:** `MetricCatalogueTest` kodla katalogu
**birbirine** karşı tutuyordu ve *istenenin verilip verilmediğini* hiç
sormuyordu. Beşinci turun `ProviderChain` dersinin aynısı, başka bir eksende —
bir muhafız, bakmadığı yönde kör.

Üç seri indi (`generation.budget.fill`, `generation.selection.costs`,
`generation.feedback`), ve test artık § 48.3'ün satırlarını **dosyadan okuyup**
her birine bir seri eşliyor.

### 6. Tetikleyicisi geçmiş bir erteleme

`EmployerName`'in javadoc'u: *"Prompt'ta da yazmalı, ve yazmıyor. Yazmak yeni
bir prompt sürümü demek… `job_analysis` bir sonraki kez sürümlendiğinde yapmaya
değer."* § 18.4.1 aynı sözü veriyordu.

`job_analysis` **v2'ye çıktı** (sorumluluklar kuralı için) ve cümle girmedi.
Koşul gerçekleşti, kontrol eden bir şey yoktu, iş düştü.

`v3` yazıldı ve aktif sürüm oldu. Kusuru `EmployerName` zaten deterministik
kapatıyor — asıl kayıp, gerekmediği hâlde sessizliğe düşen etiketler.

EK C.3'ün istediği eval koşuldu (`make test-llm`, gerçek çağrı):

| Metrik | v3 | Taban |
|---|---|---|
| `schema_conforms` | %100.0 | %99 |
| `required_skills_found` | %91.7 | %90 |
| `nonsense_refused` | %100.0 | %95 |

`required_skills_found` tabana yakın (12 vakanın 11'i) ve bir sonraki sürümde
ilk bakılacak sayı o.

**Ve bedeli bir merge kapısıydı, bunu CI öğretti.** Sürüm numarası fixture
anahtarının parçası; `active`'i çevirmek `job_analysis`'in **bütün** kayıtlarını
yetim bıraktı ve `latexTest` dört testte düştü. Kayıp yalnız o prompt'ta ağır,
çünkü `.gitignore` yalnız onun cevaplarını commit'liyor — gerekçesi orada yazılı
ve tam bu: "latexTest'in sentetik analize düşmesini engelleyen şey bunlar."
Zinciri: Faz A sentetik cevap verdi → sentetik analiz sentetik beceriler taşıdı
→ Faz D'nin sentetik yeniden yazımı **aynı saçmalıktan kurulmuş** bir
doğrulayıcıyı geçti → DOCX'te maddenin yerinde `synthetic-517` çıktı. Doğrulayıcı
kusurlu değil; girdisi olan sözlük de uydurmaydı.

Düzeltmesi **tek dosya**: testlerin oynattığı ilan için bir v3 kaydı. Sıra
§ 53.2'ye yazıldı.

**Ve bu turun kendi hatası burada.** Yerel `latexTest`'in sonucunu koşu
sürerken okudum ve "145 test, 0 hata" diye raporladım; gerçek sonuç 145 test,
**4 hata**ydı ve o sayı STATUS'a, CHANGELOG'a ve PR gövdesine öyle girdi. Aynı
turda "hesaplanıp atılan bir değer yayımlanmış sayılmaz" diye yazarken,
tamamlanmamış bir koşunun sayısını tamamlanmış gibi yayımladım. Ders § 51.7'nin
üçüncü kuralının yanına yazılmalı: **bir sayıyı raporlamadan önce onu üreten
koşunun bittiğini doğrula** — dizin dolmaya devam ediyorsa sayı henüz bir sayı
değil.

### 7. Gerisi: bayat bloklar

Sayıldıkları için buraya liste olarak giriyorlar; kararları `spec/`'te.

| Nerede | Neydi |
|---|---|
| § 11.2 | nginx CSP bloğu `B-100` öncesi hâli — Turnstile host'u yok |
| § 10.1 | Modül haritası on bir paket eksik (`shared.wire`, `shared.math` dâhil, ki spec'in gövdesi ikisini de adıyla anıyor) |
| XI-B.2 | Klasör ağacı: yedi faz sınıfı, `PipelineContext`, `FontMetricEstimator`, Thymeleaf `email/`, `golden/` yanlış kökte, `retention/` ve `latex.yml` yok |
| § 16.1 + § 47.2 | "Migration deploy'dan önce, CI adımı" — 08-28'de tersi kararlaştırılmıştı |
| § XI-A.4 | `⚠️ AÇIK KARAR` etiketi, kapanmış karar için |
| § 14.6 | `generations.trace` örneği kalıcı bir JSONB kolonunu yanlış anlatıyor (A/E yok, B/C/D/F başka) |
| § 38.5 | Font tablosu inmemiş iki yazı tipini sayıyor, inen dördünü saymıyor |
| § 23.2 | Dört ATS kontrolü sayılıyor; üçü indi, dördüncüsü yazılmadı, madde kapsaması hiç yazılmamıştı |
| § 43.3 / § 44.3 | "Sıkılaştırılacak bir rate limiter yok" — `TightenedSubjects` inmişti |
| `AnomalyDetector` | Aynı yanlış javadoc'ta, ve `report`'un üstünde **iki javadoc bloğu**: biri ölü, öteki ile çelişiyor |
| `ci.yml` | `scan` işinin yorumu "taranacak Dockerfile yok" diyor; iki tane var |
| konsept dokümanı | § 7.3 eski üç kademe (0.65/0.40), § 9 "hiçbir veri saklanmaz" (anonim profil 09-09'da `profiles` satırına taşındı) |
| INDEX / CLAUDE.md / README | Spec boyutu üç yerde üç türlü, üçü de eski: gerçek **19 dosya / 13.300 satır** |
| § 35.2 | `POST /webhooks/resend` haritada var, şemada yok (`@Hidden`), ve bu kayıtlı değildi — haritayı şemaya karşı okuyan her denetim onu "eksik uç" sanıyor |
| `Makefile` | **`test-llm` hedefi yoktu.** § 54.4 onu sayıyor, ve eksikliği zararsız değil: `gradlew llmEval`'i elle koşturan `.env`'i almıyor, yani her sağlayıcı anahtarsız atlanıyor ve eval bir **skor değil bir kesinti** olarak düşüyor. Makefile'ın `.env`'i `include`+`export` etmesinin sebebi tam olarak bu. Eklendi |

---

## Ne yazıldı

**Kod:** iki yeni `ResolutionAction`, dört redde çözüm + `EXTRACTION_TIMEOUT`'a
`retry`, `SelectionCosts` → `trace.C.estimatedAtoms`, üç yeni metrik, `V17`,
dört sözlük değerinin kaldırılması, `job_analysis/v3`, üç javadoc düzeltmesi,
`atomcv.llm.openrouter.structured-output`'un yml'e çıkması.

**Muhafız:** `everyActionIsOfferedSomewhere` (üreteni olmayan eylem),
`everyRowOf48Point3HasAMeter` (cevapsız metrik satırı — § 48.3'ü dosyadan
okuyor), `thestatusVocabularyIsTheFourTheColumnHolds` ve
`thetypeVocabularyIsTheFiveThatHaveHandlers`.

**Doküman:** yukarıdaki tablonun tamamı, EK D.6 ve D.7'nin emekliye ayrılması,
`_archive-monolith.md`'ye "otorite değil" bandı, CHANGELOG'un Aşama 2-3-4
bölümleri.

**Frontend:** `B-114` (iki yeni eylem), `B-115` (§ 37.6'nın düğmeleri),
`B-116` (üç sözlük daraldı).

---

## Dersler

- **Bir belgeyi taşımak eski kopyayı silmiyor**, ve iki kopya "tek kaynak
  burasıdır" diyorsa ikisi de yanlış. Üçüncü kez öğrenildi.
- **Kendine "tek adres" diyen bir kayıt, güncellenmediğini söyleyemez.**
  Güncellenmeyen bir ilerleme kaydı, geride kaldığını en son fark edilen şey.
- **Bir muhafız bakmadığı yönde kördür.** Katalog ile kodu birbirine tutan
  test, istenenin verilip verilmediğini hiç sormuyordu.
- **Koşulu kontrol eden bir şey yoksa erteleme gerçekleşmez.** "Bir sonraki
  sürümde" diyen bir kayıt, o sürüm geldiğinde kimseye seslenmiyor.
- **Üretilemeyen bir değerin bedeli, çıktı mı girdi mi olduğuna bağlı.** Çıktı
  bir dal, girdi bir yalan.
- **Bir yorumun "böyle bir şey yok" demesi, olmadığı anlamına gelmiyor** —
  `switch_to_manual_form` sözlükteydi, onu arayan yazanın gözünden kaçmıştı.
