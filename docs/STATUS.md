# AtomCV — Durum Panosu

> İki repo da okur ve kendi satırlarını günceller. **Kural: 60 satırı geçmez.**
> Ayrıntı repo-yerel `notes/current.md`'de.

**2026-09-09** · **`B-075`, `B-076` açık** (frontend'de) · açık `F-nnn` yok

## Backend — `atomcv-backend`

| Aşama / Adım | Durum |
|---|---|
| Aşama 0-2 · 3 — hesap ve MVP (3.7 profil editörü frontend'de) | ✅ |
| Kapanış sonrası — uçtan uca ölçüm · dilim A-M | ✅ |

**Aşama 3 · dilim 9-14:** on bir `F-nnn`. Kapanış sonrası ölçüm: dört bulgu, on kusur; **dilim I-J-H** sayfaya bir şekil verdi (`notes/archive/`).

**Dilim K — sayfa referans belgeye karşı ölçüldü.** Gerçek `master_cv.tex` gerçek bir çağrıyla içe aktarıldı: **84 atom, 6 bölüm**, kaynağa karşı **4/4 About · 7/7 Tech Stack · 14/14 proje · 56/56 madde** birebir, sadakat muhafızında **0 yanlış pozitif**.
İnen dördü: About `paragraph` düzeni (beşinci `SectionLayout`, `V9`, **`B-073`**); Tech Stack satırları ve ölçümün aynı şekli görmesi; Faz D'ye `SKILL`/`LANGUAGE`/`ABOUT_PARAGRAPH` gitmiyor; `.tex` çıkarıcısı bitişik argümanları ayırıyor.

**Dilim L — şablon referans belgenin kendisi oldu (`classic:v4`, `B-074`).**
Preamble birebir portlandı, yedi fixture yeniden ölçüldü; iki ölçüm hatası kapandı
(`\parbox` ölçüm kutusunu **yaslı** diziyordu — kırk marjinal madde tek sayfa
sözünü iki sayfa etti; madde listesinden sonraki başlık **12pt daha pahalı**,
`SECTION_LIST_CLOSE`). **Tech Stack artık ilana göre süzülüyor** (§ 33.4,
LLM'siz); yedinci golden profil `stress_long_career` o hatayı üretiyor.

**Dilim M — P3'ün yanlış pozitifleri, maliyet ve sağlayıcı politikası.** On altı kayıtlı `about_synthesis` oynatıldı: kelime sınırı tireyi kelime sayıyordu, **on token / bir verdict** haksız reddedilmiş; ilanın yazımı muhafıza kaynak oldu (prompt'a değil, § 53.2); sığmayan atomsuz entry `rejectedEntries`'e giriyor; `cover_letter` **v2**; **maliyet artık sağlayıcının bildirdiği `usage.cost`** (tablo tabanda, model fiyatlandı); her istekte `data_collection: deny`; `accessedAt` telden kalktı (`B-075`).

**Geliştiricide:** VPS/restore, ve OAuth ile Turnstile'ın gerçek uca karşı denenmesi. **`make dev` bedava** — `.env`'in `LLM_CHAIN_*`'i `local-fake`'i ezmiyor, ölçüldü.

**Test:** 1248 birim · 457 entegrasyon · latex 64/64 — 0 hata

## Frontend — `atomcv-frontend`

| Aşama / Adım | Durum |
|---|---|
| Aşama 0-2 — iskelet, profil editörü, üretim akışı + SSE | ✅ |
| Aşama 3 — **bütün dilimler** | ✅ |
| Aşama 4 — `B-071`-`B-074` karşılandı | ✅ |

On iki dilim. **Gerçek uca karşı ölçüldü** (2026-08-30) ve `F-024`-`F-027`'yi çıkardı; ölçülmeyen OAuth ile Turnstile.

**`B-071`-`B-074` (2026-09-08).** Yedinci uyarı kodu ve silinen `no_responsibilities` dalı ICU tarafında karşılandı; `paragraph` ile yedinci kod `gen:api` ile üretilen tipe girdi ve birliği genişletmekten başka bir şey yapmadı. `B-073` ve `B-074` kod işi çıkarmadı: düzen seçtiren arayüz de, elde hazırlanmış `classic` önizlemesi de yok. `F-016`'nın "sayımı suçlama" nöbeti, dayandığı yakalanmış yük silinince `errorCatalogue`'a taşındı.
**Test:** 658 birim · 51 e2e · **bundle** profil 252.5 / geçmiş 213.9 / üretim 220.3 / onboarding 217.3 / ayarlar 229.8 KB.

## Açık kararlar

| Soru | Bekleyen taraf |
|---|---|
| Anonim çalışma hesabın profiliyle birleşecek mi | **ürün** · bugün `kept_existing` |
| Hangi LLM modeli — fiyat tablosu ona bağlı | **ürün** |
| Faz D eşikleri (§ 21.2'nin 0.40/0.65'i) | **spec** · ölçüm elde |
| Maddede `emphasis` italik mi kalın mı — referans belge kalın | **spec** · çevirmek her ölçümü yeniler |

## Sonraki senkronizasyon noktası

**Sıra frontend'de: `B-075`, `B-076`, `B-077`.** İlki `accessedAt`'i ekrandan kaldırıyor
(yazıcısı yok), ikincisi EK C.1'in sağlayıcı listesini taşıyor — **gizlilik
politikası yazılabilir**; üçüncüsü beceri yankısının kanonikleştiğini söylüyor.
Model kısıtlanmıyor (2026-09-09); liste yapılandırmadan türüyor.
