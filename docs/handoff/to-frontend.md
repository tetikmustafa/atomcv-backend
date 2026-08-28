# → Frontend

> **Kanal kuralları**
>
> - Backend yazar, frontend okur ve `OPEN` → `ACK` taşır.
> - Her madde bir ID taşır (`B-nnn`), numaralar tekrar kullanılmaz.
> - **Dosya 100 satırı geçerse arşivleme gecikmiştir.** `ACK` maddeleri `resolved/`'a taşınır.
> - API _şekli_ için otorite OpenAPI şemasıdır. Burası **neden değişti + ne yapman lazım** taşır.
> - Kalıcı kural niteliğindeki maddeler `spec/`'e işlenir ve buradan silinir.

---

## OPEN

> **Dosya 100 satır sınırının dört katı, ve sebebi arşivleme gecikmesi değil:**
> **on yedi madde açık ve hiçbiri `ACK` almadı**, yani taşınacak bir şey yok.
> Sınır bir okunabilirlik kuralı; onu delen şey burada bir belge sorunu değil,
> **bir koordinasyon sorunu.** Bir madde `ACK` aldığı gün `resolved/`'a taşınır
> ve dosya kendiliğinden küçülür.
>
> Bu arada gezinebilir olsun diye aşağıda bir dizin var. Gerekçelerin kalıcı
> olanı `spec/`'e işlendi; burada yalnız *ne yapman lazım* duruyor.

### Dizin — açık maddeler

| ID | Konu | Ne yapman lazım, tek cümlede |
|---|---|---|
| `B-044` | CSRF | Her yazma isteği `X-XSRF-TOKEN` taşımalı; değeri aynı isteğin çerezinden. |
| `B-045` | `AUTHENTICATION_REQUIRED` | Yeni 401 kodu — giriş ekranına götüren tek yol. |
| `B-046` | Oturum ve yetenekler | `/auth/session` ve `/auth/logout`; yetenek kümesi ekranı sürüyor. |
| `B-047` | LinkedIn | Giriş sağlayıcısı olarak kaldırıldı; düğmeyi silin. |
| `B-048` | OAuth | İki rota: `/auth/complete` ve `/auth/error`. |
| `B-049` | Magic link | Bir rota (`/verify`) ve bir tuzak: bağlantı GET'tir, giriş POST'tur. |
| `B-050` | Turnstile + 429 | Link isteği bir widget tokenı istiyor ve 429 dönebiliyor. |
| `B-051` | CV yükleme | Bir uç, beş senkron ret, bir iş — ekran kurulacak. |
| `B-052` | Bayat varyant | Bir sözcüklemeyi düzenlemek ötekileri bayatlatıyor; uyarıyı siz gösterin. |
| `B-053` | Anonim yükleme | Aynı uç, aynı kalıp, hesap yok. |
| `B-054` | Yükseltme yanıtı | `/auth/verify` artık anonim profile ne olduğunu söylüyor. |
| `B-055` | `REWRITING` | Üretim akışında yeni bir faz görünüyor. |
| `B-056` | Cover letter | Bir bayrak, bir uç, ve reddedilebilir. |
| `B-057` | Hesap silme | `DELETE /api/v1/account`. |
| `B-058` | Geri bildirim | Bir başparmak ve 48 saatlik bir içerik izni. |
| `B-059` | Gizlilik Politikası | Alt işleyen listesine Resend + AWS SES (Tokyo). **Yayın öncesi zorunlu.** |
| `B-060` | İkinci CV | `409 PROFILE_ALREADY_EXISTS`, iki resolution, `?mode=replace`. |

### B-044 · Her yazma isteği bir CSRF token taşıyor
**Since:** commit <sha> · Adım 3.3 · **Spec:** `spec/08b-api-contract.md` § EK D.6.6

Çift-gönderim: sunucu her yanıtta okunabilir bir `XSRF-TOKEN` çerezi veriyor,
istemci güvensiz metotlarda (`POST`/`PUT`/`PATCH`/`DELETE`) `X-XSRF-TOKEN`
başlığında yankılıyor. Yankılamayan istek `403 CSRF_TOKEN_INVALID` alır.

