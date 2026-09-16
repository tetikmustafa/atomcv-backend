# AtomCV — VPS ve Dağıtım Planı

> **Bu dosya ne:** sunucu alınmasından ilk canlı sürüme kadar adım adım plan,
> ve **sunucu olmadığı için yapılamayan her işin** tek listesi.
>
> **Ne değil:** mimari doküman. Neden öyle kurulduğunun gerekçesi
> `spec/11-operations.md` § 46-49'da, adımların ilk taslağı
> `spec/14-build-guide.md` § XI-A.4'te. **Çelişirlerse bu dosya geçerlidir** —
> buradaki komutlar repodaki gerçek script'lere ve dosya yollarına göre
> yazıldı, kılavuzun taslağı bazı yerlerde onlardan ayrışıyor.
>
> **Sahibi:** `atomcv-backend`. Frontend'e senkronlanmıyor (yalnız
> `docs/spec/**` senkronlanıyor). Frontend'i ilgilendiren tek şey § 4'teki
> `NEXT_PUBLIC_SITE_URL` satırı.

---

## 0. Kararlar

Beşi 2026-09-16'da verildi. Bir kısmı aylardır "açık karar" diye duruyordu;
artık durmuyor.

| Karar | Seçilen | Neden | Nereye bağlanıyor |
|---|---|---|---|
| Sağlayıcı ve plan | **Hetzner CPX31** — 4 vCPU · 8 GB · 160 GB · ~€14/ay | Fiyat/performans, ve AB'de olması KVKK/GDPR tarafını basitleştiriyor. 8 GB gömme konteyneriyle **sıkışık ama yetiyor** — § 3'teki bellek bütçesi | S.1 |
| Alan adı | **`atomcv.mustafatetik.com`** (alt alan adı, değişmiyor) | DNS zaten Cloudflare'de, bedava, bugün başlanabilir. Gerçek alan adına geçme maliyeti EK C.5'te ve o gün ödenir | S.3, S.4, `.env` |
| TLS | **Cloudflare Origin Certificate** | 15 yıl, yenileme yok, cron yok, izlenecek bir yenileme işi yok. **Bedeli:** turuncu bulut kapatılamaz — proxy kapanırsa sertifika geçersizdir | S.4 |
| Üretimde migration | **Açılışta, tek örnekle** (bugünkü davranış) | Hiçbir ek parça yok. **Koşulu:** aynı anda tek backend örneği. Tek sunucuda zaten öyle; ölçeklenince yeniden açılacak bir karar. EK D'deki "açık karar" satırı bununla kapandı | `deploy.sh`, § 6 |
| Deploy tetikleyicisi | **Önce elle, sonra otomatik** | `deploy.yml`'ın `push` tetikleyicisi yorumda ve öyle kalacak — **ilk elle koşu ve rollback tatbikatı başarılı olana kadar**. Sonra secrets'ı ekleyen değişiklikle birlikte açılır | S.7 |

**Kapsam dışı bırakılan:** `docs/spec/`'in İngilizceye çevrilmesi (§ 55'in açık
kaynak hazırlığı kalemi). Yapılmayacak — karar 2026-09-16.

---

## 1. Önce alınacaklar

Hiçbiri sunucuya bağlı değil, hepsi kurulumdan önce hazır olabilir.

### 1.1 Hesap ve satın alma

| Ne | Neden | Not |
|---|---|---|
| Hetzner Cloud hesabı + CPX31 | Sunucu | Konum: Nürnberg / Falkenstein / Helsinki. İşletim sistemi **Ubuntu 24.04 LTS**. Sipariş ekranında **SSH anahtarını ekle**, IPv4 + IPv6 ikisini de al |
| Hetzner'in kendi yedeği (+%20) | — | **Alma.** Kendi yedek sistemimiz var ve şifreli; Hetzner'inki aynı sağlayıcıda duran şifresiz bir kopya |
| Cloudflare R2 bucket | Gecelik yedek + WAL | Ad: `atomcv-backups`. `BACKUP_REMOTE=r2:atomcv-backups` |
| Backblaze B2 bucket | **İkinci** sağlayıcı | Ad: `atomcv-archive`. `BACKUP_ARCHIVE_REMOTE=b2:atomcv-archive`. İkinci sağlayıcı bilerek: bir hesabın kapanması iki kopyayı birden götürmesin |
| Resend | E-posta | Domain doğrulaması **kurulumdan önce** başlatılabilir (SPF/DKIM/DMARC yayılması saatler alır) |
| Sentry, Axiom, UptimeRobot | Hata / log / kesinti | EK C.1 üçünü de istiyor. Ücretsiz katmanları yeter |
| Google + GitHub OAuth uygulaması | Oturum açma | Callback URL'leri alan adını istiyor: `https://atomcv.mustafatetik.com/api/v1/auth/oauth/{google,github}/callback` |
| Cloudflare Turnstile site | Bot koruması | Gizli anahtar `TURNSTILE_SECRET_KEY`, site anahtarı frontend'e |

