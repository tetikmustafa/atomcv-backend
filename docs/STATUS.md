# AtomCV — Durum Panosu

> İki repo da okur ve kendi satırlarını günceller. **Kural: 60 satırı geçmez.**
> Ayrıntı repo-yerel `notes/current.md`'de.

**2026-09-08** · **`B-071`-`B-074` açık** (frontend'de) · açık `F-nnn` yok

## Backend — `atomcv-backend`

| Aşama / Adım | Durum |
|---|---|
| Aşama 0-2 · 3 — hesap ve MVP (3.7 profil editörü frontend'de) | ✅ |
| Kapanış sonrası — uçtan uca ölçüm · dilim A-L | ✅ |

**Aşama 3 · dilim 9-14:** on bir `F-nnn`. Kapanış sonrası ölçüm: dört bulgu, on kusur; **dilim I-J-H** sayfaya bir şekil verdi (`notes/archive/`).

**Dilim K — sayfa referans belgeye karşı ölçüldü.** Gerçek `master_cv.tex` gerçek bir çağrıyla içe aktarıldı: **84 atom, 6 bölüm**, kaynağa karşı **4/4 About · 7/7 Tech Stack · 14/14 proje · 56/56 madde** birebir, sadakat muhafızında **0 yanlış pozitif**.
İnen dördü: About `paragraph` düzeni (beşinci `SectionLayout`, `V9`, **`B-073`**); Tech Stack satırları ve ölçümün aynı şekli görmesi; Faz D'ye `SKILL`/`LANGUAGE`/`ABOUT_PARAGRAPH` gitmiyor; `.tex` çıkarıcısı bitişik argümanları ayırıyor.

**Dilim L — şablon referans belgenin kendisi oldu (`classic:v4`, `B-074`).**
Preamble birebir portlandı, yedi fixture'ın maliyetleri yeniden ölçüldü. İki
ölçüm hatası kapandı: ölçüm kutusu `\parbox` yüzünden **yaslı** diziliyordu
(sayfa `\raggedright`) — marjinal bir madde tek satır ölçülüp iki satır dizildi,
kırk tanesi tek sayfa sözünü iki sayfa etti; ve birinci seviye madde listesinden
sonraki bölüm başlığı **12pt daha pahalı** (`SECTION_LIST_CLOSE`). **Tech Stack
artık ilana göre süzülüyor** (§ 33.4, LLM'siz). **Yedinci golden profil
`stress_long_career`** iki sayfa hatasını üreten şekli taşıyor.

**Geliştiricide:** `cost_usd` sıfır → bütçe freni ölü; fiyat tablosu, VPS/restore. **`make dev` bedava değil** — `.env`'in `LLM_CHAIN_*`'i eziyor.

**Test:** 1213 birim · 457 entegrasyon · latex 64/64 — 0 hata

## Frontend — `atomcv-frontend`

| Aşama / Adım | Durum |
|---|---|
| Aşama 0-2 — iskelet, profil editörü, üretim akışı + SSE | ✅ |
| Aşama 3 — **bütün dilimler**; açık `B-071`-`B-074` | ✅ |

On iki dilim. **Gerçek uca karşı ölçüldü** (2026-08-30) ve `F-024`-`F-027`'yi çıkardı; ölçülmeyen OAuth ile Turnstile.
**Test:** 649 birim · 51 e2e · **bundle** profil 252.5 / geçmiş 213.9 / üretim 220.3 / onboarding 217.3 / ayarlar 229.8 KB.

## Açık kararlar

| Soru | Bekleyen taraf |
|---|---|
| Anonim çalışma hesabın profiliyle birleşecek mi | **ürün** · bugün `kept_existing` |
| Hangi LLM modeli — fiyat tablosu ona bağlı | **ürün** |
| Faz D eşikleri (§ 21.2'nin 0.40/0.65'i) | **spec** · ölçüm elde |
| Maddede `emphasis` italik mi kalın mı — referans belge kalın | **spec** · çevirmek her ölçümü yeniler |

## Sonraki senkronizasyon noktası

**Sıra frontend'de: `B-071`-`B-074`'ün `ACK`'i** — yedinci
`ExtractionWarningCode`, silinen `no_responsibilities` dalı, beşinci
`SectionLayout` (`paragraph`), ve `classic`'in yeni görünümü (telde değişiklik
yok). **Yayın öncesi açık:** gizlilik politikası model seçimini bekliyor.