**Aksiyon:** `client.ts`'e iki satır. Çerez `HttpOnly` **değil** (okunması
gerekiyor; `sid` olmadan hiçbir şey kanıtlamıyor). Kodu gören istemci tekrar
denemesin, tokenı yeniden okusun.

**Tuzak:** çerez `SameSite=Strict` ve host'a bağlı; ayrı portlarda
(`:3000`/`:8080`) `document.cookie` göremez — Next.js rewrite'ı üzerinden tek
origin'den geçin, OAuth callback'i dahil.

### B-045 · Yeni hata kodu — `AUTHENTICATION_REQUIRED` (401)
**Since:** commit <sha> · Adım 3.3 · **Spec:** `spec/08b-api-contract.md` § EK D.6

Katalogda **oturumu hiç olmayan** isteğin karşılığı yoktu; gerekçesi spec'te.
**Aksiyon:** `errors.AUTHENTICATION_REQUIRED` anahtarı, `params` yok, tek
resolution `sign_up`. Adım 3.6 anonim oturum basınca nadirleşir.

### B-046 · `/auth/session`, `/auth/logout`, ve hesabın yetenek kümesi
**Since:** commit <sha> · Adım 3.3 · **Spec:** `spec/08-api.md` § 35.7

`GET /auth/session` → `authenticated` + `capabilities`, `no-store`.
`POST /auth/logout` → `204`, oturumu sunucuda iptal eder; oturumsuz da `204`.

§ 35.7 yalnız **anonim** kümeyi yazmıştı; hesaplı hâlinin tam tablosu artık
orada — hesapta diller `["en","tr"]`, dört yetenek `true`, kotalar 20 · 5.

**Aksiyon:** `maxAtoms` ve `anonymousExpiresAt` hesapta `null` değil, JSON'da
**hiç yok** — tipleriniz opsiyonel okusun; olmayan bir limite karşı çizilen
ilerleme çubuğu yanlış bir ekran. `allowedTemplates` bugün `["classic"]`.

### B-047 · LinkedIn ile giriş kaldırıldı
**Since:** commit <sha> · Adım 3.3 · **Spec:** `spec/02-tech-stack.md`

Uygulama açmak doğrulanmış bir şirket sayfası istiyordu; karşılığı Google ile
GitHub'ın zaten verdiği giriş.

**Aksiyon:** LinkedIn düğmesini çıkarın. **Karıştırmayın:** CV'deki
`contact.linkedin` alanı duruyor — o iletişim bilgisi, kimlik sağlayıcısı değil.

### B-048 · OAuth indi — sizden iki rota
**Since:** commit <sha> · Adım 3.3 dilim 2 · **Spec:** `spec/10-security.md` § 40.6.1

`GET /auth/providers` → yapılandırılmış sağlayıcılar (anahtarı olmayan sessizce
yok). `GET /auth/oauth/{provider}/start?next=/profile` → 302 sağlayıcıya.

**`/auth/complete?next=...`** — başarılı girişin indiği yer. Doğrudan hedefe
yönlendirmiyoruz: `SameSite=Strict` çerezi, zinciri başka bir sitede başlamış
bir isteğe **gönderilmez** — zincir Google'da başladı, ve ilk sayfa çıkışlı
görünürdü. Bu sayfa `/auth/session`'ı **aynı-origin fetch** ile sorsun, sonra
`next`'e gitsin.

