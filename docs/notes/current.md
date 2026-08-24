# İnşa Notları — Aktif

> Kural: bu dosya **200 satırı geçmez**. Aşama bitince `archive/`'a taşınır, boş başlanır.
> Kayıt tipleri: **Sapma** (doküman başka diyor) · **Ekleme** (doküman sessiz) · **Düzeltme** (doküman yanlış).
>
> Bir sapma kalıcıysa `spec/`'e işlenir ve buradan silinir.

**Aktif aşama:** Aşama 2 — ilana özel üretim.
**Plan:** `spec/14-build-guide.md` § XI-A.5, Adım 2.1-2.7 (LLM sağlayıcı hesapları →
gateway → Faz A → embedding → Faz B → kuyruk ve SSE → kota ve maliyet); aynı aşamanın
gerekçesi `spec/13-development.md` § 55, kapanış kontrol listesi § XI-A.5 sonunda.
Aşama 1'in kayıtları `archive/stage-1.md`'de, Aşama 0 ve öncesi `archive/stage-0-1.md`'de.

---

## Aşama 1'den taşınan kısıtlar

Bunlar sapma değil, **bilinçli eksikler**. Sorulmadan "düzeltilmez" — her biri
bir sonraki aşamanın işi ve erken doldurmak kararı yanlış yerden verdirir.

| Eksik | Ne zaman | Neden şimdi değil |
|---|---|---|
| `ProfileRef.Scope` yalnız `PERSISTENT` | Aşama 3 | `EPHEMERAL`'ı anonim akıştan önce eklemek, üretmenin denetimli yolu yokken sahiplik kontrolünün etrafından dolaşmanın yolu olurdu |
| `PipelineError`'da eksik durumlar | Aşama 2 | Fazıyla gelir. `AllProvidersUnavailable` (2.2) ve `UnparseableJobDescription` (2.3) indi; kalanlar erken eklenirse `params` tahmin edilmiş olur |
| ATS raporu ve `FitReport` yok | Aşama 2 | Biri PDF metin çıkarımı, diğeri Faz A istiyor (`spec/06-pipeline-d-g.md` § 23) |
| `UserScopedRepository`'de `findAll` yok | — | § 41.2 parçacığı `findByUserId` çağırıyor, o da `JpaRepository`'de yok. Alt sınıflar kendi bulucularını ekler |

## Isırmadan önce ele alınacak iki bulgu

**Atomsuz entry sayfaya hiç çıkmıyor.** Seçim atom atom çalışıyor; altında madde
olmayan bir diploma satırı aday bile değil. Gerçek çözüm § 20.2'nin modelini
değiştiriyor.

**Eşitlik atom id'siyle bozuluyor, id'ler her içe aktarımda yeniden üretiliyor.**
Aynı skor *ve* maliyetteki iki atom yer değiştiriyor — Aşama 3'ün anonim profil
devralması tam olarak bunu yapacak. İçerikten türetilen bir bozucu düzeltir.

## Aşama 1'den devredilen açık kararlar

- **Üretimde migration nasıl çalışacak.** `spec/11-operations.md` § 47 dağıtım
  öncesi `--spring.flyway.migrate-only=true` ile bir adım gösteriyor; bu gerçek
  bir Spring Boot property'si değil. Flyway şu an üretimde de açılışta çalışıyor.
- **CI'a imaj taraması.** Trivy Dockerfile'ı görüyor, derlenen imajı görmüyor;
  CI'da bir build (birkaç GB) istiyor, registry push'uyla birlikte gelir.
- **Spotless eklenecek mi.** § 47.1 `spotlessCheck` çalıştırıyor ama
  yapılandırılmış formatlayıcı yok — bugün CI'da biçim kapısı yok.

## Aşama 2'ye taşınanlar — hepsi kapandı, biri tuzağıyla duruyor

**Kota gün sınırı UTC** (`F-007`, EK D.6.5). Sayacı yazan kod bunu
**`LocalDate.now(ZoneOffset.UTC)`** ile okumak zorunda: `LocalDate.now()` bu
makinede UTC+3 döner ve testler günün 21:00'inden sonra geçmeye başlar —
**Adım 2.7'nin tuzağı bu.**

## Aşama 3'e taşınanlar

