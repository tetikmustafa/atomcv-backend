# İnşa Notları — Aktif

> Kural: bu dosya **200 satırı geçmez**. Aşama bitince `archive/`'a taşınır, boş başlanır.
> Kayıt tipleri: **Sapma** (doküman başka diyor) · **Ekleme** (doküman sessiz) · **Düzeltme** (doküman yanlış).
>
> Bir sapma kalıcıysa `spec/`'e işlenir ve buradan silinir.

**Aktif aşama:** Aşama 3 — hesap ve MVP.
**Plan:** `spec/14-build-guide.md` § XI-A.6, Adım 3.1-3.x; gerekçesi
`spec/13-development.md` § 55. Aşama 2'nin tam kaydı `archive/stage-2.md`'de,
Aşama 1 `archive/stage-1.md`'de.

---

## Aşama 2'den taşınan açık kutular

**`Axiom'da loglar görünüyor` kutusu 3.1'e taşındı.** OTLP ihracatçısı bağlı ama
bir URL verilene kadar kapalı; dataset Adım 3.1'de açılıyor. Kutu oraya yazıldı.

**`llm_invocations.user_id` NULL.** Olay kullanıcıyı taşımıyor — zincir,
`UserContext` tutan fazlardan çağrılıyor ama aşağı geçirmiyor. Günlük toplam
(bütçe freni) bunu istemiyor; **kullanıcı bazlı maliyet** istiyor.

**Sıkılaştırılacak rate limiter yok.** § 44.3 anormal kullanıcı için bunu
istiyor; anomali sinyalleri şimdilik yalnız raporluyor.

## Aşama 1'den taşınan kısıtlar — hâlâ açık

| Eksik | Ne zaman | Neden şimdi değil |
|---|---|---|
| `ProfileRef.Scope` yalnız `PERSISTENT` | Aşama 3 | `EPHEMERAL`'ı anonim akıştan önce eklemek, üretmenin denetimli yolu yokken sahiplik kontrolünün etrafından dolaşmanın yolu olurdu |
| ATS raporu ve `FitReport` yok | Aşama 3 | Biri PDF metin çıkarımı istiyor (`spec/06-pipeline-d-g.md` § 23) |
| `UserScopedRepository`'de `findAll` yok | — | § 41.2 parçacığı `findByUserId` çağırıyor, o da `JpaRepository`'de yok. Alt sınıflar kendi bulucularını ekler |

## Isırmadan önce ele alınacak iki bulgu

**Atomsuz entry sayfaya hiç çıkmıyor.** Seçim atom atom çalışıyor; altında madde
olmayan bir diploma satırı aday bile değil. Gerçek çözüm § 20.2'nin modelini
değiştiriyor.

**Eşitlik atom id'siyle bozuluyor, id'ler her içe aktarımda yeniden üretiliyor.**
Aynı skor *ve* maliyetteki iki atom yer değiştiriyor — Aşama 3'ün anonim profil
devralması tam olarak bunu yapacak. İçerikten türetilen bir bozucu düzeltir.

## Aşama 3'e taşınanlar