### 1.2 Üretilecek anahtarlar

```bash
# SSH — sunucuya giriş ve deploy hattının kimliği
ssh-keygen -t ed25519 -C "atomcv-deploy" -f ~/.ssh/atomcv

# age — yedekler sunucudan çıkmadan ÖNCE bununla şifrelenir
age-keygen -o ~/.age-key.txt
```

> **`~/.age-key.txt`'in özel yarısı sunucuya KONMAZ.** Sunucuda yalnız public
> key durur (`AGE_PUBLIC_KEY`). Özel yarı: yerel makine + parola yöneticisi.
> Sunucuyu ele geçiren biri iki yarıyı da ele geçiriyorsa şifreleme dekordur —
> `backup.sh`'in başındaki not tam olarak bunu söylüyor.
>
> **Özel yarıyı kaybetmek bütün yedekleri kaybetmektir.** Geri dönüşü yok.

---

## 2. Adım adım kurulum

### S.1 — Sunucu siparişi

Hetzner Cloud → yeni sunucu → CPX31 → Ubuntu 24.04 → SSH anahtarı seç →
IPv4 + IPv6. Çıkan IP'leri not al.

`~/.ssh/config`:

```
Host atomcv
    HostName <SUNUCU_IPv4>
    User deploy
    IdentityFile ~/.ssh/atomcv
```

### S.2 — Sunucu sertleştirme

`root` ile gir ve sırayla koş. **Çıkmadan önce ikinci bir terminalde
`ssh deploy@<IP>` çalıştığını doğrula** — çalışmıyorsa root oturumunu kapatma,
yoksa makineye giremezsin.

Komutların tamamı `spec/14-build-guide.md` § XI-A.4 Adım V.3'te: sistem
güncellemesi, Docker, `deploy` kullanıcısı (docker grubunda), **4 GB swap**
(`vm.swappiness=10`), UFW (yalnız 22/80/443), şifreyle girişin ve root
girişinin kapatılması, `unattended-upgrades`.

> **Swap bu planda opsiyonel değil.** CPX31'de 8 GB var ve § 3'ün bütçesi
> sınıra yakın; swap OOM killer ile yavaşlama arasındaki fark.

### S.3 — DNS

Cloudflare → `mustafatetik.com` → DNS:

| Tip | Ad | İçerik | Proxy |
|---|---|---|---|
| A | `atomcv` | `<SUNUCU_IPv4>` | 🟠 Proxied |
| AAAA | `atomcv` | `<SUNUCU_IPv6>` | 🟠 Proxied |

SSL/TLS modu: **Full (strict)**.

```bash
dig atomcv.mustafatetik.com +short
```

> **Turuncu bulut açık kalmak zorunda** (TLS kararının bedeli, § 0). Kapatmak
> yalnız DDoS korumasını değil, sertifikanın geçerliliğini de kaldırır.

### S.4 — TLS: Origin Certificate

Cloudflare → SSL/TLS → Origin Server → **Create Certificate**. Hostname:
`atomcv.mustafatetik.com` ve `*.atomcv.mustafatetik.com`. Çıkan iki metni
sunucuya yaz.

**Dosya yolları kritik:** `docker/nginx/nginx.conf` sertifikayı şu iki yolda
arıyor ve orası `certbot-certs` volume'ü:

```
/etc/letsencrypt/live/atomcv.mustafatetik.com/fullchain.pem
/etc/letsencrypt/live/atomcv.mustafatetik.com/privkey.pem
```

```bash
CERTDIR=/etc/letsencrypt/live/atomcv.mustafatetik.com
docker volume create atomcv_certbot-certs
docker run --rm -v atomcv_certbot-certs:/certs alpine mkdir -p "/certs/live/atomcv.mustafatetik.com"
# fullchain.pem ve privkey.pem içeriğini o dizine yaz (scp + docker cp ya da
# `docker run -i ... tee`). privkey.pem 600 olmalı.
```

