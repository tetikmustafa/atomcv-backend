# AtomCV — Durum Panosu

> İki repo da bu dosyayı okur ve kendi satırlarını günceller. **Kural: 60 satırı geçmez.**
> Ayrıntılı inşa kayıtları repo-yerel `notes/current.md`'dedir, buraya taşınmaz.

**2026-08-27** · **3.6 · 5/6 — anonim yükleme telde** · frontend'de açık: **`B-044`**–**`B-053`**, onu da bekliyor

---

## Backend — `atomcv-backend`

| Aşama / Adım | Durum |
|---|---|
| Aşama 0 · 1 — Yürüyen iskelet · **2 — İlana özel üretim** (2.1-2.7) | ✅ · ✅ 8/8 |
| **Aşama 3 — hesap ve MVP** · 3.1 dış servis hesapları | 🔄 · ✅ |
| 3.2 e-posta domain | 🔄 geliştiricide (DNS + DMARC) · 3.3 kimlik ✅ 4/4 |
| 3.4 CV yükleme ve çıkarım ✅ 4/4 · 3.5 çok dillilik ✅ 2/2 | ✅ |
| 3.6 anonim mod | 🔄 5/6 — oturum/profil/kota/iş sahibi/yükleme ✅, yükseltme ⬜ |

**Aşama 3 planı:** § XI-A.6. **Kimlik:** Redis oturum + CSRF, Google/GitHub,
magic link; `POST /auth/magic-link` üç katman rate limit + Turnstile arkasında
(§ 40.5.1). **Üretimde iki değişken zorunlu:** `TURNSTILE_SECRET_KEY` (yoksa
açılmaz) ve `FORWARD_HEADERS_STRATEGY=framework` (yoksa IP katmanı tek kova
olur) — § 46.5. **Çıkarım:** PDF/DOCX/TEX/TXT/MD, dosya hiçbir yere yazılmıyor
(§ 31.3.1); tek LLM çağrısı iki dil üretiyor (§ 31.4.1); alias sözlüğü
karşılaştırmanın iki tarafında da (§ 31.5.1). **`POST /profile/import` telde**
— `202` + iş, beş ret senkron, embedding ve ölçüm arkada (§ 31.6.1-2);
sözleşme **`B-051`**'de. **Anonim yükleme aynı uçtan** — hesap istemiyor, hak
adrese göre sayılıyor, profil hiçbir tabloda satır değil (§ 31.6.3);
sözleşme **`B-053`**'te.

**`F-001`…`F-016` kapandı, açık `F-nnn` yok.** **Test:** 885 birim · 373 entegrasyon · 48 latex — 0 hata

## Frontend — `atomcv-frontend`

| Aşama / Adım | Durum |
|---|---|
| Aşama 0 — İskelet · 1 — Profil editörü · 2 — Üretim akışı + SSE | ✅ |

**`B-044`-`B-053` açık, hiçbiri ACK almadı** — `to-frontend.md` bu yüzden 253 satır.
`/auth/complete`, `/auth/error`, `/verify`, Turnstile widget'ı sizde.
**Test:** 401 birim · 25 e2e · **bundle** profil 250.7 / üretim 214.8 KB.
**Aşama 2 gerçek uca karşı denetlendi (2026-08-25): 26/26** — yalnız
`suspicious_output` tetiklenemedi, o da beklenen sonuç.

---

## Açık kararlar (ikisini de ilgilendirir)

| Soru | Bekleyen taraf |
|---|---|
| Üretimde migration nasıl çalışır | backend · Aşama 2 |
| Atomsuz entry seçilemiyor | backend · Bölüm 20.2 modelini etkiler |

---

## Sonraki senkronizasyon noktası

**Frontend `B-044`-`B-053`'ü alsın** — dosya 253 satır, tek bir `ACK` almadı.
Backend 3.6 dilim 6'da (yükseltme). **`local-fake` fixture'ı sende.**