- **`jobs (user_id, idempotency_key)` anonim istekleri tekilleştirmiyor** —
  `user_id` NULL ve Postgres NULL'ları farklı sayıyor. `COALESCE`'lı migration
  gerekiyor (kayıt EK D.6.5'te).
- **Anonim TTL etkinlikle kayıyor**, metin "son etkinliğinden iki saat sonra"
  demeli. `spec/01-foundations.md` § 9 ve ürün dokümanı güncellenmeli; metnin
  sahibi frontend.

## Devredilen açık kararlar

- **Üretimde migration nasıl çalışacak.** `spec/11-operations.md` § 47 dağıtım
  öncesi `--spring.flyway.migrate-only=true` gösteriyor; bu gerçek bir Spring
  Boot property'si değil. Flyway şu an üretimde de açılışta çalışıyor.
- **CI'a imaj taraması.** Trivy Dockerfile'ı görüyor, derlenen imajı görmüyor.
- **Spotless eklenecek mi.** § 47.1 `spotlessCheck` çalıştırıyor ama
  yapılandırılmış formatlayıcı yok — bugün CI'da biçim kapısı yok.
- **V1 bazı enum benzeri kolonlara `CHECK` koyuyor, bazılarına koymuyor.** § 13'ü
  bilinçli yansıtıyor; eksikleri sonradan eklemek ucuz bir migration.

## Aşama 2'den öğrenilen, tekrar edecek iki şey

- **Kılavuz "tablo" dediğinde önce `V1`'e bak.** Beş kez var olan bir tablo için
  migration istedi (2.4 pgvector, 2.6 `jobs` ve `generations`, 2.7
  `usage_counters` ve `feature_flags`).
- **Toplu JPQL `update` `@Version`'ı atlar** ve **okuma, yakalanmak istenen
  bayatlığı onarır** — etag'i **önceki yazmanın yanıtından** al. Aşama 3'ün
  başvuru izlemesi ikisine de çarpacak.

---

## Aşama 3 kayıtları

### `F-016` — tek kodun arkasındaki sekiz sebep (2026-08-25)

Kalıcı karar `spec/`'e yazıldı (§ 18.1, § 18.4, EK D.6.1 ve kod tablosu).
Buraya yalnız oradan okunamayacak olan düşüyor.

**Frontend dördü bildirdi, sekiz çıktı.** Şikâyet § 18.4'ün kapısı üzerineydi,
ama `JobDescriptionPreflight` de dört verdict'i tek koda düşürüyordu ve
`(0, 0)` gönderiyordu. Yalnız bildirilen yarıyı düzeltmek, aynı hatayı ikinci
kez açtırırdı. Sekizi tek kapalı sözlükte birleşti.

**`continue_anyway` kapıda `retry` ile aynı şeydi** — akışı izleyince çıktı:
onay yalnız ön kontrolü atlıyor, kapı her hâlükârda çalışıyor. Bildirilmemişti,
ve `reason` telde ayrıştığı anda frontend'in soracağı ilk şeydi. **Sapma
değil, Ekleme:** § 18.4 resolution'lardan hiç söz etmiyordu.

**Sebep verdict'in kurucusunda duruyor**, faza dağılmış bir `switch`'te değil —
reason'sız bir verdict derlenmiyor. Üç sonda: dokuzuncu bir sebep eklenince
`everyReasonIsRaisedBySomeCheck`, `retry` dalı silinince presenter testi,
`Locale.ROOT` kaldırılınca tel değeri `not_job_lıke` olup kendi testi düşüyor.

### Frontend'in üç bulgusu — `F-013`…`F-015` kapandı (2026-08-25)

Üçünün kalıcı kararı `spec/`'e yazıldı (§ 21.8, § 27.2, § 27.4, § 35.3).
Buraya yalnız oradan okunamayacak olan düşüyor.

**`F-013` geçici, ve geçiciliği kasıtlı.** `canBeWrittenIn` tek bir eksik
sözcüklemede tüm belgeyi profilin diline indiriyor — sert bir eşik, ve doğru
olanı: **iki dilli bir CV, o dilde olmayan bir CV'den kötü.** Yumuşak bir eşik
("atomların %90'ı çevriliyse hedef dile geç") kalan %10'u sayfaya karışık
çıkarırdı, ki F-013'ün şikâyeti tam buydu. § 21.8'in çeviren fazı indiğinde
kontrol her dil için doğru olur ve kural kendiliğinden çözülür — o yüzden bir
bayrak arkasına konmadı.

**Ölçüm hedeflemeden ayrı tutuldu.** Alternatif, seçimden *sonra* seçilmiş
varyantların dilini ölçüp tarihi ona uydurmaktı (frontend'in 1. seçeneği).
Tarihi düzeltirdi, gövdeyi düzeltmezdi: geri düşüş atom atom, yani gövde
karışık kalırdı. Karar üretimin **başında** veriliyor, tek yerde.

**Ekleme — iki dil alanı, tek bayrak değil.** `contentLanguage` +
`postingLanguage` gidiyor. `B-040`'ın "karşılaştırma sunucunun" dersi burada
tersine işliyor: ekranda okunan cümle **iki dilin de adını** anıyor, yani tek
bir boolean istemciye yine iki alanı sordururdu.

**`F-014` sondası iki yönlü.** `log.warn` satırı kaldırılınca dört test
düşüyor — beklenen. İkinci sonda daha değerli: 200 olmayan yolun `detail`'ine
`response.body()` eklenince **yalnız sızıntı testi** düşüyor. Yani "gövde
basmıyor" iddiasının kendi kanıtı var, varlığı kanıtlayan testlerden bağımsız.

**`F-015`'in asıl bulgusu tabloda değil, tablonun sessizliğindeydi.** `costOf`
zaten sıfır dönüyordu ve `llm.unpriced_calls` zaten sayıyordu; ikisi de
doğruydu ve ikisi de kimseye ulaşmıyordu. Eklenen şey fiyat değil, **açılışta
bir cümle**. Ücretsiz modelin sıfırla açıkça yazılması da bunun için: sayacın
susması gerekiyor ki konuştuğunda anlamı olsun.

### Frontend'in beş bulgusu — `F-009`…`F-012` kapandı (2026-08-25)

**Düzeltme — `generalMode` diye bir alan hiç yazılmadı.** Şemada göründü,
çünkü `GenerationRequest.isGeneralMode()` türetilmiş bir metot ve **bir
record'da `isX()` Jackson ile springdoc için bir getter'dır.** Aşama 2'de
`RichContent`'te yediğimiz hatanın telin öbür yüzü: orada JSONB kolonuna
yazılan bir alan doğurdu, burada request şemasına. Genel kural artık iki
yönlü — *Jackson'ın dokunduğu bir record'daki her getter şeklindeki metot,
kimsenin bildirmediği bir alandır.* Tüm DTO record'ları tarandı, tek örnek
buydu. Şema testi `GenerationRequest`'in **tam dört** özelliği olduğunu
sabitliyor; `@JsonIgnore`'u kaldıran bir sonda ile düşürüldüğü doğrulandı.

**Ekleme — boş dize göndermek yokluk göndermekten kötüdür.** `JobProgress`
tek bir shape olarak hem `jobs.progress` kolonuna hem `phase` olayına gidiyor,
ve `NONE` boş dizelerle çıkıyordu. `JobStatusResponse` bunları zaten `null`'a
çeviriyordu, yani **akış ile yoklama aynı olayı farklı gönderiyordu** — tek
shape'in engellemesi gereken tam da bu. `@JsonInclude(NON_EMPTY)` shape'in
kendisine kondu; `pct` üzerinde açık bir `ALWAYS` var, çünkü NON_EMPTY'nin
ilkelleri atlaması Jackson'ın kararı, bizim değil.

**Ekleme — `used`, `limit`'i geçebiliyordu ve sayı doğruydu, adı yanlıştı.**
Kota sayacı **denemeleri** sayıyor: reddedilen istek birimini geri almıyor,
yoksa sınırını aşmış bir kullanıcı sayaç tavanda sabitken ucu döverdi. Kırpmak
sunucuyu yanlış aktarmak olurdu; `Usage` iki alan taşıyor — `used` (harcanan,
`0..limit`) ve `attempted` (ham sayaç). Kırpma tek bir fabrikada ve bir
invariant onu orada tutuyor: `used > limit` taşıyan bir `Usage` inşa
edilemiyor. İki alanın anlamı **şemanın kendisine** yazıldı — `@Schema`
açıklamalarıyla, çünkü springdoc javadoc'u okumuyor ve şekil için otorite
OpenAPI.

**`F-011` yalnız belge:** Next'in dev rewrite'ı proxy'lediği yanıtı gzip'liyor
ve gzip tamponluyor, yani lokalde SSE hiç akmıyor. § 30.6'ya `proxy_buffering
off`'un yanına yazıldı. Düzeltme frontend'de.

### `F-008` — uygunluk raporu indi (2026-08-25)

**Ekleme — `fit_report` tipli saklanıyor, `Map<String,Object>` değil.** Kolon ve
setter Aşama 2'den beri duruyordu ve hiçbir çağıranı yoktu. `StoredSelection`
kalıbı: JSONB'ye record map'leniyor. Bunun bedeli, Aşama 2'nin `RichContent`
dersini `FitReport`'ta baştan uygulamak — **getter şeklinde tek bir metot yok**,
`level` hesaplanıp component olarak saklanıyor.

**Ekleme — rapor sıralamayla değil, sayfayla ölçülüyor.** Faz B profilin
tamamını puanlıyor, Faz C çoğunu bütçe için düşürüyor. `SelectedSkills` yalnız
seçilen atomların becerilerini topluyor; sıralamadan kurulmuş bir rapor
belgede yer bulamamış beceriyi kullanıcının hanesine yazardı. Sondayla
doğrulandı: filtreyi kaldırınca iki test düşüyor.

**Ekleme — canonical kuralı tek yere indi.** `RelevanceScorer.canonicalSkill`
artık public ve **üç** çağıranı var (ilanın becerileri, atomun becerileri,
rapor). İkinci bir kural, puanlayıcının saydığı bir beceriye raporun "eksik"
demesine yol açardı — kimsenin açıklayamayacağı bir fark. `strip()` tarafında
birleştiler; `trim()` ve `strip()` yan yana duruyordu.

**Ekleme — seviye akışta, sayılar uçta.** `matchLevel` `completed` olayına
girdi (dört karakter, başlık bir tur beklemesin); rapor `GET /generations/{id}`
üzerinden. Raporu yalnız akışa koymak, sayfayı yenileyen istemcinin onu bir
daha hiç görememesi demekti. `GET /jobs/{id}` de `pageCount` kazandı.

**Sonda:** `record.setFitReport(...)` satırı kaldırılınca `latexTest`'teki
uçtan uca kontrol düşüyor. Yavaş hattın bu diliminde koşturulması Aşama 2'nin
dersiydi ve yine karşılığını verdi — rapor JSONB'den tipli okunuyor, o yolu
başka hiçbir lane geçmiyor.

**Açık kalan:** § 23.2 (ATS metin çıkarma) hâlâ yok, PDF metin çıkarımı
istiyor. Aşama 3.
