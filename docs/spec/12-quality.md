# Bölüm X — Kalite Güvence (51-53)

> AtomCV spec · [INDEX](../INDEX.md) · bu dosya yalnız aşağıdaki bölümleri içerir.

---

# BÖLÜM X — KALİTE GÜVENCE

## 51. Test Stratejisi

### 51.1 Test piramidi

| Katman | Araç | Kapsam hedefi | Süre | Maliyet |
|---|---|---|---|---|
| Unit | JUnit 5 + Mockito | %80+ (iş mantığı) | ~30sn | $0 |
| Mimari | ArchUnit | Kural bazlı | ~5sn | $0 |
| Entegrasyon | Testcontainers | Repository, migration | ~2dk | $0 |
| Pipeline (deterministik) | Golden fixtures | Faz B/C/E/F | ~30sn | $0 |
| Contract | WireMock | LLM adaptörleri | ~20sn | $0 |
| Frontend | Vitest + Testing Library | Bileşenler | ~1dk | $0 |
| E2E | Playwright | Kritik akışlar | ~3dk | $0 |
| LLM eval | Gerçek çağrı | Prompt kalitesi | ~5dk | ~$0.30 |

**Toplam CI süresi (LLM eval hariç): ~7 dakika, sıfır maliyet.**

### 51.2 En değerli testler

> **Not (Adım 1.9).** Dördü de yazıldı. Hangisinin nerede olduğu ve neyi
> kapsadığı **EK D.8.9**'da; izolasyon testi kasıtlı bir IDOR'a karşı
> doğrulandı. Aşağıdaki parçacıklardaki `recordedAnalyses()` Faz A ile
> geleceği için genel mod skorlamasıyla koşuluyorlar.

Bu dört test, ürünün temel garantilerini koruyor:

**1. Sayfa sınırı ihlali yok**
```java
@Test
void selectionNeverExceedsBudget() {
    for (var profile : goldenProfiles())
        for (var analysis : recordedAnalyses())
            for (var lang : List.of("en", "tr"))
                for (var pages : List.of(1, 2)) {
                    var sel = runSelection(profile, analysis, lang, pages);
                    assertThat(sel.budget().usedPt())
                        .isLessThanOrEqualTo(sel.budget().totalPt());
                }
}
```

**2. Determinizm**
```java
@Test
void scoringAndSelectionAreDeterministic() {
    var first = runPipeline(fixedInput);
    for (int i = 0; i < 50; i++)
        assertThat(runPipeline(fixedInput)).isEqualTo(first);
}
```

**3. Çok-kiracılı izolasyon**
```java
@ParameterizedTest
@MethodSource("allProtectedEndpoints")
void userCannotAccessOthersData(String method, String path) {
    var userA = createUserWithProfile();
    var userB = createUserWithProfile();
    var response = request(method, path.replace("{id}", userB.resourceId()), userA.session());
    assertThat(response.status()).isIn(403, 404);
}
```

**4. Kilitler ve yapısal kısıtlar**
```java
@Test
void locksAndStructuralConstraintsRespected() {
    var sel = runSelection(profileWithLocks, analysis);
    assertThat(sel.selected()).containsAll(profileWithLocks.alwaysIncludeAtoms());
    assertThat(sel.selected()).doesNotContainAnyOf(profileWithLocks.inactiveAtoms());
    for (var entry : visibleEntries(profileWithLocks))
        assertThat(countSelectedIn(sel, entry)).isGreaterThanOrEqualTo(entry.minAtoms());
}
```

### 51.3 Golden test set

> **Not (Adım 1.9).** Profiller yazıldı; `jobs/`, `analyses/` ve
> `content-formats/` Faz A ile birlikte Aşama 2'de gelecek. Dosyalar
> `src/test/resources` değil **`src/main/resources/golden/profiles`** altında
> (seeder üretim kodu ve aynı dosyaları okuyor), ve fixture formatı export
> formatı değil. `*.costs.json` **içerik hash'iyle** anahtarlı ve gerçek
> derleyiciye karşı doğrulanıyor: **EK D.8.9**.

