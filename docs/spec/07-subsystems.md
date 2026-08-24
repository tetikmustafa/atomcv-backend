# Bölüm VI — Alt Sistemler (26-34)

> AtomCV spec · [INDEX](../INDEX.md) · bu dosya yalnız aşağıdaki bölümleri içerir.

---

# BÖLÜM VI — ALT SİSTEMLER

## 26. Render Maliyeti Ölçüm Sistemi

Ürünün sayfa garantisi buna dayanıyor. Yanlışsa tüm iddia çöker.

### 26.1 Neden basit yaklaşımlar yetmiyor

| Yaklaşım | Sorun |
|---|---|
| Karakter sayısı / satır genişliği | LaTeX'te karakter genişlikleri eşit değil; `\textbf` daha geniş; hyphenation ve justify satır kırılmasını değiştirir |
| Her atomu ayrı derlemek | 200 atom × 3 şablon = 600 derleme — imkânsız |

### 26.2 İki katmanlı çözüm

**Katman 1 — Font metrik tahmini (derleme yok):**

```java
@Component
public class FontMetricEstimator {

    // FontBox (PDFBox içinde) ile TTF/OTF metrikleri
    private final Map<String, FontMetrics> loadedFonts;

    public double estimateWidthPt(RichContent content, TemplateCustomization c) {
        var normal = loadedFonts.get(c.fontFamily() + ":regular");
        var bold   = loadedFonts.get(c.fontFamily() + ":bold");
        double units = 0;
        for (Run run : content.runs()) {
            var m = run.isBold() ? bold : normal;
            for (int cp : run.text().codePoints().toArray())
                units += m.advanceWidth(cp);
        }
        return units * c.fontSizePt() / m.unitsPerEm();
    }

    public double estimateHeightPt(RichContent c, TemplateCustomization cu, double lineWidthPt) {
        double ratio = estimateWidthPt(c, cu) / lineWidthPt;
        int lines = (int) Math.ceil(ratio / 0.92);      // TeX satırları %92 doldurur
        return lines * baselineSkipPt(cu);
    }
}
```

Doğruluk: **~%95**. UI'da anlık geri bildirim için yeterli, optimizasyon için değil.

