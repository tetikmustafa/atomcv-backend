# AtomCV — Durum Panosu

> İki repo da bu dosyayı okur ve kendi satırlarını günceller. **Kural: 60 satırı geçmez.**
> Ayrıntılı inşa kayıtları repo-yerel `notes/current.md`'dedir, buraya taşınmaz.

**Son güncelleme:** 2026-08-26 · **3.3 dilim 3 (magic link) indi** ·
frontend'de açık: **`B-044`** – **`B-049`** (altısı da bekliyor)

---

## Backend — `atomcv-backend`

| Aşama / Adım | Durum |
|---|---|
| Aşama 0 — İskelet · Aşama 1 — Yürüyen iskelet (1.1-1.9) | ✅ |
| **Aşama 2 — İlana özel üretim** (2.1-2.7) | ✅ kapanış listesi 8/8 |
| **Aşama 3 — hesap ve MVP** | 🔄 |
| 3.1 dış servis hesapları | ✅ hesaplar açıldı, `.env` dolduruldu |
| 3.2 e-posta domain | 🔄 geliştiricide (DNS + DMARC saati) |
| 3.3 kimlik doğrulama | 🔄 3/4 — oturum+CSRF ✅, OAuth ✅, magic link ✅, rate limit+Turnstile ⬜ |

**Aşama 3 planı:** § XI-A.6; Aşama 2'nin kaydı `notes/archive/stage-2.md`'de.
**Oturum:** Redis + `HttpOnly` `sid`, kayan TTL, sunucuda iptal, CSRF
çift-gönderim, `/auth/session` + `/auth/logout`. **Üç giriş yolu:** Google,
GitHub, ve magic link (selector/verifier, POST doğrulama, tek kullanım).
E-posta Resend ya da SMTP/Mailpit. **`POST /auth/magic-link` rate limit'siz —
dilim 4 inmeden üretime açılmamalı** (§ 40.4.1). Kararlar § 40.4.1, § 40.6.1.

**Aşama 1-2:** `F-001`…`F-016` kapandı, açık `F-nnn` yok.
**Test:** 702 birim · 295 entegrasyon · 48 latex — 0 hata, 0 atlanan

## Frontend — `atomcv-frontend`

| Aşama / Adım | Durum |
|---|---|
| Aşama 0 — İskelet · 1 — Profil editörü · 2 — Üretim akışı + SSE | ✅ |

**`B-044`-`B-049` açık, hiçbiri ACK almadı** — bu yüzden `to-frontend.md`
100 satırın üstünde. `/auth/complete`, `/auth/error`, `/verify` sizde.
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

**Üç giriş yolu da telde** — frontend `B-044`-`B-049`'u alsın; üç rota onlarda.
Sonrası dilim 4: rate limit + Turnstile, ki magic link'i üretime açan şey o.
