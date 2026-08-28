# Aşama 2'yi elle test etme

> Repo-yerel, senkronlanmaz. Aşama 2 kapandığında yazıldı; uçlar değiştikçe
> güncellenir ya da silinir.

> ⚠️ **Bu dosya Aşama 2'de yazıldı ve bir yeri eskidi.** Adım 3.3 CSRF'i açtı:
> aşağıdaki çıplak `curl -X POST` komutları bugün **403** alır. Çerez kavanozlu
> hali ve Aşama 3'ün turu `manual-test-stage-3.md` § 0'da. Swagger UI etkilenmez.

Her şey **Git Bash**'ten. `local` profilinde kimlik doğrulama başlığı gerekmez —
`LocalDevCurrentUser` sabit bir geliştirici kullanıcısı döndürür — ve `DevSeeder`
açılışta `senior_backend_tr` golden profilini ekler. Yani "profil oluştur"
adımını atlayabilirsin.

## 0. Ayağa kaldır

```bash
make dev-full        # postgres, redis, mailpit + latex, embeddings
                     # --build kasıtlı: bayat latex imajı X-Page-Count
                     # göndermez ve backend onu haklı olarak reddeder
```

Sonra **ayrı bir kabukta**:

```bash
make dev             # backend, local+local-fake (LLM çağrısı yok, para yok)
```

`dev-full` backend'i çalıştırmaz, yalnız container'ları. İlk `dev-full` BGE-M3
modelini indirir (~2.5 GB) — embedding container'ı sağlıklı olana kadar skorlama
`WITHOUT_EMBEDDING` ağırlıklarıyla çalışır, yani **beklemek zorunda değilsin**;
sadece kalite düşer ve bu bilinçli (§ 28.4).

Hazır mı:

```bash
curl -s localhost:8080/actuator/health          # {"status":"UP"}
curl -s localhost:8080/api/v1/profile | head -c 300
```

## Swagger UI ile mi, curl ile mi

`http://localhost:8080/swagger-ui/index.html` — kimlik doğrulama gerekmediği için
**Try it out** doğrudan çalışır. Ama iki adım Swagger'da yanıltıcıdır:

| Adım | Swagger'da |
|---|---|
| `POST /generations`, `GET /jobs/{id}`, `/account/usage`, tüm profil uçları | ✅ rahatça |
| `GET /jobs/{id}/stream` (SSE) | ⚠️ **canlı akmaz.** Swagger yanıtın tamamını bekler; akış terminal olayda kapandığı için sonunda hepsini birden gösterir. Olayların **sırayla geldiğini** görmek istiyorsan `curl -N` |
| `GET /generations/{id}/download` | ✅ "Download file" bağlantısı çıkar; ama **iki PDF'i karşılaştırmak** (5. adım) kabuk ister |
| Kota / kill switch / kuyruk dayanıklılığı | ⚠️ env değişkeni, `psql` ya da `Ctrl-C` ister — Swagger'dan yapılamaz |

Yani: **akışı önce Swagger'dan sür, ilerlemeyi curl'den izle.** Aşağıdaki adımlar
her ikisi için de geçerli; Swagger'da olanı `→ Swagger` ile işaretli.

**Swagger'da izlenecek sıra** (en hızlı tur):

1. `GET /api/v1/account/usage` → sayaçlar ve `resetsAt`
2. `POST /api/v1/generations` → **202**, gövdeden `jobId`'yi kopyala
3. `GET /api/v1/jobs/{jobId}` → birkaç saniye içinde `completed`, `generationId` çıkar
4. `GET /api/v1/generations/{generationId}/download` → **Download file**
5. `GET /api/v1/profile/atoms` → bir `variantId` seç,
   `PATCH /api/v1/profile/atoms/{id}/variants/{variantId}` ile metnini değiştir
6. Aynı `download`'u tekrar → **PDF değişmemeli** (5. adımın asıl testi)
7. `GET /api/v1/account/usage` → `used` bir artmış olmalı

`POST /generations` gövdesi için Swagger'ın hazır örneği **yetmez** — ön kontrol
onu "ilan gibi okunmuyor" diye 422 ile reddeder (§ 18.1: en az 150 karakter,
40 kelime, iki sinyal kelimesi). Şunu yapıştır:

```json
{
  "jobDescription": "We are seeking a senior backend engineer to join our payments team.

Responsibilities: design and operate distributed services in Go, own the reliability of a high throughput ledger, and mentor other engineers as the team grows.

Requirements: several years of production experience with Go and PostgreSQL, comfort with observability tooling, and a track record of shipping. Preferred qualifications include Kubernetes and Terraform. Apply with a short note about the systems you have run.",
  "maxPages": 1
}
```