- **`jobs (user_id, idempotency_key)` anonim istekleri tekilleştirmiyor** —
  `user_id` NULL ve Postgres NULL'ları farklı sayıyor. `COALESCE`'lı migration
  gerekiyor; ertelendi (kayıt EK D.6.5'te).
- **Anonim TTL etkinlikle kayıyor**, metin "son etkinliğinden iki saat sonra"
  demeli. `spec/01-foundations.md` § 9 ve ürün dokümanı güncellenmeli; metnin
  sahibi frontend.

## Aşamasız açık

**V1 bazı enum benzeri kolonlara `CHECK` koyuyor, bazılarına koymuyor.** § 13'ü
bilinçli yansıtıyor; kısıt uydurmuyor. Eksikleri sonradan eklemek ucuz bir
migration — `CHECK` tabloyu yeniden yazmıyor.

---

## Aşama 2 kayıtları

> **Adım 2.1-2.5'in kayıtları `archive/stage-2-steps-1-5.md`'ye taşındı**
> (2026-08-24). Kalıcı kararlar `spec/`'te; aşağıdaki iki tablo canlı indeks ve
> burada kalıyor. **İleriye dönük tek madde:** toplu JPQL `update` `@Version`'ı
> atlar — 2.7'nin kota sayaçları bayat etag üretecek, etag'i **önceki yazmanın
> yanıtından** al. (2.6'da ısırmadı: `jobs`'ta da `generations`'ta da `version`
> kolonu yok.)

**Spec'e promote edilen kararlar.** Kalıcı oldukları için `spec/`'te
duruyorlar; burada yalnız nerede oldukları:

| Konu | Nerede |
|---|---|
| Fixture'ların dosya sisteminde olması, adının hash olması, `synthesize` bayrağı | `13-development.md` § 54.2 |
| A/B kovasının CRC32 olması | `12-quality.md` § 53.3 |
| `schema-retries`, bilinmeyen sağlayıcı id'si, env-driven zincir | `07-subsystems.md` § 27.3 |
| `structured-output`'un tespit değil yapılandırma olması | `07-subsystems.md` § 27.2 |
| `local-fake`'in zinciri override etmesi | `13-development.md` § 54.2 |
| Prompt/fence bölünmesi, sağlayıcı arızasının çevrilmemesi, kapı sırası | `05-pipeline-a-c.md` § 18.3-18.4 |
| Cache anahtarının prompt sürümü taşıması, fail-open, sıra | `05-pipeline-a-c.md` § 18.6 |
| Faz B'nin benzerlik, ölçekleme ve `skillOverlap` kararları | `05-pipeline-a-c.md` § 19.2 |
| Fake embedding'in tohumu ve birim uzunluk, profil ayrımı | `13-development.md` § 54.2 |
| Sağlık kontrolünün `/health` olması, kısmi cevabın reddi | `07-subsystems.md` § 28.4 |
| `LlmProvider`'ın `LlmOutcome` döndürmesi, `JsonSchema` | `07-subsystems.md` § 27.1 |
| Ön kontrolün dört ret sebebinin telde ayrışmaması, sıralama | `05-pipeline-a-c.md` § 18.1 |
| `Result`/`PipelineError`'ın `shared/error`'da olması | `03-architecture.md` § 10.1 |
| Keyword bileşeninin atomun sözcüklerini okuması, öbek kuralı | `05-pipeline-a-c.md` § 19.2 |
| `isHealthy()`'nin sinyal olması, iki katmanlı geri çekilme | `07-subsystems.md` § 28.4 |
| `cvLanguage: "auto"`'nun iki moddaki anlamı | `04-data-model.md` § 14.3 |

**Kasıtlı ihlalle doğrulanmış sondalar.** Her biri bir kez düşürüldü; hangisinin
neyi gerçekten ölçtüğü buradan okunur.

| Sonda | İhlal | Ne düştü |
|---|---|---|
| `shared`'ın bağımsızlığı | `shared.error` → `compilation` referansı | `sharedIsIndependent` + `noCycles` |
| Kapalı `action` sözlüğü | onuncu değer eklendi | birim testi **ve** `OpenApiSchemaIT` |
| Redis fail-open | `try/catch` kaldırıldı | birim testi + *gerçekten kapalı porta* bağlanan IT |
| `atoms.embedding` eşlemesi | `@JdbcTypeCode` kaldırıldı | `AtomEmbeddingMappingIT`'in altı testi |
| Faz B determinizmi | eşitlik bozucusu kaldırıldı | giriş sırası çıkışa sızdı |
| Keyword kaynağı | `contentTokens` → `tags` | 1 test |
| Etiket kapsamlaması | `where tag.profileId` → totoloji | 2 test |
| § 28.4 geri çekilmesi | sağlık kontrolü + catch silindi | 3 test |
| `..jobs..` ham repository | `JobJpaRepository` public + çağrı | ArchUnit |
| `..generation..` ham repository | `GenerationJpaRepository` public + çağrı | 2 ArchUnit kuralı |
| Anlık görüntü gidiş-dönüşü | özelleştirme serialize'dan gizlendi | 1 IT |
| `..api..` → `JobQueue` | controller'a kapsamsız okuma | ArchUnit + çapraz kullanıcı IT'si |
| Idempotency | anahtar araması silindi | 1 test |
| Kuyruk canlılığı | `SKIP LOCKED` kaldırıldı | *yalnız* bloklanma testi — bkz. 2.6 |
| Graceful shutdown | `releaseLocks` atlandı | 1 test |
| Retry backoff | `runAfter` = şimdi | 1 test |

**Ekleme — `atoms.embedding` eşlendi**; `hibernate-vector`'ün sürümü elle
sabitlendi (Boot'un BOM'u o modülü yönetmiyor). **Düzeltme —
`ddl-auto=validate` vektör boyutunu denetlemiyor**; ders `CLAUDE.md`'de.

---

---

## Adım 2.6 kayıtları — kuyruk

**Düzeltme — sondam yanlış geçti.** `SKIP LOCKED` kaldırıldı, 14 test de geçti:
düz `FOR UPDATE` kilidi bekleyip yüklemi yeniden değerlendiriyor. Gerçek fark
**canlılık**; onu ölçen test bir kilidi açık tutup claim'in *hemen* boş
dönmesini bekliyor. Kural § 30.2'de, ders `CLAUDE.md`'de.

**Ekleme — kuyruğun iki okuyucusu ayrı tip** (§ 30.2); ArchUnit `..jobs..` ve
`..generation..` için kendi satırlarını kazandı.

**Ekleme — toplayıcının iki kuralı ve backoff'un taşması § 30.4-30.5'te:** hak
geri verilmiyor, hakkı bitmiş iş `failed`'e gidiyor, üs kaydırmadan sınırlanıyor.

**Düzeltme — CI'da düşen test, yerelde geçen kod.** `Set.copyOf`/`Map.copyOf`
**her JVM çalıştırmasında farklı** sırayla dolaşıyor (üç ölçüm, üç sıra). İki
yerde ısırdı: `TagRepository.labelsByAtom` sorgunun sırasını atıyordu, `Job`'un
üç JSONB kolonu da `JobWorker`'ın sıralı kurduğu hata haritasını bozuyordu.
İkisi de `Collections.unmodifiable*` + `Linked*`; kural `CLAUDE.md`'de.

**Karar — Aşama 2'de PDF baytı saklanmıyor** (2026-08-24, EK D.6.3). R2 Adım
3.1'de; indirme `selection_state`'ten yeniden render ediyor. Devredilen
"`generations`'a yazan yok" kısıtı kapandı. **Sapma:** anlık görüntü
`customizationId` değil özelleştirmenin kendisini taşıyor (§ 14.5) — işaret
edilecek satır yok, hiçbir şeye çözülen id anlık görüntüyü işe yaramaz kılardı.

**Düzeltme — `"\s+"` tek ters bölüyle yazılmıştı.** Java 15'ten beri `\s`
geçerli bir string kaçışı ve **tek boşluk** demek: desen sessizce "boşluk
dizileri"ne daralmış, satır sonları normalleştirmeden sağ çıkmış, aynı ilanın
PDF'ten ve tarayıcıdan gelen hâli farklı hash'lenmişti. Derleniyor ve doğru
görünüyor. Çıkarma sırasında yakalandı, çünkü artık iki çağıran aynı cevabı
istiyor: cache ve `generations.jd_hash`.

**Ekleme — kayıt yalnız belge çıkınca yazılıyor.** `selection_state` satırın
sebebi; seçimden önce düşen koşunun arızası işin üstünde yaşıyor.
`GenerationStatus`'ta bu yüzden `queued`/`running` yok — tek iş üzerinde iki
durum makinesi haber vermeden ayrışır.

**Ekleme — prompt sürümü *çalışan* sürüm.** `promptVersionFor` saf bir fonksiyon,
iki kez sorulunca aynı cevabı veriyor. Varsayılanı yazmak, alanın işe yaradığı
tek durumda — A/B deneyinde — yanlış olurdu.

**Bilinçli boşluk:** handler uçtan uca koşturulmadı — kuyruğa koyan bir şey yok
ve gerçek koşu LaTeX container'ı + `local-fake` istiyor. O test uca ait.

**Düzeltme — § 30.6'nın `label`'ı düz metin taşıyordu, § 35.4 ile çelişiyordu.**
Anahtar tarafı seçildi (`generation.phase.<FAZ>`); § 30.6 ve EK D.6.4
düzeltildi, frontend maddesi `B-038`. Tek dilde gönderilen bir cümle her yeni
dilde yeniden gönderilirdi ve ilerleme satırı üründe en çok görülen metin.

**Ekleme — ArchUnit'e üçüncü bir IDOR satırı.** `JobQueue` Spring Data değil,
yani mevcut iki kural onu görmüyor ve bir controller'da **derlenirdi**: sahiplik
kontrolü olmadan id ile iş okumak. Kural `..api..`'nin `JobQueue`'ya bağlanmasını
yasaklıyor; servisten kuyruğa koymak serbest kalıyor. Kasıtlı ihlalde hem kural
hem çapraz kullanıcı testi düştü.

**Ekleme — ilerleme satıra da yazılıyor, yalnız olaya değil.** Yeniden bağlanan
istemcinin yakalanacağı bir yer olmalı; kimseye gönderilmemiş olay yok olur
(EK D.6.4). Bedeli faz başına bir update.

**Kalan (Adım 2.6, üçüncü dilim):** SSE kaydı ve ucu (`streamUrl`),
`GET /generations/{id}/download` (anlık görüntüden yeniden render), ve
`POST /generations/general`'ın kaldırılması. Frontend'in senkronizasyon noktası
orası (`STATUS.md`, `B-022`).
