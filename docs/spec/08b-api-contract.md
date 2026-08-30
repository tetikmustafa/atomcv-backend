# Bölüm VII/1-ek — API Sözleşme Kararları

> AtomCV spec · [INDEX](../INDEX.md)
>
> Bu dosya, inşa sırasında karara bağlanan **API sözleşme ayrıntılarıdır**: kapalı sözlükler, hata kataloğu, `params` disiplini. Not değil **referanstır** — OpenAPI şeması otoritedir, burası gerekçe ve bağlam taşır.

---

### D.6 — API sözleşmesi (Bölüm 35)

Frontend, Aşama 0'ın sonunda on altı sözleşme boşluğu çıkardı: dokümanın
adlandırmadığı enum'lar, tanımlamadığı başlıklar, örneklemediği yanıt şekilleri.
Sorular ve cevaplar iki ayrı dosyada duruyordu (`BACKEND-CONTRACT-GAPS.md`,
`docs/backend-contract-response.md`); ikisi de buraya taşınıp silindi. **Tek
kaynak burasıdır.**

Aşağıdaki kararların altısı ilk endpoint'ten önce, springdoc şeması yazılırken
uygulanır; gerisi ait olduğu aşamada. **Otorite yayınlanan OpenAPI şemasıdır**,
buradaki düzyazı değil — bu yüzden enum'lar ve başlıklar şemaya girer, yalnız
mutlu yol gövdelerine değil.

#### D.6.1 — Kapalı sözlükler

**`resolutions[].action`** — Bölüm 35.4 üçünü, 35.5 bir tanesini adlandırıyor,
Bölüm 11.5 ve 11.8 ikisini düzyazıyla anlatıp adlandırmıyor. Tam küme:

