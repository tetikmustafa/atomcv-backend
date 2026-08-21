# Bölüm III — Sistem Mimarisi (9-11)

> AtomCV spec · [INDEX](../INDEX.md) · bu dosya yalnız aşağıdaki bölümleri içerir.

---

# BÖLÜM III — SİSTEM MİMARİSİ

## 9. Mimari Genel Bakış

### 9.1 Katmanlı yapı

```mermaid
flowchart TB
    subgraph L1["KATMAN 1 — VERİ GİRİŞİ"]
        A1[CV Yükleme<br/>PDF/DOCX/TEX]
        A2[GitHub OAuth]
        A3[Manuel Form]
        A4[Serbest Metin + Tercihler]
    end

    subgraph L2["KATMAN 2 — MASTER PROFİL"]
        B1[(Atomlar + Varyantlar<br/>Format-bağımsız)]
        B2[Etiketler · Önem · Kilitler · Alternatifler]
    end

    subgraph L3["KATMAN 3 — ÜRETİM HATTI"]
        C1["Faz A: İlan Analizi 🤖"]
        C2["Faz B: Skorlama ⚙️"]
        C3["Faz C: Seçim ⚙️"]
        C4["Faz D: Yeniden Yazım 🤖"]
        C5["Faz E: Render ⚙️"]
        C6["Faz F: Doğrulama ⚙️"]
    end

    subgraph L4["KATMAN 4 — ÇIKTI"]
        D1[PDF]
        D2[DOCX]
        D3[Ham Kaynak]
        D4[Cover Letter]
    end

    subgraph L5["KATMAN 5 — KALICILIK"]
        E1[(Hesap · Profil)]
        E2[(Başvuru Takibi)]
    end

    A1 & A2 & A3 & A4 --> B1
    B1 --> B2 --> C1
    C1 --> C2 --> C3 --> C4 --> C5 --> C6
    C6 -->|Sapma| C3
    C6 --> D1 & D2 & D3 & D4
    D1 --> E2
    B2 -.-> E1
```

### 9.2 Katman sorumlulukları

| Katman | Sorumluluk | Bilmediği |
|---|---|---|
| 1 — Veri Girişi | Ham veriyi standart atom yapısına çevirmek | Nasıl skorlanacağı, nasıl render edileceği |
| 2 — Master Profil | Tek doğruluk kaynağı (single source of truth) | Hangi formatta çıktı üretileceği |
| 3 — Üretim Hattı | Profil + İlan → optimize seçim | Hangi formatların desteklendiği (sadece kapasite parametresi alır) |
| 4 — Çıktı | Seçilmiş içeriği formata dökmek | Nasıl seçildiği |
| 5 — Kalıcılık | Hesap, geçmiş, tercihler | İş mantığı |

**Bağımsızlık prensibi:** Yeni format eklemek Katman 3'ü, yeni skorlama kriteri eklemek Katman 4'ü etkilemez.

---

## 10. Modül Yapısı

> **Repo ayrımı:** Proje iki ayrı GitHub reposundan oluşur — `atomcv-backend` ve `atomcv-frontend`. Aşağıdaki modül yapısı **yalnızca backend reposunu** tanımlar. Frontend yapısı Bölüm 36 ve XI-B.3'tedir. Repo ayrımının tüm sonuçları Bölüm XI-B.1'de toplanmıştır.

### 10.1 Modüler monolit organizasyonu (backend repo)

```
src/main/java/com/mustafatetik/atomcv/
├── identity/                    # Kimlik, oturum, hesap
│   ├── api/                     #   REST controller'lar
│   ├── domain/                  #   User, Session, OAuthIdentity
│   ├── service/
│   └── repository/
├── profile/                     # Master Profil
│   ├── api/
│   ├── domain/                  #   Profile, Section, Entry, Atom, AtomVariant,
│   │                            #   ProfileTree, content/{RichContent, Run, Mark}
│   ├── service/                 #   ProfileAssembler
│   └── repository/              #   kapsamlı cepheler + paket-özel Spring Data arayüzleri
├── ingestion/                   # Profil oluşturma
│   ├── extraction/              #   PDF/DOCX/TEX metin çıkarımı
│   ├── structuring/             #   LLM ile yapılandırma
│   ├── normalization/           #   Beceri, tarih, run dönüşümü
│   └── github/                  #   GitHub entegrasyonu
├── generation/                  # Üretim hattı
│   ├── pipeline/                #   Orkestratör, PipelineContext, ErrorPresenter
│   ├── phases/                  #   A, B, C, D, F, G
│   ├── scoring/                 #   Skorlama algoritması
│   ├── selection/               #   Bin-packing optimizasyon
│   └── validation/              #   Yeniden yazım doğrulayıcıları
├── rendering/                   # Render katmanı
│   ├── model/                   #   RenderRequest, RenderableSection
│   ├── latex/                   #   LatexRenderer, InlineRenderer, escape
│   ├── html/
│   ├── docx/
│   ├── measurement/             #   Ölçüm dokümanı + log parse
│   └── template/                #   Şablon config, customization
├── llm/                         # LLM Gateway
│   ├── gateway/                 #   LlmProvider arayüzü, chain
│   ├── providers/               #   OpenRouter, Gemini, OpenAI, Anthropic, DeepSeek
│   ├── prompts/                 #   PromptRegistry
│   └── telemetry/               #   llm_invocations kaydı
├── embedding/                   # Embedding altyapısı
├── compilation/                 # LaTeX derleme istemcisi
├── jobs/                        # Kuyruk ve worker
│   ├── queue/
│   ├── workers/
│   └── sse/                     #   İlerleme bildirimi
├── tracking/                    # Başvuru takibi
├── billing/                     # Kota, maliyet, anomali
└── shared/                      # Ortak
    ├── security/                #   User-scoped repository base, CSRF
    ├── error/                   #   Result, PipelineError, ErrorCode, Resolution
    ├── config/
    └── util/
```

