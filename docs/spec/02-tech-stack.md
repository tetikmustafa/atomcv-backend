# Bölüm II — Teknoloji Seçimleri (5-8)

> AtomCV spec · [INDEX](../INDEX.md) · bu dosya yalnız aşağıdaki bölümleri içerir.

---

# BÖLÜM II — TEKNOLOJİ SEÇİMLERİ

## 5. Teknoloji Yığını

### 5.1 Backend

| Teknoloji | Ne için | Neden seçildi |
|---|---|---|
| **Java 21** | Ana backend dili | Virtual threads (I/O-bound LLM çağrıları için ideal, reactive karmaşıklığı olmadan), sealed interfaces (Result tipi ve hata hiyerarşisi için), records, pattern matching. Geliştiricinin mevcut uzmanlığı. |
| **Spring Boot 3.x** | Uygulama framework'ü | Olgun ekosistem, güçlü güvenlik katmanı, mükemmel test altyapısı, geliştiricinin deneyimi |
| **Spring Web MVC** | REST API | Virtual threads ile bloklayan kod yazılabiliyor; WebFlux'a gerek yok |
| **Spring Data JPA + Hibernate** | ORM | İlişkisel model ağırlıklı; `@Version` ile optimistic locking bedava |
| **Flyway** | Veritabanı migration | Versiyonlu, sıralı, checksum korumalı şema evrimi. Elle DDL asla. |
| **Jakarta Bean Validation** | Girdi doğrulama | Deklaratif, standart |
| **Resilience4j** | Retry, circuit breaker, timeout | LLM ve derleme servisleri için dayanıklılık |
| ~~Bucket4j~~ → Redis'te kayan pencere | Rate limiting | **Alınmadı (Adım 3.3, dilim 4).** Bölüm 40.5 sınırlarını "3 istek / 15 dakika" diye yazıyor, ki bu pencere; token kovası ortalaması aynı çıkan başka bir kuraldır. Kararı `Retry-After` verdi — bir sonraki slotun ne zaman boşaldığını yalnız pencere söyleyebilir. Sıralı küme + tek Lua script; yeni bağımlılık yok, oturum ve OAuth state'in zaten kullandığı kalıp. § 40.5.1 |
| **Spring RestClient** | HTTP istemcisi | LLM API'lerine raw REST çağrıları için; SDK bağımlılığı yok |
| **Apache PDFBox** | PDF metin çıkarımı | En olgun Java PDF kütüphanesi; **FontBox** ile TTF/OTF metrik okuma da bedava geliyor |
| **Apache POI** | DOCX okuma/yazma | Java'da standart |
| **Thymeleaf** | E-posta şablonları | Sunucu tarafında render; ayrı JS ekosistemi gerektirmiyor |
| **springdoc-openapi** | API şeması üretimi | Frontend tip üretiminin kaynağı |

**Neden .NET değil:** .NET 9 teknik olarak rekabetçi (daha düşük bellek, daha modern dil ergonomisi). Ancak: (a) Apache PDFBox/POI'nin doküman işleme olgunluğu .NET karşılıklarından belirgin üstün ve bu projenin çekirdek ihtiyacı, (b) virtual threads bu I/O-bound iş yükü için async/await'ten daha az bulaşıcı, (c) geliştiricinin mevcut yetkinliği — karmaşık bir sistemi öğrenirken inşa etmenin bilişsel maliyeti asıl problemlerden çalar.

**Neden Python değil:** Embedding API/container üzerinden erişildiği için ML kütüphanesi gerekmiyor. İkinci dil = ikinci CI hattı, ikinci bağımlılık ağacı.

### 5.2 Frontend

