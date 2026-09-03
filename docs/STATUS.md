# AtomCV — Durum Panosu

> İki repo da okur ve kendi satırlarını günceller. **Kural: 60 satırı geçmez.**
> Ayrıntılı inşa kayıtları repo-yerel `notes/current.md`'dedir.

**2026-09-04** · **`B-071` açık** (frontend'de) · açık `F-nnn` yok

---

## Backend — `atomcv-backend`

| Aşama / Adım | Durum |
|---|---|
| Aşama 0-2 · 3 — hesap ve MVP (3.7 profil editörü frontend'de) | ✅ |
| Aşama 4 — uçtan uca ölçüm · dilim A-E | ✅ · şablon dilimi park |

**Aşama 3 · dilim 9-14:** on bir `F-nnn` indi (§ 18.4.1, § 34.4.2); kapanış denetimi `notes/kapanis-denetimi.md`'de.

**Aşama 4 — dört bulgu, yedi kusur** (`notes/archive/stage-4-e2e-findings.md`).
**Bulgular kusurlarla eşleşmedi:** "eksik Tech Stack" render sanılıyordu, Faz C
çıktı; iki uydurma cümle Faz D sanılıyordu, ikisi de **çıkarımdan** geliyordu.

**Park: gerçek Klasik şablonu** (`feat/klasik-template`, `wip`) — komutlar,
etiketli iletişim bloğu ve `INLINE_LIST` çalışıyor, geometri v2'ye ölçüldü;
açık: bir ArchUnit döngüsü, altı test, `minimal_edge`'de %3.2 drift.

**Geliştiricide, artık ölçülmüş:** `cost_usd` sıfır → günlük bütçe freni ölü;
`llm_invocations.job_id` 108/108 NULL; `local-record` girdiyi saklamıyor, o
yüzden çıkarım sadakatinin yanlış pozitifi ölçülemiyor. Ayrıca fiyat tablosu ve
VPS/restore.

**Test:** 1095 birim · 451 entegrasyon · latex 49/49 — 0 hata

## Frontend — `atomcv-frontend`

| Aşama / Adım | Durum |
|---|---|
| Aşama 0-2 — iskelet, profil editörü, üretim akışı + SSE | ✅ |
| Aşama 3 — **bütün dilimler**; açık tek madde `B-071` | ✅ |

On iki dilim (dökümü kendi `notes/`'larında). **Gerçek uca karşı ölçüldü**
(2026-08-30, MSW kapalı) ve `F-024`-`F-027`'yi çıkardı. **Ölçülmeyen yalnız
OAuth ile Turnstile** — ikisi de yapılandırılmış bir dağıtım istiyor.

**Test:** 649 birim · 51 e2e · **bundle** profil 252.5 / geçmiş 213.9 / üretim 220.3 / onboarding 217.3 / ayarlar 229.8 KB.

## Açık kararlar (ikisini de ilgilendirir)

| Soru | Bekleyen taraf |
|---|---|
| Hesabın profili varken anonim çalışma birleşecek mi | **ürün** · bugün `kept_existing` |
| Hangi LLM modeli — fiyat tablosu ona bağlı | **ürün** |

## Sonraki senkronizasyon noktası

**Sıra frontend'de: `B-071` ve üç cevabın `ACK`'i.** `B-071` yedinci
`ExtractionWarningCode`'u (`unsupported_by_source`) ICU `select`'ine ekletiyor —
çıkarım belgede olmayan bir ad yazdığında gözden geçirme ekranında görünmeli.
`F-025`-`F-027`'nin üçü de istemcide iş çıkarmıyor. **Yayın öncesi açık:**
gizlilik politikası model seçimini bekliyor.
