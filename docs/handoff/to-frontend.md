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

> **Dosya şu an 100 satır sınırının üstünde, ve sebebi arşivleme gecikmesi
> değil:** dokuz madde birden açık ve hiçbiri henüz `ACK` almadı, yani
> taşınacak bir şey yok. Gerekçelerin kalıcı olanı `spec/`'e işlendi, burada
> yalnız *ne yapman lazım* duruyor. İlk `ACK`'lerle sınırın altına düşecek.

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

**"Yeniden üret" için ayrı bir uç yok** — o düğme, İngilizce varyanta bir
`PATCH` ile `userEdited`'ı geri almalı. **Bunu şimdilik yapamazsınız:** varyant
`PATCH`'i içerik yazınca `userEdited`'ı `true` yapıyor ve geri almanın yolu yok.
Bir sonraki dilimde bir uç açacağım — **düğmeyi çizin, bağlamayın.**
"Benim halimi koru" ise sunucuya hiçbir şey sormuyor: kullanıcı uyarıyı
kapatır, satır bayat kalır. Bu doğru davranış, eksik değil.

**Yenileme başarısız olabilir** ve bu da sessiz: iş `TRANSLATION_FAILED` (422,
parametresiz) ile düşerse sözcükleme **bayat kalır**. Ekranınız zaten doğru
şeyi gösteriyor olur; ayrıca bir hata bildirimi göstermeyin.

---

## ACK — frontend tamamladı, backend arşivleyebilir

_(`B-037`…`B-043` `resolved/to-frontend-2026-08.md`'de)_

---

## Kalıcı kurallar

Eski maddelerin `spec/`'e işlendiği yerlerin tablosu
`resolved/to-frontend-2026-08.md`'ye taşındı (2026-08-24) — dosya sınırı.
