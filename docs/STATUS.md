# AtomCV — Durum Panosu

> İki repo da bu dosyayı okur ve kendi satırlarını günceller. **Kural: 60 satırı geçmez.**
> Ayrıntılı inşa kayıtları repo-yerel `notes/current.md`'dedir, buraya taşınmaz.

**2026-09-02** · açık `B-nnn` yok · **`F-025` ve `F-027` indi** · açık
`F-nnn`: **`F-026`**

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

**Test:** 1071 birim · 450 entegrasyon · latex 49/49 — 0 hata

## Frontend — `atomcv-frontend`

| Aşama / Adım | Durum |
|---|---|
| Aşama 0 — İskelet · 1 — Profil editörü · 2 — Üretim akışı + SSE | ✅ |
| Aşama 3 — **bütün dilimler** ✅ · açık `B-nnn` kalmadı | ✅ |

On iki dilim (dökümü kendi `notes/`'larında). Geçit uyarıları adlandırıyor,
geçmiş satırı rol ve şirketle etiketli. **Gerçek uca karşı ölçüldü**
(2026-08-30, MSW kapalı; mektup ve hesap silme dahil): ölçüm mock'un kapı
sırasını yanlışladı ve `F-024`-`F-027`'yi çıkardı. **Ölçülmeyen yalnız OAuth
ile Turnstile** — ikisi de yapılandırılmış bir dağıtım istiyor.

**Test:** 649 birim · 51 e2e · **bundle** profil 252.5 / geçmiş 213.9 / üretim 220.3 / onboarding 217.3 / ayarlar 229.8 KB.

## Açık kararlar (ikisini de ilgilendirir)

| Soru | Bekleyen taraf |
|---|---|
| Hesabın profili varken anonim çalışma birleşecek mi | **ürün** · bugün `kept_existing` |
| Hangi LLM modeli — fiyat tablosu ona bağlı | **ürün** |

## Sonraki senkronizasyon noktası

**Sıra hâlâ backend'de: `F-026`** — dört mektup taslağının dördünün de
reddedilmesi. **`F-027`:** hesabı olmayan oturum artık `401`, ve bu yüzden
`DELETE /account`'un ikinci basışı `204` değil `401`. **`F-025`:** işveren
adı ilanda geçmiyorsa siliniyor — kara liste değil doğrulanabilir bir kural,
prompt cümlesi `job_analysis` `v2`'ye bırakıldı. İkisi de istemcide iş
çıkarmıyor. **Yayın öncesi:** gizlilik politikası model seçimini bekliyor.
