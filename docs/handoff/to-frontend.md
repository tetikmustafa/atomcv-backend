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

**Neden:** § D.6.6 CSRF'i "kimlikle birlikte gelir" diye bağlamıştı; kimlik indi.
Çift-gönderim: sunucu her yanıtta okunabilir bir `XSRF-TOKEN` çerezi veriyor,
istemci güvensiz metotlarda (`POST`/`PUT`/`PATCH`/`DELETE`) bunu
`X-XSRF-TOKEN` başlığında yankılıyor.

**Aksiyon:** `client.ts`'e iki satır. Çerez `HttpOnly` **değil** — okunması
gerekiyor, ve güvenli: token `sid` olmadan hiçbir şey kanıtlamıyor, `sid` ise
`HttpOnly` kalıyor. Yankılamayan istek `403` + `CSRF_TOKEN_INVALID` alır; doğru
davranış tekrar denemek değil tokenı yeniden okumak — her `GET` taze bir tane
taşıyor.

**Tuzak:** çerez `SameSite=Strict` ve host'a bağlı; ayrı portlarda
(`:3000`/`:8080`) `document.cookie` göremez. Next.js rewrite'ı üzerinden tek
origin'den geçin — OAuth callback'i de dahil.

### B-045 · Yeni hata kodu — `AUTHENTICATION_REQUIRED` (401)
**Since:** commit <sha> · Adım 3.3 · **Spec:** `spec/08b-api-contract.md` § EK D.6

**Neden:** Katalogda **oturumu hiç olmayan** isteğin karşılığı yoktu.
`ANONYMOUS_SESSION_EXPIRED` süresi *dolmuş* oturumun,
`FEATURE_REQUIRES_ACCOUNT` erişilemeyen bir *özelliğin* adı; ikisinden birini
ödünç almak sunucuya hiç var olmamış bir oturum hakkında cümle kurdururdu.

**Aksiyon:** `errors.AUTHENTICATION_REQUIRED` anahtarı. `params` yok, tek
resolution `sign_up`. Adım 3.6 anonim oturum basmaya başlayınca **nadirleşir,
yanlış olmaz.**

### B-046 · `/auth/session`, `/auth/logout`, ve hesabın yetenek kümesi
**Since:** commit <sha> · Adım 3.3 · **Spec:** `spec/08-api.md` § 35.7

**Neden:** `GET /api/v1/auth/session` → `authenticated` + `capabilities`,
`no-store`. `POST /api/v1/auth/logout` → `204`, oturumu sunucuda iptal eder ve
çerezi siler; oturumsuz çağrılması da `204`.

**Asıl mesele: § 35.7 yalnız *anonim* kümeyi yazmış** (`"authenticated": false`),
hesaplı hâli hiçbir bölümde yok. Uydurmak yerine karar § 35.7'ye işlendi:

| Alan | Anonim | Hesap |
|---|---|---|
| `allowedLanguages` | `["en"]` | `["en","tr"]`, yapılandırılabilir |
| `canCustomizeTemplate` · `canEditAtomControls` · `canAddAlternatives` · `canSaveHistory` | `false` | `true` |
| `dailyGenerationQuota` · `dailyProfileQuota` | 5 · 3 | `QuotaService`'ten — bugün 20 · 5 |
| `maxAtoms` | 60 | **alan yok** |
| `quotaResetsAt` | `null` | mutlak an (EK D.6.5) |
| `anonymousExpiresAt` | Adım 3.6'da | **alan yok** |

**Aksiyon:** `maxAtoms` ve `anonymousExpiresAt` hesapta `null` değil, JSON'da
**hiç yok** — tipleriniz ikisini de opsiyonel okumalı; olmayan bir limite karşı
çizilen ilerleme çubuğu yanlış bir ekran. `allowedTemplates` bugün tek eleman:
`["classic"]` — kayıtta bir tane var, render edilemeyecek bir şablonu
listelemek üretimde patlayan bir seçenek olurdu. İkincisi gelince liste
kendiliğinden büyür.

**Henüz giriş yolu yok.** OAuth sıradaki dilim; bugün oturumu yalnız `local`
kısayolu başlatıyor, yani `authenticated` geliştirmede hep `true`, üretimde hep
`false`.

### B-047 · LinkedIn ile giriş kaldırıldı
**Since:** commit <sha> · Adım 3.3 · **Spec:** `spec/02-tech-stack.md`, `spec/13-development.md`

**Neden:** Geliştirici kararı. Üçünün içinde yalnız LinkedIn, uygulama
açabilmek için **doğrulanmış bir şirket sayfası** istiyor, ve karşılığında
Google ile GitHub'ın zaten verdiği girişi veriyor.

**Aksiyon:** Giriş ekranından LinkedIn düğmesini çıkarın. Kalanlar `google` ve
`github`; `V2` migration'ı `oauth_identities.provider` CHECK'ini de ikiye
indirdi. **Karıştırmayın:** CV'deki `contact.linkedin` alanı duruyor — o bir
iletişim bilgisi, kimlik sağlayıcısı değil.

---

## ACK — frontend tamamladı, backend arşivleyebilir

_(`B-037`…`B-043` `resolved/to-frontend-2026-08.md`'de)_

---

## Kalıcı kurallar

Eski maddelerin `spec/`'e işlendiği yerlerin tablosu
`resolved/to-frontend-2026-08.md`'ye taşındı (2026-08-24) — dosya sınırı.