```
src/test/resources/golden/
├── profiles/
│   ├── senior_backend_tr.json       # TR, 3 deneyim, 8 proje
│   ├── junior_frontend_en.json      # zayıf, 2 okul projesi
│   ├── career_changer.json          # alakasız geçmiş
│   ├── academic_long.json           # 15 yıl, 20+ yayın
│   ├── minimal_edge.json            # sınırda: 1 deneyim, 3 beceri
│   └── *.costs.json                 # önceden ölçülmüş render_costs
├── jobs/
│   ├── backend_go_k8s_en.txt
│   ├── data_engineer_tr.txt         # Türkçe ilan
│   ├── vague_short.txt              # "backend developer lazım"
│   ├── very_long_corporate.txt      # 15.000 karakter
│   ├── anonymous_company.txt
│   ├── injection_attempt.txt        # gizli talimat
│   ├── mixed_language.txt
│   ├── no_requirements_section.txt
│   └── unrelated_marketing.txt
├── analyses/                        # Faz A çıktıları (fixture)
└── content-formats/                 # her JSONB sürümü için örnek
```

**Aynı profiller lokal geliştirmede seed data olarak kullanılır** — tek kaynak, iki fayda.

### 51.4 Mimari kurallar (ArchUnit)

> **Uygulanan kural kümesi buradakinden geniş — bkz. EK D.4.** Ham repository
> yasağı `..api..` yanında `..service..`'i de kapsar (mutlak kural 3 ikisini de
> söylüyor) ve `..profile..` için repository paketinin dışına çıkma yasağı
> eklenmiştir.

> **Boş kural sessizce geçer.** `src/test/resources/archunit.properties`
> `archRule.failOnEmptyShould=false` taşıyordu, çünkü modül paketlerinde yalnız
> `package-info.java` varken kurallar "failed to check any classes" ile
> düşüyordu. Ayar açıkken bir paketi yeniden adlandırmak, o kuralın hiçbir şeyle
> eşleşmeyip **geçmesine** yol açar. Modüller gerçek sınıf taşımaya başladığında
> kaldırılır.

```java
@ArchTest static final ArchRule noCycles = slices()
    .matching("com.mustafatetik.atomcv.(*)..").should().beFreeOfCycles();

@ArchTest static final ArchRule sharedIsIndependent = noClasses()
    .that().resideInAPackage("..shared..")
    .should().dependOnClassesThat().resideInAnyPackage("..profile..","..generation..");

@ArchTest static final ArchRule noRawRepositoryInApi = noClasses()
    .that().resideInAPackage("..api..")
    .should().dependOnClassesThat().areAssignableTo(JpaRepository.class);

@ArchTest static final ArchRule noPiiInLogs = /* Bölüm 48.1 */;

@ArchTest static final ArchRule noLocaleSensitiveCase = /* Bölüm 38.4 */;

@ArchTest static final ArchRule renderersAreDeterministic = noClasses()
    .that().resideInAPackage("..rendering..")
    .should().dependOnClassesThat().resideInAPackage("..llm..");
```

Son kural önemli: **renderer'ın LLM'e bağımlı olması derleme zamanında engelleniyor.**

### 51.5 Dev endpoint güvenliği

```java
@Test
void devEndpointsAbsentInProductionProfile() {
    var ctx = new SpringApplicationBuilder(App.class).profiles("prod")
        .web(WebApplicationType.NONE).run();
    assertThat(ctx.containsBean("devController")).isFalse();
    assertThat(ctx.containsBean("devSeeder")).isFalse();
}
```

### 51.6 Anonim mod gizlilik testi

> **Test "hiçbir tabloda" değil, "kullanıcı verisi tablolarında" satır sayısını
> denetler — bkz. EK D.1.** Kuyruk (`jobs.anon_session_id`) ve `llm_invocations`
> Postgres'te durur; anonim bir üretim oralara yazarsa bu beklenen davranıştır.

```java
@Test
void anonymousGenerationWritesNothingToDatabase() {
    var before = dbSnapshot.rowCountsAllTables();
    anonymousClient.createProfile(sampleData);
    anonymousClient.generate(sampleJobDescription);
    assertThat(dbSnapshot.rowCountsAllTables()).isEqualTo(before);
}
```