| Teknoloji | Ne için | Neden seçildi |
|---|---|---|
| **Next.js 16 (App Router)** | Framework | Landing/SEO için SSG, uygulama için client-side. Mevcut deneyim. Turbopack varsayılan bundler; 15.x backport dalına geçtiği için 16'dan başlandı (EK D.10 · 1). |
| **React 19 + TypeScript** | UI | Tip güvenliği; karmaşık editör durumu için gerekli |
| **Tailwind CSS** | Stil | Hızlı iterasyon, tutarlı tasarım sistemi |
| **shadcn/ui (Radix)** | Bileşen kütüphanesi | Erişilebilirlik (focus trap, ARIA, klavye) bedava geliyor |
| **TanStack Query** | Sunucu durumu | Cache, retry, optimistic update, granüler invalidation |
| **Zustand** | İstemci durumu | Sadece geçici UI durumu (hangi bölüm açık vb.) |
| **React Hook Form + Zod** | Form yönetimi | Şema tabanlı doğrulama; backend şemasıyla hizalanabilir |
| **dnd-kit** | Sürükle-bırak | Klavye sensörü ile erişilebilir sıralama |
| **react-pdf** | PDF önizleme | Lazy yüklenir (~300 KB) |
| **react-diff-viewer-continued** | Diff görünümü | Master/tailored karşılaştırma |
| **next-intl** | i18n | ICU MessageFormat (çoğul kuralları) |
| **openapi-typescript** | Tip üretimi | Backend şemasından otomatik; elle tip yazma senkronizasyon hatası kaynağı |

**Kritik karar:** Next.js API route'larına **iş mantığı konmaz**. BFF yok. Tüm mantık Spring'de kalır, Next.js sadece sunum katmanıdır. Aksi halde mantık iki yere dağılır.

### 5.3 Veri Katmanı