**`/auth/error?code=OAUTH_FAILED&reason=...`** — hata burada iniyor.
`OAUTH_FAILED` tek kod, yedi sebep (`F-016`'nın şekli): `state_invalid`,
`declined`, `provider_disabled`, `provider_unavailable`, `email_missing`,
`email_unverified`, `account_disabled`. **`declined` kullanıcının vazgeçmesi,
hata değil.** `next` sunucuda doğrulanıyor: yalnız düz bir yol, gerisi `/`.

### B-049 · Magic link indi — bir rota ve bir tuzak
**Since:** commit <sha> · Adım 3.3 dilim 3 · **Spec:** `spec/10-security.md` § 40.4.1

`POST /auth/magic-link` `{email}` → **her zaman `202`, gövdesiz**. Hesabın var
olup olmadığı tam da gizlenecek şey (§ 40.4); "kayıtlıysa gönderildi"
cümlesini **siz** yazıyorsunuz ve iki halde de aynı.

**Sizden bir rota: `/verify?s=..&v=..`** — bağlantı buraya iner, ve bu `GET`
**doğrulama yapmamalı**: kurumsal posta tarayıcıları bağlantılara otomatik
tıklıyor, tek kullanımlık tokenı tarayıcı harcarsa kullanıcı hiç giremiyor
(§ 40.3). Sayfa bir düğme göstersin; düğme `POST /auth/verify`
`{selector, verifier}` yapsın → `204` + oturum çerezi.

**Tuzak — o sayfa önce bir `GET` yapmak zorunda.** `POST /auth/verify` CSRF
tokenı istiyor, token `XSRF-TOKEN` çerezinden okunuyor, ve e-postadan gelen
kullanıcının tarayıcısında o çerez **henüz yok**. Sayfa yüklenince önce
`/auth/session`'ı çağırın, sonra POST edin — yoksa her magic link girişi 403
alır.

**Yeni kod `MAGIC_LINK_INVALID`, `params` yok — bilerek.** Süresi dolmuş,
kullanılmış, yanlış ve hiç var olmamış **tek bir cevap**; ayırt edilebilirse
tahmin yürüten kişi hangi yarının doğru olduğunu öğrenir. Tek bir metin yazın,
sebep sormayın.

### B-050 · Magic link isteği artık bir Turnstile tokenı istiyor, ve 429 dönebiliyor
**Since:** commit <sha> · Adım 3.3 dilim 4 · **Spec:** `spec/10-security.md` § 40.5.1

**`B-049`'un ucu değişti.** `POST /auth/magic-link` gövdesi artık
`{email, challengeToken}`. Turnstile widget'ını o forma koyun ve ürettiği
tokenı `challengeToken` olarak gönderin; site key sizde, secret bizde.

**Alan adı `challengeToken`, `turnstileToken` değil** — kod tarafında da
`CHALLENGE_FAILED`. `OTLP_*` kararının aynısı: bu kodu siz render edip mesaj
kataloğunuzda saklıyorsunuz, Cloudflare'den çıkmak kullanıcıya görünen bir
cümleyi yalana çevirmemeli.

**İki yeni cevap, ikisi de `202` yerine geçebilir:**

- **`403 CHALLENGE_FAILED`**, parametresiz. Eksik, süresi dolmuş, harcanmış ve
  sahte token tek bir cevap — çünkü hepsinde yapılacak şey aynı: **widget'ı
  sıfırlayın ve yeniden sordurun.** Token tek kullanımlık, yani başarısız bir
  gönderimden sonra eskisini tekrar yollamak da bu hatayı verir.
- **`429 RATE_LIMITED`**, `params.resetsAt` (mutlak an) + `Retry-After`
  başlığı (saniye). Sınırlar: adres başına 3/15dk, IP başına 10/sa. **Cümleyi
  `Retry-After`'dan kurun, `resetsAt`'ten değil** — kullanıcının saati yanlışsa
  doğru olan tek şey o; `resetsAt`'i yalnız "şu saatte tekrar deneyin" yazacaksanız
  kullanın. Hangi katmanın reddettiğini yayınlamıyoruz, tek metin yazın.

**Yerelde `challengeToken` göndermeseniz de çalışır** — secret'ı olmayan bir
dağıtımda challenge kapalı ve istek geçiyor. Bu bir kolaylık, sözleşme değil:
üretimde alan boşsa istek `403` alır, o yüzden widget'ı en baştan takın.

### B-051 · CV yükleme telde — bir uç, beş ret, bir iş
**Since:** commit <sha> · Adım 3.4 · **Spec:** `spec/07-subsystems.md` § 31.2, § 31.6.1

**`POST /api/v1/profile/import`**, `multipart/form-data`, tek parça: `file`.
Cevap **`202` + `Location: /api/v1/jobs/{id}`** ve gövdede `jobId` — üretimle
**birebir aynı kalıp**, yani `AcceptedJobResponse` ve SSE akışı sizde zaten var.

**`Idempotency-Key` gönderin.** Yükleme, kötü bir bağlantının en kolay
tekrarlattığı istek, ve profil çıkarımının günlük hakkı üründeki **en küçük**
hak. Aynı anahtarla ikinci istek aynı işi döndürüyor ve ikinci birim
harcanmıyor.

**Beş ret, hepsi senkron** — dosya hakkında karar verilebilecek her şey 202'den
önce veriliyor, sekiz saniye sonra düşen bir işle değil:

| Kod | HTTP | Ne yapmalı |
|---|---|---|
| `UNSUPPORTED_DOCUMENT` | 415 | **`params.accepted`'ı okuyun** — kabul edilen uzantı listesi orada |
| `DOCUMENT_TOO_LARGE` | 413 | `params.limitBytes` |
| `PDF_ENCRYPTED` | 422 | "açık bir kopya yükleyin" |
| `PDF_NOT_TEXT_BASED` | 422 | "taranmış görsel olabilir; metin tabanlı PDF ya da elle giriş" |
| `EXTRACTION_EMPTY` | 422 | "bilgi çıkaramadık" → manuel form |
| `PROFILE_QUOTA_EXCEEDED` | 429 | `params.limit` + `resetsAt`, ve `Retry-After` başlığı |

**Dosya seçicinin `accept` listesini gömmeyin.** `415`'in `params.accepted`'ı
kabul edilen uzantıları yayınlıyor (`pdf`, `docx`, `tex`, `txt`, `md`); tek
sahibi sunucu, ve bir biçim eklendiğinde mesajınız sizin sürümünüzü beklemiyor.

**İşin terminal olayı** şunları taşıyor: `profileId`, `sectionCount`,
`atomCount`, `warningCount`, `detectedLanguage`. `warningCount` orada, çünkü
§ 31.6'nın gözden geçirme ekranı **sorunlu bölümleri açık** başlatmalı ve bunu
profili çekmeden önce bilmeniz gerekiyor.

**İş de başarısız olabilir**, ve üçü sizin: `LANGUAGE_UNDETECTED` (422,
`params.detectedCandidates` — en fazla tek elemanlı; kullanıcıya dili sorun),
`EXTRACTION_EMPTY` (422 → manuel form), `ALL_PROVIDERS_UNAVAILABLE` (503 →
tekrar dene; bu **tekrar edilebilir** olan tek ret).

**İki şey henüz yok, ikisi de sizde bir şey değiştirmiyor:** arka plandaki
embedding ve ölçüm tetiklemesi (§ 31.6'nın `t=25s` kutusu) bir sonraki dilimde;
`local-fake` için gerçek bir fixture da orada. Bugün yerel geliştirmede çıkan
profil şema şeklinde ama anlamsız — **ucun sözleşmesi doğru, içeriği değil.**

### B-052 · Bir sözcüklemeyi düzenlemek ötekileri bayatlatıyor — ekranı siz kuruyorsunuz
**Since:** commit <sha> · Adım 3.5 · **Spec:** `spec/07-subsystems.md` § 32.2, § 32.2.1

Kullanıcı Türkçe maddeyi düzenleyince İngilizcesi **bayat** işaretleniyor.
Sunucu ne yapacağına kendi karar vermiyor — **siz soruyorsunuz.**

**Varyant nesnesi artık iki bayrak taşıyor**, ve uyarı ikisinin **çiftinden**
kuruluyor:

| `stale` | `userEdited` | Ne demek | Ekranda |
|---|---|---|---|
| `false` | — | Güncel | — |
| `true` | `false` | Kaynağı değişti, **arka planda yenileniyor** | "güncelleniyor" göstergesi yeter |
| `true` | `true` | Kaynağı değişti **ama bu sözcüklemeyi sen yazdın** | § 32.2'nin iki düğmesi |

Üçüncü satır maddenin tamamı. Sunucu, kullanıcının kendi yazdığı bir
sözcüklemeyi **asla** kendiliğinden yenilemiyor; onu yalnız işaretleyip
bırakıyor. § 32.2'nin metni:

> ⚠ Bu maddenin Türkçe hali güncellendi, İngilizce halini sen düzenlemiştin.
>   [ İngilizceyi yeniden üret ] [ Benim halimi koru ]

**"Yeniden üret" düğmesi bağlanabilir** — varyanta `PATCH` gönderin, gövdede
`{"userEdited": false}`. Sunucu bayrağı temizliyor ve satır bayatsa çeviriyi
**hemen** kuyruğa alıyor. `{"userEdited": true}` **reddediliyor**: bir
sözcükleme kelime yazarak sizin olur, iddia ederek değil.

"Benim halimi koru" sunucuya hiçbir şey sormuyor: kullanıcı uyarıyı kapatır,
satır bayat kalır. Bu doğru davranış, eksik değil.

**Yenileme başarısız olabilir** ve bu da sessiz: iş `TRANSLATION_FAILED` (422,
parametresiz) ile düşerse sözcükleme **bayat kalır**. Ekranınız zaten doğru
şeyi gösteriyor olur; ayrıca bir hata bildirimi göstermeyin.

### B-053 · Anonim kullanıcı artık CV yükleyebiliyor — aynı uç, aynı kalıp
**Since:** commit <sha> · Adım 3.6 · **Spec:** `spec/07-subsystems.md` § 31.6.3

`POST /api/v1/profile/import` **hesap istemiyor**. `GET /api/v1/session` ile
alınan anonim oturum çerezi yeterli; `B-051`'in her satırı aynen geçerli —
`202`, `Location`, `Idempotency-Key`, beş senkron ret, aynı terminal olay.
**Ayrı bir uç, ayrı bir akış yok**; hesabı olan ve olmayan için tek kod yolu
yazın.

**İşin `jobId`'sini anonim çağıran da izleyebiliyor.** `GET /api/v1/jobs/{id}`
ve SSE akışı oturumla yetkilendiriliyor — çerez giderse iş de erişilemez olur,
ki § 41.3'ün bilinçli tercihi bu.

**Üç fark, üçü de sizde bir şey değiştirebilir:**

| Ne | Anonimde | Hesapta |
|---|---|---|
| Günlük hak | **adrese** göre sayılıyor, oturuma göre değil (§ 44.1) | kullanıcıya göre |
| `PROFILE_QUOTA_EXCEEDED` | aynı ofisten başkası harcamış olabilir — mesaj "hakkınız doldu" demesin, "şu an bu ağdan daha fazla deneme yapılamıyor" desin | kişiye ait |
| Profilin ömrü | oturumun TTL'i (etkinlikle kayan iki saat) | kalıcı |

**Anonim profil hiçbir tabloda satır değil** — Redis'te tek bir belge. Bunun
sizin için tek pratik sonucu: **profil ekranında "kaydedildi" demeyin.** § 9'un
sözü tam olarak bu; kaydolmadan çalışan kişi arkasında bir şey bırakmıyor, ve
yükseltme akışı (bir sonraki dilim) inene kadar iki saat sonrası yok.

**Anonim içe aktarımda embedding ve ölçüm koşmuyor.** İlk üretim vektörsüz
skorlama (§ 28.4) ve ölçülmemiş tahmin (§ 20.4) ile çalışıyor — ikisi de zaten
tarif edilmiş bozulmuş-ama-çalışan yol. Kullanıcıya bunu söylemeyin; söylenecek
tek şey seçim tahminî olduğunda ekranın zaten gösterdiği not.

### B-054 · Giriş yanıtı değişti: anonim profilin ne olduğunu söylüyor
**Since:** commit <sha> · Adım 3.6 · **Spec:** `spec/10-security.md` § 41.3.3

**`POST /api/v1/auth/verify` artık `204` değil `200`**, ve gövdesi tek alan:
`{"profileUpgrade": "..."}`. OAuth tarafında aynı bilgi iniş adresinde:
`/auth/complete?next=...&profile=...`. **`204` bekleyen istemci kırılır.**

Dört değer, dört farklı cümle:

| Değer | Ne oldu | Ne göstermeli |
|---|---|---|
| `none` | Kişi hiçbir şey taşımıyordu — girişlerin çoğu | **Hiçbir şey.** Bu normal giriş |
| `upgraded` | Anonim profil artık hesabın | Kısa bir onay yeter; profil zaten orada |
| `kept_existing` | Hesabın zaten profili vardı, anonim olan **taşınmadı** | Kişiye söyleyin: az önce yüklediği CV hesabına geçmedi, mevcut profili duruyor |
| `unavailable` | Depo okunamadı, çalışma kayboldu | Bunu "taşınacak bir şey yoktu" gibi göstermeyin — bir aksaklık oldu deyin |

**`kept_existing` ve `unavailable`'da anonim profil artık erişilemez.** Çerez
değişti, ve anonim profil tam olarak o çerezle kapsanıyordu (§ 41.3). Yani bu
iki durumda kişinin iki saatlik emeği gitmiş oluyor — söylenmesi gereken şey
bu, "birleştirebilirsin" değil. **Birleştirme akışı yok ve planlanmadı**;
ürün kararı verilmedi.

**Yükseltilen profil aynı profil.** Atom ve bölüm id'leri değişmiyor, yani
anonim ekranda tuttuğunuz seçimler ve açık/kapalı durumlar giriş sonrası hâlâ
geçerli — id'lerle eşleştiriyorsanız yeniden yüklemeniz gerekmiyor.

### B-055 · Üretim akışında yeni bir faz görünüyor: `REWRITING`
**Since:** commit <sha> · Adım 3.8 · **Spec:** `spec/06-pipeline-d-g.md` § 21

İlana özel üretim artık Faz D'yi koşuyor: seçilen maddelerin en fazla sekizi
ilana göre yeniden yazılıyor. SSE ilerleme akışında bunun karşılığı **yeni bir
faz anahtarı**:

```
{"phase":"D","labelKey":"generation.phase.REWRITING","pct":60}
```

**Yapmanız gereken tek şey `generation.phase.REWRITING` çevirisini eklemek**
(TR/EN). Anahtarı tanımayan bir ekran bugün ya boş bir satır ya da ham anahtarı
gösterir — kırılmaz, ama %60'ta okunmaz bir şey yazar.

Sıra: `ANALYSING` (10) → `MEASURING` (30) → `SCORING` (50) → **`REWRITING`
(60)** → `RENDERING` (70). Faz D **her üretimde görünmez**: genel CV modunda
hiç koşmuyor, ve ilan hiçbir beceri adı taşımıyorsa atlanıyor. Yani bu fazı
görmemek bir hata değil, ve ilerleme çubuğu 50'den 70'e atlayabilir.

**Yeniden yazma sessizce başarısız olabilir ve bu doğru davranış.** Bir madde
denetimden geçmezse kişinin kendi cümlesi basılıyor; ne bir hata kodu ne bir
uyarı iniyor. Kullanıcıya "yeniden yazıldı/yazılmadı" diye bir şey göstermeyin
— CV zaten doğru CV.

### B-056 · Cover letter telde — bir bayrak, bir uç, ve reddedilebilir
**Since:** commit <sha> · Adım 3.8 · **Spec:** `spec/07-subsystems.md` § 34

**İki yol var ve ikisi de sizde.**

1. `POST /api/v1/generations` gövdesine **`"coverLetter": true`** — CV ile
   birlikte yazılır. **Varsayılan `false`**, çünkü ikinci bir LLM çağrısı.
2. **`POST /api/v1/generations/{id}/cover-letter/regenerate`** — sonradan, ya da
   yeniden. Gövde tamamen opsiyonel:
   `{"style": "default|shorter|more_formal", "companyNote": "..."}`.
   Boş gövde (`{}`) geçerli bir istek.

Yanıt: `{"generationId", "coverLetter", "style"}`. `GET /generations/{id}` de
artık **`coverLetter`** alanı taşıyor (yazılmadıysa alan yok). **Düz metin**,
paragraflar arası boş satırla — § 34.7 belge üretmiyor, çünkü mektup bir forma
ya da e-postaya yapıştırılıyor.

**Yeni ve önemli: bu uç reddedebilir.** `422 COVER_LETTER_REJECTED`,
`params.issues` bir dizi (`unsupported_claim`, `number_invented`,
`experience_overstated`, `wrong_company`, `length_out_of_range`, `cliche`),
çözüm eylemi `retry`. Sebebi: mektubun arkasında **orijinal yok** — CV'de
reddedilen bir cümlenin yerine kişinin kendi cümlesi basılıyor, mektupta
basılacak bir şey yok. **Bunu bir hata ekranı gibi göstermeyin**; "bu taslak
denetimden geçmedi, tekrar dene" doğru cümle. `issues` kullanıcıya ne olduğunu
söylemek için orada.

`429 RATE_LIMITED` da mümkün: saatte on mektup. `params.resetsAt` var.

**Üretim sırasında istenen mektup CV'yi düşürmez.** `coverLetter: true` ile
üretilen bir CV'de mektup yazılamadıysa iş yine `completed` oluyor ve
`GET /generations/{id}` mektup alanını taşımıyor — düğmeyle tekrar istenebilir.

### B-057 · Hesap silme telde — `DELETE /api/v1/account`
**Since:** commit <sha> · Adım 3.9 · **Spec:** `spec/16-cost-legal.md` § 57.4

`DELETE /api/v1/account` → **`204`**, ve yanıt `Set-Cookie` ile oturum çerezini
temizliyor. Gövde yok, onay alanı yok: uç zaten oturum + CSRF arkasında.
**"Emin misin" ekranı sizde** — ve neyin gittiğini saymalı, çünkü geri dönüşü
yok: profil, atomlar, bütün üretimler ve belgeleri, kuyruk işleri, sayaçlar,
e-posta tercihleri.

İki şey **bilerek kalıyor** ve gizlilik metninde de böyle yazmalı: maliyet
geçmişi (kullanıcı bağı koparılmış olarak — artık kimseyi göstermiyor) ve
hard bounce/şikâyet etmiş bir adresin suppression kaydı (adrese ait, hesaba
değil; silmek o adrese yeniden posta atmamıza izin verirdi). Ayrıca **LLM
sağlayıcıları kendi taraflarında kısa süreli log tutabiliyor** — § 57.4 bunun
kullanıcıya söylenmesini istiyor, yeri gizlilik politikası.

**İkinci basış da `204`.** Silinmiş hesabı tekrar silmek hata değil; idempotent.

Silmeden sonra çerez temizlendiği için istemci **anonim** duruma düşüyor —
`GET /auth/session` yeni bir anonim oturum verir. Kullanıcıyı ana sayfaya
atmak doğru davranış.

### B-058 · Geri bildirim telde — bir başparmak, ve 48 saatlik bir izin
**Since:** commit <sha> · Adım 3.9 · **Spec:** `spec/11-operations.md` § 48.4

`POST /api/v1/generations/{id}/feedback` → `200`.

```jsonc
{ "rating": 1,                 // 1 | -1, ZORUNLU, başka değer 400
  "category": "density",       // selection|writing|format|density|other, ops.
  "comment": "...",            // ops., en fazla 4000
  "contentGranted": false }    // ops.
```

**Yalnız başparmak zorunlu.** Sebep sormadan önce yargıyı kabul eden bir form
daha çok ve daha iyi yargı toplar — kategori ile yorum, söyleyecek şeyi olanlar
için.

**Üretim başına tek yargı.** Öbür başparmağa basmak fikrini değiştirmek; ikinci
bir satır açılmıyor, var olan güncelleniyor. Ekranda "geri bildirimini
gönderdin" yerine **mevcut seçimi göstermek** doğru davranış.

**`contentGranted` § 48.4'ün rızası ve asıl dikkat isteyen yer.** Bu üründe
başka her şey şekillerden teşhis ediliyor (karakter sayısı, satır sayısı,
render maliyeti); bu, içeriğe açılan **tek kapı**. İşaretlemek 48 saat açıyor,
`false` göndermek **geri alıyor**. Yanıt grant'i geri yolluyor:

```jsonc
"contentGrant": { "open": true, "expiresAt": "...",
                  "accessedAt": null, "revokedAt": null }
```

**`accessedAt` biri gerçekten bakana kadar `null`** — ve kişiye gösterilmeli.
Kontrol edilemeyen bir onay kutudan ibaret. Ekranda "izin verdin, henüz
bakılmadı / şu tarihte bakıldı" cümlesi bu alandan kuruluyor. İkinci bir "evet"
pencereyi ileri **itmiyor**; 48 saat ilk kabulden başlıyor.

**Yorum geri yollanmıyor** (mutlak kural 4 ile aynı sebep: kişi onu zaten
yazdı, elinde). Saklanıyor ama loglanmıyor.

### B-059 · Gizlilik Politikası'na iki alt işleyen ve bir bölge
**Since:** Adım 3.2 · **Spec:** `spec/14-build-guide.md` § 3.2, `spec/16-cost-legal.md`
**Action:** Politika sayfasındaki alt işleyen listesine e-posta yolunu ekleyin —
bugün orada yok.

Giriş bağlantıları **Resend** üzerinden gidiyor, Resend de altta **AWS SES**
kullanıyor. Bu kurulumun bölgesi **`ap-northeast-1` (Tokyo)** ve öyle kalıyor
(2026-08-28 kararı) — yani e-posta adresi ve gönderim üstverisi **AB dışında**
işleniyor. Politika "veriler AB'de işlenir" gibi bir cümle taşıyorsa yanlış;
taşımıyorsa bile liste eksik.

Yazılacak asgari şey: *e-posta teslimatı — Resend (AWS SES, Tokyo)*.

EK C.1'in "Gizlilik Politikası yayında ve sağlayıcı listesi doğru" maddesi
yayından önce bunu istiyor. **Bu maddeyi kapatmadan MVP yayına alınmamalı.**

### B-060 · İkinci CV artık 409 dönüyor — iki yeni resolution, bir query parametresi
**Since:** Adım 3.4 · **Spec:** `spec/08b-api-contract.md` (409 satırı), `spec/07-subsystems.md` § 31.6.2
**Action:** `POST /api/v1/profile/import` yeni bir senkron ret üretiyor ve
**resolution sözlüğü ikiye büyüdü** — ICU mesajı olmayan bir action ham anahtar
olarak ekrana düşer.

Hesabın **içeriği olan** bir profili varken yükleme **`409
PROFILE_ALREADY_EXISTS`** alıyor. Gövdedeki `resolutions`:

| action | ne yapmalı |
|---|---|
| `replace_profile` | Aynı isteği `?mode=replace` ile tekrar gönderin. Mevcut profil silinir, CV yenisi olur. |
| `keep_existing_profile` | İsteği bırakın. Profil olduğu gibi kalır. |

**Üçüncü bir seçenek yok ve olmayacak — birleştirme sunmayın.** Atom düzeyinde
tekilleştirme demek (Bölüm 7) ve Aşama 4 işi; şimdi sunmak ya yapamayacağınız
bir eylemi adlandırır ya da içeriği sessizce çoğaltır (P8).

**"Profil var mı" değil, "içinde bir şey var mı".** Bir kez giriş yapıp
uygulamayı açan herkeste boş bir profil satırı oluşuyor; boş satır 409
üretmiyor, ilk yükleme sorunsuz geçiyor.

**`mode` yalnız `replace` değerini tanıyor**; başka her şey yokmuş gibi
okunuyor — bir yazım hatası onay yerine geçmesin diye.

---

## ACK — frontend tamamladı, backend arşivleyebilir

_(`B-037`…`B-043` `resolved/to-frontend-2026-08.md`'de)_

---

## Kalıcı kurallar

Eski maddelerin `spec/`'e işlendiği yerlerin tablosu
`resolved/to-frontend-2026-08.md`'ye taşındı (2026-08-24) — dosya sınırı.