> **Yol adı "letsencrypt" diyor ama içinde bir Origin Certificate var.** nginx
> sertifikayı kimin imzaladığını umursamıyor; yolu değiştirmemek `nginx.conf`'a
> dokunmamak demek. **Bilinçli sapma** — bir sonraki okuyucu certbot arayıp
> bulamasın diye buraya yazıldı. Compose'daki `certbot-webroot` volume'ü ve
> nginx'in `/.well-known/acme-challenge/` bloğu da kullanılmıyor; Let's
> Encrypt'e dönülürse ikisi de yerinde.

### S.5 — `.env` ve ilk kalkış

```bash
ssh atomcv
sudo mkdir -p /opt/atomcv && sudo chown deploy:deploy /opt/atomcv
cd /opt/atomcv
git clone https://github.com/tetikmustafa/atomcv-backend.git .
cp .env.example .env && chmod 600 .env
nano .env
```

**`.env`'de doldurulması zorunlu olanlar** (tamamı `.env.example`'da, orada
her birinin ne olduğu yazılı):

| Grup | Anahtarlar |
|---|---|
| Temel | `APP_BASE_URL=https://atomcv.mustafatetik.com`, `DEPLOY_ENV=prod` |
| Veritabanı | `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` |
| Oturum | `SESSION_COOKIE_DOMAIN`, `SESSION_COOKIE_SECURE=true`, `FORWARD_HEADERS_STRATEGY` |
| LLM | `OPENROUTER_API_KEY` + `OPENROUTER_MODEL`, ve zincirde kullanılacak diğer sağlayıcılar; `LLM_CHAIN_CHEAP`, `LLM_CHAIN_MID` |
| Kimlik | `OAUTH_GOOGLE_*`, `OAUTH_GITHUB_*`, `TURNSTILE_SECRET_KEY` |
| E-posta | `RESEND_API_KEY`, `RESEND_WEBHOOK_SECRET`, `EMAIL_FROM`, `EMAIL_FROM_NAME`, `EMAIL_REPLY_TO` |
| Yedek | `AGE_PUBLIC_KEY`, `BACKUP_REMOTE`, `BACKUP_ARCHIVE_REMOTE`, `BACKUP_KEEP_*` |
| Gözlem | `SENTRY_DSN`, `OTLP_*` (Axiom), `UMAMI_APP_SECRET` |
| Maliyet | `ANOMALY_DAILY_BUDGET_USD` |

> **`.env` hem compose'un hem Spring'in okuduğu dosya.** Yerelde bu bir kez
> ısırdı: `.env`'e konan gerçek bir üretim anahtarı yerel davranışı sessizce
> değiştirdi. Üretimde yönü tersine dönmüyor ama kural aynı: **bu dosyaya
> konan her şey uygulamaya da geçer.**

