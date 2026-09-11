# AtomCV — Durum Panosu

> İki repo da okur ve kendi satırlarını günceller. **Kural: 60 satırı geçmez.**
> Ayrıntı repo-yerel `notes/current.md`'de.

**2026-09-11** · **sıra frontend'de** — `B-088`…`B-094` ACK bekliyor

## Backend — `atomcv-backend`

| Aşama / Adım | Durum |
|---|---|
| Aşama 0-3 — hesap, MVP, anonim akış (kapanış denetimi 08-28) | ✅ |
| Aşama 4 — Faz G, üç şablon + Katman B, takip, DOCX, eval, bütçeler | ✅ |

**Aşama 4'te inenler.** **Faz G** düzenleme döngüsü: elle aç/kapa (`B-088`, kotasız) ve doğal dil (`B-089`, kotalı) — düzenleme render'a değil selection state'e uygulanıyor, sayfa sınırı yirmi düzenleme sonra da duruyor. **Üç şablon** (`B-090`, `B-092`), **Katman B** slider'ları (`B-091`, V12 `template_capacities`), **başvuru takibi** (`B-093`), **DOCX indirme** (`B-094`). Test tarafında: gerçek modele karşı **LLM eval** lane'i (`llmEval`, § 53.5), **performans bütçeleri** (`performance-budgets.yaml`, § 52.6) ve golden set'in üç şablona genişletilmesi.

**Sayfa garantisi: üçünün de tuttuğu gerçek derleyiciye karşı doğrulandı** — yedi golden profil, hepsi %3 içinde. Genişletme **beş kusur** çıkardı ve beşi de aynı cümleydi: *sayfanın dizdiği ama ölçümün hiç görmediği bir şey.* Listeden sonraki bölüm başlığı; sabit sanılan başlık bloğu (V13 `profiles.header_costs`, artık ölçülüyor); `\resumeItem`'ın iki ayrı kaçak boşluğu; compact'in aynı boşluğu iki kez yazması. **İkisi kullanıcıya ulaşmıştı** — compact'te ve modern'de birer profil ikinci sayfaya taşıyordu. `B-095` açıldı, düzeltildi, kapandı. **Şablon sürümleri yükseldi** (`classic:v6`, `compact:v2`, `modern:v3`); sürüm yalnız ölçüm anahtarlarında geçiyor, API'de değil.

**Ölçümler:** Faz D eşiklerine hiçbir gerçek skor ulaşmıyor (`PhaseDReachTest`, sebep aritmetik); `cover_letter` **v1** (v2 turu 169 kelime, bant 255-290); üç BOM override'ı hâlâ gerekli, `SecurityPatchFloorTest` tutuyor.

**Geliştiricide:** VPS/restore (**restore sonrası anonim satırları silmek**, § 49.4); OAuth, Turnstile ve `B-083`'ün challenge'ı gerçek uca karşı denenmedi; GitHub entegrasyonu (§ 31.8) gerçek bir OAuth uygulaması istiyor.

**Test:** 1698 birim · 523 entegrasyon · latex 141 — 0 hata

## Frontend — `atomcv-frontend`

| Aşama / Adım | Durum |
|---|---|
| Aşama 0-2 — iskelet, profil editörü, üretim akışı + SSE | ✅ |
| Aşama 3 — **bütün dilimler** | ✅ |
| Aşama 4 — `B-071`-`B-074` karşılandı | ✅ |

On iki dilim. **Gerçek uca karşı ölçüldü** (2026-08-30) ve `F-024`-`F-027`'yi çıkardı; ölçülmeyen OAuth ile Turnstile.

**`B-071`-`B-074` (2026-09-08).** Yedinci uyarı kodu ve silinen `no_responsibilities` dalı ICU tarafında karşılandı; `paragraph` ile yedinci kod `gen:api` ile üretilen tipe girdi ve birliği genişletmekten başka bir şey yapmadı. `B-073` ve `B-074` kod işi çıkarmadı: düzen seçtiren arayüz de, elde hazırlanmış `classic` önizlemesi de yok. `F-016`'nın "sayımı suçlama" nöbeti, dayandığı yakalanmış yük silinince `errorCatalogue`'a taşındı.
**`B-085`-`B-087` (2026-09-09), geldikleri gün karşılandı.** Şema yeniden üretildi ve kesişim tipi kalktı; ön yazı kontrolü artık `canWriteCoverLetter` okuyor; `params.feature`'ın dördü de kendi ICU dalını aldı. **`F-029`'un ölçüm hatası frontend'deydi:** ölçülen şey diskteki üretilmiş `api.d.ts`'ti, canlı şema değil.
**Test:** 658 birim · 51 e2e · **bundle** profil 252.5 / geçmiş 213.9 / üretim 220.3 / onboarding 217.3 / ayarlar 229.8 KB.

## Açık kararlar

| Soru | Bekleyen taraf |
|---|---|
| Faz D eşiklerinin normalizasyonu | **veri** · `default` setli üretim biriktiğinde |

_Kapandı 09-09: model `openai/gpt-5.6-sol`; `emphasis` kalın, bedeli sıfır; anonim çalışma **profiliyle üretimleriyle** taşınıyor (hesabın profili varsa `kept_existing`, ikisi de sönüyor)._

## Sonraki senkronizasyon noktası

**Sıra frontend'de.** `B-088`…`B-094` yedi madde açık: Faz G'nin iki ucu, üç
şablon, Katman B, takip ve DOCX. `B-095` sayfa garantisi için açılmıştı,
düzeltilip `resolved/`'a indi — frontend'den bir şey istemiyor, tek kalıcı
sonucu yükselen şablon sürümleri. Geri kalan her şey dağıtımı bekliyor.