| Teknoloji | Ne için | Neden seçildi |
|---|---|---|
| **PostgreSQL 17** | Ana veritabanı | Veri ilişkisel (kullanıcı→profil→bölüm→atom→varyant). NoSQL burada elle tutarlılık yönetmek demek olurdu. |
| **pgvector** | Vektör arama | Ayrı vektör veritabanı, semantik aramayı üç servise dokunan bir işe çevirir (uygulama DB'si + vektör DB'si + embedding API'si). pgvector ile iki servise iner; dokümanlar ve vektörler aynı tabloda, tutarlılık transactional, senkronizasyon problemi hiç oluşmuyor. |
| **JSONB** | Esnek şema alanları | `content`, `tags`, `render_costs`, `preferences` gibi şema-esnek veriler. Klasik "ilişkisel çekirdek + JSONB kenarlar" deseni. |
| **Redis** | Oturum, cache, anonim depolama, rate limit sayaçları | TTL desteği anonim mod için doğal |
| **PostgreSQL kuyruğu** | İş kuyruğu | `SELECT FOR UPDATE SKIP LOCKED` ile atomik iş alma. Ayrı kuyruk altyapısı (RabbitMQ/Kafka) bu ölçekte gereksiz karmaşıklık. Transactional kalıcılık bedava. |
| **Postgres LISTEN/NOTIFY** | Instance'lar arası pub/sub | SSE olaylarının dağıtımı; Redis pub/sub'a gerek yok |

### 5.4 AI / ML

| Teknoloji | Ne için | Neden seçildi |
|---|---|---|
| **Raw REST (SDK yok)** | LLM erişimi | SDK'lar sürüm kırılmaları ve gereksiz bağımlılık getiriyor; kendi soyutlama katmanımız zaten var |
| **Çoklu sağlayıcı** | Dayanıklılık + maliyet | OpenRouter, Gemini, OpenAI, Anthropic, DeepSeek. Env-driven fallback zinciri. |
| **Yapılandırılmış çıktı** | Şema garantisi | JSON Schema (OpenAI), responseSchema (Gemini), forced tool_use (Claude), json_object (DeepSeek) |
| **BGE-M3 (self-host)** | Embedding | Çok dilli (Türkçe etiketleri de doğru gömer), KVKK açısından veri dışarı çıkmıyor, `content_hash` cache'i sayesinde CPU inference yeterli |
| **text-embeddings-inference** | Embedding sunucusu | HuggingFace'in resmi, hafif, HTTP arayüzlü sunucusu |

**Model kademesi (maliyet optimizasyonu):**

| Faz | Model sınıfı | Gerekçe |
|---|---|---|
| A — İlan analizi | Ucuz | Yapılandırılmış çıkarım, kolay görev |
| D — About sentezi | Orta | Kaliteli yazım, tek çağrı |
| D — Madde yeniden yazımı | Ucuz | Küçük, dar kapsamlı, çok sayıda |
| G — Düzenleme parse | Ucuz | Yapılandırılmış çıkarım |
| Profil çıkarımı | Orta | Uzun girdi/çıktı, doğruluk kritik |

Model adları **env değişkeni**dir, koda gömülmez — model isimlendirmeleri hızla değişiyor.

### 5.5 Doküman Üretimi

| Teknoloji | Ne için | Neden seçildi |
|---|---|---|
| **XeLaTeX** | PDF derleme | Unicode'u doğrudan işler. pdflatex'te Türkçe İ/ı karakterleri `inputenc`/`fontenc` ile sorunlu. Bedeli 2-3× yavaşlık, çok dilli üründe ödemeye değer. |
| **Tectonic** (alternatif) | PDF derleme | Daha küçük saldırı yüzeyi, daha küçük imaj. İkincil seçenek. |
| `\savebox` + `\typeout` | Render maliyeti ölçümü | TeX'in kendisine ölçtürüyoruz — hata payı sıfır |
| **Font whitelist** | Güvenlik + tutarlılık | Latin Modern, TeX Gyre Pagella/Termes/Heros, Fira Sans, Source Sans 3. Hepsi Latin Extended (Türkçe) kapsıyor. |

**Neden self-host, dış API değil:** Önceki nesilde dış derleme API'si (latexonline.cc, ytotech) sürekli sorun çıkardı — timeout, format uyumsuzluğu, tek hata noktası. Self-host tam kontrol veriyor.

### 5.6 Altyapı ve DevOps

| Teknoloji | Ne için | Neden seçildi |
|---|---|---|
| **Docker + Docker Compose** | Konteynerizasyon | Tek uygulama, tek geliştirici, öngörülebilir yük |
| **Nginx** | Reverse proxy, TLS, rate limit, güvenlik header'ları | Sektör standardı; öğrenme değeri. Caddy daha kolay (otomatik TLS) ama Nginx deneyimi daha aktarılabilir. |
| **certbot** | TLS sertifikası | Let's Encrypt otomatik yenileme |
| **GitHub Actions** | CI/CD | Public repo'da sınırsız dakika |
| **GHCR** | Container registry | GitHub entegrasyonu, public imaj ücretsiz |
| **Hetzner Cloud** | VPS | En iyi fiyat/performans; Almanya (KVKK/GDPR açısından AB) |
| **Cloudflare** | CDN, DDoS, WAF, DNS | Ücretsiz katman fazlasıyla yeterli |

**Kubernetes neden yok:** Tek uygulama, tek geliştirici, ücretsiz ürün, öngörülebilir yük. K8s yüksek operasyonel yük getirir, karşılığında hiçbir şey vermez. İhtiyaç doğarsa Docker Compose'dan geçiş zaten kolay.

**Mikroservis neden yok:** Aynı gerekçe. Modüler monolit, net modül sınırlarıyla aynı faydayı dağıtık sistem karmaşıklığı olmadan sağlıyor. Tek istisna: LaTeX derleyicisi ayrı container — ama mikroservis olduğu için değil, **güvenlik izolasyonu** için.

### 5.7 Gözlemlenebilirlik

| Teknoloji | Ne için | Neden seçildi |
|---|---|---|
| **Axiom** | Log, metrik, trace | 500 GB/ay ücretsiz, 30 gün saklama. Self-host Loki ~1 GB RAM yer; managed'da sunucu maliyeti sıfır. Gözlem verisi gözlenen sistemin üzerinde yaşamamalı. |
| **OpenTelemetry** | Enstrümantasyon | Sağlayıcı bağımsızlığı — endpoint değiştirmek yeterli |
| **Sentry** | Hata takibi | Ücretsiz katman; stack trace ve bağlam |
| **UptimeRobot** | Dış uptime kontrolü | Sunucu düşerse içeriden haber alınamaz |
| **Umami / Plausible** | Analitik | Çerezsiz → çerez izni banner'ı gerekmez; GDPR/KVKK uyumlu |

**ELK neden yok:** Elasticsearch tek başına 3-4 GB RAM, Logstash 1 GB. Uygulamanın tamamından fazla yer kaplar. Ayrıca Elastic'in kalıcı ücretsiz bulut katmanı yok.

### 5.8 Dış Servisler

| Servis | Ne için | Neden seçildi |
|---|---|---|
| **Resend** | Transactional e-posta | 3.000/ay ücretsiz, modern API, iyi DNS kurulum rehberi |
| **Cloudflare Turnstile** | Bot koruması | Ücretsiz, CAPTCHA'sız UX, Cloudflare ekosisteminde |
| **Cloudflare R2** | Nesne depolama (PDF, yedek) | S3 uyumlu, **egress ücretsiz** (restore testi bedava) |
| **OAuth: Google/GitHub** | Kimlik | Magic link'e alternatif; e-posta teslimat riskini azaltır |

**Kendi SMTP sunucusu neden yok:** VPS IP'leri evrensel olarak güvenilmez kabul edilir. Gmail/Outlook doğrudan spam'e atar. IP itibarı aylar sürer, tek şikayet sıfırlar.

### 5.9 Test

| Teknoloji | Ne için |
|---|---|
| **JUnit 5 + Mockito** | Unit testler |
| **Testcontainers** | Entegrasyon (gerçek Postgres+pgvector) |
| **Vitest + Testing Library** | Frontend unit |
| **Playwright** | E2E |
| **ArchUnit** | Mimari kural zorlama (PII log yasağı, repository kuralı) |
| **WireMock** | LLM sağlayıcı contract testleri |
| **OWASP ZAP** | Güvenlik taraması |
| **Trivy** | Container imaj taraması |
| **OWASP Dependency-Check / Dependabot** | Bağımlılık açıkları |
| **CodeQL** | Statik kod analizi |
| **`scripts/check-bundle-size.mjs`** | Frontend bundle bütçesi — hazır araçlar Next'in içerik-hash'li chunk'larını rota rota ölçemiyor (EK D.10 · 13) |

---

## 6. Design Patterns

| Pattern | Nerede | Neden |
|---|---|---|
| **Strategy** | LLM sağlayıcıları, Renderer'lar, Seçim algoritması | Yeni sağlayıcı/şablon = yeni sınıf; mevcut kod değişmez |
| **Ports & Adapters (Hexagonal)** | Tüm dış servisler, anonim/kalıcı store | Dış servisler arayüz arkasında; testte mock'lanabilir; anonim mod pipeline'a dokunmadan çalışır |
| **Repository (user-scoped)** | Tüm veri erişimi | IDOR'u **yapısal olarak** engeller — kritik güvenlik kararı |
| **Pipeline / Chain of Responsibility** | Faz A→G | Her faz bağımsız, test edilebilir, sıra konfigüre edilebilir |
| **Factory** | Renderer seçimi | Şablon adı → renderer örneği |
| **Result / Either** | Pipeline hata yönetimi | Exception yerine tipli hata; "kullanıcıya ne söyleyeceğiz" kararı akışta kalır |
| **Value Object** | Atom, Score, RenderCost, ProfileRef | Primitive obsession'dan kaçınma; `ProfileRef` tipi yanlış store'a gitmeyi derleme zamanında yakalar |
| **Specification** | Skorlama kriterleri | Kriterler kompozit olarak birleştirilebilir |
| **Template Method** | Renderer'ların ortak iskeleti | Ölçüm/final modları aynı preamble'ı paylaşır |
| **Observer / Event** | LLM invocation kaydı, iş ilerlemesi | Yan etkiler ana akıştan ayrışır |

### 6.1 En kritik pattern: User-Scoped Repository

```java
// ❌ ASLA
atomRepository.findById(atomId);

// ✅ HER ZAMAN
userScopedAtomRepository.findById(currentUser, atomId);
```

`WHERE user_id = ?` filtresini geliştiricinin hatırlamasına bırakmak, IDOR (Insecure Direct Object Reference) açığının en yaygın kaynağıdır. Base repository sınıfında zorunlu kılınır ve ArchUnit ile denetlenir.

---

## 7. Algoritmalar

| Algoritma | Nerede | Karmaşıklık |
|---|---|---|
| **Kosinüs benzerliği** | Faz B — embedding karşılaştırması (pgvector) | O(d) |
| **Jaccard benzerliği** | Faz B — etiket/beceri kümesi örtüşmesi | O(n) |
| **0/1 Knapsack (greedy + local swap)** | Faz C — içerik seçimi | O(n log n) |
| **Azalan getiri (diminishing returns)** | Faz C — çeşitlilik kısıtı | O(1) per atom |
| **Jaro-Winkler + embedding** | Ingestion — kaynak birleştirme/deduplication | O(n·m) |
| **Exponential backoff + jitter** | Kuyruk retry | O(1) |
| **Sliding window** | Rate limiting | O(1) |
| **Murmur3 hash bucketing** | Prompt A/B testi | O(1) |
| **HNSW** | pgvector indeksi (10k+ satırda) | O(log n) |

### 7.1 Neden greedy, DP değil

Faz C'deki seçim problemi tam olarak 0/1 knapsack değil — entry başlığı bağımlılığı (bir atom seçilince ait olduğu entry'nin sabit maliyeti tetikleniyor) ve çeşitlilik kısıtı doğrusal olmayan bileşenler ekliyor.