Ölçüldü: **202**, iş birkaç saniyede `completed`, indirme 21 KB'lik gerçek bir
PDF veriyor (`Content-Disposition: attachment`, dosya adı yalnız tarih).

## 1. Kota görünüyor mu (§ 44.1)

```bash
curl -s localhost:8080/api/v1/account/usage | jq
```

İki metrik de gelmeli, `used: 0`, `resetsAt` **UTC gece yarısı** (Türkiye'de
03:00). Bu, sınırın çarpılmadan önce görünmesi kararı.

## 2. İlana özel üretim — 202 (§ 35.3)

```bash
POSTING='We are seeking a senior backend engineer to join our payments team.
Responsibilities: design and operate distributed services in Go, own the
reliability of a high throughput ledger, and mentor other engineers.
Requirements: several years of production experience with Go and PostgreSQL,
comfort with observability tooling, and a track record of shipping. Preferred
qualifications include Kubernetes and Terraform. Apply with a short note.'

JOB=$(jq -nc --arg jd "$POSTING" '{jobDescription:$jd}' \
  | curl -s -X POST localhost:8080/api/v1/generations \
      -H 'Content-Type: application/json' -H 'Idempotency-Key: elle-test-1' \
      -d @- | tee /dev/stderr | jq -r .jobId)
```

Beklenen: **202**, gövdede `jobId`, `status: "queued"`, `streamUrl`.

## 3. SSE akışı (§ 30.6)

**İşi kuyruğa koyduktan hemen sonra** başka bir kabukta:

```bash
curl -N localhost:8080/api/v1/jobs/$JOB/stream
```

Görmen gerekenler:

```
id:1
event:phase
data:{"phase":"A","label":"generation.phase.ANALYSING","pct":10,"detail":""}
...
event:completed
data:{"generationId":"...","pageCount":1}
```

Sonra akış **kapanır**. Üç şeye dikkat:

- `label` bir **çeviri anahtarı**, cümle değil — metni frontend yazacak.
- Bağlanır bağlanmaz güncel durum geliyor, yani **iş bitmişken bağlansan bile**
  `completed` alırsın. Denemek için: iş bittikten sonra aynı komutu tekrar çalıştır.
- Terminal olaydan sonra akış kapanıyor; zaman aşımıyla değil.

## 4. Durum ucu — akışın geri düşüşü (EK D.6.4)

```bash
curl -s localhost:8080/api/v1/jobs/$JOB | jq
```

`completed` olduğunda `generationId` var, `error` yok. `failed` olsaydı tersi.

```bash
GEN=$(curl -s localhost:8080/api/v1/jobs/$JOB | jq -r .generationId)
```

## 5. İndirme — anlık görüntüden yeniden render (EK D.6.3)

```bash
curl -s -o cv.pdf -D - localhost:8080/api/v1/generations/$GEN/download | head -5
```

`Content-Type: application/pdf`, `Content-Disposition: attachment`. **Asıl test
bu:** bir maddeyi düzenle, sonra aynı indirmeyi tekrar yap — PDF **değişmemeli**,
çünkü üretim anındaki metinden render ediliyor.

```bash
# bir atomun metnini değiştir, sonra:
curl -s -o cv2.pdf localhost:8080/api/v1/generations/$GEN/download
cmp cv.pdf cv2.pdf && echo "aynı belge — doğru"
```

## 6. Idempotency (§ 30.7)

Aynı anahtarla tekrar gönder: **aynı `jobId`** dönmeli, ikinci iş oluşmamalı.

```bash
jq -nc --arg jd "$POSTING" '{jobDescription:$jd}' \
  | curl -s -X POST localhost:8080/api/v1/generations \
      -H 'Content-Type: application/json' -H 'Idempotency-Key: elle-test-1' \
      -d @- | jq -r .jobId          # 2. adımdaki ile aynı olmalı
```

## 7. Senkron ön kontroller (§ 35.3) — kuyruğa hiç girmemeli

```bash
# ilan gibi okunmayan metin
curl -s -X POST localhost:8080/api/v1/generations \
  -H 'Content-Type: application/json' -d '{"jobDescription":"beni işe alın"}' | jq
# → 422 UNPARSEABLE_JOB_DESCRIPTION, resolutions içinde continue_anyway

# kullanıcı ısrar ederse (EK D.6.1)
curl -s -X POST localhost:8080/api/v1/generations \
  -H 'Content-Type: application/json' \
  -d '{"jobDescription":"beni işe alın","acknowledgePreflight":true}' | jq
# → 202
```

## 8. Genel CV modu — aynı uç, ilan yok (§ 19.4)

```bash
curl -s -X POST localhost:8080/api/v1/generations \
  -H 'Content-Type: application/json' -d '{}' | jq
```

