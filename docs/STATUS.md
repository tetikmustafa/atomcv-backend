# AtomCV — Durum Panosu

> İki repo da okur ve kendi satırlarını günceller. **Kural: 60 satırı geçmez.**
> Ayrıntı repo-yerel `notes/current.md`'de.

**2026-09-07** · **`B-071`, `B-072` açık** (frontend'de) · açık `F-nnn` yok

## Backend — `atomcv-backend`

| Aşama / Adım | Durum |
|---|---|
| Aşama 0-2 · 3 — hesap ve MVP (3.7 profil editörü frontend'de) | ✅ |
| Kapanış sonrası — uçtan uca ölçüm · dilim A-J | ✅ |

**Aşama 3 · dilim 9-14:** on bir `F-nnn` indi (`kapanis-denetimi.md`).
**Kapanış sonrası ölçüm — dört bulgu, on kusur** (`notes/archive/`), ve
bulgular kusurlarla eşleşmedi: "eksik Tech Stack" render sanılıyordu Faz C
çıktı, iki uydurma cümle Faz D sanılıyordu ikisi de **çıkarımdan** geliyordu.

**Dilim I-J-H — bir CV'nin sıralamadan önce bir şekli var.** Altı bölüm her
sayfada, tabanlarıyla (**576/708pt**), kalan 132pt ilana göre. Üçü ölçüldü ve
indi: `inline_list` satırı **169.55pt** hayalet mobilya yazıyordu (sayfanın
¼'ü); § 18.4'ün `no_responsibilities`'i **gerçek ilanların çoğunu**
reddediyordu (`job_analysis` v2, `B-072`); import About'a "Professional
Summary" başlığı uyduruyordu (`V6`-`V8`). **Ölçüm:** gerçek profil + gerçek
ilan, 1 sayfa, `used 336/341`, altı bölüm, uydurma sıfır.

**Geliştiricide:** `cost_usd` sıfır → bütçe freni ölü; fiyat tablosu,
VPS/restore.

**Test:** 1138 birim · 456 entegrasyon · latex 51/51 — 0 hata

## Frontend — `atomcv-frontend`

| Aşama / Adım | Durum |
|---|---|
| Aşama 0-2 — iskelet, profil editörü, üretim akışı + SSE | ✅ |
| Aşama 3 — **bütün dilimler**; açık tek madde `B-071` | ✅ |

On iki dilim (dökümü kendi `notes/`'larında). **Gerçek uca karşı ölçüldü**
(2026-08-30, MSW kapalı) ve `F-024`-`F-027`'yi çıkardı; ölçülmeyen yalnız OAuth
ile Turnstile.

**Test:** 649 birim · 51 e2e · **bundle** profil 252.5 / geçmiş 213.9 / üretim 220.3 / onboarding 217.3 / ayarlar 229.8 KB.

## Açık kararlar

| Soru | Bekleyen taraf |
|---|---|
| Anonim çalışma hesabın profiliyle birleşecek mi | **ürün** · bugün `kept_existing` |
| Hangi LLM modeli — fiyat tablosu ona bağlı | **ürün** |
| Faz D eşikleri (§ 21.2'nin 0.40/0.65'i) | **spec** · ölçüm elde |

## Sonraki senkronizasyon noktası

**Sıra frontend'de: `B-071`, `B-072` ve üç cevabın `ACK`'i.** `B-071` yedinci
`ExtractionWarningCode`'u ekletiyor, `B-072` `no_responsibilities` dalını
sildiriyor; `F-025`-`F-027` istemcide iş çıkarmıyor. **Yayın öncesi açık:**
gizlilik politikası model seçimini bekliyor.