Gizlilik vaadi, dokümanda yazan bir cümle değil, **CI'da zorlanan bir kural.**

---

## 52. Performans Bütçeleri

### 52.1 Backend

| İşlem | p50 | p95 |
|---|---|---|
| Profil okuma (200 atom) | 80ms | 200ms |
| Atom PATCH | 30ms | 80ms |
| Faz B (skorlama) | 30ms | 60ms |
| Faz C (seçim) | 15ms | 40ms |
| Faz E (render) | 150ms | 300ms |
| LaTeX derleme (XeLaTeX) | 4s | 7s |
| Ölçüm derlemesi | 12s | 20s |
| **Pipeline toplam** | **8s** | **14s** |

### 52.2 ⚠️ N+1 problemi — en olası performans hatası

```java
// ❌ 1 + 200 + 400 + 200 = 801 sorgu
profile.getSections().forEach(s -> s.getAtoms().forEach(a -> {
    a.getVariants().size(); a.getTags().size();
}));

// ✅ 4 düz sorgu + bellekte birleştirme
var sections = sectionRepo.findByProfileId(id);
var entries  = entryRepo.findByProfileId(id);
var atoms    = atomRepo.findByProfileId(id);
var variants = variantRepo.findByProfileId(id);    // profile_id denormalize kolonu
return ProfileAssembler.assemble(sections, entries, atoms, variants);
```

> **Uygulanan imza `assemble(profileId, sections, entries, atoms, variants)` —
> bkz. EK D.5.** Dört ayrı sorgu, yanlış kapsamı geçirmek için dört fırsattır;
> fonksiyon her satırın profilini doğrular.

Karmaşık `JOIN FETCH` zincirleri kartezyen çarpım üretir ve daha da yavaşlar.

```java
@Test
void profileLoadUsesLimitedQueries() {
    var counter = QueryCountInspector.start();
    profileService.load(seedProfileId);
    assertThat(counter.count()).isLessThanOrEqualTo(6);
}
```

### 52.3 Frontend

| Metrik | Hedef |
|---|---|
| LCP (landing) | < 2.0s |
| LCP (editör) | < 2.5s |
| INP | < 200ms |
| CLS | < 0.1 |
| İlk JS — paylaşılan taban | < 175 KB gzip |
| İlk JS — pazarlama rotaları (landing, legal) | < 200 KB gzip |
| İlk JS — uygulama rotaları (editör, üretim) | < 280 KB gzip; rotanın kendi payı < 105 KB |

**Tek sayı yerine üç sayı, ve rota sınıfına göre iki tavan.** Karar Frontend
Aşama 0'da alındı, ölçüme dayanıyor: React + Next runtime'ının **paylaşılan
tabanı tek başına 168.1 KB**, pazarlama rotalarının kendi payı **0 KB** (hepsi
server component). Kalan ~30 KB'a dnd-kit + React Hook Form + Zod sığmıyor —
daha tek bileşen yazılmadan. Bu bölüm **LCP'de zaten aynı ayrımı yapıyor**
(landing 2.0s, editör 2.5s) ve gerekçe birebir taşınıyor: landing ilk temas ve
anonim huninin en ince yeri; editöre kararlı bir kullanıcı bilinçli bir eylemle
geliyor.

Üç sayının işi farklı: **taban** yalnız bağımlılık değişiminde oynar ve
oynadığı gün fark edilmelidir; **rotanın kendi payı** özellik işinin kontrol
ettiği şeydir; **toplam** bu bölümün tavanıdır. Tek eşik kötü bir tel kapandır —
taban bütçenin çoğunu yer, alarm sıradan işte öter, yükseltilir, sonra kimse
inanmaz.

Zorlayan kopya frontend reposundaki `bundle-budget.json`; buradaki sayılar
tavandır ve **karar olmadan yükseltilmez** (EK D.10 · 13, 14).

### 52.4 LaTeX optimizasyonu

