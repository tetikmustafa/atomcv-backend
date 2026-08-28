# AtomCV — Durum Panosu

> İki repo da bu dosyayı okur ve kendi satırlarını günceller. **Kural: 60 satırı geçmez.**
> Ayrıntılı inşa kayıtları repo-yerel `notes/current.md`'dedir, buraya taşınmaz.

**2026-08-28** · **3.8 kapandı (5/5) — Faz D ve cover letter telde** · frontend'de açık: **`B-044`**–**`B-056`**, on üçü de bekliyor

---

## Backend — `atomcv-backend`

| Aşama / Adım | Durum |
|---|---|
| Aşama 0 · 1 — Yürüyen iskelet · **2 — İlana özel üretim** (2.1-2.7) | ✅ · ✅ 8/8 |
| **Aşama 3 — hesap ve MVP** · 3.1 dış servis hesapları | 🔄 · ✅ |
| 3.2 e-posta domain | 🔄 geliştiricide (DNS + DMARC) · 3.3 kimlik ✅ 4/4 |
| 3.4 CV yükleme ve çıkarım ✅ 4/4 · 3.5 çok dillilik ✅ 2/2 | ✅ |
| 3.6 anonim mod ✅ 6/6 · 3.7 profil editörü **frontend'de** | ✅ |
| 3.8 Faz D ve cover letter ✅ 5/5 · **sıradaki: 3.9 hukuki ve kapanış** | ✅ |

**Aşama 3 planı:** § XI-A.6. **Kimlik:** Redis oturum + CSRF, Google/GitHub,
magic link; `POST /auth/magic-link` üç katman rate limit + Turnstile arkasında
(§ 40.5.1). **Üretimde iki değişken zorunlu:** `TURNSTILE_SECRET_KEY` (yoksa
açılmaz) ve `FORWARD_HEADERS_STRATEGY=framework` (yoksa IP katmanı tek kova
olur) — § 46.5. **Çıkarım:** PDF/DOCX/TEX/TXT/MD, dosya saklanmıyor (§ 31.3.1);
tek LLM çağrısı iki dil üretiyor (§ 31.4.1); alias sözlüğü karşılaştırmanın iki
tarafında da (§ 31.5.1). **`POST /profile/import`** — `202` + iş, beş ret
senkron (§ 31.6.1-2), sözleşme **`B-051`**. **Anonim yükleme aynı uçtan**: hesap
istemiyor, hak adrese göre, profil hiçbir tabloda satır değil (§ 31.6.3) —
**`B-053`**. **Yükseltme girişin içinde**, id'leriyle birlikte; hesabın profili
varsa yazılmıyor ve söyleniyor (§ 41.3.3), `/auth/verify` artık `200` + gövde —
**`B-054`**.

**`F-001`…`F-016` kapandı, açık `F-nnn` yok.** **Test:** 989 birim · 384 entegrasyon — 0 hata · **latex 44/48** (dört test fixture'sız düşüyor, 3.8 öncesi de)

## Frontend — `atomcv-frontend`

| Aşama / Adım | Durum |
|---|---|
| Aşama 0 — İskelet · 1 — Profil editörü · 2 — Üretim akışı + SSE | ✅ |

**`B-044`-`B-054` açık, hiçbiri ACK almadı** — `to-frontend.md` bu yüzden 279 satır.
`/auth/complete`, `/auth/error`, `/verify`, Turnstile widget'ı sizde.
**Test:** 401 birim · 25 e2e · **bundle** profil 250.7 / üretim 214.8 KB.
**Aşama 2 gerçek uca karşı denetlendi (2026-08-25): 26/26** — yalnız
`suspicious_output` tetiklenemedi, o da beklenen sonuç.

## Açık kararlar (ikisini de ilgilendirir)

| Soru | Bekleyen taraf |
|---|---|
| Üretimde migration nasıl çalışır | backend · Aşama 2 |
| Atomsuz entry seçilemiyor | backend · § 20.2 modelini etkiler |
| Hesabın profili varken anonim çalışma birleşecek mi | **ürün** · bugün `kept_existing` |

## Sonraki senkronizasyon noktası

**Frontend `B-044`-`B-056`'yı alsın** — 336 satır, tek bir `ACK` almadı. **`B-054`
kırıcı** (`/auth/verify` artık `204` değil), **`B-056` yeni yüzey** (cover letter
ucu, reddedebilir). Backend 3.9'a geçiyor. **`local-fake` fixture'ı sende.**
