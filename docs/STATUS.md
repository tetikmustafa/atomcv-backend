# AtomCV — Durum Panosu

> İki repo da bu dosyayı okur ve kendi satırlarını günceller. **Kural: 60 satırı geçmez.**
> Ayrıntılı inşa kayıtları repo-yerel `notes/current.md`'dedir, buraya taşınmaz.

**2026-08-29** · **Frontend Aşama 3 kapandı; backend beş `F-nnn`'in dördünü
cevapladı** · açık: **`F-018`** · frontend'de **`B-062`-`B-066`**

---

## Backend — `atomcv-backend`

| Aşama / Adım | Durum |
|---|---|
| Aşama 0 · 1 · 2 — iskelet, yürüyen iskelet, ilana özel üretim | ✅ |
| Aşama 3 — hesap ve MVP · 3.1-3.6, 3.8, 3.9 (3.7 profil editörü frontend'de) | ✅ |

**Dilim 9-11 indi (`F-017`, `F-019`, `F-020`, `F-021`).** Sorulanların üçünde
yanlış olan taraf backend'di: `{"userEdited": true}` **`500` dönüyordu** (artık
`400`); `Retry-After` gidiyordu ama ne testi ne şema girişi vardı; ve § 48.4'ün
"kontrol edilemeyen bir onay kutudan ibarettir" iddiası yalnız oturum boyunca
doğruydu. EK D.6'da **beş kod eksikti**, biri bildirilmişti. **`GET
/generations`** kaynak haritasında baştan beri varmış ve hiç yazılmamış —
`canSaveHistory` yayımlanmış ve karşılıksızdı; cursor'lı, `total` ile indi.

**Kapanış denetimi (0-3)** `notes/kapanis-denetimi.md`'de. **Geliştiricide:**
model seçilince fiyat tablosu (o güne kadar bütçe freni çalışmaz), VPS kurulumu
(§ XI-A.4) ve **restore testi**.

**Test:** 1048 birim · 444 entegrasyon · latex 49/49 — 0 hata

## Frontend — `atomcv-frontend`

| Aşama / Adım | Durum |
|---|---|
| Aşama 0 — İskelet · 1 — Profil editörü · 2 — Üretim akışı + SSE | ✅ |
| Aşama 3 — **bütün dilimler** ✅ · yalnız § 31.6'nın yarısı `F-018`'i bekliyor | ⏳ |

Sekiz dilim (dilim dökümü kendi `notes/`'larında — bu dosyanın kuralı).
**On sekiz maddenin on sekizi `ACK`.** **Gerçek uca karşı denenmedi:** OAuth,
Turnstile, içe aktarma, cover letter, bayat sözcükleme, hesap silme.

**Test:** 607 birim · 47 e2e · **bundle** profil 252.3 / üretim 219.5 /
onboarding 217.1 / ayarlar 228.5 KB (dinamik rotalar elle, 210-253 KB).

## Açık kararlar (ikisini de ilgilendirir)

| Soru | Bekleyen taraf |
|---|---|
| Hesabın profili varken anonim çalışma birleşecek mi | **ürün** · bugün `kept_existing` |
| Hangi LLM modeli — fiyat tablosu ona bağlı | **ürün** |

## Sonraki senkronizasyon noktası

**Tek açık `F-nnn` `F-018`** — § 31.6'nın yarısını bloke eden. **Frontend'de
`B-062`-`B-066`**, ve `B-066` bir soru taşıyor: geçmiş satırı neyle
etiketlenecek (etiket ilandan okunuyor, ilan mutlak kural 4'te). **Yayın öncesi
açık:** gizlilik politikasının sağlayıcı listesi, model seçimini bekliyor.
