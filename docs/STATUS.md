# AtomCV — Durum Panosu

> İki repo da okur ve kendi satırlarını günceller. **Kural: 60 satırı geçmez.**
> Ayrıntı repo-yerel `notes/current.md`'de.

**2026-09-07** · **`B-071`, `B-072`, `B-073` açık** (frontend'de) · açık `F-nnn` yok

## Backend — `atomcv-backend`

| Aşama / Adım | Durum |
|---|---|
| Aşama 0-2 · 3 — hesap ve MVP (3.7 profil editörü frontend'de) | ✅ |
| Kapanış sonrası — uçtan uca ölçüm · dilim A-K | ✅ |

**Aşama 3 · dilim 9-14:** on bir `F-nnn`. Kapanış sonrası ölçüm: dört bulgu, on
kusur; **dilim I-J-H** sayfaya bir şekil verdi (`notes/archive/`).

**Dilim K — sayfa referans belgenin kendisine karşı ölçüldü.** Gerçek
`master_cv.tex` gerçek bir çağrıyla içe aktarıldı: **84 atom, 6 bölüm**, kaynağa
karşı **4/4 About · 7/7 Tech Stack · 14/14 proje · 56/56 madde** birebir,
sadakat muhafızında **0 yanlış pozitif**; üretim tek sayfa, uydurma sıfır. İnen
dördü: About artık **`paragraph`** düzeni (beşinci `SectionLayout`, `V9`,
**`B-073`**); Tech Stack `Kategori: öğe, öğe` satırları ve **ölçüm de aynı şekli
görüyor** (`MeasurableItem.layout` — yoksa basılan satır ölçülenden geniş);
Faz D'ye `SKILL`/`LANGUAGE`/`ABOUT_PARAGRAPH` gönderilmiyor; `.tex` çıkarıcısı
bitişik argümanları ayırıyor (`GPA: 3.212022 -- 2026` idi).

**Altıncı golden profil `master_cv_en`** — yazılmadı, **okundu** — ilk koşusunda
Faz C'de belirlenimsizlik yakaladı: eşit puanlı entry'ler UUID sırasıyla
seçiliyordu, yani aynı CV iki kez okununca iki farklı sayfa. **Geliştiricide:**
`cost_usd` sıfır → bütçe freni ölü; fiyat tablosu, VPS/restore. **Ve `make dev`
bedava değil** — `.env`'in `LLM_CHAIN_*`'i `local-fake`'i eziyor, ölçüldü.

**Test:** 1181 birim · 457 entegrasyon · latex 54/54 — 0 hata

## Frontend — `atomcv-frontend`

| Aşama / Adım | Durum |
|---|---|
| Aşama 0-2 — iskelet, profil editörü, üretim akışı + SSE | ✅ |
| Aşama 3 — **bütün dilimler**; açık `B-071`, `B-072`, `B-073` | ✅ |

On iki dilim. **Gerçek uca karşı ölçüldü** (2026-08-30) ve `F-024`-`F-027`'yi
çıkardı; ölçülmeyen OAuth ile Turnstile. **Test:** 649 birim · 51 e2e · **bundle** profil 252.5 / geçmiş 213.9 / üretim 220.3 / onboarding 217.3 / ayarlar 229.8 KB.

## Açık kararlar

| Soru | Bekleyen taraf |
|---|---|
| Anonim çalışma hesabın profiliyle birleşecek mi | **ürün** · bugün `kept_existing` |
| Hangi LLM modeli — fiyat tablosu ona bağlı | **ürün** |
| Faz D eşikleri (§ 21.2'nin 0.40/0.65'i) | **spec** · ölçüm elde |
| Maddede `emphasis` italik mi kalın mı — referans belge kalın | **spec** · çevirmek her ölçümü yeniler |

## Sonraki senkronizasyon noktası

**Sıra frontend'de: `B-071`, `B-072`, `B-073` ve üç cevabın `ACK`'i** —
yedinci `ExtractionWarningCode`, silinen `no_responsibilities` dalı, beşinci
`SectionLayout` (`paragraph`). **Yayın öncesi açık:** gizlilik politikası model
seçimini bekliyor.