İlk kalkış ve doğrulama — compose komutunu sunucuda çalıştır (bu repoda
`.claude/settings.json` üretim compose'unu koşturmayı reddediyor, bilerek):

```bash
docker compose -f docker-compose.prod.yml up -d
docker compose -f docker-compose.prod.yml ps
curl -I  https://atomcv.mustafatetik.com
curl -sf https://atomcv.mustafatetik.com/actuator/health
```

Servisler: `nginx`, `frontend`, `backend`, `postgres` (pgvector/pg17), `redis`,
`latex`, `embeddings`. `umami` ayrı bir profilde (`--profile analytics`).

### S.6 — Yedekleme

```bash
curl https://rclone.org/install.sh | sudo bash
sudo apt install -y age
rclone config     # iki uzak: r2 (Cloudflare R2) ve b2 (Backblaze B2)
```

İki cron girdisi — **ikisi de zorunlu**:

```bash
crontab -e
0 3 * * *   /opt/atomcv/scripts/backup.sh      >> /var/log/atomcv-backup.log 2>&1
*/5 * * * * /opt/atomcv/scripts/archive-wal.sh >> /var/log/atomcv-wal.log 2>&1
```

> **`archive-wal.sh` koşmazsa Postgres yazmayı durdurur.** Script'in kendi
> başlığındaki büyük harfli uyarı bu: `archive_mode=on` açık, segmentler
> `walarchive` volume'ünde birikiyor ve onları alıp şifreleyip gönderen tek
> şey bu script. Kurulumun **aynı gününde** ilk koşuyu elle yap ve R2'de
> dosyaları gör.

### S.7 — Deploy hattı

GitHub → Settings → Secrets and variables → Actions:

| Secret | Değer |
|---|---|
| `SSH_PRIVATE_KEY` | `~/.ssh/atomcv` dosyasının **içeriği** |
| `SSH_HOST` | Sunucu IPv4 |
| `SSH_USER` | `deploy` |

Sonra sırayla:

1. **Elle koş:** GitHub Actions → Deploy → *Run workflow* (`workflow_dispatch`
   şu an tek tetikleyici). İmajı `ghcr.io`'ya basar ve sunucuda
   `scripts/deploy.sh backend <sha>` çağırır.
2. **İlk koşuda `ssh-agent` adımını izle.** `deploy.yml`'ın kendi notu:
   bu adım bu repoda **hiç çalışmadı**, sürüm yükseltmesi doğrulanmadı.
3. **Rollback tatbikatı** (§ 6) — bilerek bozuk bir deploy.
4. İkisi de geçtiyse `deploy.yml`'daki `push: branches: [main]` tetikleyicisini
   yorumdan çıkar. **Aynı değişiklikte**, secrets zaten dururken.

---

## 3. CPX31'de bellek bütçesi

8 GB, ve tahmin gerekmiyor: `docker-compose.prod.yml` her servise **açık bir
limit** koyuyor.

| Servis | `limits.memory` | Not |
|---|---|---|
| `embeddings` (BGE-M3, CPU) | **2.5 G** | En büyük tek kalem |
| `backend` (JVM) | **1 G** | `MaxRAMPercentage=70` → ~717 MB heap |
| `postgres` | **1 G** | Ayrıca 768 MB `reservations` — yani her koşulda ayrılmış |
| `latex` | **1 G** | Derleme anında sıçrar; semafor eşzamanlılığı sınırlıyor |
| `frontend` (Next.js) | **512 M** | |
| `umami` | 512 M | Yalnız `--profile analytics` ile kalkar |
| `nginx`, `redis` | limitsiz | Küçük; ikisi birlikte birkaç yüz MB |

**Analitik kapalıyken tavan 6 GB + nginx/redis.** 8 GB'lık makinede işletim
sistemi ve sıçramalar için ~1.5 GB kalıyor — çalışır, ama bol değil. En dar
an: aynı anda bir ölçüm derlemesi, bir gömme turu ve gecelik yedek dökümü.
**4 GB swap tam bunun için** ve bu planda opsiyonel değil.

**Sıkışırsa sırayla:** (1) `umami`'yi kaldırma — 512 MB'ı zaten ayrı profilde
duruyor, (2) LaTeX eşzamanlılığını düşür, (3) CPX41'e çık. **Gömme
konteynerini kapatmak son çare** — Faz B ağırlığı yeniden dağıtıyor ve ürün
çalışmaya devam ediyor, ama alaka kalitesi düşer ve **bunu kullanıcı
görmez**.

---

## 4. Sunucu ayağa kalkınca açılan işler

Hepsi bugün **yapılamıyor**, ve hepsi bir dağıtım bekliyor. Sıra önemli:
üstteki üçü bir kullanıcının ilk on saniyesinde.

| # | İş | Nereden geldi | Nasıl doğrulanır |
|---|---|---|---|
| 1 | **Turnstile widget'ı çiziliyor mu** | `B-100` — nginx CSP'si Turnstile'ı blokluyordu, düzeltildi ama **hiç görülmedi** | Kayıt ekranını aç, widget görünmeli, konsolda CSP ihlali olmamalı |
| 2 | **OAuth sıçraması** (Google + GitHub) | `B-048` · geliştiricide | Uçtan uca giriş; callback URL'leri § 1.1'deki hâliyle |
| 3 | **Sihirli bağlantı + Turnstile** | `B-050`, `B-083`'ün challenge'ı | `POST /auth/magic-link` 403 dönmemeli, e-posta gelmeli |
| 4 | **⚠️ Gerçek restore testi** | § 49.4 · EK C.1'in tek uyarı işaretli maddesi | `./scripts/restore.sh` — **varsayılanı scratch veritabanı**, üretime dokunmaz. Sıradan bir salı günü koşulabilir, ve o yüzden koşulmalı |
| 5 | **Rollback tatbikatı** | EK C.1 | § 6 |
| 6 | **E-posta teslimatı** (SPF/DKIM/DMARC) | EK C.1 | Gerçek bir adrese giriş bağlantısı; spam kutusuna düşmemeli |
| 7 | **`ProcessorAudit` ile yayımlanan sağlayıcı sayfası** | `B-076`'dan kalan tek şey | Açılış log satırındaki liste ile gizlilik sayfasındaki liste birebir mi |
| 8 | **Analitik + huni ölçümü** | § 55 Aşama 4 · frontend'in "ölçümü alacak bir dağıtım istiyor" dediği şey | `--profile analytics` ile `umami`'yi kaldır |
| 9 | **`NEXT_PUBLIC_SITE_URL`** | Frontend SEO'su — `robots.txt`, `sitemap.xml`, canonical | **Frontend reposunda** ayarlanır |
| 10 | **Fiyat tablosu / anomali alarmı** | EK C.1 | Açılışta `LlmPricingAudit` sessiz mi, `llm.unpriced_calls` artmıyor mu; anomali e-postası geliyor mu |

---

## 5. Yayın kontrol listesi

EK C.1 (`spec/17-appendix-abc.md`) otoritedir ve buraya kopyalanmıyor — iki
kopya ayrışır. **Bugünkü durumu:** güvenlik, veri, maliyet ve hukuk
başlıklarının kod tarafı yazılmış ve testli; işaretlenemeyen maddelerin
tamamı § 4'ün tablosunda, çünkü hepsi sunucu bekliyor.

---

## 6. Rollback ve ilk tatbikat

`scripts/deploy.sh` sağlıklı olmayan bir sürümü kendi geri alıyor: yeni SHA'yı
`.env.deploy`'a yazıyor, `docker compose up -d --no-deps <bileşen>` ile yalnız
o bileşeni kaldırıyor, `http://localhost:8080/actuator/health`'i yokluyor, ve
geçmezse önceki SHA'ya dönüyor. **İki bileşen bağımsız** — backend'in geri
alınması frontend'i yerinde bırakır.

**Tatbikat:** kasten bozuk bir imaj deploy et (örneğin `.env`'de veritabanı
şifresini boz), health'in düştüğünü ve önceki sürüme dönüldüğünü gör, sonra
`.env`'i düzelt. **Bunu ilk gün yap** — çalıştığını görmeden rollback'i var
saymak, denetimlerin tekrar tekrar bulduğu şeyin ta kendisi.

> **Migration geri alınmaz.** Kararımız migration'ı açılışta koşturmak (§ 0),
> yani rollback **kodu** geri alır, şemayı değil. Bu yüzden her migration
> geriye dönük uyumlu olmalı: kolon silme ve daraltma iki sürüme yayılır.
> V16'nın `CHECK`'i buna örnek — önce kod iki değer yazmayı bıraktı, sonra
> kısıt geldi.

---

## 7. Bu repoda öğrenilmiş tuzaklar

Her biri bir hata ayıklama turu tuttu; ikisi doğrudan deploy'a ait.

- **`gradlew test` bir JDK'da koşuyor, ürün bir JRE'de çalışıyor.**
  `RandomGenerator.getDefault()` `jdk.random` istiyor ve hiçbir JRE imajında
  yok: uygulama yerelde açıldı, imajdan kurulan ilk konteynerde öldü.
  **Bağımlılık ya da temel imaj değişince `docker build` + bir `docker run`** —
  bu sınıf hatayı gören tek şey o.
- **`deploy.yml`'ın `ssh-agent` adımı hiç çalışmadı.** Sürüm yükseltmesi
  doğrulanmamış; ilk elle koşuda özellikle izle.
- **`archive-wal.sh` koşmazsa Postgres yazmayı durdurur** (§ S.6).
- **Yarım bir ayar hiç ayar olmamasından daha görünmez** — `wal_level` ayarlanıp
  `archive_mode` unutulduğunda sistem çalışıyor gibi görünür ve
  kurtarılamaz durumda olur. İkisi de `docker-compose.prod.yml`'da.
- **`.env` hem compose'a hem Spring'e gider** (§ S.5).

---

## Kayıt

- Kararlar: § 0. Bu dosya kurulum ilerledikçe **güncellenir** — adımlar
  işaretlenmez, yapılanlar kısaltılıp gerçeğe çevrilir.
- Sunucu ayağa kalktığında `STATUS.md`'nin "Geliştiricide" satırı ve
  `handoff/to-frontend.md`'nin "Dağıtım bekleyen doğrulamalar" bölümü kısalır.
- Bir adım burada söylenenden başka türlü sonuçlanırsa: **kalıcıysa
  `spec/`'e**, geçiciyse `notes/current.md`'ye.
