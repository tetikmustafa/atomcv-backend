# AtomCV — Durum Panosu

> İki repo da okur ve kendi satırlarını günceller. **Kural: 60 satırı geçmez.**
> Ayrıntılı inşa kayıtları repo-yerel `notes/current.md`'dedir.

**2026-09-07** · **`B-071` açık** (frontend'de) · açık `F-nnn` yok

---

## Backend — `atomcv-backend`

| Aşama / Adım | Durum |
|---|---|
| Aşama 0-2 · 3 — hesap ve MVP (3.7 profil editörü frontend'de) | ✅ |
| Kapanış sonrası — uçtan uca ölçüm · dilim A-G | ✅ |

**Aşama 3 · dilim 9-14:** on bir `F-nnn` indi; denetim
`notes/kapanis-denetimi.md`'de. **Kapanış sonrası ölçüm — dört bulgu, yedi kusur** (`notes/archive/`);
**bulgular kusurlarla eşleşmedi:** "eksik Tech Stack" render sanılıyordu Faz C
çıktı, iki uydurma cümle Faz D sanılıyordu ikisi de **çıkarımdan** geliyordu.
**Dilim F**'te Klasik şablonu indi; drift'i **`ITEM_LINE`**'ın bir ölçüm
artefaktı olduğunu görmek kapattı.

**Dilim G — ölçümün kendisi indi:** `llm_invocations.job_id` yazılıyor; TEI
istemcisi parçalıyor, yani 32 atomdan büyük her profil ilk kez gerçek sunucuya
gömülüyor; `trace.D` § 14.6'nın `rejectReasons`'ını taşıyor; `local-record`
çıkarımın kaynak belgesini de saklıyor.

**Geliştiricide:** `cost_usd` sıfır → bütçe freni ölü; fiyat tablosu,
VPS/restore. **Spec kararı:** Faz D'nin `FLOOR_SCORE` 0.40'ı gerçek
embedding'le bile ulaşılamıyor (ölçülen en yüksek skor **0.3577**).

**Test:** 1116 birim · 453 entegrasyon · latex 50/50 — 0 hata

## Frontend — `atomcv-frontend`

| Aşama / Adım | Durum |
|---|---|
| Aşama 0-2 — iskelet, profil editörü, üretim akışı + SSE | ✅ |
| Aşama 3 — **bütün dilimler**; açık tek madde `B-071` | ✅ |

On iki dilim (dökümü kendi `notes/`'larında). **Gerçek uca karşı ölçüldü**
(2026-08-30, MSW kapalı) ve `F-024`-`F-027`'yi çıkardı. **Ölçülmeyen yalnız
OAuth ile Turnstile.**

**Test:** 649 birim · 51 e2e · **bundle** profil 252.5 / geçmiş 213.9 / üretim 220.3 / onboarding 217.3 / ayarlar 229.8 KB.

## Açık kararlar (ikisini de ilgilendirir)

| Soru | Bekleyen taraf |
|---|---|
| Hesabın profili varken anonim çalışma birleşecek mi | **ürün** · bugün `kept_existing` |
| Hangi LLM modeli — fiyat tablosu ona bağlı | **ürün** |

## Sonraki senkronizasyon noktası

**Sıra frontend'de: `B-071` ve üç cevabın `ACK`'i.** `B-071` yedinci
`ExtractionWarningCode`'u (`unsupported_by_source`) ICU `select`'ine ekletiyor;
`F-025`-`F-027` istemcide iş çıkarmıyor. **Yayın öncesi açık:** gizlilik
politikası model seçimini bekliyor.
