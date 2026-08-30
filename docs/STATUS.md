# AtomCV — Durum Panosu

> İki repo da bu dosyayı okur ve kendi satırlarını günceller. **Kural: 60 satırı geçmez.**
> Ayrıntılı inşa kayıtları repo-yerel `notes/current.md`'dedir, buraya taşınmaz.

**2026-08-30** · **`F-022`-`F-024`'ün üçü de indi** · açık `F-nnn` yok ·
frontend'de **`B-068`-`B-070`**

---

## Backend — `atomcv-backend`

| Aşama / Adım | Durum |
|---|---|
| Aşama 0 · 1 · 2 — iskelet, yürüyen iskelet, ilana özel üretim | ✅ |
| Aşama 3 — hesap ve MVP · 3.1-3.6, 3.8, 3.9 (3.7 profil editörü frontend'de) | ✅ |

**Dilim 9-13: sekiz `F-nnn`'in sekizi de indi**, ve çoğunda yanlış olan taraf
backend'di — üç ayrı istisna `500` dönüyordu, EK D.6'da beş kod eksikti,
`F-018`'de uyarının yeri yanlıştı. Ayrıntı `resolved/`'da.

**Dilim 13'ün kalıcı kararı § 57.6:** geçmiş satırı `roleTitle` ve
`companyName` taşıyor, ve **mutlak kural 4'ün sınırı** orada üç ölçütle
yazılı — listede olmayan alan istisna değil.

**Kapanış denetimi (0-3)** `notes/kapanis-denetimi.md`'de. **Geliştiricide:**
model seçilince fiyat tablosu, VPS kurulumu (§ XI-A.4) ve **restore testi**.

**Test:** 1054 birim · 448 entegrasyon · latex 49/49 — 0 hata

## Frontend — `atomcv-frontend`

| Aşama / Adım | Durum |
|---|---|
| Aşama 0 — İskelet · 1 — Profil editörü · 2 — Üretim akışı + SSE | ✅ |
| Aşama 3 — **bütün dilimler** ✅ · `B-068`-`B-070` açık | ⏳ |

On dilim (dökümü kendi `notes/`'larında). § 31.6'nın geçidi ve geçmiş ekranı
indi; satırın etiketi ile uyarıların adları `B-070`/`B-069` ile geldi.
**Gerçek uca karşı ölçüldü** (2026-08-30, MSW kapalı) — biri mock'un kapı
sırasını yanlışladı, biri `500` çıkardı (`F-024`). **Ölçülmeyen:** OAuth ve
Turnstile (dağıtım ister), hesap silme ve mektup üretimi.

**Test:** 643 birim · 51 e2e · **bundle** profil 252.5 / üretim 220.3 /
geçmiş 213.8 / onboarding 217.3 / ayarlar 229.8 KB (dinamik rotalar elle).

## Açık kararlar (ikisini de ilgilendirir)

| Soru | Bekleyen taraf |
|---|---|
| Hesabın profili varken anonim çalışma birleşecek mi | **ürün** · bugün `kept_existing` |
| Hangi LLM modeli — fiyat tablosu ona bağlı | **ürün** |

## Sonraki senkronizasyon noktası

**Sıra frontend'de: `B-068`-`B-070`.** Üçü de `gen:api` istiyor. `B-070`
satıra iki alan koyuyor; `B-069` altı uyarı kodunu adlandırılabilir yapıyor,
yani § 31.6'nın geçidi artık hangi uyarı olduğunu söyleyebilir; `B-068`'de
yapacak bir şey yok. **Yayın öncesi açık:** gizlilik politikasının sağlayıcı
listesi, model seçimini bekliyor.
