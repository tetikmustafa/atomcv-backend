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

### B-044 · Her yazma isteği bir CSRF token taşıyor
**Since:** commit <sha> · Adım 3.3 · **Spec:** `spec/08b-api-contract.md` § EK D.6.6

Çift-gönderim: sunucu her yanıtta okunabilir bir `XSRF-TOKEN` çerezi veriyor,
istemci güvensiz metotlarda (`POST`/`PUT`/`PATCH`/`DELETE`) `X-XSRF-TOKEN`
başlığında yankılıyor. Yankılamayan istek `403 CSRF_TOKEN_INVALID` alır.

**Aksiyon:** `client.ts`'e iki satır. Çerez `HttpOnly` **değil** — okunması
gerekiyor, ve `sid` olmadan hiçbir şey kanıtlamıyor. Kodu gören istemci tekrar
denemesin, tokenı yeniden okusun: her `GET` taze bir tane taşıyor.

**Tuzak:** çerez `SameSite=Strict` ve host'a bağlı; ayrı portlarda
(`:3000`/`:8080`) `document.cookie` göremez. Next.js rewrite'ı üzerinden tek
origin'den geçin — OAuth callback'i de dahil.

### B-045 · Yeni hata kodu — `AUTHENTICATION_REQUIRED` (401)
**Since:** commit <sha> · Adım 3.3 · **Spec:** `spec/08b-api-contract.md` § EK D.6

Katalogda **oturumu hiç olmayan** isteğin karşılığı yoktu; gerekçesi spec'te.
**Aksiyon:** `errors.AUTHENTICATION_REQUIRED` anahtarı, `params` yok, tek
resolution `sign_up`. Adım 3.6 anonim oturum basınca nadirleşir.

### B-046 · `/auth/session`, `/auth/logout`, ve hesabın yetenek kümesi
**Since:** commit <sha> · Adım 3.3 · **Spec:** `spec/08-api.md` § 35.7

`GET /auth/session` → `authenticated` + `capabilities`, `no-store`.
`POST /auth/logout` → `204`, oturumu sunucuda iptal eder; oturumsuz da `204`.

§ 35.7 yalnız **anonim** kümeyi yazmıştı; hesaplı hâli artık spec'te.

| Alan | Anonim | Hesap |
|---|---|---|
| `allowedLanguages` | `["en"]` | `["en","tr"]`, yapılandırılabilir |
| `canCustomizeTemplate` · `canEditAtomControls` · `canAddAlternatives` · `canSaveHistory` | `false` | `true` |
| `dailyGenerationQuota` · `dailyProfileQuota` | 5 · 3 | bugün 20 · 5 |
| `maxAtoms` | 60 | **alan yok** |
| `quotaResetsAt` | `null` | mutlak an |
| `anonymousExpiresAt` | Adım 3.6'da | **alan yok** |

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
yok; düğmeleri buna göre çizin). `GET /auth/oauth/{provider}/start?next=/profile`
→ 302 sağlayıcıya.

**`/auth/complete?next=...`** — başarılı girişin indiği yer. Doğrudan hedefe
yönlendirmiyoruz: oturum çerezi `SameSite=Strict` ve tarayıcı, zinciri başka
bir sitede başlamış bir isteğe onu **göndermez** — zincir Google'da başladı.
Doğrudan gitseydik ilk sayfa çıkışlı görünür, yalnız elle yenileme düzeltirdi.
Bu sayfa `/auth/session`'ı **aynı-origin fetch** ile sorsun, sonra `next`'e
gitsin.

**`/auth/error?code=OAUTH_FAILED&reason=...`** — hata burada iniyor.
`OAUTH_FAILED` tek kod, yedi sebep (`F-016`'nın istediği şekil):
`state_invalid`, `declined`, `provider_disabled`, `provider_unavailable`,
`email_missing`, `email_unverified`, `account_disabled`. **`declined`
kullanıcının vazgeçmesi, hata değil** — dili ona göre olsun.

`next` sunucuda doğrulanıyor: yalnız bu sitede düz bir yol, gerisi `/`.

---

## ACK — frontend tamamladı, backend arşivleyebilir

_(`B-037`…`B-043` `resolved/to-frontend-2026-08.md`'de)_

---

## Kalıcı kurallar

Eski maddelerin `spec/`'e işlendiği yerlerin tablosu
`resolved/to-frontend-2026-08.md`'ye taşındı (2026-08-24) — dosya sınırı.
