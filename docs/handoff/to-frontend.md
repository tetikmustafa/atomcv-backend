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

### B-044 · Her yazma isteği artık bir CSRF token taşıyor
**Since:** commit <sha> · Adım 3.3 · **Spec:** `spec/08b-api-contract.md` § EK D.6.6

**Neden:** Kimlik indi, ve § D.6.6 CSRF'i "kimlikle birlikte gelir" diye
bağlamıştı. Çift-gönderim (double submit) deseni açık: sunucu her yanıtta
okunabilir bir `XSRF-TOKEN` çerezi veriyor, istemci güvensiz metotlarda
(`POST`/`PUT`/`PATCH`/`DELETE`) bunu `X-XSRF-TOKEN` başlığında yankılıyor.

**Aksiyon:** `client.ts`'teki fetch sarmalayıcısına iki satır. Çerez `HttpOnly`
**değil** — okunması gerekiyor, ve bu güvenli: token tek başına bir kimlik
belgesi değil, `sid` olmadan hiçbir şey kanıtlamıyor. `sid` `HttpOnly` kalıyor.

Yankılamayan bir istek **`403` + `CSRF_TOKEN_INVALID`** alıyor — kod katalogda
zaten vardı, artık telde de var. Bu kodu gören istemcinin doğru davranışı
tekrar denemek değil, tokenı yeniden okumak: `GET` yanıtlarının hepsi taze bir
tane taşıyor.

**Bir tuzak:** çerez `SameSite=Strict` ve host'a bağlı. Geliştirmede frontend
`:3000`, backend `:8080` ise `document.cookie` onu **göremez** — Next.js
rewrite'ı üzerinden tek origin'den geçmeniz gerekiyor. Üretimde nginx zaten
tek origin veriyor.

### B-045 · Yeni hata kodu — `AUTHENTICATION_REQUIRED` (401)
**Since:** commit <sha> · Adım 3.3 · **Spec:** `spec/08b-api-contract.md` § EK D.6

**Neden:** Katalogda **oturumu hiç olmayan** bir isteğin karşılığı yoktu.
`ANONYMOUS_SESSION_EXPIRED` süresi *dolmuş* anonim oturum için,
`FEATURE_REQUIRES_ACCOUNT` erişilemeyen bir *özellik* için. `sid` çerezi hiç
gelmeyen bir isteğin cevabı yazılmamıştı; ikisinden birini ödünç almak sunucuya
hiç var olmamış bir oturum hakkında cümle kurdururdu.

**Aksiyon:** `errors.AUTHENTICATION_REQUIRED` anahtarı. `params` yok, tek
resolution `sign_up`. Adım 3.6 çerezi olmayana anonim oturum basmaya
başlayınca bu kod **nadirleşir ama yanlış olmaz** — kullanıcı kapsamlı bir uca
hiçbir şey taşımadan gelen isteğin cevabı olarak kalır.

### B-046 · `/auth/session`, `/auth/logout`, ve hesabın yetenek kümesi
**Since:** commit <sha> · Adım 3.3 · **Spec:** `spec/08-api.md` § 35.7

**Neden:** İkisi de telde. `GET /api/v1/auth/session` → `authenticated` +
`capabilities`, `no-store`. `POST /api/v1/auth/logout` → `204`, oturumu
sunucuda iptal eder ve çerezi siler; oturumsuz çağrılması da `204`.

**Ama asıl söylenmesi gereken şu: § 35.7 yalnız *anonim* kümeyi yazmış.**
Örnek gövdesi `"authenticated": false` taşıyor ve hesaplı hâli hiçbir bölümde
tarif edilmemiş. Sessizce uydurmak yerine kararı burada bildiriyoruz:

| Alan | Anonim (§ 35.7) | Hesap (yeni) |
|---|---|---|
| `allowedLanguages` | `["en"]` | `["en", "tr"]` — yapılandırılabilir |
| `canCustomizeTemplate` / `canEditAtomControls` / `canAddAlternatives` / `canSaveHistory` | `false` | `true` |
| `dailyGenerationQuota` / `dailyProfileQuota` | 5 / 3 | `QuotaService`'ten — bugün 20 / 5 |
| `maxAtoms` | 60 | **alan yok** |
| `quotaResetsAt` | `null` | mutlak an (EK D.6.5) |
| `anonymousExpiresAt` | Adım 3.6'da | **alan yok** |

**Aksiyon:** `maxAtoms` ve `anonymousExpiresAt` hesapta **yok** — `null` değil,
JSON'da hiç bulunmuyorlar. Bir hesap için tavan yok, ve olmayan bir limite
karşı çizilen bir ilerleme çubuğu yanlış bir ekran. Tipleriniz ikisini de
opsiyonel okumalı.

**`allowedTemplates` bugün tek eleman taşıyor: `["classic"]`.** § 35.7'nin
örneği üçünü sayıyor ama kayıtta bir tane var; render edilemeyecek bir şablonu
listelemek üretim anında patlayan bir seçenek olurdu. İkincisi geldiğinde
liste kendiliğinden büyür, `gen:api` fark üretmez.

**`sid` çerezi anonim ve hesaplı oturumda aynı** (§ D.6.6). Kimlik doğrulama
istemci tarafında bir `capabilities` sorusu olarak kalıyor.

**Henüz giriş yolu yok.** OAuth bir sonraki dilim; şu an oturum başlatan tek
şey `local` profilindeki geliştirme kısayolu. Yani `authenticated` bugün
geliştirmede hep `true`, üretimde hep `false`.

---

## ACK — frontend tamamladı, backend arşivleyebilir

_(`B-037`…`B-043` `resolved/to-frontend-2026-08.md`'de)_

---

## Kalıcı kurallar

Eski maddelerin `spec/`'e işlendiği yerlerin tablosu
`resolved/to-frontend-2026-08.md`'ye taşındı (2026-08-24) — dosya sınırı.
