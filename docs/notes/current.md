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
| `atoms.embedding` eşlenmemiş | 2.4 sonrası | Sağlayıcı indi; kalan yalnız `vector(1024)`'ün Hibernate eşlemesi. Kolon `V1`'de var, migration gerekmiyor |
| `ProfileRef.Scope` yalnız `PERSISTENT` | Aşama 3 | `EPHEMERAL`'ı anonim akıştan önce eklemek, üretmenin denetimli yolu yokken sahiplik kontrolünün etrafından dolaşmanın yolu olurdu |
| `tags` / `atom_tags` entity'siz | Aşama 2 | Aşama 2 skorlamasından önce okuyan yok |
| `generations`'a yazan yok | Aşama 2 | Hat `GeneratedDocument` döndürüyor; kalıcı kayıt, kuyruk ve `GET /generations/{id}/download` birlikte gelir (`spec/08b-api-contract.md` § D.6.3) |
| `PipelineError`'da eksik durumlar | Aşama 2 | Fazıyla gelir. `AllProvidersUnavailable` (2.2) ve `UnparseableJobDescription` (2.3) indi; kalanlar erken eklenirse `params` tahmin edilmiş olur |
| ATS raporu ve `FitReport` yok | Aşama 2 | Biri PDF metin çıkarımı, diğeri Faz A istiyor (`spec/06-pipeline-d-g.md` § 23) |
| `UserScopedRepository`'de `findAll` yok | — | Bölüm 41.2 parçacığı `findByUserId` çağırıyor, o da `JpaRepository`'de yok. Alt sınıflar kendi daraltılmış bulucularını ekler |

## Isırmadan önce ele alınacak iki bulgu

**Atomsuz entry sayfaya hiç çıkmıyor.** Seçim atom atom çalışıyor, dolayısıyla
altında madde olmayan bir diploma satırı aday bile değil. Golden fixture'lar her
eğitim entry'sine bir atom vererek etrafından dolaşıyor; gerçek çözüm
`spec/05-pipeline-a-c.md` § 20.2'nin modelini değiştiriyor ve Aşama 2'ye ait.

**Eşitlik atom id'siyle bozuluyor, id'ler her içe aktarımda yeniden üretiliyor.**
Aynı skora *ve* aynı maliyete sahip iki atom, aynı içerik yeniden içe
aktarıldığında yer değiştiriyor — Aşama 3'teki anonim profil devralma tam olarak
bunu yapacak. İçerikten türetilen bir eşitlik bozucu düzeltir; bilinçli karar
verilmeli.

## Aşama 1'den devredilen açık kararlar

- **Üretimde migration nasıl çalışacak.** `spec/11-operations.md` § 47 dağıtım
  öncesi `--spring.flyway.migrate-only=true` ile bir adım gösteriyor; bu gerçek
  bir Spring Boot property'si değil. Flyway şu an üretimde de açılışta çalışıyor.
- **CI'a imaj taraması.** Dockerfile artık var, yani Trivy'nin yanlış-yapılandırma
  taraması onu görüyor; ama derlenen imaj taranmıyor. Bu, CI'da bir build (birkaç
  GB) istiyor ve registry push'uyla birlikte gelir.
- **Spotless eklenecek mi.** `spec/11-operations.md` § 47.1 `spotlessCheck`
  çalıştırıyor, ama yapılandırılmış bir formatlayıcı yok — bugün CI'da hiç
  biçim kapısı yok.

## Aşama 2'ye taşınanlar

- ~~**Kota sıfırlaması bir zaman dilimi istiyor.**~~ **Kapandı (F-007):** gün
  sınırı **UTC**, `resetsAt` telde offset'li mutlak bir ISO-8601 anı. Karar
  `spec/08b-api-contract.md` EK D.6.5'e yazıldı, `period` kolonunun yorumu da
  `spec/04-data-model.md`'de. Sayacı yazan kod bunu **`LocalDate.now(ZoneOffset.UTC)`**
  ile okumak zorunda: `LocalDate.now()` bu makinede UTC+3 döner ve testler
  günün 21:00'inden sonra geçmeye başlar — bir sonraki adımın tuzağı bu.
- ~~Üç `local-*` profil dosyası yok.~~ **Kapandı** (Adım 2.2). Davranış
  `spec/13-development.md` § 54.2'de, sessiz yutulmaya karşı koruma
  `LocalProfileConfigTest`'te — negatif kontrolü içinde.