**Katman 2 — Kesin ölçüm (TeX'in kendisi ölçer):**

Tek derleme, tüm atomlar. Bölüm 22.4'teki ölçüm dokümanı derlenir, log parse edilir:

```java
private static final Pattern COST = Pattern.compile(
    "ATOMCOST\\|([^|]+)\\|([\\d.]+)pt\\|([\\d.]+)pt"
);

public Map<String, Double> parse(String texLog, CapacityModel capacity) {
    var result = new HashMap<String, Double>();
    var m = COST.matcher(texLog);
    while (m.find()) {
        double height = Double.parseDouble(m.group(2));
        double depth  = Double.parseDouble(m.group(3));
        result.put(m.group(1), height + depth + capacity.baselineSkipPt());
    }
    return result;
}
```

**Süre:** ~200 atom / 12-20 saniye (XeLaTeX). Arka planda.

> **Not (Adım 1.9).** Yukarıdaki `height + depth + baselineSkip` formülü bir
> madde listesi içindeki içerik için **yanlış**: kutu sayfayı kendi yüksekliği
> kadar değil, satır sayısı kadar baseline ilerletiyor. Madde başına ~8 punto
> fazla sayıyordu. Uygulanan formül ve onu bulan test: **EK D.8.10**.

### 26.3 Kritik: punto ile çalış, satır değil

```
❌ Her atomu tam satıra yuvarla → 16 atomda 16 satıra kadar hata birikir

✅ Punto ile topla, sadece en sonda kapasiteyle karşılaştır
   Sayfa metin yüksekliği: 648pt (letter, 0.5in margin)
   Sabit maliyetler:       −142pt
   Serbest bütçe:           506pt
   Σ(atom yükseklikleri) ≤ 506pt
```

`render_costs` JSONB'sinde punto saklanır:
```json
{ "classic:v2": 27.7, "modern:v3": 25.1, "compact:v1": 21.4 }
```

> **Not (Adım 1.9).** Bu uyarı ölçümü satıra çevirip artığı kaybetmek için
> geçerli. Bir madde listesinde n satırın yüksekliği **tam olarak** n
> baseline'dır — orada satıra yuvarlamak yaklaşım değil, TeX'in aritmetiği
> (EK D.8.10). Toplama yine puntoyla yapılıyor.

### 26.4 Sabit maliyetler

Şablon config'inde bir kez ölçülür:

```json
{
  "templateId": "classic",
  "version": 2,
  "pageTextHeightPt": 648.0,
  "baselineSkipPt": 13.6,
  "fixedCosts": {
    "heading": 42.0,
    "sectionHeader": 18.5,
    "entryHeader": 24.0,
    "projectHeader": 14.0,
    "itemizeOverhead": 6.0,
    "educationEntry": 24.0,
    "languagesBlock": 38.0
  }
}
```

### 26.5 Geçersizleşme (invalidation)

> **Not (Adım 1.8).** "Font-metrik tahmini" katmanı **FontBox'sız** yazıldı:
> bağımlılık eklemek yerine kasten daha kötümser bir tahmin var ve tek sözü
> gerçek derleyiciden **asla az yazmaması** (EK D.8.7). %8 güvenlik payı
> aynen uygulanıyor.

```
Metin değişti
  → plain_text değişti → content_hash değişti
  → render_costs NULL'landı, cost_measured_at NULL
  → ölçüm işi kuyruğa (priority 200)

Şablon sürümü arttı
  → o şablonun tüm render_costs anahtarları geçersiz
  → tembel ölçüm: o şablon ilk kullanıldığında ölçülür

Ölçüm henüz yoksa
  → font-metrik tahmini + %8 güvenlik payı
  → trace.C.estimatedAtoms sayacı artar
```

### 26.6 Kalibrasyon geri bildirimi

```java
// Faz F sonrası
double predicted = selection.budget().usedPt();
double actual = pdfAnalyzer.measureContentHeight(pdf);
double driftPct = Math.abs(actual - predicted) / predicted;

telemetry.gauge("template.estimation.drift", driftPct,
    tags("template", customization.templateId()));

if (driftPct > 0.03) {
    // Sürekli sapma varsa güvenlik payını otomatik artır
    calibrationService.increaseSafetyMargin(customization.templateId());
}
```

Sistem kendi tahmin hatasını zamanla öğrenir.

---

## 27. LLM Gateway

### 27.1 Ortak arayüz

```java
public interface LlmProvider {
    String id();
    boolean isAvailable();      // API anahtarı var mı
    ModelTier tier();

    <T> LlmOutcome<T> callStructured(StructuredRequest<T> req);
}

public sealed interface LlmOutcome<T> {
    record Answered<T>(LlmResponse<T> response) implements LlmOutcome<T> {}
    record Failed<T>(LlmFailure failure)        implements LlmOutcome<T> {}
}

public record LlmFailure(Kind kind, String provider, String detail) {
    public enum Kind {
        RATE_LIMITED(true), SERVER_ERROR(true), TIMEOUT(true), UNREACHABLE(true),
        SCHEMA_MISMATCH(false), REQUEST_REJECTED(false);
        // true → zincirdeki sonraki sağlayıcı; false → aynı sağlayıcıda retry (27.3)
        public boolean tryNextProvider() { ... }
    }
}

// Prompt'un yanındaki schema.json'ın (53.1) üstünde bir value object.
// name gerekli: OpenAI/OpenRouter response_format'ta şema adı istiyor,
// Anthropic adaptörü onu zorlanan tool'un adı olarak kullanıyor (27.2).
public record JsonSchema(String name, JsonNode node) {}
```

**Sağlayıcı `Result` değil `LlmOutcome` döndürür.** Ayrım hata tipinde: tek bir
sağlayıcının 429'u ya da şema uyumsuzluğu **kullanıcıya çıkmaz**, çünkü hata
kataloğunda (EK D.6) LLM için yalnız iki kod var —
`ALL_PROVIDERS_UNAVAILABLE (503, tried[])` ve `EMBEDDING_UNAVAILABLE (503)`.
`PipelineError` kullanıcının gördüğü hiyerarşidir; sağlayıcı seviyesindeki
başarısızlık `llm` modülünün içinde kalır ve dışarı yalnız zincirin sonucu çıkar.

Bunun kabul edilen sonucu: ısrarlı bir şema uyumsuzluğu da kullanıcıya
`ALL_PROVIDERS_UNAVAILABLE` görünür. Kullanıcı için ayrım yok — ikisi de "model
cevap vermedi" — ama telemetride var: `llm_invocations.outcome` `schema_error`
olarak ayrı duruyor.

```java
public record StructuredRequest<T>(
    String promptId,
    String promptVersion,
    String systemPrompt,
    String userPrompt,
    JsonSchema outputSchema,
    Class<T> resultType,
    ModelTier preferredTier,
    Duration timeout
) {}

public record LlmResponse<T>(
    T data,
    String provider,
    String model,
    int inputTokens,
    int outputTokens,
    int cachedTokens,
    long latencyMs
) {}
```

### 27.2 Sağlayıcı adaptörleri

| Sağlayıcı | Endpoint | Yapılandırılmış çıktı mekanizması |
|---|---|---|
| **OpenRouter** | `/api/v1/chat/completions` | `response_format: json_schema`, desteklenmiyorsa `json_object` + şema promptta |
| **Gemini** | `/v1beta/models/{m}:generateContent` | `generationConfig.responseMimeType` + `responseSchema` |
| **OpenAI** | `/v1/chat/completions` | `response_format: { type: json_schema, strict: true }` |
| **Anthropic** | `/v1/messages` | **Forced tool call** — tek tool tanımla, `tool_choice: {type:"tool", name:...}`, sonucu `tool_use` bloğundan oku |
| **DeepSeek** | `/chat/completions` | `response_format: json_object` (şema promptta, şemasız mod) |

**Claude'un farkı önemli:** Bare JSON mode yok; forced tool use tek güvenilir yol. Bu, adaptörde ayrı kod yolu gerektirir.

**"Desteklenmiyorsa" tespitle değil yapılandırmayla çözülür.** Hangi mekanizmayı hangi modelin desteklediği modele ait bir olgudur ve yanıt bunu güvenilir biçimde söylemez; hata metnine bakarak tahmin etmek her başarısızlığı sessizce zayıf moda düşürürdü. Adaptör başına açık bir ayar taşınır — OpenRouter'da `atomcv.llm.openrouter.structured-output: JSON_SCHEMA | JSON_OBJECT`. `JSON_SCHEMA` `strict: true` ile gider; `strict` olmadan sağlayıcı şemayı öneri sayar ve § 53.5'in Faz A için istediği %99 uyum tutmaz.

### 27.3 Fallback zinciri

```yaml
llm:
  chain:
    cheap:   [gemini, deepseek, openrouter]
    mid:     [openai, anthropic, openrouter]
  models:
    gemini:     ${GEMINI_MODEL}
    openai:     ${OPENAI_MODEL}
    anthropic:  ${ANTHROPIC_MODEL}
    deepseek:   ${DEEPSEEK_MODEL}
    openrouter: ${OPENROUTER_MODEL}
```

```java
public <T> Result<LlmResponse<T>> call(StructuredRequest<T> req) {
    var chain = config.chainFor(req.preferredTier());
    var tried = new ArrayList<String>();

    for (String providerId : chain) {
        var provider = registry.get(providerId);
        if (!provider.isAvailable()) continue;      // anahtar yok → sessizce atla

        tried.add(providerId);
        var result = provider.callStructured(req);

        if (result.isOk()) {
            telemetry.record(result.value(), req);
            return result;
        }
        if (!isRetryableWithNextProvider(result)) return result;   // şema hatası → zinciri deneme
    }
    return Result.err(new AllProvidersUnavailable(tried));
}
```

**Önemli ayrım:** 429/5xx/timeout → sonraki sağlayıcı. Şema uyumsuzluğu → aynı sağlayıcıda retry (farklı sağlayıcı da aynı hatayı verecek). Ayrımı `LlmFailure.Kind.tryNextProvider()` taşır (27.1).

**`tried` boş olabilir ve bu normaldir.** Anahtarı olmayan sağlayıcı *sessizce* atlanır ve `tried`'a yazılmaz: beş vendor listeleyen bir zincir, tek anahtarlı bir kurulumda eksik değil olağan durumdur. Boş liste "hiçbir şey yapılandırılmamış" demek, "hepsi çöktü" değil.

`ProviderChain.call` `Result<LlmResponse<T>>` döndürür — hata tipi `PipelineError` ve bugün üretebildiği tek durum `AllProvidersUnavailable(tried)`. Sağlayıcıdan zincire dönüşüm burada olur.

**Aynı sağlayıcıdaki retry sayısı:** `atomcv.llm.schema-retries`, varsayılan **1**. Bir kez sapan model çoğu zaman ikincide tutturur; daha fazlası yanlış bir prompt için tekrar tekrar ödemektir. Sayı tükendiğinde yürüyüş durur, sonraki vendor denenmez.

**Zincirdeki bilinmeyen sağlayıcı id'si ölümcül değildir**, `warn` basılıp atlanır. Ölümcül olsaydı yukarıdaki beş vendorlu varsayılan zincirle, adaptörlerin yalnız biri yazılmışken açılış yapılamazdı; sessiz olsaydı bir yazım hatası fark edilmezdi.

**Zincir env-driven'dır** (§ 5.4): `LLM_CHAIN_CHEAP` / `LLM_CHAIN_MID` virgülle ayrılmış id listesi taşır, yani sıra sürüm çıkmadan değiştirilebilir.

### 27.4 Maliyet optimizasyonları

| Teknik | Kazanç |
|---|---|
| Prompt caching (sistem promptu sabit) | Cache'lenmiş input'ta belirgin indirim |
| Batch API (acil olmayan işler) | ~%50 |
| Model kademesi (ucuz/orta ayrımı) | Kata varan fark |
| Structured output | Çıktı token'ı kısalır |
| İlan analizi cache | Tekrar maliyeti sıfır |
| Alternatif varyantlardan seçim | Yeniden yazım maliyeti sıfır |

### 27.5 Telemetri

```java
@EventListener
public void onInvocation(LlmInvocationEvent e) {
    invocationRepo.save(e.toEntity());                          // detay (içerik YOK)
    counterRepo.addCost(e.subjectType(), e.subjectId(), "llm_cost", e.costUsd());
    meterRegistry.counter("llm.cost.usd",
        "provider", e.provider(), "prompt", e.promptId()).increment(e.costUsd());
}
```

**Fiyat tablosu konfigürasyonda:**
```yaml
pricing:
  ${GEMINI_MODEL}: { input: 0.10, output: 0.40, cachedInput: 0.025 }
  ${DEEPSEEK_MODEL}: { input: 0.14, output: 0.28 }
```

---

## 28. Embedding Altyapısı

### 28.1 Model seçimi

**BGE-M3, self-host** (text-embeddings-inference container).

| Gerekçe | Detay |
|---|---|
| Çok dilli | Kullanıcının Türkçe etiketlerini de doğru gömer |
| KVKK | CV içeriği hiç dışarı çıkmaz |
| Maliyet | $0 (sadece ~2.5 GB RAM) |
| Performans | `content_hash` cache'i sayesinde nadir çalışır; CPU inference yeterli |

**Boyut:** 1024 (BGE-M3 dense çıktısı)

### 28.2 Ne zaman hesaplanır

```
Atom oluşturuldu/düzenlendi
  → EN varyantının content_hash'i değişti mi?
     → Evet: embedding işi kuyruğa (priority 150)
     → Hayır: atla

İlan analizi tamamlandı
  → embeddingTarget() sentezle → embed → bellekte tut (kalıcılık yok)
```

**Embedding her zaman EN varyantından hesaplanır.** `atoms.embedding_hash`, EN varyantının `content_hash`'ini tutar.

### 28.3 Arayüz

```java
public interface EmbeddingProvider {
    float[] embed(String text);
    List<float[]> embedBatch(List<String> texts);
    int dimensions();
}
```

Ports & Adapters — ileride API'ye geçilirse tek adapter değişikliği.

### 28.4 Fallback

Embedding servisi düşerse skorlama tamamen durmamalı:

```java
if (!embeddingProvider.isHealthy()) {
    // Embedding bileşeni (0.40) devre dışı, diğerleri yeniden ağırlıklandırılır
    return new ScoringWeights(0.0, 0.42, 0.42, 0.16);
}
```

Kalite düşer ama sistem çalışır. Kullanıcıya bilgi verilmez (iç detay), ama telemetriye kaydedilir.

**`isHealthy()` TEI'nin kendi `/health`'ini sorar, port testi yapmaz.** Container portu ağırlıklar yüklenmeden çok önce açar; "bir şey dinliyor mu" diye soran bir kontrol, 2.5 GB'lık ilk açılışın tamamı boyunca *sağlıklı* raporlar ve skorlama her çağrıya 503 dönen bir servise karşı çalışır.

**`isHealthy()` bir sinyaldir, garanti değil.** Geçmiş bir anı anlatır, ve
`true` döndükten sonra çağrının kendisi hâlâ düşebilir. Bu yüzden geri çekilme
iki katmanlı: kontrol, bilinen bir arızada gidiş dönüşü hiç harcamamak için;
`EmbeddingException` yakalaması, üretimin *ortasında* başlayan arıza için.
Yalnız kontrol olsaydı fallback sadece üretimden önce başlamış kesintileri
kapsardı.

**Kısmi cevap reddedilir.** Servis istenenden az vektör dönerse ya da boyut 1024 değilse çağrı hata verir: eksik bir cevap yanlış vektörü yanlış atomla eşleştirir, ve bu hiç vektör olmamasından kötüdür — profil başkasının maddesine göre skorlanır ve hiçbir şey bozuk görünmez.

---

## 29. LaTeX Container

> **Not (Aşama 1).** Çalışan container bu bölümden üç noktada ayrılıyor:
> rlimit'ler servise değil **derleme başına** uygulanıyor (servise
> uygulandığında JVM kendi heap'ini ayıramıyordu), `/compile` yanıtı
> **`X-Page-Count`** başlığı taşıyor (Faz F'nin saydığı sayfa oradan geliyor),
> ve xelatex'e `max_print_line=10000` veriliyor — TeX logu 79 sütunda
> katlanınca ölçüm satırları okunamıyordu. EK D.8.1, D.8.6, D.8.9.

### 29.1 Güvenlik gerekçesi

**LaTeX bir programlama dilidir.** `\write18` shell komutu çalıştırabilir, `\input{/etc/passwd}` dosya okuyabilir. Kullanıcı içeriği LaTeX'e girdiği için bu **doğrudan RCE yüzeyi**.

Not: Kullanıcı ham LaTeX **yazamıyor** (Bölüm 33), ama escape hatası veya beklenmedik bir girdi ihtimaline karşı savunma katmanlı olmalı.

### 29.2 Container yapılandırması

```dockerfile
FROM debian:bookworm-slim

RUN apt-get update && apt-get install -y --no-install-recommends \
      texlive-xetex texlive-latex-recommended texlive-fonts-recommended \
      fonts-firacode fonts-texgyre \
 && rm -rf /var/lib/apt/lists/*

# Whitelist fontları
COPY fonts/ /usr/local/share/fonts/
RUN fc-cache -fv

# Preamble format dosyası (1-2 saniye kazandırır)
COPY preamble.tex /opt/
RUN cd /opt && xelatex -ini -jobname="cvfmt" "&xelatex preamble.tex\dump"

RUN useradd -m -u 1000 texuser
USER texuser
WORKDIR /home/texuser

COPY --chown=texuser server.jar /opt/server.jar
ENTRYPOINT ["java", "-jar", "/opt/server.jar"]
```

### 29.3 Çalışma zamanı izolasyonu

```yaml
latex:
  networks: [latex-isolated]      # internal: true → internet YOK
  read_only: true
  tmpfs: [/tmp:size=256m]
  user: "1000:1000"
  security_opt: [no-new-privileges:true]
  cap_drop: [ALL]
  deploy:
    resources:
      limits: { cpus: '1.5', memory: 1G }
```

### 29.4 İşlem başına izolasyon

Uzun ömürlü container + kısa ömürlü process (her istek için `docker run` yapmak 1-2sn gecikme ekler):

```java
public byte[] compile(String source, Duration timeout) throws CompilationException {
    Path jobDir = Files.createTempDirectory("/tmp", "job-");
    try {
        Files.writeString(jobDir.resolve("doc.tex"), source);

        var pb = new ProcessBuilder(
            "xelatex",
            "-no-shell-escape",           // ← ZORUNLU
            "-interaction=nonstopmode",
            "-halt-on-error",
            "-output-directory=" + jobDir,
            jobDir.resolve("doc.tex").toString()
        );
        pb.environment().clear();          // ortam değişkeni sızdırma
        pb.environment().put("PATH", "/usr/bin:/bin");
        pb.environment().put("TEXMFVAR", jobDir.toString());

        Process p = pb.start();
        if (!p.waitFor(timeout.toSeconds(), TimeUnit.SECONDS)) {
            p.destroyForcibly();
            throw new CompilationException("timeout");
        }
        return Files.readAllBytes(jobDir.resolve("doc.pdf"));
    } finally {
        FileUtils.deleteDirectory(jobDir.toFile());   // her durumda temizle
    }
}
```

**ulimit ayarları** (container entrypoint'inde):
```bash
ulimit -t 20      # CPU saniyesi
ulimit -v 524288  # sanal bellek (512 MB)
ulimit -f 10240   # dosya boyutu (10 MB)
```

### 29.5 Eşzamanlılık

```java
private final Semaphore slots = new Semaphore(3);

public byte[] compileWithLimit(String source) throws Exception {
    if (!slots.tryAcquire(30, TimeUnit.SECONDS))
        throw new CompilationException("queue_timeout");
    try { return compile(source, Duration.ofSeconds(20)); }
    finally { slots.release(); }
}
```

3 eşzamanlı derleme, kaynak kullanımına öngörülebilir tavan koyuyor (Postgres'i aç bırakmamak için).

### 29.6 Warm-up

```java
@PostConstruct
public void warmUp() {
    compile(MINIMAL_DOCUMENT, Duration.ofSeconds(30));   // font cache ısınsın
}
```

İlk derleme her zaman yavaş (~5sn). Warm-up bunu kullanıcıdan gizler.

---

## 30. Kuyruk ve Asenkron İşler

### 30.1 Neden PostgreSQL kuyruğu

| Kriter | Postgres | Redis/RabbitMQ |
|---|---|---|
| Ek altyapı | Yok | Var |
| Kalıcılık | Transactional | Kısmi |
| Atomiklik | `SKIP LOCKED` | Lua/ack mekanizması |
| Kapasite | ~1000 iş/sn | Çok daha fazla (gereksiz) |
| İş durumu sorgulama | SQL ile doğal | Ek yapı |

### 30.2 İş alma

```sql
UPDATE jobs SET
    status = 'running', locked_by = :workerId,
    locked_at = now(), heartbeat_at = now(), attempts = attempts + 1
WHERE id = (
    SELECT id FROM jobs
    WHERE status = 'queued' AND run_after <= now()
    ORDER BY priority, created_at
    FOR UPDATE SKIP LOCKED
    LIMIT 1
)
RETURNING *;
```

**`SKIP LOCKED`'ın aldığı şey mükerrer claim değil, bloklanmama.** Ölçüldü:
sözcükler kaldırıldığında dört worker sekiz işi hâlâ mükerrersiz alıyor, çünkü
READ COMMITTED'de düz `FOR UPDATE` kilidi bekliyor, serbest kalınca yüklemi
yeniden değerlendiriyor, satırı artık `queued` bulmayıp bir sonrakine geçiyor.
Fark **canlılık**: `SKIP LOCKED` olmadan boştaki her yoklama tek bir yavaş
üretimin arkasına park edebilir, ve kuyruk kuyruk olmaktan çıkar. Testi de buna
göre kurmak gerekiyor — mükerrerliği ölçen bir test iki sözcük silindiğinde
geçmeye devam eder (`CLAUDE.md` · Testing Requirements).

**Kuyruğun iki okuyucusu ayrı tiplerdir.** `JobQueue` worker içindir ve
kapsamsızdır — worker'ın davranan bir kullanıcısı yoktur, sıradakini alır.
`JobRepository` kullanıcı içindir ve her okuması kapsamlıdır: iş id'si sisteme
ait olup tarayıcıya verilen tek tanımlayıcıdır ve ilerleme akışı onunla
adreslenir (mutlak kural 3). Tek sınıf, üstünde kapsamsız bir metot taşıyan
kapsamlı bir repository olurdu — birinin er geç controller'dan çağıracağı şekil.

**`jobs`'ta `version` kolonu yok ve olmamalı.** İki worker'ı tek satırdan uzak
tutan şey iyimser kilitleme değil, claim'in kendisidir; ikinci ve daha zayıf bir
cevap eklemek çözülmüş bir soruyu yeniden açardı.

### 30.3 Öncelik sınıfları

```
 10  generation        (kullanıcı ekranda bekliyor)
 50  profile_extract   (kullanıcı bekliyor)
 80  email             (magic link — gecikme kritik)
100  translation       (arka plan)
150  embedding         (arka plan)
200  measurement       (arka plan)
```

### 30.4 Dayanıklılık

```java
// Heartbeat (her 20 saniye)
@Scheduled(fixedRate = 20_000)
public void heartbeat() { jobRepo.touchHeartbeat(workerId, runningJobIds); }

// Zombi toplayıcı
@Scheduled(fixedDelay = 60_000)
public void reclaimStale() { jobRepo.reclaim(Duration.ofMinutes(2)); }
// Deneme HAKKI geri verilmez, ve hakkı bitmiş iş kuyruğa değil 'failed'e gider

// Graceful shutdown
@PreDestroy
public void shutdown() {
    acceptingNewJobs = false;
    if (!executor.awaitTermination(30, SECONDS)) jobRepo.releaseLocks(workerId);
}
```

**Toplayıcı denemeyi geri vermez.** Üretimin ortasında öldürülmüş bir worker'ı
pekâlâ üretimin kendisi öldürmüş olabilir; kendini sonsuza kadar geri alan bir
iş, tek bir zehirli payload'ın kuyruğu düşürme yoludur. **Denemesi bitmiş bir iş
kuyruğa dönemez**, ama `running` bırakılamaz da: `failed`'e alınır, yoksa
birinin izlediği ekranda hiç durmayan bir spinner olur.

**Her instance kendi işlerini de toplar.** Koşul sahiplik değil heartbeat'tir:
ölü görünecek kadar takılmış bir instance, kendisi tarafından da başkası
tarafından da ölü sayılır.

### 30.5 Retry politikası

```java
boolean isRetryable(PipelineError e) {
    return switch (e) {
        case AllProvidersUnavailable ignored  -> true;
        case CompilationFailed ignored        -> true;
        case EmbeddingUnavailable ignored     -> true;
        case InsufficientProfile ignored      -> false;
        case UnparseableJobDescription ignored -> false;
        case ConflictingPreferences ignored   -> false;
        case FeatureRequiresAccount ignored   -> false;
        case QuotaExceeded ignored            -> false;
        case RewriteValidationFailed ignored  -> false;
        case PageLimitExceeded ignored        -> false;
    };
}

long backoffMs(int attempts) {
    return (long)(Math.pow(2, attempts) * 1000) + random.nextInt(1000);   // jitter
}
```

**Üs kaydırmadan önce sınırlanır ve sonuç bir tavana vurur (5 dakika).**
`2^attempts` 63'te `long`'u taşırır ve gecikme negatife döner: iş hemen çalışır,
ve sonsuza kadar öyle yapar. Normalde bir iş o kadar denenmez, ama zombi
toplayıcı bir işi retry bütçesinin ima ettiğinden daha çok kez geri verebilir.

**Jitter süs değil.** Onsuz aynı kesintiye düşen bütün işler aynı ana geri gelir
ve birlikte yine düşer.

### 30.6 SSE ilerleme bildirimi

```java
@GetMapping(value = "/api/v1/jobs/{jobId}/stream", produces = TEXT_EVENT_STREAM_VALUE)
public SseEmitter stream(@PathVariable UUID jobId, @AuthenticationPrincipal Principal p) {
    jobAccess.assertOwnership(p, jobId);           // ← IDOR koruması
    var emitter = new SseEmitter(Duration.ofMinutes(5).toMillis());
    emitter.onTimeout(() -> registry.remove(jobId, emitter));
    emitter.onCompletion(() -> registry.remove(jobId, emitter));
    return registry.subscribe(jobId, emitter);
}
```

**Olay tipleri:**

> **Düzeltme — `label` bir çeviri anahtarıdır, cümle değil.** Aşağıdaki örnek
> düz metin taşıyordu ve § 35.4 ile çelişiyordu: sunucu anahtar gönderir,
> metni frontend yazar. Tek dilde gönderilen bir cümle her yeni dilde yeniden
> gönderilmek zorunda kalırdı, ve ilerleme satırı üründe en çok görülen metin.
> Değerler `generation.phase.<FAZ>` biçiminde.

```
event: phase
data: {"phase":"B","label":"generation.phase.SCORING","pct":50,"detail":"4/7"}

event: completed
data: {"generationId":"...","pageCount":1,"matchLevel":"STRONG"}

event: failed
data: {"code":"CONFLICTING_PREFERENCES","params":{...},"resolutions":[...]}
```

**Çok-instance dağıtımı** (ileride):
```java
jdbc.execute("NOTIFY job_progress, ?", jsonPayload);   // Postgres LISTEN/NOTIFY
```

### 30.7 Idempotency

```java
var existing = jobRepo.findByUserAndKey(userId, req.idempotencyKey());
if (existing.isPresent()) return existing.get();      // çift tıklama → aynı iş
```

---

## 31. Profil Oluşturma (Ingestion)

### 31.1 Yaklaşım: değeri öne al, emeği geriye bırak

```
CV yükle (10 saniye emek)
    ↓
Çıkarım (kullanıcı bekliyor ama bir şey görüyor)
    ↓
Gözden geçir (5 dakika emek) ← artık ürünün ne yaptığını gördü
    ↓
İlk CV üretimi (değer anı)
    ↓
İyileştirmeler (isteğe bağlı, zamana yayılabilir)
```

### 31.2 Dosya doğrulama sırası (ucuzdan pahalıya)

```java
1. Uzantı + MIME kontrolü
2. Magic byte kontrolü          // uzantı yalan söyleyebilir
3. Boyut kontrolü (≤10 MB)
4. Metin çıkarımı
5. Çıkarılan metin uzunluğu     // <100 karakter → taranmış görsel
```

### 31.3 Metin çıkarımı

| Format | Araç | Dikkat |
|---|---|---|
| PDF | PDFBox `PDFTextStripper` | `setSortByPosition(true)` — çok sütunlu düzen |
| DOCX | Apache POI | Makro çalıştırılmaz |
| TEX | Doğrudan + komut temizliği | Regex ile `\command{}` sadeleştirme |
| TXT/MD | Doğrudan | — |

**Karışık metin tespiti:**
```java
boolean looksScrambled(String text) {
    return avgLineLength(text) < 20 || orphanWordRatio(text) > 0.3;
}
```
Şüpheliyse LLM'e "bu metin karışık olabilir, sırayı düzeltmeye çalış" notu eklenir.

**Taranmış PDF:**
```
Bu PDF'ten metin çıkaramadık — taranmış bir görsel olabilir.
Metin tabanlı bir PDF yükleyebilir veya bilgilerini elle girebilirsin.
```
OCR kapsam dışı.

### 31.4 LLM ile yapılandırma (tek çağrı)

```json
{
  "detectedLanguage": "tr",
  "languageConfidence": 0.96,
  "contact": { "name": "...", "email": "...", "phone": "...", "linkedin": "...", "github": "..." },
  "sections": [
    {
      "kind": "experience",
      "title": "Deneyim",
      "entries": [
        {
          "title": "Part-time Data Engineer",
          "organization": "Brisa Bridgestone Sabancı",
          "location": "İstanbul",
          "startDate": "2025-09",
          "endDate": null,
          "atoms": [
            {
              "text_source": "300 bin satırlık veriyi Microsoft Fabric ile Lakehouse'a taşıyan ETL hatları geliştirdim",
              "text_en": "Engineered ETL pipelines processing 300K rows into a Lakehouse using Microsoft Fabric",
              "emphasis_source": ["ETL", "Microsoft Fabric", "300 bin satır"],
              "emphasis_en": ["ETL", "Microsoft Fabric", "300K rows"],
              "skills": ["python", "microsoft-fabric", "etl"],
              "metrics": ["300,000 rows"],
              "properNouns": ["Microsoft Fabric", "Lakehouse"],
              "tags": ["data-engineering", "etl", "has-metric"]
            }
          ]
        }
      ]
    }
  ],
  "warnings": [
    { "code": "AMBIGUOUS_DATE", "detail": "Bir deneyimin bitiş tarihi okunamadı", "path": "sections[0].entries[1]" }
  ]
}
```

**Kritik:** İngilizce karşılık (`text_en`) **aynı çağrıda** üretilir — ayrı çeviri adımı yok. Kaynak dil zaten EN ise ikinci alan istenmez.

### 31.5 Kod tarafı normalizasyon

```
1. Beceri normalizasyonu    "React.js" → "react"  (alias sözlüğü + Locale.ROOT)
2. Tarih ayrıştırma          "Eyl 2025" / "09/2025" / "September 2025" → 2025-09
3. Run yapısı üretimi        emphasis listesi → runs (ilk eşleşme kuralı)
4. plainText + contentHash
5. display_order atama
6. Ters kronolojik sıralama  (deneyim/proje)
7. Etiket kanonikleştirme
```

**Tarih ayrıştırma:** Ayrıştırılamayan tarih **uydurulmaz** — `null` bırakılır ve `warnings`'e eklenir.

**Türkçe locale tuzağı:**
```java
// ❌ Sunucu locale'i TR ise "SQL".toLowerCase() → "sqı"
// ✅ Kimlik/eşleştirme normalizasyonunda DAİMA
text.toLowerCase(Locale.ROOT)
```
JVM: `-Duser.language=en -Duser.country=US`

### 31.6 Gözden geçirme ekranı (zorunlu)

```
┌────────────────────────────────────────────────┐
│ Bilgilerini kontrol et                         │
│ CV'nden çıkardıklarımız — düzeltebilirsin      │
│                                                │
│ ⚠ 2 nokta dikkat gerektiriyor                  │
│                                                │
│ ▶ İletişim                              ✓      │
│ ▶ Eğitim (1)                            ✓      │
│ ▼ Deneyim (3)                           ⚠      │
│    └ Brisa · Digital Transformation Intern     │
│      Bitiş tarihi okunamadı → [ 09/2025 ] ⚠    │
│ ▶ Projeler (8)                          ✓      │
│ ▶ Beceriler (24)                        ✓      │
│                                                │
│              [ Onayla ve Devam Et ]            │
└────────────────────────────────────────────────┘
```

**Tasarım kuralları:**
- Bölümler varsayılan **kapalı** (200 atomu birden görmek bunaltır)
- Sorunlu olanlar otomatik açık
- Kritik uyarılar çözülmeden "Onayla" aktif olmaz
- Inline düzenleme (ayrı moda geçme yok)

**Arka planda paralel çalışanlar:**
```
t=8s   Çıkarım bitti → ekran açılır
       ├── Embedding hesaplama (~5s)
       └── XeLaTeX ölçümü (~15s)
t=25s  Her şey hazır (kullanıcı hâlâ inceliyor)
```

### 31.7 Manuel form

Aşamalı doldurma:
```
1. Temel bilgiler   ~30sn
2. Eğitim           ~1dk    (en az 1)
3. Deneyim/Proje    ~3dk    (en az 1)
4. Beceriler        ~1dk    (öneri destekli)
5. Kendini anlat    ~1dk    (opsiyonel)
6. Tercihler        ~30sn   (opsiyonel)
```

**Madde yazma yardımı:**
```
Ne yaptın? Mümkünse sayı ekle.
Örnek: "Python ile ETL hatları kurarak 300 bin satırlık veriyi işledim"
💡 Sayısal sonuçlar (%, adet, süre) CV'ni güçlendirir
```

**Opsiyonel LLM yardımı:** "Güçlendir" butonu — kullanıcı onaylamadan uygulanmaz, yeni bilgi eklemez.

**Beceri girişi:** Kanonik taksonomiden öneri destekli (normalizasyon garantisi).

### 31.8 GitHub entegrasyonu

```java
GET /user                              → isim, bio, blog, konum
GET /user/repos?sort=updated&per_page=15
GET /repos/{owner}/{repo}/languages
GET /repos/{owner}/{repo}/readme
```

**Sadece public veri.** Private repo izni istenmez.

**Filtreleme:**
```java
boolean isSignificant(Repo r) {
    return !r.isFork()
        && r.sizeKb() > 50
        && (r.stars() > 0 || r.hasReadme() || r.commitCount() > 10)
        && !isTemplateOrTutorial(r.name());   // hello-world, test, learning-*
}
```

**Birleştirme:**
```
CV'den:     "Order Management System — mikroservis mimarisi, 4 servis"
GitHub'dan: languages: [Java, Dockerfile], stars: 3

Birleşim:
  ├── Anlatı: CV'den (kullanıcının kendi ifadesi)
  ├── skills: birleşim (Java → verified: true)
  └── url: GitHub linki
```

Eşleştirme: Jaro-Winkler (repo adı ↔ proje başlığı) + embedding benzerliği.

**Öneri olarak sunulur, otomatik eklenmez.**

### 31.9 Tamamlanma ölçütü

```java
int completeness(Profile p) {
    int s = 0;
    s += p.hasContact()              ? 15 : 0;
    s += p.hasEducationOrExperience()? 20 : 0;
    s += min(p.experienceCount() * 10, 20);
    s += min(p.projectCount() * 5, 15);
    s += min(p.skillCount(), 10);
    s += p.hasSelfDescription()      ? 10 : 0;
    s += p.atomsWithMetrics() >= 3   ? 10 : 0;   // kalite sinyali
    return s;
}
```

**Üretim eşiği:** iletişim + (1 eğitim VEYA 1 deneyim/proje) + 3 beceri ≈ %45

> **Not (Adım 1.2).** Formülün yüklemleri (`hasContact`, `skillCount`,
> `atomsWithMetrics`) burada tanımsız; karara bağlanan halleri **EK D.6.2**'de.
> Yukarıdaki %45 tahmini de tam değil: hesap eğitimle **38**, deneyimle **48**
> veriyor, ve ikisi de testle sabitlendi. Sayı **okumada** hesaplanıyor.

### 31.10 Hata durumları

| Durum | Davranış |
|---|---|
| Taranmış PDF | Net mesaj + manuel forma yönlendir |
| Çıkarımda 0 atom | "CV'nden bilgi çıkaramadık" + manuel form |
| Şifreli PDF | Reddet, açık versiyon iste |
| Dil algılanamadı | Kullanıcıya sor |
| Çıkarım timeout | Kuyruk retry ×3, sonra manuel forma yönlendir |
| Kota aşımı | "Günlük profil oluşturma hakkın doldu" |

---

## 32. Çok Dillilik

### 32.1 Model

```
ZORUNLU:
  ├── EN varyantı   → sistemin çalışma dili (embedding, skorlama)
  └── Kaynak dil    → kullanıcının profili doldurduğu dil

OPSİYONEL (profil ayarından):
  └── Diğer diller  → EN üzerinden pivot ile üretilir
```

Türk kullanıcı için bu TR + EN demek. Alman kullanıcı için DE + EN.

### 32.2 Senkronizasyon

Kullanıcı TR varyantı düzenledi → EN varyantı bayat:

```java
@EventListener
public void onVariantUpdated(VariantUpdatedEvent e) {
    variantRepo.markDerivedStale(e.variantId(), e.newContentHash());
    if (!derivedVariant.isUserEdited()) {
        jobQueue.enqueue(translationJob(derivedVariant), Priority.BACKGROUND);
    }
}
```

**Kullanıcı düzenlemesi korunur:**
```
⚠ Bu maddenin Türkçe hali güncellendi, İngilizce halini sen düzenlemiştin.
  [ İngilizceyi yeniden üret ] [ Benim halimi koru ]
```

### 32.3 ⚠️ Kritik: Türkçe metin İngilizceden uzun

Türkçe, aynı içerik için tipik olarak **%10-20 daha uzun** metin üretir (sondan eklemeli yapı).

**Sonuç:** İngilizce 1 sayfaya sığan atom kümesi, Türkçede taşabilir.

**Pipeline'a etkisi — sıralama meselesi:**
```
❌ YANLIŞ: Seç (EN maliyetiyle) → sonra çevir → taşma
✅ DOĞRU:  Dil seç → o dilin varyant maliyetleriyle optimize et
```

```java
// Faz C içinde
var targetLang = ctx.options().cvLanguage();
double cost = atom.variantFor(targetLang)
    .flatMap(v -> v.measuredCost(customizationId))
    .orElseGet(() -> estimateWithMargin(atom, targetLang));
```

**Yan etki (doğru davranış):** Aynı ilana TR ve EN CV üretilirse seçilen atom kümeleri farklı olabilir. Kullanıcıya belirtilir:
```
ℹ Türkçe metinler daha uzun olduğu için bu sürümde 2 madde daha az yer aldı.
```

### 32.4 Ölçüm

Her dil × her customization için ayrı ölçüm gerekir. **İyi haber:** tek ölçüm derlemesinde tüm dillerin atomları birlikte ölçülebilir (ayrı `\savebox` blokları). 2 dil = 1 derleme, 2× atom.

### 32.5 Pivot çeviri

```
TR (kaynak) ──→ EN (pivot) ──→ DE / FR / ES
```

Doğrudan TR→DE yerine EN üzerinden gitmek daha kaliteli (modellerin EN hizalaması en güçlü).

Uyarı: "Almanca varyantlar otomatik üretildi, gözden geçirmen önerilir."

---

## 33. Şablon ve Özelleştirme Sistemi

### 33.1 Üç katman

| Katman | Örnek | Maliyet |
|---|---|---|
| **A — Bedava** | Bölüm sırası, gizleme, renkler, başlık metni, madde işareti stili | Yeniden ölçüm YOK |
| **B — Ölçüm gerektirir** | Font boyutu/ailesi, margin, satır aralığı, girinti | Bir ölçüm derlemesi |
| **C — Yasak** | Ham LaTeX yazma | RCE riski |

### 33.2 Parametreli özelleştirme

```java
public record TemplateCustomization(
    String baseTemplateId,                          // whitelist
    FontFamily fontFamily,                          // enum
    @Range(min=9, max=12) double fontSizePt,
    @Range(min=0.4, max=1.0) double marginInches,
    @Range(min=0.9, max=1.3) double lineSpacing,
    HexColor accentColor,                           // regex: ^[0-9A-Fa-f]{6}$
    List<SectionLayout> sections                    // sıra + görünürlük + başlık
) {}
```

**Aralıklar dar tutulur** — kötü sonuç fiziksel olarak imkânsız olsun. Kullanıcıya uyarı verilir ama engellenmez:
> "Font boyutunu 9pt yaptın — ATS okunabilirliği düşebilir, 10pt önerilir."

### 33.3 UX akışı (Katman B)

```
1. Kullanıcı slider'ı oynatıyor
   → Font metrik tahmini ile anlık önizleme (~%92)
2. Bırakıyor (debounce 800ms)
   → "Yeniden hesaplanıyor..." + ölçüm işi kuyruğa
3. Ölçüm bitiyor
   → Kesin değerler yerleşiyor
```

Ölçüm bitmeden üretim yapılırsa tahmin + %8 pay kullanılır.

### 33.4 Özel bölümler

```java
enum SectionLayout {
    BULLET_LIST,    // madde listesi
    ENTRY_LIST,     // başlık + tarih + maddeler
    INLINE_LIST,    // virgülle ayrılmış tek satır
    TWO_COLUMN      // yan yana iki liste
}
```

Kullanıcı "Sertifikalar", "Yayınlar", "Gönüllü Çalışmalar" ekler; düzen tipini seçer. Her düzen tipinin sabit maliyeti şablon config'inde bir kez ölçülür.

### 33.5 Şablon kataloğu

| Şablon | Karakter | Yaklaşık kapasite |
|---|---|---|
| **Klasik** | Sade, ATS-güvenli, akademik/kurumsal | ~54 satır/sayfa |
| **Modern** | Hafif renkli başlıklar, teknoloji sektörü | ~50 satır/sayfa |
| **Kompakt** | Yüksek yoğunluk, çok deneyimli profiller | ~64 satır/sayfa |

Her şablonun kapasitesi **bir kez ölçülür**, config'de saklanır.

---

## 34. Cover Letter Üretimi

### 34.1 Neden ayrı bir problem

CV'de güvenlik **seçim**den geliyordu. Cover letter'da bu koruma yok — serbest metin üretimi. Üstelik uydurma daha tehlikeli, çünkü birinci tekil şahısla iddia ediliyor.

### 34.2 Çözüm: atomlardan besle

```
Girdi:
├── Seçilmiş atomlar (Faz C çıktısı)   ← gerçek içerik
├── İlan analizi (Faz A)
├── Kullanıcı tercihleri (ton, dil)
└── Profil: isim, iletişim

Kısıt:
└── Yalnızca seçilmiş atomlardaki bilgilerden bahsedilebilir
```

Cover letter, CV'nin "anlatı versiyonu" olur — tutarlılık bedava gelir.

### 34.3 Bölümlü yapı

```java
public record CoverLetterDraft(
    String greeting,     // şirket bilinirse isim, yoksa jenerik
    String opening,      // pozisyon + neden ilgileniyorum (1-2 cümle)
    String body,         // 2-3 somut kanıt, atomlardan türetilmiş
    String closing,      // eylem çağrısı (1-2 cümle)
    String signature
) {}
```

**Body kuralı:** En yüksek skorlu 2-3 atomu seç, her birini bir cümleye dönüştür, ilandaki gereksinimle açıkça bağla.

### 34.4 Doğrulama

```java
validate(coverLetter, selectedAtoms, profile):
    ✓ Geçen her teknoloji, seçilmiş atomların skills birleşiminde mi?
    ✓ Geçen her sayı, atomların metrics'inde mi?
    ✓ Deneyim süresi iddiası profil tarihleriyle tutarlı mı?   // ← en sık uydurma
    ✓ Şirket adı doğru mu? (JD'den)
    ✓ Uzunluk 250-400 kelime aralığında mı?
    ✗ Klişe ifade var mı?
```

**Klişe filtresi (yasaklı):**
```
"I am writing to express my interest in..."
"I believe I would be a great fit..."
"I am a passionate/dedicated/results-driven..."
"Thank you for considering my application"
```

### 34.5 Şirket bilgisi eksikliği

```
Şirket adı (opsiyonel):  [                    ]
Bu şirket hakkında bildiğin bir şey? (opsiyonel)
[                                              ]
```

İkincisi doldurulursa kişiselleştirme ekler — **kullanıcının verdiği bilgi**, LLM'in uydurduğu değil.

### 34.6 Yeniden üretim

```
[ Yeniden oluştur ]  [ Daha kısa yap ]  [ Daha resmi yap ]
```

Tek LLM çağrısı, ucuz. Kullanıcı birkaç varyant deneyebilmeli.

### 34.7 Sayfa bütçesi

Cover letter render edilmiyor (düz metin kopyalanıyor) → punto ölçümü gereksiz. **250-400 kelime** sınırı yeterli. PDF isteniyorsa aynı şablon sistemi "letter" düzeniyle kullanılır.

---