**202.** `POST /generations/general` artık **yok** (`B-022` kapandı); boş gövde
genel moddur ve `generations.job_description` NULL kalır.

## 9. Kota doluyor mu (§ 44.1)

Varsayılan 20/gün. Hızlı yol: sınırı düşürüp yeniden başlat.

```bash
QUOTA_GENERATIONS_PER_USER=2 make dev
```

Üçüncü istekte **429**, `params` içinde `metric` ve `resetsAt`, `Retry-After`
başlığı. `GET /account/usage` sayacı gösterir.

Başarısız bir üretimin hakkı **geri verilir** (§ 44.2) — kotayı doldurmadan
denemek için embedding/latex container'ını durdurup bir üretim yaptır, sonra
`usage` bak.

## 10. Kill switch (§ 44.3)

```bash
docker compose exec postgres psql -U atomcv -d atomcv -c \
  "INSERT INTO feature_flags (key, enabled) VALUES ('generation.new_requests', false)
   ON CONFLICT (key) DO UPDATE SET enabled = false;"
```

Yeni üretim: **503 `GENERATION_PAUSED`**. Ama **profil hâlâ okunur ve dışa
aktarılabilir** — asıl kontrol edilecek şey bu:

```bash
curl -s -o /dev/null -w '%{http_code}\n' localhost:8080/api/v1/profile      # 200
curl -s -o /dev/null -w '%{http_code}\n' "localhost:8080/api/v1/profile/export?format=json"  # 200
```

Geri aç:

```bash
docker compose exec postgres psql -U atomcv -d atomcv -c \
  "UPDATE feature_flags SET enabled = true WHERE key = 'generation.new_requests';"
```

## 11. Sağlayıcı fallback (§ 27.3)

`local-fake` tek sağlayıcı kullanır, yani zinciri elle bozmak gerekir:

```bash
LLM_CHAIN_CHEAP=yok-boyle-bir-saglayici,fake make dev
```

Log'da bilinmeyen id için uyarı, üretim yine de çalışır. Zincirde çalışan hiçbir
şey kalmazsa: **503 `ALL_PROVIDERS_UNAVAILABLE`**, ve iş **yeniden denenir**
(§ 30.5) — `jobs.attempts` artar.

## 12. Kuyruğun dayanıklılığı (§ 30.4)

Bir üretim koşarken backend'i `Ctrl-C` ile durdur, sonra tekrar başlat.

```bash
docker compose exec postgres psql -U atomcv -d atomcv -c \
  "SELECT status, attempts, locked_by FROM jobs ORDER BY created_at DESC LIMIT 3;"
```

İş `queued`'a dönmüş ve `locked_by` NULL olmalı — graceful shutdown kilitleri
geri veriyor. Vermeseydi zombi toplayıcı iki dakika sonra alırdı.

## 13. Maliyet ve anomali (§ 44.3)

```bash
docker compose exec postgres psql -U atomcv -d atomcv -c \
  "SELECT provider, model, input_tokens, output_tokens, cost_usd, outcome
   FROM llm_invocations ORDER BY created_at DESC LIMIT 5;"
```

`local-fake` altında `cost_usd` **0** olur (fake modelin fiyatı yok) ve bu
doğru — fiyatı bilinmeyen model sıfır eder, tahmin etmez;
`llm.unpriced_calls` sayacı artar. Gerçek fiyat görmek için `make record`
gerekir ve o para harcar.

Bütçe frenini denemek (para harcamadan):

```bash
docker compose exec postgres psql -U atomcv -d atomcv -c \
  "INSERT INTO llm_invocations (prompt_id, prompt_version, provider, model, cost_usd, outcome)
   VALUES ('job_analysis','v1','fake','fake-model', 999, 'success');"
```

Anomali geçişi 15 dakikada bir koşar; beklemek istemezsen sıklaştır:

```bash
sh ./gradlew bootRun --args='--spring.profiles.active=local,local-fake   --atomcv.anomaly.cron=0/20 * * * * *'
```

Sonra `feature_flags`'e bak: fren çekilmiş olmalı ve **kendiliğinden kalkmaz**
(§ 44.3 — sebebin giderildiğini zamanlanmış bir iş bilemez).

---

## Bilerek test edilemeyecek olanlar

- **Axiom'da loglar** — dataset Adım 3.1'de açılıyor. OTLP ihracatçısı bağlı ama
  `OTLP_URL` verilmeden kapalı.
- **Kullanıcı bazlı maliyet** — `llm_invocations.user_id` NULL; olay kullanıcıyı
  taşımıyor. Günlük toplam (bütçe freni) etkilenmiyor.
- **Anonim akış** — Aşama 3.
