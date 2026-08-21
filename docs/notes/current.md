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
| `atoms.embedding` eşlenmemiş | Aşama 2 | `vector(1024)`'ün Hibernate tipi yok ve embedding'i hesaplayan bir şey de yok |
| `ProfileRef.Scope` yalnız `PERSISTENT` | Aşama 3 | `EPHEMERAL`'ı anonim akıştan önce eklemek, üretmenin denetimli yolu yokken sahiplik kontrolünün etrafından dolaşmanın yolu olurdu |
| `tags` / `atom_tags` entity'siz | Aşama 2 | Aşama 2 skorlamasından önce okuyan yok |
| `generations`'a yazan yok | Aşama 2 | Hat `GeneratedDocument` döndürüyor; kalıcı kayıt, kuyruk ve `GET /generations/{id}/download` birlikte gelir (`spec/08b-api-contract.md` § D.6.3) |
| `PipelineError` yalnız üç durum | Aşama 2 | Kalanları erken eklemek, frontend'in mesajlarının isteyeceği `params`'ı tahmin etmek olurdu |
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

**Düzeltme — toplu JPQL `update` `@Version`'ı atlıyor.** Frontend'in F-001'i
`atom_variants`'ta yakaladı: promote'ta demote edilen satır değişiyor ama `version`'ı
sabit kalıyordu, çünkü demote tek satırlık bir `@Modifying` sorgu. `update versioned`
oldu. Kuralın kendisi `spec/08-api.md` § 35.6'ya yazıldı; buraya not düşülmesinin
nedeni **tekrar edecek olması** — Aşama 2'de kota sayaçları ve `generations` durum
geçişleri de toplu update isteyecek ve aynı sessizlikle etag üretecek.

Yanına iki şey: sorgu artık `and variant.isPrimary = true` taşıyor, çünkü hepsini
sürümlemek promote'u **tamamen kırıyor** (promote edilen satırın merge'ü kendi
bilmediği bir sürüme çarpıyor). Bu kasıtlı olarak denendi ve dört test düştü —
`spec/12-quality.md` § 51.4'ün istediği doğrulama.

**Ekleme — entry tarih aralığı sıralı olmak zorunda.** F-002; `endDate >= startDate`,
`PATCH`'te yamanın sonucuna karşı ölçülüyor. `spec/08-api.md` § 35.2'ye yazıldı.
Doküman sessizdi ve altındaki hiçbir katman ters aralığı reddetmiyordu.

**Düzeltme — yazma yanıtındaki `completeness` yazmadan öncesini taşıyordu.**
F-003. `ProfileService.replace()` rakamı hiç hesaplamıyordu; yalnız `readOwn()`
hesaplıyor. İki baş ucu da artık kaydetmeden önce yeniden hesaplıyor. Kural
`spec/08-api.md` § 35.6'ya yazıldı ve buraya not düşülmesinin nedeni **testin
kendisi**: `currentEtag()` bir `GET` yapıyor ve `GET` saklı rakamı tazeliyor,
yani iki yazma arasındaki her okuma yakalanmak istenen bayatlığı onarıyor.
Tercihleri ölçen test düzeltmesiz de geçti; ETag'i önceki yazmanın yanıtından
alınca düştü. Aşama 2'de kota sayaçları ve `generations` durumları da aynı
şekilde okumayla kendini onaran yüzeyler olacak.

**Sapma — `PUT /profile` gövdesinde `sourceLanguage` artık zorunlu.** F-004.
Şema "replace" diyordu, davranış tek bu alanda "merge"dü. Kolon `NOT NULL`,
yani temizlenecek değer yok; `DEFAULT`'a düşürmek Türkçe yazılmış bir profili
sessizce İngilizceye çevirirdi. Alan zorunlu yapıldı — omit eden istek 400.
`enabledLanguages` zaten `@NotEmpty` idi, yani dil çifti artık bütünüyle
zorunlu. **Kırıcı sözleşme değişikliği:** `B-035`.

**Ekleme — `params.fields` isteğin gönderdiği alanı adlandırır.** F-005. Tarih
kuralı ihlali hangi uçtan tetiklenirse tetiklensin `endDate` diyordu. Kural
`spec/08-api.md` § 35.2'de tabloyla. Yanında bir davranış değişikliği daha:
hiçbir tarihe dokunmayan bir `PATCH` artık denetlenmiyor — aksi hâlde F-002'den
önce ters kaydedilmiş bir satır ilgisiz bir başlık düzenlemesini, düzeltilecek
alanı adlandıramadan reddederdi.

**Düzeltme — sözcükleme silme tek kural değil, iki.** F-006. `deleteVariant`
"son sözcükleme" (`fields: ["variantId"]`) ve "birincil, başkası var"
(`fields: ["primary"]`) diye ayrı ayrı reddediyor; frontend ikisini de
`variantId` ölçmüş, muhtemelen mock'tan. Birinci durumun testi yalnız 400'ü
kontrol ediyordu, yani ayrımın kendisi test edilmemişti. Kural
`spec/08-api.md` § 35.2'ye yazıldı, test iki alanı da sabitliyor.

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

**Ekleme/Sapma — fake sağlayıcı ve prompt kovası.** Üçü de kalıcı olduğu için
spec'e yazıldı, buradan silindi: fixture'ların dosya sisteminde durması, adının
girdinin hash'i olması ve `synthesize` bayrağının üç profildeki değeri
`spec/13-development.md` § 54.2'de; A/B kovasının murmur3 yerine CRC32 olması
`spec/12-quality.md` § 53.3'te.

**Ekleme — zincir ve OpenRouter adaptörünün dört kararı.** Dördü de kalıcı
olduğu için spec'e yazıldı, buradan silindi: `schema-retries`'ın varsayılanı,
zincirdeki bilinmeyen id'nin ölümcül olmaması ve zincirin env-driven olması
`spec/07-subsystems.md` § 27.3'te; `structured-output`'un tespit değil
yapılandırma olması § 27.2'de; `local-fake`'in zinciri override etmek zorunda
olması `spec/13-development.md` § 54.2'de.

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
