# AtomCV — Durum Panosu

> İki repo da bu dosyayı okur ve kendi satırlarını günceller. **Kural: 60 satırı geçmez.**
> Ayrıntılı inşa kayıtları repo-yerel `notes/current.md`'dedir, buraya taşınmaz.

**Son güncelleme:** 2026-08-26 · **Aşama 3 başladı** — 3.3 dilim 1 (oturum) indi ·
frontend'de açık: **`B-044`, `B-045`, `B-046`**

---

## Backend — `atomcv-backend`

| Aşama / Adım | Durum |
|---|---|
| Aşama 0 — İskelet · Aşama 1 — Yürüyen iskelet (1.1-1.9) | ✅ |
| **Aşama 2 — İlana özel üretim** (2.1-2.7) | ✅ kapanış listesi 8/8 |
| **Aşama 3 — hesap ve MVP** | 🔄 |
| 3.1 dış servis hesapları · 3.2 e-posta domain | ⬜ geliştiricide (hesap + DNS) |
| 3.3 kimlik doğrulama | 🔄 1/4 dilim — oturum+CSRF ✅, OAuth ⬜, magic link ⬜, rate limit+Turnstile ⬜ |

**Aşama 3 planı:** § XI-A.6; Aşama 2'nin kaydı `notes/archive/stage-2.md`'de.
**Oturum:** Redis + `HttpOnly` `sid`, kayan TTL, sunucuda iptal, CSRF
çift-gönderim, `GET /auth/session` + `POST /auth/logout`. **Giriş yolu henüz
yok** — OAuth sıradaki dilim; bugün oturumu yalnız `local` kısayolu başlatıyor.
Kataloga `AUTHENTICATION_REQUIRED` (401), § 35.7'ye hesabın yetenek kümesi.

**Aşama 1-2:** `F-001`…`F-016` kapandı, açık `F-nnn` yok.
**Test:** 639 birim · 268 entegrasyon · 48 latex — 0 hata, 0 atlanan

## Frontend — `atomcv-frontend`

| Aşama / Adım | Durum |
|---|---|
| Aşama 0 — İskelet · 1 — Profil editörü · 2 — Üretim akışı + SSE | ✅ |

**`B-042`, `B-043` kapandı; `B-044`-`B-046` açıldı** — CSRF başlığı, yeni
401 kodu, `/auth/session` + hesabın yetenek kümesi. `gen:api` gerekiyor.
**Test:** 401 birim · 25 e2e · **bundle** profil 250.7 / üretim 214.8 KB.
**Aşama 2 gerçek uca karşı denetlendi (2026-08-25): 26/26** — yalnız
`suspicious_output` tetiklenemedi, ve o beklenen sonuç (`notes/current.md`).

---

## Açık kararlar (ikisini de ilgilendirir)

| Soru | Bekleyen taraf |
|---|---|
| Üretimde migration nasıl çalışır | backend · Aşama 2 |
| Anonim akış kuyruğu kullanacak mı | backend · Aşama 3 |
| Atomsuz entry seçilemiyor | backend · Bölüm 20.2 modelini etkiler |

---

## Sonraki senkronizasyon noktası

**`/auth/session` ve `capabilities` telde** — frontend `B-044`-`B-046`'yı alsın.
Sonrası OAuth (3.3 dilim 2), ki Adım 3.1'in Google client ID'sini bekliyor.