```dockerfile
RUN fc-cache -fv                                    # font cache build zamanında
RUN xelatex -ini -jobname="cvfmt" "&xelatex preamble.tex\dump"   # 1-2sn kazanç
```

+ Container warm-up (Bölüm 29.6)

### 52.5 Soğuk başlangıç

```bash
# Deploy sonrası, trafiği yönlendirmeden önce
curl -sf localhost:8080/actuator/health
curl -sf localhost:8080/api/v1/warmup      # tipik sorguları çalıştırır
```

JVM CDS (`-XX:ArchiveClassesAtExit`) ile başlangıç ~%30 düşer.

### 52.6 Bütçe dosyası

```yaml
# performance-budgets.yaml
backend:
  profile_load:     { p50: 80ms,  p95: 200ms }
  phase_scoring:    { p50: 30ms,  p95: 60ms }
  phase_selection:  { p50: 15ms,  p95: 40ms }
  pipeline_total:   { p50: 8s,    p95: 14s }
frontend:
  lcp_editor: 2500ms
  inp: 200ms
  bundle_initial_kb: 200
```

Testler bu dosyayı okur. Bütçe değiştirmek bilinçli bir karar olur (PR'da görünür).

CI makineleri değişken hızda olduğu için eşiği **2-3 kat cömert** tut — amaç mikro-optimizasyon değil, "biri O(n²) döngü ekledi" durumunu yakalamak.

---

## 53. Prompt Yönetimi ve Değerlendirme

### 53.1 Promptlar versiyonlanmış dosyalarda

```
src/main/resources/prompts/
├── job_analysis/       { v1.md, v2.md, schema.json }
├── profile_extraction/ { v1.md, schema.json }
├── atom_rewrite/       { v1.md }
├── about_synthesis/    { v1.md }
├── cover_letter/       { v1.md }
├── edit_intent/        { v1.md }
└── translation/        { v1.md }
```

**Neden DB değil:** Prompt ile onu tüketen kod (şema, parse mantığı, doğrulayıcı) birlikte değişir. DB'de tutarsan ayrışırlar.

> **Düzeltme (Adım 3.4).** § 53.1 promptların dosyada olmasını istiyor ve
> `PromptRegistry.validateConfiguredPrompts()` eksik bir dosyayı **açılışta**
> hataya çevirmek için yazılmıştı — ama onu üretim kodunda **hiçbir şey
> çağırmıyordu.** Artık ayrı bir bean `@PostConstruct`'ta çağırıyor;
> `ApplicationReadyEvent` değil, çünkü o andan sonra port zaten açık ve istek
> görmüş bir örnek "açılamamış" sayılmaz. Kurucuya konulamıyor: sahte
> sağlayıcının testleri dosyası olmayan prompt id'leri yapılandırıyor ve kendi
> kayıt defterini elle kuruyor — ayrı bean tam olarak orada yok.

### 53.2 Aktif sürüm konfigürasyondan

```yaml
prompts:
  active:
    job_analysis: v2
    atom_rewrite: v1
  experiments:
    atom_rewrite: { enabled: true, variant: v2, trafficPct: 10 }
```

Deploy etmeden geri alma imkânı verir.

### 53.3 A/B testi

```java
String selectVersion(String promptId, String bucketKey) {
    var exp = config.experiment(promptId);
    if (exp == null || !exp.enabled()) return config.activeVersion(promptId);
    int bucket = Math.abs(Hashing.murmur3_32()
        .hashString(promptId + ":" + bucketKey, UTF_8).asInt()) % 100;
    return bucket < exp.trafficPct() ? exp.variant() : config.activeVersion(promptId);
}
```

`bucketKey` = **userId** (requestId değil) — aynı kullanıcı hep aynı varyantı görsün. Anahtarı olmayan çağıran (anonim) aktif sürümü alır: rastgele bir değerle kovalamak kullanıcıyı oturum ortasında varyantlar arasında gezdirirdi.

**Hash murmur3 değil `java.util.zip.CRC32`.** Tek bir hash için Guava bağımlılığı alınmadı (§ 5.4'ün "SDK yok" gerekçesi). CRC32 JDK'da, spesifikasyonu belirlenmiş ve yüz kovaya bölmek için yeterince dağılıyor. Yukarıdaki parçacığın tuzağından da kaçınıyor: `Math.abs(Integer.MIN_VALUE)` hâlâ negatiftir ve negatif int'in modülü de negatiftir — CRC32 işaretsiz bir `long` döndürür.

### 53.4 LLM eval — sadece prompt değişikliğinde

**Kritik:** Metin karşılaştırması yapılmaz (LLM her seferinde farklı kelime seçer). **Özellikler (properties) ölçülür.**

```java
@Test @Tag("llm-eval")
void rewritePreservesFactualContent() {
    var results = new EvalReport();
    for (var atom : goldenAtoms()) {              // 30-50 iyi seçilmiş vaka
        var rewritten = rewritePhase.rewriteSingle(atom, jobAnalysis);
        results.record("numbers_preserved",  containsAll(rewritten, atom.metrics()));
        results.record("entities_preserved", containsAll(rewritten, atom.properNouns()));
        results.record("no_new_technologies", extractTech(rewritten).isSubsetOf(atom.skills()));
        results.record("length_within_bounds", lengthRatio(rewritten, atom) < 1.25);
    }
    assertThat(results.rate("numbers_preserved")).isGreaterThanOrEqualTo(0.98);
    assertThat(results.rate("no_new_technologies")).isEqualTo(1.00);   // ← SIFIR TOLERANS
}
```

### 53.5 Eşikler

| Metrik | Faz | Eşik |
|---|---|---|
| Şema uyumu | A | %99+ |
| Zorunlu beceri yakalama | A | %90+ |
| Anlamsız ilan tespiti | A | %95+ |
| Sayı korunumu | D | %98+ |
| Özel isim korunumu | D | %98+ |
| **Yeni teknoloji uydurma** | D | **%0** |
| Uzunluk artışı | D | <%25 |
| Doğrulama red oranı | D | <%5 |
| Sayfa sapma oranı | F | <%2 |

### 53.6 Karşılaştırma raporu

```
PROMPT EVAL — atom_rewrite: v1 → v2
════════════════════════════════════════════
Örneklem: 40 atom × 5 ilan = 200 çağrı

Metrik                    v1      v2      Δ
────────────────────────────────────────────
Sayı korunumu           99.2%   99.5%   +0.3  ✓
Özel isim korunumu      98.1%   97.2%   -0.9  ⚠
Yeni teknoloji            0.0%    0.3%   +0.3  ✗ BLOKER
Uzunluk artışı          +14%    +19%    +5    ⚠
Ort. uygunluk skoru      78.4    81.2   +2.8  ✓
────────────────────────────────────────────
Maliyet/çağrı         $0.0012 $0.0019  +58%
Gecikme (p50)           840ms  1120ms   +33%

SONUÇ: ✗ Birleştirilemez — uydurma tespit edildi
```

Bu rapor, "uygunluk skoru arttı ama uydurma da arttı" gibi gizli takasları görünür kılıyor.

### 53.7 Maliyet kontrolü

| Teknik | Etki |
|---|---|
| Örneklem küçük (30-50 vaka) | Ana kaldıraç |
| Sadece değişen prompt'u test et | Gereksiz çağrı yok |
| Faz A çıktılarını fixture olarak dondur | Zincirleme çağrı yok |
| Batch API | %50 |

**Prompt PR'ı başına ~$0.30. Aylık $2-5.**

**Nightly yapma** — üretim telemetrisi (`llm_invocations`) aynı bilgiyi bedava veriyor.

### 53.8 Üretim–test tutarlılığı

Aynı doğrulayıcı sınıfları hem testte hem üretimde çalışır:

```java
@Component
public class RewriteValidator {
    public ValidationResult validate(RichContent original, String rewritten, Atom atom) { ... }
}
// Faz D bunu üretimde kullanır; eval suite aynı sınıfı test için kullanır
```

Ayrı implementasyonlar "testte geçiyor, canlıda bozuk" durumu doğurur.

---