### 10.2 Modüller arası kurallar

1. **Modüller yalnızca public arayüzler üzerinden haberleşir.** İç sınıflar package-private.
2. **Döngüsel bağımlılık yasak.** ArchUnit ile denetlenir.
3. **`generation` modülü `rendering`'i yalnızca `CapacityModel` üzerinden tanır** — hangi formatların desteklendiğini bilmez.
4. **`shared` hiçbir iş modülüne bağımlı olamaz.**

```java
@ArchTest
static final ArchRule moduleDependencies = 
    slices().matching("com.mustafatetik.atomcv.(*)..")
            .should().beFreeOfCycles();

@ArchTest
static final ArchRule sharedIsIndependent =
    noClasses().that().resideInAPackage("..shared..")
               .should().dependOnClassesThat().resideInAnyPackage(
                   "..profile..", "..generation..", "..rendering..");
```

---

## 11. Deployment Topolojisi

### 11.1 Container yapısı

```yaml
# docker-compose.prod.yml
services:
  nginx:
    image: nginx:alpine
    ports: ["80:80", "443:443"]
    volumes:
      - ./nginx.conf:/etc/nginx/nginx.conf:ro
      - certbot-certs:/etc/letsencrypt:ro
    depends_on: [frontend, backend]

  frontend:
    # İki repo bağımsız deploy edilir, tek bir GIT_SHA yoktur (bkz. 47.3)
    image: ghcr.io/tetikmustafa/atomcv-frontend:${FRONTEND_SHA}
    environment:
      - NEXT_PUBLIC_API_URL=/api
    deploy:
      resources:
        limits: { cpus: '0.5', memory: 512M }

  backend:
    image: ghcr.io/tetikmustafa/atomcv-backend:${BACKEND_SHA}
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=70 -Duser.language=en -Duser.country=US
    depends_on: [postgres, redis, latex, embeddings]
    deploy:
      resources:
        limits: { cpus: '2.0', memory: 1G }

  postgres:
    image: pgvector/pgvector:pg17
    volumes: [pgdata:/var/lib/postgresql/data]
    environment:
      - POSTGRES_DB=atomcv
    command: >
      postgres -c shared_buffers=256MB
               -c max_connections=50
               -c wal_level=replica
               -c archive_mode=on
    deploy:
      resources:
        limits: { cpus: '1.5', memory: 1G }
        reservations: { cpus: '0.5', memory: 768M }   # garantili taban

  redis:
    image: redis:7-alpine
    command: redis-server --maxmemory 128mb --maxmemory-policy allkeys-lru
    volumes: [redisdata:/data]

  latex:
    build: ./docker/latex
    networks: [latex-isolated]          # ← dış ağ erişimi YOK
    read_only: true
    tmpfs: [/tmp]
    user: "1000:1000"
    security_opt: [no-new-privileges:true]
    cap_drop: [ALL]
    deploy:
      resources:
        limits: { cpus: '1.5', memory: 1G }

  embeddings:
    image: ghcr.io/huggingface/text-embeddings-inference:cpu-latest
    command: --model-id BAAI/bge-m3 --port 8081
    volumes: [modelcache:/data]
    deploy:
      resources:
        limits: { cpus: '1.0', memory: 2.5G }

  umami:
    image: ghcr.io/umami-software/umami:postgresql-latest
    depends_on: [postgres]

volumes:
  pgdata:
  redisdata:
  modelcache:
  certbot-certs:

networks:
  default:
  latex-isolated:
    internal: true    # ← internet erişimi yok
```

### 11.2 Nginx yapılandırması

```nginx
# Rate limit zonları
limit_req_zone $binary_remote_addr zone=api:10m rate=10r/s;
limit_req_zone $binary_remote_addr zone=auth:10m rate=1r/s;

server {
    listen 443 ssl http2;
    server_name atomcv.mustafatetik.com;

    ssl_certificate     /etc/letsencrypt/live/atomcv.mustafatetik.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/atomcv.mustafatetik.com/privkey.pem;
    ssl_protocols TLSv1.2 TLSv1.3;

    # Güvenlik header'ları
    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
    add_header X-Content-Type-Options nosniff always;
    add_header X-Frame-Options DENY always;
    add_header Referrer-Policy strict-origin-when-cross-origin always;
    add_header Content-Security-Policy "default-src 'self'; img-src 'self' data: https:; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; connect-src 'self'" always;

    client_max_body_size 10M;

    # SSE — buffering KAPALI olmalı
    location /api/v1/jobs/ {
        proxy_pass http://backend:8080;
        proxy_buffering off;
        proxy_cache off;
        proxy_read_timeout 300s;
        proxy_http_version 1.1;
        proxy_set_header Connection '';
    }

    # Auth endpoint'leri — sıkı limit
    location /api/v1/auth/ {
        limit_req zone=auth burst=3 nodelay;
        proxy_pass http://backend:8080;
        include proxy_params.conf;
    }

    location /api/ {
        limit_req zone=api burst=20 nodelay;
        proxy_pass http://backend:8080;
        proxy_read_timeout 60s;
        include proxy_params.conf;
    }

    location / {
        proxy_pass http://frontend:3000;
        include proxy_params.conf;
    }

    gzip on;
    gzip_types text/plain text/css application/json application/javascript;
}
```

**Aynı domain kararı:** `atomcv.mustafatetik.com/api/*` → backend. Alt domain (`api.atomcv.mustafatetik.com`) kullanılmıyor çünkü: CORS gerekmiyor ve `SameSite=Strict` çerez çalışıyor.

---