| action | Anlamı | İstemci davranışı |
|---|---|---|
| `increase_page_limit` | `maxPages`'i `params.maxPages`'e yükselt | Yeni seçenekle yeniden gönder |
| `review_pins` | Sabitlenmiş içerik incelemesini aç | Profile git, sabitlere filtrele |
| `keep_top_pinned` | En iyi `params.keep` sabiti tut | Daraltılmış kümeyle yeniden gönder |
| `sign_up` | Özellik hesap gerektiriyor | Kayda git, durumu koru |
| `paste_full_posting` | İlan metni yetersizdi | İlan alanına odaklan |
| `continue_as_general_cv` | İlansız devam | Boş `jobDescription` ile yeniden gönder |
| `continue_anyway` | **Ön kontrol** reddetti ama kullanıcı ısrar ediyor | Aynı metni ön kontrolü atlayan onayla yeniden gönder (Adım 2.3; Bölüm 18.1 üç çıkış yolu sunuyor, sözlükte ikisi vardı). **Yalnız `reason` ön kontrolden geldiğinde sunulur** — § 18.4'ün kapısı onaydan etkilenmez |
| `switch_to_manual_form` | Çıkarım başarısız | Manuel profil formuna git |
| `complete_profile` | Üretecek kadar profil yok | Profil düzenleyiciyi aç (Adım 1.8'de eklendi; Bölüm 25.3 bu adı kullanıyordu, sözlükte yoktu) |
| `retry` | Geçici hata | Değiştirmeden yeniden gönder |

**Sayı olan bir kapalı sözlük springdoc'ta üç şeyi birden istiyor**
(`F-019`): `@Schema(type = "integer", format = "int32", allowableValues =
{"1", "-1"})`, ve **enum bildiriminin üstünde**, onu taşıyan özelliğin
üstünde değil. `allowableValues` bir `String[]`, yani özelliğin tipi ne
olursa olsun tırnaklı basıyor, ve openapi-typescript enum'a inanıyor:
`FeedbackRequest.rating` bir süre `"1" | "-1"` diye üretildi, istemci —
doğru olarak — sayı gönderirken.

| Ne denendi | Ne çıktı |
|---|---|
| `Short` + `allowableValues` | `type: integer`, `enum: ["1","-1"]` — tırnaklı |
| aynısı + özellikte `type` | aynı, tırnaklı |
| enum + `@JsonValue short` | `type: string`, enum düştü |
| enum + `@Schema(type=integer)` | `type: integer`, **enum tamamen yok** |
| enum + `type` + `allowableValues` | ✅ `enum: [1,-1]` |

**`@JsonValue` tek başına yetmiyor** — yetiyormuş gibi duran kısım o.
`OpenApiSchemaIT` üçünü birden tutuyor.

**Metin olan kapalı bir sözlük ise `@Schema(implementation = X.class)` ile
yayımlanıyor** (`F-023`), alanın tipi `String` kalsa bile: `ImportWarning.code`
JSONB'den geri okunduğu için `String` duruyor, şeması yine de enum.

**`params.reason`** (`UNPARSEABLE_JOB_DESCRIPTION`) — bir ilanın neden analize
dönüşemediği. Sekiz değer, tek kod: kod API sonucunu adlandırıyor, `reason`
kullanıcıya söylenecek cümleyi. `confidence` ve `skillsFound` sekizden yalnız
ikisini ölçer ve ön kontrolden sıfır gelir, dolayısıyla **mesaj önce `reason`'a
göre seçilir**.

| `reason` | Nereden | Anlamı |
|---|---|---|
| `too_short` | ön kontrol (§ 18.1) | Analiz edilecek kadar metin yok |
| `too_long` | ön kontrol | İlan değil, sayfa |
| `low_entropy` | ön kontrol | Düzyazı olamayacak kadar az farklı sözcük |
| `not_job_like` | ön kontrol | Düzyazı, ama iş ilanına benzemiyor |
| `low_confidence` | kapı (§ 18.4) | Model tahmin ettiğini bildirdi |
| `too_few_skills` | kapı | İkiden az aranan beceri |
| `no_responsibilities` | kapı | Sorumluluk yok; Faz B'nin eşleyeceği bir şey yok |
| `suspicious_output` | kapı | Bir alan o alanın olabileceğinden çok uzun — cevap analiz şeklinde değil |

Ayrım kullanıcıya görünür: ön kontrol **kullanıcının metnini** reddetti ve
kullanıcı sezgiselden iyi bilebilir; kapı **modelin cevabını** reddetti ve
metinde düzeltilecek bir şey yok. Çıkış yolları buna göre değişiyor — § 18.4'ün
tablosu.

**Frontend kendi resolution'ını uydurmaz.** Listeyi sunucu sahiplenir; istemci
yalnız render eder ve isterse resolution satırının dışına düz bir "kapat"
kontrolü koyar.

**Hata kodları — tam katalog.** Bölüm 35.5 on pipeline hatasını sayıyor,
Bölüm 31.10'daki ingestion durumları düzyazıyla anlatılıp kodsuz bırakılmış.
Her kodun `params` anahtarları **ve tipleri** burada: ICU mesajı bunlarsız
yazılamaz, çünkü `{pinnedPages, number}` biçimlendirir, `{pinnedPages}`
yalnızca yerine koyar.

| Kod | HTTP | `params` |
|---|---|---|
| `INSUFFICIENT_PROFILE` | 422 | `completeness: integer`, `missing: string[]` |
| `UNPARSEABLE_JOB_DESCRIPTION` | 422 | `reason: string`, `confidence: number`, `skillsFound: integer` |
| `CONFLICTING_PREFERENCES` | 409 | `pinnedPages: number`, `maxPages: integer` |
| `FEATURE_REQUIRES_ACCOUNT` | 403 | `feature: string` |
| `QUOTA_EXCEEDED` | 429 | `metric: string`, `resetsAt: timestamp` |
| `ALL_PROVIDERS_UNAVAILABLE` | 503 | `tried: string[]` |
| `COMPILATION_FAILED` | 502 | `detail: string`, `rawSourceAvailable: boolean` |
| `PAGE_LIMIT_EXCEEDED` | 422 | `actual: integer`, `limit: integer` |
| `REWRITE_VALIDATION_FAILED` | 500 | `atomId: uuid`, `issues: string[]` |
| `COVER_LETTER_REJECTED` | 422 | `issues: string[]` |
| `EMBEDDING_UNAVAILABLE` | 503 | — |
| `GENERATION_PAUSED` | 503 | — |
| `UNSUPPORTED_DOCUMENT` | 415 | `accepted: string[]` |
| `DOCUMENT_TOO_LARGE` | 413 | `limitBytes: integer` |
| `PDF_NOT_TEXT_BASED` | 422 | — |
| `PDF_ENCRYPTED` | 422 | — |
| `EXTRACTION_EMPTY` | 422 | — |
| `EXTRACTION_TIMEOUT` | 504 | — |
| `LANGUAGE_UNDETECTED` | 422 | `detectedCandidates: string[]` |
| `PROFILE_QUOTA_EXCEEDED` | 429 | `limit: integer`, `resetsAt: timestamp` |
| `ANONYMOUS_SESSION_EXPIRED` | 401 | — |
| `ATOM_LIMIT_EXCEEDED` | 422 | `limit: integer`, `current: integer` |
| `NO_ANONYMOUS_PROFILE` | 404 | — |
| `PROFILE_ALREADY_EXISTS` | 409 | — |
| `GENERATION_ARTIFACT_EXPIRED` | 410 | — |
| `CSRF_TOKEN_INVALID` | 403 | — |
| `AUTHENTICATION_REQUIRED` | 401 | — |
| `OAUTH_FAILED` | 400 | `reason: string` |
| `MAGIC_LINK_INVALID` | 400 | — |
| `TRANSLATION_FAILED` | 422 | — |
| `RATE_LIMITED` | 429 | `resetsAt: timestamp` |
| `CHALLENGE_FAILED` | 403 | — |
| `RESOURCE_NOT_FOUND` | 404 | — |
| `VERSION_CONFLICT` | 412 | — |
| `PRECONDITION_REQUIRED` | 428 | — |
| `VALIDATION_FAILED` | 400 | `fields: string[]` |
| `INTERNAL_ERROR` | 500 | — |
| `METHOD_NOT_ALLOWED` | 405 | — |
| `NOT_ACCEPTABLE` | 406 | — |
| `UNSUPPORTED_MEDIA_TYPE` | 415 | — |

**`F-017`: tabloda beş satır eksikti, biri bildirilmişti.** Frontend
`COVER_LETTER_REJECTED`'ı gördü; tabloyu `ErrorCode`'a karşı okuyan bir muhafız
yazılınca dördü daha çıktı. Eksikliğin bedeli belge değil test:
**frontend'in katalog testi `params`'ı bu tablodan okuyor**, yani satırı olmayan
bir kodun mesajı yanlış argümanla yazılsa da yeşil kalıyor — `B-043`'ün delik
bulduğu yerin aynısı.

`GENERATION_PAUSED` § 44.3'ün acil freni, § 10-security.md'de tarif ediliyordu
ve buraya hiç geçmemişti. `METHOD_NOT_ALLOWED`, `NOT_ACCEPTABLE` ve
`UNSUPPORTED_MEDIA_TYPE` D.6.8'in kendi tablosunda duruyordu; **iki tablo bir
kataloğun tamamı değil**, ve "tam katalog" diyen bu.

**Tablo artık bir testin okuduğu şey.** `ErrorCatalogueSpecTest` her satırı
enum'a karşı doğruluyor — kod, HTTP durumu, parametre adları, tipleri ve
sıraları — ve iki yönde de: koda karşılık gelmeyen bir satır da düşürüyor.
Sıra da denetleniyor, çünkü tabloyu okuyan kişi ICU mesajını hangi
parametrenin önce geldiğine bakarak yazıyor.

**`COVER_LETTER_REJECTED`'ın `issues` sözlüğü kapalı** ve altı değerli:
`unsupported_claim`, `number_invented`, `experience_overstated`,
`wrong_company`, `length_out_of_range`, `cliche` (§ 34.4.1). Kapalılık
`CoverLetterIssue` enum'uyla zorlanıyor, yani listeye yedincisi ancak bu belge
de değişerek eklenebilir — frontend altısını ICU'da adlandırabilir.

**Adım 1.2'de eklenen dört kod.** CRUD'un ihtiyacı olan ve dokümanın hiç
adlandırmadığı durumlar: bulunamayan kaynak, `If-Match` uyuşmazlığı (Bölüm 35.6
durumu veriyor, kodu vermiyor), girdi doğrulama, ve beklenmeyen hata için bir
son çare. `RESOURCE_NOT_FOUND` ile `VERSION_CONFLICT` **parametresizdir**: hangi
kaynağın kastedildiğini istemci zaten bilir (isteği o attı), ve advice
katmanının elinde o bilgi olmadığı için tek alternatif uydurmaktı.

**`EXTRACTION_TIMEOUT` için 504 seçildi**; doküman bir durum vermiyordu.

**Adım 3.5: `TRANSLATION_FAILED` (422).** Arka plan işinden geliyor ve
neredeyse hiç kullanıcıya çıkmıyor — kişinin gördüğü şey sözcüklemenin **hâlâ
bayat işaretli** kalması, ki o da doğru cümle. Kod, `GET /jobs/{id}`'ye soran
bir istemciye "internal error"dan iyisini söyleyebilmek için var. Parametresiz:
çevirinin düşürdüğü şey kullanıcının kendi içeriği (mutlak kural 4).

**Adım 3.4 dilim 4: `POST /profile/import` telde.** `202` + `Location` +
`jobId`, üretimle birebir aynı kalıp. Beş ret senkron (`415`, `413`, iki `422`,
`429`), üç ret işten geliyor (`LANGUAGE_UNDETECTED`, `EXTRACTION_EMPTY`,
`ALL_PROVIDERS_UNAVAILABLE`). `PROFILE_QUOTA_EXCEEDED`'ın `limit`'i
yapılandırmadan **geri okunuyor** — hatanın doğduğu yerde uydurulan bir sayı,
sunucunun kendi yapılandırmasını yanlış bildirmesi olurdu.

**Adım 3.6 dilim 6: `POST /auth/verify` artık `204` değil `200`.** Gövde tek
alan taşıyor — `profileUpgrade` — ve OAuth tarafında aynı şey iniş adresinin
`profile` parametresi. **Değişikliğin sebebi tek seferlikliği:** anonim
profilin ne olduğu istemcinin bir kez okuyup davrandığı bir olgu; `/session`'a
eklenseydi iki hafta boyunca tekrarlanır, istemci de mesajı gösterip
göstermediğini hatırlamak zorunda kalırdı. Dört değer: `upgraded`, `none`,
`kept_existing`, `unavailable` (§ 41.3.3).

**Adım 3.6 dilim 5: `POST /profile/import` kimlik istemiyor.** Anonim oturum
çerezi yeterli; sözleşmenin geri kalanı değişmiyor — aynı `202`, aynı ret
kümesi, aynı terminal olay. Değişen tek şey `PROFILE_QUOTA_EXCEEDED`'ın kime
ait olduğu: anonim çağıranda hak **adrese** göre sayılıyor (§ 44.1), yani aynı
ağdan başka biri harcamış olabilir ve mesaj kişiye ait bir hak gibi
okunmamalı.

**Adım 3.4 dilim 2: iki kodun ilk kullanıcısı çıktı.** `LANGUAGE_UNDETECTED`
ve `EXTRACTION_EMPTY` EK D.6'da duruyordu ve hiçbir şey üretmiyordu; artık
profil çıkarımı ikisini de üretiyor. `EXTRACTION_EMPTY` **iki sebebi birden
taşıyor** — modelin hiçbir şey bulamadığı CV ve alan uzunluğu denetiminin
reddettiği cevap — ve bu § 43.2'nin gereği: ikisini ayıran bir mesaj,
enjeksiyonu yazana fark edildiğini söylerdi. `LANGUAGE_UNDETECTED`'ın
`detectedCandidates`'ı en fazla tek elemanlı: model bir sıralama değil bir dil
döndürüyor, ve düşük güvenli tahmin tek aday olarak sunuluyor.

**Adım 3.4 dilim 1'de eklenen iki kod.** EK D.6 § 31.10'un tablosunu
kodluyor, o da dosya kabul edildikten *sonra* başlıyor; § 31.2'nin ilk iki
basamağının kodu yoktu.

`UNSUPPORTED_DOCUMENT` **kabul edilen uzantı listesini yayınlıyor**, istemciye
gömülmesin diye: bir biçim eklendiğinde dosya seçicinin mesajı frontend sürümü
beklemeden doğru olur. Tek kod üç sebep için — okumadığımız bir uzantı,
uzantıyla çelişen bir medya tipi, ikisiyle de çelişen baytlar — çünkü hepsinde
kullanıcının yapacağı şey aynı, ve cümle her hâlükârda "PDF, DOCX, TEX, TXT ya
da MD yükle" diye bitiyor.

`DOCUMENT_TOO_LARGE` **sınırı yayınlıyor, gönderilen boyutu değil.** İstemci ne
yüklediğini biliyor, ve sunucunun okuması bu hatanın çoğu kez doğduğu noktada
güvenilir değil: Spring çok büyük bir multipart'ı baytlar sayılmadan reddediyor.
Bazen doğru olan bir sayı, hatada hiç olmamasından kötüdür.

**Adım 3.3 dilim 4'te eklenen iki kod.** Bölüm 40.5 ile 44.4 davranışı
tanımlıyor, kodu vermiyordu.

`RATE_LIMITED` **hangi katmanın reddettiğini yayınlamaz.** Kullanıcının okuduğu
cümle üç durumda da aynı — bekle, sonra tekrar dene — ve global katmanı adıyla
söylemek, servisi yoklayan birine trafiğinin isabet ettiğini bildirir. Katman
operatöre log satırıyla gider. Bölüm 40.4'ün sorusunu da açmaz: adres katmanı
yalnızca o pencereyi zaten kendisi harcamış bir çağıranı reddedebilir, yani
dönen cevap onun ne yaptığını anlatır, adresin hesabı olup olmadığını değil.

`CHALLENGE_FAILED` **satıcının değil işin adını taşır** — `OTLP_*` kararının
aynısı: bu kodu frontend render ediyor ve mesaj kataloğunda saklıyor,
Cloudflare'den çıkmak kullanıcıya görünen bir cümleyi yalana çevirmemeli.
Parametresizdir: eksik, süresi dolmuş, harcanmış ve sahte token istemciye tek
bir iş bırakır — widget'ı sıfırla, yeniden sor.

**Katalog kodda zorlanıyor, yalnız belgelenmiyor.** `params`, hata nesnesi
kurulurken bildirime karşı doğrulanır: eksik anahtar, fazladan anahtar ve yanlış
tip kurulumda patlar. Eksik bir parametre küçük bir kusur değildir — frontend'in
ICU mesajı onu yerine koyar ve kullanıcı "Sabitlediğin içerik {pinnedPages}
sayfa tutuyor" okur. P4'ün önlemek için var olduğu şey tam olarak budur ve
burada patlaması, ekran görüntüsünde keşfedilmesinden ucuzdur.

**`params` asla kullanıcı içeriği taşımaz** (mutlak kural 4): sayı, sınır,
tanımlayıcı ve alan adı taşır — sorunun şeklini, ona sebep olan metni değil.

#### D.6.2 — Hata gövdesi, ETag, sayfalama (Aşama 1)

| Konu | Tür | Karar |
|---|---|---|
| Hata gövdesindeki `title` (Bölüm 35.4'ün Türkçe örneği yanıltıyor) | Düzeltme | **Geliştiriciye yöneliktir, sabit İngilizcedir, kullanıcıya hiç gösterilmez.** RFC 7807 `title`'ın oluşumlar arası sabit olmasını ister; Bölüm 35.4'ün kendi kuralı da sunucunun metin değil çeviri anahtarı gönderdiğini söylüyor. Frontend'in `title`'ı yalnız log'a yazması doğru davranıştır. |
| ETag kapsamı | Ekleme | Yalnız V1'in `version` kolonu verdiği altı tablo: `profiles`, `sections`, `entries`, `atoms`, `atom_variants`, `applications`. **`generations`'ın `version`'ı yok**, dolayısıyla üretim kaynakları ETag ve `If-Match` taşımaz. Sonuç ekranı iyimser kilit isterse bu bir şema değişikliğidir, geç fark edilen bir eksik değil. |
| ETag biçimi | Ekleme | Tekil kaynak GET'inde `ETag: "7"`; koleksiyon yanıtlarında **her öğede `version` alanı**. Editör N sürümü öğrenmek için N istek atmak zorunda kalmaz. |
| Atom bazlı GET | Ekleme | **Yok.** Editör zaten tüm profili yüklüyor ve koleksiyon her öğenin `version`'ını taşıyor; alan bazlı PATCH için gereken her şey elde. `GET /profile/atoms/{id}` somut bir çağıran çıkınca eklenir — ilk aday Bölüm 37.5'teki bayatlama akışı. |
| Sayfalama | Ekleme | `GET /profile/atoms` **sayfalanmaz**. `/generations` ve `/applications` Aşama 2'de gelirken cursor tabanlı: `{ items, nextCursor }`. Offset sayfalama, üstten büyüyen listelerde satır atlar. |

**Gövdeyi üreten katman (Adım 1.2).** `ProblemDetailAdvice`, her hatayı aynı
şekle çeviriyor:

| Konu | Tür | Karar |
|---|---|---|
| `type` alanı | Sapma | **Göreli**: `/errors/conflicting-preferences`. Bölüm 35.4'ün örneği üretim alan adını kullanıyor, ama ürün dokümanı ne ismin ne alan adının koda gömülmesine izin veriyor (EK C.5) — RFC 7807 göreli referansa izin verir. |
| `title` alanı | Ekleme | Koddan **türetilir** (`CONFLICTING_PREFERENCES` → "Conflicting preferences"), ayrı bir listede tutulmaz. RFC 7807 başlığın oluşumlar arası sabit olmasını ister; bakımı ayrı bir liste, kayan bir listedir. |
| Yanıt durumu | Ekleme | Handler'lar `ResponseEntity` döner. Çıplak bir `ProblemDetail` dönmek yanıtın durumunu belirlemiyor — gövde 409 derken yanıt 500 gidiyordu. |
| Bilinmeyen yol | Ekleme | `NoResourceFoundException` → **404 `RESOURCE_NOT_FOUND`**, son çareye düşmez. Eski bir yer imi ya da bir tarayıcı botu, 500 üretip log'u yığınla dolduracak kadar sıradan. |
| Çapraz kiracı yazma denemesi | Ekleme | **500 `INTERNAL_ERROR`** + kimliksiz bir log satırı. 403 dönmek satırın varlığını doğrulardı; 404 dönmek de yanlış olurdu, çünkü okumalar zaten boş dönüyor — buraya ulaşan bir istek meşru bir istemciden gelemez, koddaki bir kusurdur. |
| Doğrulama hatası | Ekleme | Yalnız **alan adları** yayınlanır, reddedilen değer değil: değer kullanıcı içeriğidir ve log'lanan, ekran görüntüsü alınan bir gövdede yeri yoktur (mutlak kural 4). |
| `params` sıralaması | Düzeltme | `Map.copyOf` **kullanılmaz**. JDK'nın değişmez map'leri her JVM çalışmasında farklı tuzlanan bir sırayla dolaşılır; aynı hata iki koşuda farklı serileşiyordu. `LinkedHashMap` ile ekleme sırası korunuyor. |

**Yazma işlemleri (Adım 1.2).**

| Konu | Tür | Karar |
|---|---|---|
| `If-Match` zorunlu | Ekleme | Bölüm 35.6 başlığı gösteriyor ama zorunlu olduğunu söylemiyor. **Zorunlu.** Önkoşulsuz bir yazma, P8'in yasakladığı şeyin ta kendisi: iki sekme açık, ikinci kayıt kazanıyor, ilk düzenleme kimseye söylenmeden gidiyor. İstemcide sürüm zaten var (tekilde ETag, koleksiyonda öğe başına `version`), yani istemek bedava. Başlık yoksa **428 `PRECONDITION_REQUIRED`**. |
| Önkoşulun kontrol yeri | Ekleme | Yazan transaction'ın **içinde**. Kontrolle kayıt arasına bir şey giremiyor; girerse de `version` kolonu yakalıyor. |
| Zayıf etiket | Ekleme | `W/"7"` kabul edilir — bir vekil sunucu etiketi yolda zayıflatabilir, satırı tanımlayan içindeki sürümdür. Tırnaksız `7` kabul edilmez. |
| `PUT /profile` semantiği | Ekleme | **Değiştirir, yamalamaz**: gönderilmeyen alan temizlenir. `preferences` bu gövdenin parçası değil — başlığını düzenleyen biri, unutarak yazım tercihlerini sıfırlamasın diye. |
| `PATCH /profile/preferences` yerine `PUT` | Sapma | Bölüm 35.2 `PATCH` diyor. Uygulanan **`PUT /api/v1/profile/preferences`**: tercihler bir ayar formudur, istemci her zaman tüm nesneyi taşır, ve nested bir merge-patch'in belirsizliğini (bir alanı silmekle göndermemek arasındaki fark) taşımaya değmez. |
| Ayrıştırılamayan gövde | Ekleme | `HttpMessageNotReadableException` → **400 `VALIDATION_FAILED`**, alan adıyla. Bu olmadan bozuk bir JSON ya da record constructor'ının reddettiği bir değer son çareye düşüp 500 dönerdi. |
| Doğrulama sınırları | Ekleme | Uzunluklar API katmanında (`headline` 200, `selfDescription` 4000, `customInstructions` 1000, `maxPages` 1-10). Kolonlar `TEXT` kalıyor — Türkçe bir başlık İngilizcesinden uzun ve kimse sınırı cümlenin ortasında keşfetmemeli — ama sınırsız alan, sınırsız satır, sınırsız render ve sınırsız prompt demek. |

**Koleksiyon kaynakları — bölümler (Adım 1.2).**

| Konu | Tür | Karar |
|---|---|---|
| `PATCH` yalnız adlandırılanı değiştirir | Ekleme | Bölüm 35.6'nın kuralı. Bölümlerin **her kolonu `NOT NULL`**, yani "gönderilmedi" ile "null yapıldı" ayrımına burada hiç gerek yok. Entry'lerde tarih ya da kurum meşru biçimde temizlenebildiği için orada bir null kontrolünden fazlası gerekecek. |
| `displayOrder` yamalanamaz | Ekleme | Bir bölümü taşımak komşularını da numaralandırır; bu tek satırdaki bir alan değil, listenin tamamı üzerinde bir işlemdir. `POST /sections/reorder` yapar. |
| Sıralama isteği **tam liste** ister | Ekleme | Eksik liste, geri kalanın yerini sunucunun tahmin etmesi demek; iki istemci farklı tahmin ederse iki satır aynı pozisyonu iddia eder. Tam liste ayrıca çağrıyı idempotent yapıyor. |
| Sıralamada `If-Match` yok | Ekleme | İstek zaten çağıranın sıraya dair **tüm görüşünü** taşıyor; "bunları şu sıraya koy" demenin anlamı budur. Bayat bir sıralama pozisyon kaybettirir, içerik değil. |
| Oluşturma sona ekler | Ekleme | Yeni bölümün nereye ait olduğu listenin tamamına dair bir karar; istemci bunu reorder ile verir, başka bir sekmenin çoktan aldığı bir indeksi tahmin ederek değil. `201` + `ETag`. |
| Silme `If-Match` ister ve **cascade eder** | Ekleme | Bölümle birlikte entry'leri, atomları ve varyantları gider (veritabanı cascade'i). Yumuşatılmadı: açık bir silme kullanıcının kararıdır, sonucu gizlemek asıl sürpriz olurdu. |
| Bölüm yanıtında `version` **alanı var** | Sapma | Profil başında yoktu (D.6.2). Bölümler hem tek başına hem koleksiyon içinde dönüyor; alanın hangi endpoint'in döndürdüğüne göre kaybolması, `ETag`'in yanında küçük bir tekrardan daha kötü olurdu. |
| Sözlükler artık JSON'da da küçük harf | Düzeltme | `SectionKind`, `SectionLayout`, `AtomKind`, `AtomSource`, `VariantAuthor` yalnız JPA converter'ı taşıyordu; API gövdesinde `EXPERIENCE` gidiyordu. Hepsine `@JsonValue`/`@JsonCreator` eklendi (D.9 · 6'nın sözü). |

**Koleksiyon kaynakları — entry'ler (Adım 1.2).**

| Konu | Tür | Karar |
|---|---|---|
| `GET /profile/entries` | Ekleme | Bölüm 35.2 entry'ler için **hiç `GET` listelemiyor**. Onsuz editör bir deneyim listesini render edemez. Eklendi, isteğe bağlı `?sectionId=` süzgeciyle. |
| `POST /profile/entries/reorder` | Ekleme | Aynı boşluk sıralamada da vardı. İstek **bir bölüme** kapsanır (`sectionId` + o bölümün tam id listesi); iki bölüme yayılan bir liste, sıralama kılığında bir taşıma olurdu. |
| "Dokunma" ile "temizle" ayrımı | Ekleme | Entry'nin kolonları nullable: iş sürerken bitiş tarihi yoktur, yanlış yazılmış bir kurum boşaltılabilmelidir. Java'nın üç durumlu bir `Optional`'ı yok — Jackson **eksik** bir `Optional` alanını da `Optional.empty()` okur, yani açık `null`'dan ayırt edilemez. `JsonNullable` (`jackson-databind-nullable`) bu ayrımı taşıyor: tanımsız → dokunma, tanımlı-null → temizle, tanımlı-değer → ata. |
| Şemada sarmalayıcı görünmüyor | Ekleme | Üç durum **Java'nın meselesi**, sözleşmenin değil: telde alan yalnızca null olabilen bir değerdir. `@Schema(implementation = …, nullable = true)` ile öyle yayınlanıyor; aksi hâlde üretilen istemci doldurulacak bir `{ present, value }` nesnesiyle kalırdı. Bir test bunu sabitliyor. |
| Entry `PATCH`'inde `sectionId` yok | Ekleme | Bir entry'yi başka bölüme taşımak iki listeyi birden numaralandırır; bu bir alan düzenlemesi değil, sıralama işlemidir. Taşıma ucu gerektiğinde ayrıca eklenecek. |
| Başka profilin bölümüne entry | Ekleme | `sectionId` kapsamlı repository üzerinden çözülüyor, yani başkasının bölüm id'sini göndermek 400 `VALIDATION_FAILED` verir — satırın varlığını doğrulamayan bir cevap. |

**Koleksiyon kaynakları — atomlar ve varyantlar (Adım 1.2).**

| Konu | Tür | Karar |
|---|---|---|
| Atom **içeriğiyle birlikte** yaratılır | Ekleme | `POST /atoms` içerik ister ve birincil varyantı aynı transaction'da yazar. Varyantsız bir atom, kimsenin okuyamadığı bir olgudur: renderer basacak, ölçüm ölçecek bir şey bulamaz. O durumun hiç var olmaması, sonradan temizlenmesinden ucuz. |
| Kontroller ve metin ayrı uçlarda | Ekleme | Bölüm 35.2 `PATCH /atoms/{id}` için zaten "kontroller" diyor. Metin varyantın; ikisi ayrı satır, ayrı sürüm. Cümleyi atom üzerinden düzenlemek, iki satırın tek bir önkoşulu paylaşması olurdu. |
| Varyant `PATCH`'i içeriğin **tamamını** alır | Ekleme | Cümle run run değil, cümle olarak düzenlenir. Sunucu düz metni ve hash'i tek yetkili değerden türetiyor; hash değişince ölçülmüş maliyetler de düşüyor (EK D.3). |
| Dil+ton çakışması | Ekleme | `(atom, language, tone)` tekil indeksi var. İkinci bir aynı çift, kısıt ihlalinin 500 olarak yüzeye çıkması yerine **400 `VALIDATION_FAILED`** ile reddediliyor. |
| Birincil varyant terfisi | Ekleme | Atom başına tek birincil (kısmi tekil indeks). Terfi, eskisini **ayrı bir toplu güncellemeyle** düşürüyor: iki yazımı persistence context'e bırakmak, Hibernate'in sırayı ters kurup indekse takılmasına açık kapı bırakırdı. |
| Son varyant ve birincil silinemez | Ekleme | Bir atom bir varyantını korumak zorunda, ve aralarında bir varsayılan. İkisi de 400 döner; istemcinin yapacağı bir şey kalır (başkasını terfi ettir, ya da atomu sil), okunamaz bir atom kalmaz. |
| İçerik kuralları istemcinin hatasıdır | Düzeltme | `href`siz `link` run'ı ya da gelecekten bir `v` damgası, model constructor'ında `IllegalArgumentException` üretiyordu ve son çareye düşüp **500** dönüyordu. İstek gövdesinden gelen bir ihlal istemcinin hatasıdır: artık 400 `VALIDATION_FAILED` (`fields: ["content"]`). |
| Varyantların yüklenmesi | Ekleme | Liste ucu tüm varyantları **tek sorguda** çekip atoma göre grupluyor; atom başına sorgu, Bölüm 52.2'nin yasakladığı desenin ta kendisi olurdu. |

**Tamamlanma ve profil silme (Adım 1.2).**

| Konu | Tür | Karar |
|---|---|---|
| Bölüm 31.9'un tanımsız yüklemleri | Ekleme | Formül ağırlıkları veriyor, yüklemleri metot adından okumaya bırakıyor. Karara bağlananlar: **iletişim** = ad **ve** e-posta (CV başlığı bu ikisi olmadan render edilemez; telefon iyidir ama üretimi engellemez), **beceri sayısı** = `kind = skill` atomları (nerede asılı oldukları değil, ne oldukları), **metrikli atom** = `metrics` dizisi boş olmayan atom. |
| Ne zaman hesaplanır | Ekleme | **Okumada**, her yazımda değil. Formül profilin tamamını sayıyor; yazımda güncellemek her bölüm, entry ve atom ucuna profilin tamamını yükleme maliyeti bindirirdi. Sayı okunduğu yerde hesaplanıyor, ve `profiles.completeness` kolonu **yalnız değiştiğinde** yazılıyor — o kolon Aşama 2'deki ön kontrol kapısı için var (Bölüm 25.5). |
| Doküman eşiği | Düzeltme | Bölüm 31.9 "iletişim + (1 eğitim VEYA 1 deneyim) + 3 beceri ≈ %45" diyor. Hesap: **eğitimle 38**, **deneyimle 48** — dokümanın tahmini ikisinin arasında. Test ikisini de sabitliyor. |
| `DELETE /profile` | Ekleme | Profil ve altındaki her şey gider, **hesap kalır**: profili olmayan bir kullanıcı, henüz başlamamış bir kullanıcıdır ve sonraki okuma ona boş bir profil verir. `If-Match` zorunlu — geri alınamayan tek çağrı. |

#### D.6.3 — İndirme ve dışa aktarma (Aşama 1)

- Baytlar doğrudan API'den, `Content-Disposition: attachment` ile. Dosya adı,
  biliniyorsa şirket ve pozisyonu taşır.
- 14 günlük saklama dolduğunda `410 Gone` + `GENERATION_ARTIFACT_EXPIRED` +
  `retry` resolution'ı. Bunu vermek ucuz: `generations.selection_state`
  `pdf_expires_at`'ten bağımsız kalıcı bir anlık görüntüdür, yani PDF her zaman
  yeniden üretilebilir — süre dolması kullanıcıya emeğine mal olmaz.
- **Aşama 2'de bayt saklanmıyor** (karar: 2026-08-24). R2 hesabı Adım 3.1'de
  açılıyor, dolayısıyla `pdf_key` ve `pdf_expires_at` NULL kalıyor ve indirme
  **`content_snapshot`'tan** yeniden render ediyor: bir derleme, sıfır LLM
  çağrısı. **`selection_state` tek başına yetmez** — atomları id'yle adlandırır
  ve o id'lerin altındaki metin `atom_variants`'ta durmadan düzenlenir; profili
  yeniden okuyan bir indirme, işverene gönderilenden **başka bir belge** verir
  ve bunu kimse söylemez. `content_snapshot`, § 22.2'nin id taşımayan
  `RenderRequest`'inin kendisidir, yani ikinci koşu birebir aynı girdiyi alır.
  Anlık görüntüsü olmayan bir satır `410` + `retry` döner; bugünün profilinden
  render etmek, hiç gönderilmemiş bir belge üretmek olurdu.
  Yani yukarıdaki geri düşüş Aşama 2'de tek yol; `410 Gone` yolu R2 ile
  birlikte gelecek. Bunun bedeli indirme başına bir derleme, karşılığı da
  aynı `selection_state`'in aynı PDF'i üretmesinin ölçülebilir olması.
- `GET /profile/export` biçimi `?format=json|markdown` ile seçer; indirme
  endpoint'iyle aynı desen. Bilinmeyen biçim 400 `VALIDATION_FAILED`
  (`fields: ["format"]`).

| Konu | Tür | Karar |
|---|---|---|
| JSON dışa aktarımın şekli | Ekleme | **İç içe** (bölüm → entry → atom → varyant), düzenleme uçlarının aksine. Bir export ya bir insan tarafından okunur ya bütün olarak geri beslenir; ikisi de yapıyı görmek ister. Öğe şekilleri API'nin **zaten yayınladığı** şekillerdir, yani export'tan çıkan şey şemada tarif edilmiş olan şeydir. |
| Markdown, CV render'ı değildir | Ekleme | Sayfa bütçesi, şablon ve ölçüm yok — bu veri kopyasıdır, ve profil hiçbir CV'ye sığmayacak kadar uzun olsa da okunabilir kalır. Bu yüzden `rendering` modülünde değil, `profile` içinde. |
| Mark'lar Markdown'a çevrilmez | Ekleme | Atom metni **düz metin** olarak yazılır. Mark'lar semantiktir; onları yıldıza çevirmek, verinin bilerek taşımadığı bir sunum uydurmak olurdu (P1). |
| Markdown kaçışı | Ekleme | Yalnız **satır içinde** anlam değiştiren karakterler kaçırılır (`` \ ` * _ [ ] < > | ``). `.` `-` `#` `+` yalnız satır başında anlamlıdır ve her satırın başını bu kod yazıyor; hepsini kaçırmak `name@example\.com` gibi, insanların okuduğu bir dosyayı ters bölü çöplüğüne çevirirdi. |
| Yanıt karakter kümesi | Düzeltme | `text/markdown;charset=UTF-8`. Charset belirtilmezse istemci ISO-8859-1'e düşüyor ve "İstanbul" bozuk geliyor — test bunu yakaladı. |
| Dosya adı | Ekleme | `atomcv-profile-<tarih>.md`. İsim konmuyor: indirme klasörlerine, vekil sunucu loglarına ve ekran görüntülerine kişisel veri taşımanın karşılığı yok (mutlak kural 4). |

#### D.6.4 — İş durumu ve SSE (Aşama 2)

Her SSE olayı bir `id` taşır ve yeniden bağlanmada `Last-Event-ID` onurlandırılır
(o noktadan itibaren tekrar oynatma, en azından güncel durumu yeniden gönderme).
Bunsuz ilerleme ekranının tek bir hata modu olur: iş çoktan bitmişken spinner
sonsuza kadar döner — P4'ün yasakladığı sessiz kötü sonuç.

**Uygulanan yol ikinci seçenek** (Adım 2.6): bağlanır bağlanmaz güncel durum
gönderiliyor, `Last-Event-ID`'den oynatma yapılmıyor. Gerçek replay iş başına
tampon ister ve anlık durum aynı işi görüyor — üstelik yalnız yeniden
bağlananları değil, **202 ile abonelik arasında biten işleri** de kurtarıyor.
`id` tek bir akış içinde sıralamadır; istemci sürekliliğine değil, terminal
olaya güvenmeli. Akış terminal olayla kapanır.

```json
// GET /api/v1/jobs/{id}
{
  "jobId": "...",
  "status": "queued | running | completed | failed",
  "phase": "C",
  "label": "generation.phase.RENDERING",   // çeviri anahtarı, cümle değil
  "pct": 60,
  "generationId": "...",
  "error": { "code": "...", "params": {}, "resolutions": [] }
}
```

Sahibi olmayan bir iş **404** döner, 403 değil: bir id'nin var olduğunu
yabancıya söylemek de bilgidir (mutlak kural 3).

**Biten iş `pct: 100` taşır ve `phase`/`label` taşımaz.** Son geçtiği fazı
saklayan bir satır, istemciye "Rendering, %70" ile "completed"ı yan yana
göstertir — kendi yanındaki sözcükle tartışan bir ilerleme çubuğu. **Düşen iş
ilerlemesini korur**, çünkü nerede durduğu, satırın nedeni hakkında
söyleyebileceği en yararlı şeydir.

`generationId` yalnız `completed`'da, `error` yalnız `failed`'da bulunur; bu
ikisi terminal durumlardır. Akış terminal olay olmadan kapanırsa bu endpoint'i
yoklamak (polling) kabul edilebilir bir geri düşüştür.

#### D.6.5 — Idempotency ve kota (Aşama 2)

`Idempotency-Key`, para harcayan veya iş başlatan her POST'ta onurlandırılır:
`/generations`, `/generations/{id}/edits`,
`/generations/{id}/cover-letter/regenerate`, `/ingestion/cv`. Anahtarlar 24 saat
saklanır.

> **Kayda geçirilmiş kusur.** V1'deki
> `CREATE UNIQUE INDEX ON jobs (user_id, idempotency_key) WHERE idempotency_key IS NOT NULL`
> anonim istekleri tekilleştirmez: orada `user_id` NULL'dır ve Postgres NULL'ları
> birbirinden farklı sayar, yani aynı anahtar ikinci bir iş açar.
> `COALESCE(user_id::text, anon_session_id)` üzerinden bir migration gerekir.
> Hemen düzeltilmedi, çünkü anonim akışın kuyruğu kullanıp kullanmayacağı hâlâ
> açık.

Kota: `429` ile birlikte `Retry-After` başlığı ve `params` içinde `resetsAt`.
Sayaçlar (`generationsUsedToday`, `dailyGenerationQuota`, `quotaResetsAt`)
`capabilities` içinde de yayınlanır — sınır, çarpılmadan önce görünür olur.

> **Karar (F-007): gün sınırı UTC, `resetsAt` mutlak bir andır.**
> `usage_counters.period` bir `DATE` ve o tarih **UTC** takviminde okunur;
> sayaç UTC gece yarısında döner, Türkiye'de saat 03:00'e denk gelir.
>
> Gerekçe: kolon zaten saat dilimsiz bir `DATE` ve UTC onu tek anlamlı kılan
> okuma. Sunucunun saat dilimi değişse de aynı satır aynı günü gösterir,
> yaz saati sınırı yoktur, ve sayaç sorgusu sunucunun yerelini hiç sormaz.
> Uygulamaya gömülü bir `Europe/Istanbul`, ilk kez o dilimin dışına çıkan
> kullanıcıda sessizce yanlış olurdu; istemcinin bildirdiği saat dilimi ise
> kota kaçırmak için ayarlanabilir bir alan olurdu.
>
> **Telde `resetsAt` her zaman offset taşıyan bir ISO-8601 anıdır**
> (`2026-08-22T00:00:00Z`), yalnız saat değil. İstemci onu kullanıcının
> yerelinde biçimlendirir; "kotan 03:00'te yenilenir" cümlesini yazacak taraf
> frontend'dir ve bunu ancak mutlak bir an ile doğru yazabilir. Aynı kural
> `capabilities.quotaResetsAt` ve `QUOTA_EXCEEDED` /
> `PROFILE_QUOTA_EXCEEDED` `params`'ı için de geçerli.
>
> `Retry-After` bunun yanında saniye cinsinden kalır — HTTP'nin kendi alanı,
> ve istemci saati yanlışsa doğru olan tek değer odur.

#### D.6.6 — Anonim oturum, CSRF, profil devralma (Aşama 3)

| Konu | Karar |
|---|---|
| Anonim oturum çerezi | Hesaplı oturumla **aynı `sid` çerezi**. Kimlik doğrulama, istemci tarafında bir `capabilities` sorusu olarak kalır. |
| Süre bilgisi | `capabilities` içinde `anonymousExpiresAt` (ISO 8601). |
| Süre dolduğunda | `401` + `ANONYMOUS_SESSION_EXPIRED` + `sign_up` resolution'ı. |
| TTL davranışı (Bölüm 9 "2 saat sonra silinir" diyor) | **TTL kayar: etkinlikte tazelenir.** Mutlak iki saat, inceleme ekranında çalışmakta olan kullanıcıyı keserdi — P8'in önlemek için var olduğu emek kaybı. Kullanıcıya gösterilen metin "son etkinliğinden iki saat sonra" demeli. |
| CSRF (Bölüm 40.1 adını koyup tanımlamıyor) | Spring Security'nin double-submit varsayılanı: sunucu okunabilir (HttpOnly olmayan) `XSRF-TOKEN` çerezi verir, istemci güvensiz metotlarda (POST/PUT/PATCH/DELETE) `X-XSRF-TOKEN` başlığında yankılar, uyuşmazlıkta `403` + `CSRF_TOKEN_INVALID`. Oturum çerezi zaten `SameSite=Strict` olduğu için asıl vektör kapalı; bu derinlemesine savunmadır, o yüzden kimlikle birlikte gelir, öne çekilmez. |
| Profil devralma | `POST /api/v1/profile/claim` → `200`, `404 NO_ANONYMOUS_PROFILE`, `409 PROFILE_ALREADY_EXISTS`. 409 yalnız **değiştir veya koru** sunar, **birleştir sunmaz**: birleştirme atom düzeyinde tekilleştirme demek (Bölüm 7, Jaro-Winkler + embedding) ve o Aşama 4 işi. Erken sunmak ya endpoint'i alakasız bir işe bağlar ya da içeriği sessizce çoğaltan bir birleştirme gönderir — P8 ikincisini yasaklar. API, yerine getiremeyeceği bir resolution'ı adlandırmamalı. |

**`AUTHENTICATION_REQUIRED` neden ayrı bir kod (Adım 3.3).** Katalog uzun süre
oturumu **hiç olmayan** bir isteğe cevapsızdı: `ANONYMOUS_SESSION_EXPIRED`
süresi dolmuş anonim oturumun, `FEATURE_REQUIRES_ACCOUNT` erişilemeyen bir
özelliğin adı. `sid` çerezi taşımayan bir istek ikisi de değil. Süre dolma
kodunu ödünç almak sunucuya hiç var olmamış bir oturum hakkında cümle
kurdururdu — kullanıcının okuduğu cümle, ve logların sonradan düzeltemeyeceği
bir teşhis. Tek resolution `sign_up`. Adım 3.6 çerezsiz çağırana anonim oturum
basmaya başlayınca kod **nadirleşir, yanlış olmaz**: kullanıcı kapsamlı bir uca
hiçbir şey taşımadan gelen isteğin cevabı olarak kalır.

**`MAGIC_LINK_INVALID` bilerek parametresizdir (Adım 3.3).** Katalogda başka
her yerde kapalı bir `reason` daha iyi bir şekildir; burada açıktır — süresi
dolmuş, kullanılmış, yanlış verifier ve hiç var olmamış ayırt edilebilirse
tahmin yürüten kişi tahmininin hangi yarısının doğru olduğunu öğrenir
(§ 40.4.1).

**`OAUTH_FAILED` tek kod, yedi sebep (Adım 3.3).** `reason` kapalı bir sözlük:
`state_invalid`, `declined`, `provider_disabled`, `provider_unavailable`,
`email_missing`, `email_unverified`, `account_disabled`. `F-016`'nın istediği
şekil — istemci tek bir ICU anahtarını `select` ile çözer, sonradan eklenen bir
sebep `other` dalına düşer, ham anahtar ekrana çıkmaz.

**Ve genelde gövdede değil, sorgu parametresinde gelir.** İki OAuth ucu da
tarayıcı gezinmesidir; hata, frontend'in hata rotasına `?code=OAUTH_FAILED&reason=...`
ile yönlendirmedir. Katalogdaki 400, biri onu JSON olarak isterse ne olacağıdır.

#### D.6.7 — Kapsam dışı bırakılanlar

- **Sunucu tarafı render API çağırmaz.** Kimlik doğrulamalı her fetch tarayıcıda
  kalır; server component'ler yalnız kabuk ve statik içerik render eder.
  Frontend'in `client.ts` dosyasındaki açıklayıcı `throw` doğru davranıştır,
  yer tutucu değil. Bu değişirse iç ağ adresi ve çerez taşıma kararı gerekir.
- **`/api/v1/warmup` public API değildir** (Bölüm 52.5). OpenAPI şemasının
  dışında tutulur, nginx üzerinden yönlendirilmez, üretilen tiplerde
  görünmemelidir.

**Etkinleştirici.** springdoc-openapi ilk endpoint'le birlikte gelir. On altı
maddenin altısı, `npm run gen:api` çalışabilir olduğu anda kendiliğinden kapanır
— ama yalnız şema enum'ları ve başlıkları taşıyorsa.

#### D.6.8 — Frontend'in ikinci senkronizasyon isteği (Aşama 1 kapanışı)

Frontend, profil editörünü yayımlanan şemaya bağlarken her iddiayı **çalışan
sunucuya karşı** denetledi ve ikinci bir `DOC-SYNC-REQUEST.md` yazdı. Sonuç
üç kutuya ayrıldı: dokümanın yanlış olduğu yerler, şemanın eksik olduğu
yerler, ve sunucunun bozuk olduğu yerler. **Üçüncüsü en pahalısıydı ve
frontend onun yalnız bir yüzünü görmüştü.**

**Protokol düzeyindeki reddetmeler 500 dönüyordu.** `ProblemDetailAdvice`'ın
`Exception` yakalayıcısı Spring MVC'nin istek reddi istisnalarını da yutuyordu:

| İstek | Önce | Sonra |
|---|---|---|
| `Content-Type: application/merge-patch+json` | 500 `INTERNAL_ERROR` | **415** `UNSUPPORTED_MEDIA_TYPE` |
| `PUT` (yalnız `PATCH` kabul eden yolda) | 500 | **405** `METHOD_NOT_ALLOWED` + `Allow` |
| `Accept: text/plain` | 500 | **406** `NOT_ACCEPTABLE` |
| `?sectionId=not-a-uuid` | 500 | **400** `VALIDATION_FAILED`, `fields: ["sectionId"]` |

Üçü de tam stack trace ile `ERROR` seviyesinde loglanıyordu, yani herhangi bir
istemcinin bozuk isteği üretimin 500 oranını yükseltip logu dolduruyordu.
İlkinin sebebi bizzat bu doküman: Bölüm 35.6 merge-patch yazıyordu, hiçbir
controller onu kabul etmiyordu, dolayısıyla **spesifikasyonu izleyen istemciye
sunucunun bozulduğu söyleniyordu.** Bölüm 35.6 düzeltildi (media type ve
`If-Match` örneği), üç yeni kod katalogda: `METHOD_NOT_ALLOWED`,
`NOT_ACCEPTABLE`, `UNSUPPORTED_MEDIA_TYPE`.

**Varyant yaması taşıdığından fazlasını değiştiriyordu.** `PATCH
…/variants/{id}` için `content` zorunluydu, yani bir sözcüklemeyi varsayılan
yapmak metnin tamamını geri göndermeyi gerektiriyordu — metin düzenlemesi
olmayan bir yazmada. Frontend'in bu yüzden benimsediği "metni aynen geri
gönder" çözümü diğer iki hatayı ortaya çıkardı:

- `tone` istekten koşulsuz yazılıyordu, yani o çözüm **kullanıcının seçtiği
  tonu sessizce siliyordu** (P8). Alanı yalnız opsiyonel yapmak yetmez:
  göndermemek ile temizlemek farklı anlamlara gelmeli, bu yüzden `tone` artık
  bir `JsonNullable` — entry'nin null'lanabilir kolonlarındaki desenin aynısı.
- `userEdited` her yamada set ediliyordu. Anlamı "bu cümleyi bir insan yazdı"
  ve Aşama 2'nin çeviri işi neyi yeniden üretebileceğine ona bakarak karar
  verecek; yani bir promote, kimsenin dokunmadığı metni kullanıcının kendi işi
  gibi işaretliyordu. Artık yalnız sözcük taşıyan bir yazmayı izliyor.

İstek tipi ikiye ayrıldı: `VariantWrite` (POST) `content` istemeye devam
ediyor — sözcüklemesi olmayan atom kimsenin okuyamayacağı bir olgudur —
`VariantPatch` (PATCH) hiçbir şey istemiyor.

**Şema, API'nin zaten verdiği sözleri söylemiyordu.** Hepsi anotasyon, davranış
değişikliği değil; ama ilan edilmemiş bir garanti kimsenin güvenemeyeceği ve
iki tarafta da tek bir test kızarmadan kaldırılabilecek bir garantidir.

| # | Eksik | Karar |
|---|---|---|
| B.1 | `ApiError.code` ve `.status` opsiyonel görünüyordu | **Zorunlu.** D.9 · 12 her hatanın bir kod taşıdığını söylüyor; opsiyonel yayımlanınca her tüketici sözleşmenin "olamaz" dediği bir dala bakmak zorunda kalıyordu. |
| B.2 | Yazma yanıtlarında `ETag` ilan edilmemişti | **Her tekil kaynak yazmasında ilan edildi** (`POST` 201 dahil). Koleksiyon okumaları bilerek taşımıyor. |
| B.3 | `Run.m` şemada opsiyonel, D.9 · 4 "her zaman dizi" diyor | **İkisi de doğru, yön farkı.** Şema değişmedi; D.2'ye satır eklendi. |
| B.4 | On operasyon hiç `200` ilan etmiyordu | **Hepsine eklendi.** Aralarında her koleksiyon okuması ve her kısmi yazma var. |
| B.5 | `EntryPatch`'in temizlenebilir alanları düz `string` | `nullable = true` bir OpenAPI **3.0** bayrağı; bu doküman 3.1 ve orada null bir *tip*. springdoc bayrağı sessizce düşürüyordu. `types = {"string", "null"}` ile düzeltildi — ama `implementation` bırakılırsa springdoc `{ present, value }` sarmalayıcısını bileşen olarak yayımlıyor, yani aynı kusurun öteki yüzü. İki yarım da teste bağlandı. |
| B.6 | `/profile/export` yalnız JSON ilan ediyordu | **`text/markdown` de ilan edildi.** Şemaya güvenen istemci markdown'ı JSON diye ayrıştırıp ilk karakterde patlıyordu. |
| B.7 | Operasyon id'leri konumsal (`list_2`) | **Adlandırıldı** (`listAtoms`, `patchSection`, …). Üreticiler isimleri bunlardan türetiyor. |

**Golden set'e ikinci bir sözcükleme eklendi.** Sunucudaki hiçbir atomun
birden fazla varyantı yoktu ve her sözcükleme Türkçeyken `enabledLanguages`
`["en"]` idi — yani sekmeler, promote ve bayatlık yolu iki tarafta da yalnız
mock'larla vardı. `senior_backend_tr` artık iki dili de açıyor ve ilk
maddesinde İngilizce bir alternatif taşıyor. Fixture formatı iki alan
büyüdü (`enabledLanguages`, `alternatives`); okuyucu aynı dil+ton çiftini iki
kez talep eden fixture'ı reddediyor, yoksa seeder açılışta bir kısıt ihlaliyle
ölüyor ve hangi dosyanın yanlış olduğunu söylemiyor. Maliyetler yeniden
kaydedildi: bir yeni kayıt, mevcut her sayı aynı.

**Frontend'in doğruladığı, değişiklik istemeyen davranışlar** (D.9 · 25-28'de
tekrar edilir, çünkü bir sonraki oturum bunları deneyerek keşfetmesin): atom ve
varyant sürümleri **bağımsız ilerler**; hiçbir şeyi değiştirmeyen bir yazma
sürümü **artırmaz**; bir atom **son birincil sözcüklemesini bırakmaz** (400,
`fields: ["primary"]`); promote **öncekini indirir ve listeyi yeniden sıralar**,
ama yanıt yalnız yazılan sözcüklemeyi taşır.

**Kanıtlanan koruyucular.** 415/405/400/406'nın hepsi düzeltmeden **önce**
çalışan sunucuda 500 olarak gösterildi; promote-only yaması 400 olarak
gösterildi; null'lanabilirlik testi `nullable = true` geri konularak
kızartıldı. `OpenApiSchemaIT` 8 testten 16'ya, `AtomApiIT` 17'den 19'a,
`ProblemDetailAdviceTest` 7'den 12'ye çıktı.

#### D.6.9 — Gerçek uca karşı üçüncü ölçüm (`F-024`, Aşama 3 kapanışı)

Frontend MSW'yi kapatıp bütün kapıları çalışan sunucuya karşı denetledi.
**Ölçtüğü her şey doğru çıktı** — `413` → `409` → `415`/`422` sırası,
`{"userEdited": true}` → `400`, `405`/`406`/`415`, `rating: 0` → `400`,
cursor'lı sayfalama ve bozuk cursor'ın `400`'ü, CSRF'siz yazmanın `403`'ü —
**biri hariç.**

| İstek | Önce | Sonra |
|---|---|---|
| `POST /profile/import`, multipart gövde, **`file` parçası yok** | 500 `INTERNAL_ERROR` | **400** `VALIDATION_FAILED`, `fields: ["file"]` |

Sebebi D.6.8'in listesindekilerin aynısı, bir istisna sonra:
`MissingServletRequestPartException`'ın `ProblemDetailAdvice`'ta işleyicisi
yoktu, son çareye düşüyordu, ve sunucu kullanıcıya "isteğin beni bozdu"
diyordu. `handleBadParameter` **zaten** tam bu gerekçeyle query parametresinin
eksiğini yakalıyordu; bir parça, aynı kusurun başka bir zarfı.

**Bunun bir aşama boyunca durabilmesinin sebebi kayda değer.** Frontend'in
kendi formu bu isteği üretemiyor — dosya seçilmeden göndermiyor — yani hiçbir
ekran testi buraya ulaşamazdı. Bulunması, ekranın değil **sunucunun**
denetlenmesini gerektirdi. *Bir uca yalnız kendi arayüzünden bakan, o ucun
başka istemcilere ne dediğini hiç görmez.*
