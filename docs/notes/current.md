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
| `generations`'a yazan yok | Aşama 2 | Hat `GeneratedDocument` döndürüyor; kalıcı kayıt, kuyruk ve `GET /generations/{id}/download` birlikte gelir (`spec/08b-api-contract.md` § D.6.3) |
| `PipelineError`'da eksik durumlar | Aşama 2 | Fazıyla gelir. `AllProvidersUnavailable` (2.2) ve `UnparseableJobDescription` (2.3) indi; kalanlar erken eklenirse `params` tahmin edilmiş olur |
| ATS raporu ve `FitReport` yok | Aşama 2 | Biri PDF metin çıkarımı, diğeri Faz A istiyor (`spec/06-pipeline-d-g.md` § 23) |
| `UserScopedRepository`'de `findAll` yok | — | Bölüm 41.2 parçacığı `findByUserId` çağırıyor, o da `JpaRepository`'de yok. Alt sınıflar kendi daraltılmış bulucularını ekler |

## Isırmadan önce ele alınacak iki bulgu

**Atomsuz entry sayfaya hiç çıkmıyor.** Seçim atom atom çalışıyor; altında madde
olmayan bir diploma satırı aday bile değil. Golden fixture'lar her eğitim
entry'sine bir atom vererek dolanıyor; gerçek çözüm § 20.2'nin modelini
değiştiriyor.

**Eşitlik atom id'siyle bozuluyor, id'ler her içe aktarımda yeniden üretiliyor.**
Aynı skor *ve* aynı maliyetteki iki atom yer değiştiriyor — Aşama 3'ün anonim
profil devralması tam olarak bunu yapacak. İçerikten türetilen bir bozucu
düzeltir; karar verilmeli.

## Aşama 1'den devredilen açık kararlar

- **Üretimde migration nasıl çalışacak.** `spec/11-operations.md` § 47 dağıtım
  öncesi `--spring.flyway.migrate-only=true` ile bir adım gösteriyor; bu gerçek
  bir Spring Boot property'si değil. Flyway şu an üretimde de açılışta çalışıyor.
- **CI'a imaj taraması.** Trivy Dockerfile'ı görüyor, derlenen imajı görmüyor;
  CI'da bir build (birkaç GB) istiyor, registry push'uyla birlikte gelir.
- **Spotless eklenecek mi.** § 47.1 `spotlessCheck` çalıştırıyor ama
  yapılandırılmış formatlayıcı yok — bugün CI'da biçim kapısı yok.

## Aşama 2'ye taşınanlar

- ~~**Kota sıfırlaması bir zaman dilimi istiyor.**~~ **Kapandı (F-007):** gün
  sınırı **UTC**, `resetsAt` telde offset'li mutlak bir ISO-8601 anı. Karar
  `spec/08b-api-contract.md` EK D.6.5'e yazıldı, `period` kolonunun yorumu da
  `spec/04-data-model.md`'de. Sayacı yazan kod bunu **`LocalDate.now(ZoneOffset.UTC)`**
  ile okumak zorunda: `LocalDate.now()` bu makinede UTC+3 döner ve testler
  günün 21:00'inden sonra geçmeye başlar — bir sonraki adımın tuzağı bu.
- ~~Üç `local-*` profil dosyası yok.~~ **Kapandı** (2.2) — `spec/13-development.md`
  § 54.2, koruması `LocalProfileConfigTest`'te.
- ~~`archunit.properties`'teki `failOnEmptyShould`.~~ **Kapandı, madde yanlıştı:**
  öyle bir dosya yoktu. İlk sondanın yanlış geçmesi `CLAUDE.md`'ye yazıldı.

## Aşama 3'e taşınanlar

