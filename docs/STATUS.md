# AtomCV — Durum Panosu

> İki repo da bu dosyayı okur ve kendi satırlarını günceller. **Kural: 60 satırı geçmez.**
> Ayrıntılı inşa kayıtları repo-yerel `notes/current.md`'dedir, buraya taşınmaz.

**2026-08-28** · **Aşama 0-3 kapanış denetimi yapıldı** · frontend'de açık:
**`B-044`**–**`B-060`**, **on yedisi de `ACK` beklemiyor**

---

## Backend — `atomcv-backend`

| Aşama / Adım | Durum |
|---|---|
| Aşama 0 · 1 · 2 — iskelet, yürüyen iskelet, ilana özel üretim | ✅ |
| Aşama 3 — hesap ve MVP · 3.1 hesaplar ✅ · **3.2 e-posta domain ✅** | ✅ |
| 3.3 kimlik · 3.4 çıkarım · 3.5 çok dillilik · 3.6 anonim · 3.8 Faz D · 3.9 hukuki | ✅ |
| 3.7 profil editörü **frontend'de** | ⏳ |

**Kapanış denetimi (0-3): sekiz dilimin yedisi indi**, kaydı
`notes/kapanis-denetimi.md`'de. Kapananların telde görüneni: **saklama süresi**
(payload 7 gün / ilan 30 gün), ikinci CV için **409 + `mode=replace`**,
**ATS geri okuma** (§ 23.2), **deploy altyapısı**, **Gemini adaptörü**
(zincirin tek halkası vardı), **Resend webhook + suppression**.

**Açık kalan tek kod maddesi:** atomsuz entry'nin sayfaya çıkabilmesi — sayfa
sınırı garantisine dokunduğu için ayrı bir oturuma bırakıldı
(`notes/sonraki-oturum-atomsuz-entry.md`).

**Geliştiricide:** yeni model seçilince fiyat tablosu (o güne kadar bütçe freni
çalışmaz), VPS kurulumu (§ XI-A.4) ve **restore testi**.

**Test:** 1027 birim · 427 entegrasyon — 0 hata · latex 48/48 fixture'larla

## Frontend — `atomcv-frontend`

| Aşama / Adım | Durum |
|---|---|
| Aşama 0 — İskelet · 1 — Profil editörü · 2 — Üretim akışı + SSE | ✅ |

**`B-044`-`B-060` açık, hiçbiri ACK almadı** — `to-frontend.md` 463 satır ve
sınır 100. **Bu bir belge sorunu değil, koordinasyon sorunu**: ACK gelmeden
taşınacak bir şey yok. Dosyanın başında artık bir dizin var.
`/auth/complete`, `/auth/error`, `/verify`, Turnstile widget'ı sizde.
**Test:** 401 birim · 25 e2e · **bundle** profil 250.7 / üretim 214.8 KB.

## Açık kararlar (ikisini de ilgilendirir)

| Soru | Bekleyen taraf |
|---|---|
| Atomsuz entry sayfaya çıkabilmeli | backend · ayrı oturum, § 20.2 |
| Hesabın profili varken anonim çalışma birleşecek mi | **ürün** · bugün `kept_existing` |
| Hangi LLM modeli — fiyat tablosu ona bağlı | **ürün** |

## Sonraki senkronizasyon noktası

**Frontend `B-044`-`B-060`'ı alsın.** Backend'in MVP payı bitti; sırada 3.7
profil editörü ve bu on yedi madde sizde. **`B-059` yayın öncesi zorunlu**
(gizlilik politikası, alt işleyen listesi).
