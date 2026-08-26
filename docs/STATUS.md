# AtomCV — Durum Panosu

> İki repo da bu dosyayı okur ve kendi satırlarını günceller. **Kural: 60 satırı geçmez.**
> Ayrıntılı inşa kayıtları repo-yerel `notes/current.md`'dedir, buraya taşınmaz.

**Son güncelleme:** 2026-08-26 · **3.3 dilim 2 (OAuth) indi** ·
frontend'de açık: **`B-044`** – **`B-048`**

---

## Backend — `atomcv-backend`

| Aşama / Adım | Durum |
|---|---|
| Aşama 0 — İskelet · Aşama 1 — Yürüyen iskelet (1.1-1.9) | ✅ |
| **Aşama 2 — İlana özel üretim** (2.1-2.7) | ✅ kapanış listesi 8/8 |
| **Aşama 3 — hesap ve MVP** | 🔄 |
| 3.1 dış servis hesapları | ✅ hesaplar açıldı, `.env` dolduruldu |
| 3.2 e-posta domain | 🔄 geliştiricide (DNS + DMARC saati) |
| 3.3 kimlik doğrulama | 🔄 2/4 — oturum+CSRF ✅, OAuth ✅, magic link ⬜, rate limit+Turnstile ⬜ |

**Aşama 3 planı:** § XI-A.6; Aşama 2'nin kaydı `notes/archive/stage-2.md`'de.
**Oturum:** Redis + `HttpOnly` `sid`, kayan TTL, sunucuda iptal, CSRF
çift-gönderim, `/auth/session` + `/auth/logout`. **Giriş var:** Google ve
GitHub; state Redis'te ve tek kullanımlık, birleştirme yalnız doğrulanmış
e-postada. LinkedIn kaldırıldı. Kataloga `AUTHENTICATION_REQUIRED` ve
`OAUTH_FAILED`; kararlar § 40.6.1 ve § 35.7'de.

**Aşama 1-2:** `F-001`…`F-016` kapandı, açık `F-nnn` yok.
**Test:** 676 birim · 285 entegrasyon · 48 latex — 0 hata, 0 atlanan

## Frontend — `atomcv-frontend`

| Aşama / Adım | Durum |
|---|---|
| Aşama 0 — İskelet · 1 — Profil editörü · 2 — Üretim akışı + SSE | ✅ |

**`B-044`-`B-048` açık** — CSRF başlığı, yeni 401 kodu, `/auth/session` +
yetenek kümesi, LinkedIn kalktı, OAuth iki rota istiyor. `gen:api` şart.
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

**Giriş uçtan uca telde** — frontend `B-044`-`B-048`'i alsın; `/auth/complete`
ve `/auth/error` rotaları onlarda. Sonrası magic link (3.3 dilim 3), ki Adım
3.2'nin DNS doğrulamasını bekliyor.