- ~~`archunit.properties`'teki `failOnEmptyShould`.~~ **Kapandı, ve madde
  yanlıştı:** öyle bir dosya yoktu, tek kurala verilmiş bir
  `allowEmptyShould(true)` vardı; gerekçesi Adım 1.4'te doldu, kaldırıldı.
  İlk sondanın yanlış geçmesi `CLAUDE.md` · Testing Requirements'a yazıldı.

## Aşama 3'e taşınanlar

- **`CREATE UNIQUE INDEX ON jobs (user_id, idempotency_key)` anonim istekleri
  tekilleştirmiyor.** Orada `user_id` NULL ve Postgres NULL'ları birbirinden
  farklı sayıyor, yani aynı anahtar ikinci bir iş yaratıyor.
  `COALESCE(user_id::text, anon_session_id)` üzerinden anahtarlayan bir migration
  gerekiyor — ertelendi, çünkü anonim akışın kuyruğu kullanıp kullanmayacağı hâlâ
  açık (`STATUS.md` · açık kararlar).
- **Anonim TTL etkinlikle kayıyor**, dolayısıyla kullanıcıya görünen metin "iki
  saat" değil "son etkinliğinden iki saat sonra" demeli. `spec/01-foundations.md`
  § 9 ve ürün dokümanı hâlâ mutlak ifadeyi taşıyor; ikisi de güncellenmeli ve
  metnin sahibi frontend.

## Aşamasız açık

**V1 bazı enum benzeri kolonlara `CHECK` koyuyor** (`sections.layout`,
`applications.status`, `jobs.status`), bazılarını yorum olarak bırakıyor
(`sections.kind`, `atoms.kind`, `generations.status`, `jobs.type`,
`llm_invocations.outcome`). Bu, `spec/04-data-model.md` § 13'ü bilinçli olarak
yansıtıyor; kısıt uydurmuyor. Eksikleri sonradan eklemek ucuz bir migration,
çünkü `CHECK` tabloyu yeniden yazmıyor.

---

## Aşama 2 kayıtları

**`F-001`…`F-006` kapandı ve kuralları `spec/08-api.md` § 35.2 / § 35.6'ya
işlendi** (toplu update `@Version`, entry tarih aralığı, yazma yanıtındaki
`completeness`, `sourceLanguage` zorunluluğu, `params.fields`, sözcükleme
silmenin iki ayrı reddi). Buradan silindiler; **ikisi Aşama 2'de tekrar
edeceği için duruyor:**

- **Toplu JPQL `update` `@Version`'ı atlar.** Kota sayaçları (2.7) ve
  `generations` durum geçişleri (2.6) de toplu update isteyecek ve aynı
  sessizlikle bayat etag üretecek. `update versioned` — ama *hepsini*
  sürümlemek promote'u kırıyor, bu kasıtlı denendi ve dört test düştü.
- **Okuma, yakalanmak istenen bayatlığı onarır.** `completeness` testi
  düzeltmesiz de geçiyordu çünkü etag'i almak için yapılan `GET` saklı rakamı
  tazeliyordu. Kota sayaçları ve `generations` durumları da aynı şekilde
  okumayla kendini onaran yüzeyler olacak — etag'i **önceki yazmanın
  yanıtından** al.


**Sapma — `Result` ve `PipelineError` `shared/error/`'a taşındı.** Bölüm 27.1
`LlmProvider.callStructured`'ı `Result<LlmResponse<T>>` döndürüyor; `generation`
de Faz A/B ile `llm`'e bağımlı olacak, yani ikisi birlikte `noCycles`'ı düşüren
bir döngü. Tahmin değil: § 25.2 `AllProvidersUnavailable` ve
`EmbeddingUnavailable`'ı zaten `PipelineError` durumu sayıyor, yani iki modül de
tipi görmek zorunda. Bölüm 10.1 `PipelineError`'ı **zaten** `shared/error/`'da
gösteriyordu; taşımayı engelleyen tek şey Aşama 1'de eklenen
`CompilationFailed(CompilationException.Kind, …)` imzasıydı — `shared` hiçbir iş
modülüne bakamaz (Bölüm 10.2, kural 4).

Enum `shared/error/CompilationFailureKind`'a çıkarıldı; `CompilationException`
onu import ediyor (`compilation → shared` serbest). § 25.2'nin kendi imzası
`(String detail, boolean rawSourceAvailable)` — o yola gitmek retry kararının
okuduğu ayrımı düz metne çevirirdi, bu yüzden enum korundu. `shared`'ın
bağımsızlığı kasıtlı bir ihlalle doğrulandı: `shared.error`'dan
`compilation`'a bir referans konduğunda `sharedIsIndependent` **ve** `noCycles`
birlikte düşüyor — ikincisi taşımanın gerekçesini birebir gösteriyor.