- **`jobs (user_id, idempotency_key)` anonim istekleri tekilleştirmiyor** —
  `user_id` NULL ve Postgres NULL'ları farklı sayıyor. `COALESCE`'lı migration
  gerekiyor; ertelendi (kayıt EK D.6.5'te).
- **Anonim TTL etkinlikle kayıyor**, metin "son etkinliğinden iki saat sonra"
  demeli. `spec/01-foundations.md` § 9 ve ürün dokümanı güncellenmeli; metnin
  sahibi frontend.

## Aşamasız açık

**V1 bazı enum benzeri kolonlara `CHECK` koyuyor** (`sections.layout`,
`applications.status`, `jobs.status`), bazılarını yorum olarak bırakıyor
(`sections.kind`, `atoms.kind`, `generations.status`, `jobs.type`,
`llm_invocations.outcome`). Bu, `spec/04-data-model.md` § 13'ü bilinçli olarak
yansıtıyor; kısıt uydurmuyor. Eksikleri sonradan eklemek ucuz bir migration,
çünkü `CHECK` tabloyu yeniden yazmıyor.

---

## Aşama 2 kayıtları

**`F-001`…`F-006` kapandı** (§ 35.2 / § 35.6). **İkisi ileriye dönük:**

- **Toplu JPQL `update` `@Version`'ı atlar.** 2.7'nin kota sayaçları toplu
  update isteyecek ve aynı sessizlikle bayat etag üretecek. `update versioned` —
  ama *hepsini* sürümlemek promote'u kırıyor, denendi, dört test düştü.
  (2.6'da ısırmadı: `jobs`'ta da `generations`'ta da `version` kolonu yok.)
- **Okuma, yakalanmak istenen bayatlığı onarır.** `completeness` testi
  düzeltmesiz de geçiyordu, çünkü etag'i alan `GET` saklı rakamı tazeliyordu.
  Etag'i **önceki yazmanın yanıtından** al.

**Düzeltme — build guide iki kez "migration" dedi, ikisi de yanlıştı:** 2.4'ün
pgvector kolonu da 2.6'nın `jobs` tablosu da `V1`'de vardı. Üçüncüsünü görürsen
**önce `V1`'e bak**.

**`continue_anyway` sözlüğe girdi** (§ 18.1, EK D.6.1) — frontend maddesi
`B-037`, hâlâ açık.

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
| Kuyruk canlılığı | `SKIP LOCKED` kaldırıldı | *yalnız* bloklanma testi — bkz. 2.6 |
| Graceful shutdown | `releaseLocks` atlandı | 1 test |
| Retry backoff | `runAfter` = şimdi | 1 test |

**Ekleme — `atoms.embedding` eşlendi**; `hibernate-vector`'ün sürümü elle
sabitlendi (Boot'un BOM'u o modülü yönetmiyor). **Düzeltme —
`ddl-auto=validate` vektör boyutunu denetlemiyor**; ders `CLAUDE.md`'de.

---

## Adım 2.5 kapanışı

**Düzeltme — keyword bileşeni tag bileşeninin kopyasıydı.** `atom.tags()`
okuyordu, ama `jdTags` ilanın keyword'lerini zaten içeriyor: tek sinyal 0.35
ağırlıkla iki kez sayılıyor, atomun kendi metni hiç okunmuyordu. Ölü duran
`titleTokens`, `contentTokens` adıyla bileşene bağlandı; kural § 19.2'ye yazıldı.

**Ekleme — `atom_tags`'te `profile_id` yok, kapsamlama join'den geçiyor.**
`AtomTag`, `ProfileOwned`'ı uygulayamayan tek profil satırı. Etiketler
`ProfileAssembler`'a **beşinci sorgu olarak eklenmedi** (§ 52.2 dört diyor);
beşinciyi yalnız ilan modu ödüyor.

**Ekleme — kapıların sırası maliyete göre.** Profil ön kontrolü bedava ve önce,
Faz A ölçümden önce. İkisinin de testi var: sırayı bozmak ne derlemeyi kırar ne
çıktıyı değiştirir, yalnız para harcatır.

**Aşama 2.6'ya taşınan — § 19.4'ün ikincil kriterleri ilan modunda okunmuyor.**
Bölüm "yakın skorlu atomlar arasında ve genel CV modunda" diyor; bugün yalnız
ikincisi var, ilan modunda eşitlik doğrudan atom id'siyle bozuluyor. "Yakın"ın
ne demek olduğu tanımlı değil ve tanımlamak `RelevanceScorer`'ın sözleşmesini
değiştiriyor — uydurulmadı, sorulacak.

---

## Adım 2.6 kayıtları — kuyruk

**Düzeltme — sondam yanlış geçti.** `SKIP LOCKED` kaldırıldı, 14 test de geçti:
READ COMMITTED'de düz `FOR UPDATE` kilidi bekleyip yüklemi yeniden
değerlendiriyor, yani mükerrerliği ölçen test o cümleyi hiç ölçmüyordu. Gerçek
fark **canlılık**; onu ölçen test bir kilidi açık tutup claim'in *hemen* boş
dönmesini bekliyor ve ihlalde düşüyor. Kural § 30.2'de, genel ders
`CLAUDE.md`'de.

**Ekleme — kuyruğun iki okuyucusu ayrı tip** (§ 30.2). ArchUnit `..jobs..`'a
kendi satırını kazandı.

**Ekleme — toplayıcının iki kuralı ve backoff'un taşması § 30.4-30.5'e yazıldı:**
deneme hakkı geri verilmiyor, hakkı bitmiş iş `failed`'e alınıyor, ve
`2^attempts` 63'te `long`'u taşırdığı için üs kaydırmadan önce sınırlanıyor.

**Düzeltme — CI'da düşen test, yerelde geçen kod.** `TagRepository.labelsByAtom`
sorgunun sırasını `Set.copyOf` ile atıyordu; JDK'nın değişmez koleksiyonları
**her JVM çalıştırmasında farklı** sırayla dolaşıyor (aynı üç elemanlı kümeyi üç
kez ölçtüm, üç farklı sıra). Yerelde tuttu, runner'da tutmadı. Aynı tuzağın
ikinci örneği `Job`'un üç JSONB kolonundaydı — `JobWorker` hata haritasını
`code` başa gelsin diye sıralı kuruyor, `Map.copyOf` onu bozuyordu. İkisi de
`Collections.unmodifiable*` + `Linked*`'e çevrildi, kural `CLAUDE.md`'ye yazıldı.

**Kalan (Adım 2.6, ikinci dilim):** `generations` kaydı, `POST /generations` +
202, SSE kaydı ve ucu, `GET /jobs/{id}`, idempotency, download, ve
`POST /generations/general`'ın kaldırılması. Frontend'in senkronizasyon noktası
orası (`STATUS.md`).