Dinamik programlama uygulanabilir ama:
- Maliyetler kesirli punto (27.7pt) → tamsayı tablo için ölçekleme gerekir
- Entry bağımlılığı DP durumuna ek boyut ekler
- Atom sayısı düşük (50-300) → greedy pratikte optimuma çok yakın
- **Greedy hata ayıklanabilir** — kullanıcıya "neden seçildi" açıklaması yapabilmek için önemli

İleride optimalite kritik olursa, Strategy deseni sayesinde aynı arayüzün arkasında ILP çözücü (OR-Tools) implementasyonuna geçilebilir.

---

## 8. Reddedilen Alternatifler

Bu tablo, ileride "neden bu seçilmemişti?" sorusunun tekrar tartışılmaması için.

| Alternatif | Neden reddedildi |
|---|---|
| Mikroservis mimarisi | Tek geliştirici, öngörülebilir yük; dağıtık sistem karmaşıklığı karşılıksız |
| Kubernetes | Aynı gerekçe; yüksek operasyonel yük |
| NoSQL (MongoDB vb.) | Veri ilişkisel; elle tutarlılık yönetmek gerekirdi |
| Ayrı vektör veritabanı | pgvector aynı işi yapıyor, senkronizasyon problemi yok |
| Redis kuyruğu (BullMQ vb.) | Postgres kuyruğu bu ölçekte yeterli, ek altyapı yok, transactional |
| Self-host Loki/ELK | RAM maliyeti; gözlem verisi gözlenen sistemde yaşamamalı |
| Caddy (Nginx yerine) | Nginx sektör standardı, öğrenme değeri daha yüksek |
| JWT (localStorage'da) | XSS'e savunmasız, iptal edilemez. HttpOnly session cookie tercih edildi. |
| LLM SDK'ları | Sürüm kırılmaları, gereksiz bağımlılık; kendi soyutlamamız var |
| pdflatex | Türkçe Unicode sorunları (İ/ı) |
| Dış LaTeX derleme API'si | Önceki nesilde sürekli sorun; tek hata noktası |
| Offset tabanlı vurgu | Metin düzenlenince offset'ler kayar |
| Alt-metin (substring) tabanlı vurgu | Belirsizlik (aynı kelime iki kez geçerse) |
| Markdown işaretleyici | Escape sorunu; semantik bilgi kaybı |
| Kendi SMTP sunucusu | IP itibarı problemi |
| Google Analytics | Çerez izni gerektirir, gizlilik konumlandırmasıyla çelişir |
| OCR (taranmış PDF) | Ek bağımlılık, kalite riski, düşük fayda |
| LinkedIn veri export | Yüksek kullanıcı sürtünmesi; GitHub daha iyi sinyal |
| Öğrenen kalibrasyon (feedback loop) | İstatistiksel anlamlılık için çok veri/zaman gerekir; orantısız karmaşıklık |
| Ham LaTeX düzenleme izni | Doğrudan RCE yüzeyi |
| Ücretli katmanlar | Ürün konumu gereği |

---