Şimdi taşındı çünkü **ucuzdu**: iki tipin 11 çağrı dosyası vardı ve hepsi
`generation` ile `compilation` içindeydi. Faz A yazıldıktan sonra değil.

**Sapma — `LlmProvider` `Result` değil `LlmOutcome` döndürüyor; `JsonSchema`
eklendi.** Kalıcı olduğu için `spec/07-subsystems.md` § 27.1 ve § 27.3'e
yazıldı, buradan silindi. Özeti: hata kataloğunda tek sağlayıcının
başarısızlığı için kod yok, dolayısıyla o `PipelineError` olamaz.
`PipelineError` bu adımda tek durum kazandı — `AllProvidersUnavailable(tried)`,
§ 25.2 ve katalogda birebir yazılı.

**Düzeltme — § 18.1'in üç çıkış yolundan birinin adı yoktu.** Sözlük
`continue_anyway` ile onuncu değerini kazandı; kural EK D.6.1 ve § 18.1'e
yazıldı, frontend maddesi `B-037`. Buraya not düşülmesinin nedeni **sondanın
kendiliğinden çalışması**: eklemeden önce `theActionVocabularyIsTheAgreedEight`
düştü, yani kapalı sözlüğün kapalılığı gerçekten korunuyor. **İki ayrı yerde
düştü:** birim testi enum'u, `OpenApiSchemaIT` ise *yayımlanan şemayı* ölçüyor —
ikincisi değerin frontend'in üretilen tipine gerçekten girdiğini kanıtlıyor,
birincisi yalnız enum'da olduğunu. Bir sonraki sözlük genişletmesinde ikisi de
düşecek ve `B-nnn` yazmayı hatırlatacak.

**Ekleme — ön kontrolün dört ret sebebi telde ayrışmıyor.** Katalog tek kod
yayımlıyor, ayrım `Verdict` enum'uyla içeride kalıyor: "ilan reddedildi"
metriği hiçbir şey söylemez, "düşük entropiden reddedildi" sezgisel kuralın
gözden geçirilmesi gerektiğini söyler. Kural ve sıralama (uzunluk entropiden
önce) § 18.1'e, enum toleransı § 18.2'ye yazıldı.

**Ekleme — eksik nesneler boş nesne olarak okunuyor.** `role`, `company` ve
`experienceYears` yoksa `null` değil boş karşılıkları oluyor; listeler zaten
öyleydi. Sebebi `embeddingTarget()`: `role.title()`'a bakıyor ve modelin
`role`'ü hiç yazmadığı bir cevap onu `NullPointerException` ile düşürürdü.
Kapı zaten başlıksız bir analizi kendi ölçütleriyle reddediyor, yani ayrıca
null kontrolü yapmanın kimseye faydası yok.

**Ekleme — boş ilanla `JobAnalysisPhase.analyse` çağırmak programlama hatası.**
Genel CV modu buraya hiç gelmez; `Result.err` yerine `IllegalArgumentException`
çünkü bu kullanıcının yapabileceği bir şey değil, çağıranın yanlış dallanması.

**Ekleme — Redis cache'in dört kararı § 18.6'ya yazıldı:** anahtarın prompt
sürümünü taşıması, yalnız kapıdan geçenin yazılması, arızanın ıskalamaya
dönüşmesi ve sıranın ön kontrol → cache → çağrı olması. Buraya not düşülen tek
şey **sondanın nasıl kurulduğu**: fail-open iki katmanda birden ölçülüyor —
birim testi mock'a fırlattırıyor, entegrasyon testi *gerçekten kapalı bir
porta* bağlanıyor. İkincisi olmasa "catch bloğu var" ölçülmüş olurdu, "Lettuce
gerçekten o istisnayı atıyor" değil. `try/catch` kaldırıldığında ikisi de
düştü.

**Düzeltme — Adım 2.4'ün "pgvector kolonu + migration" maddesi yanlıştı.**
Kolon `V1`'de zaten var; eksik olan **Hibernate eşlemesi** ve migration yazmak
mutlak kural 2'yi ihlal etmeden mümkün değil. `spec/14-build-guide.md`'de
düzeltildi. Eşleme bir sonraki dilime kaldı.

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
| Fake embedding'in tohumu ve birim uzunluk, profil ayrımı | `13-development.md` § 54.2 |
| Sağlık kontrolünün `/health` olması, kısmi cevabın reddi | `07-subsystems.md` § 28.4 |

**Aşama 2.5'e taşınan:** § 28.4'ün ağırlık yeniden dağıtımı — `ScoringWeights`
henüz yok. `isHealthy()` bu dilimde indi, onu okuyan taraf Faz B ile geliyor.
