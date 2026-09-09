# AtomCV — Durum Panosu

> İki repo da okur ve kendi satırlarını günceller. **Kural: 60 satırı geçmez.**
> Ayrıntı repo-yerel `notes/current.md`'de.

**2026-09-09** · **kanal iki yönde de boş** — açık `B-nnn` ve `F-nnn` yok

## Backend — `atomcv-backend`

| Aşama / Adım | Durum |
|---|---|
| Aşama 0-2 · 3 — hesap ve MVP (3.7 profil editörü frontend'de) | ✅ |
| Kapanış sonrası — uçtan uca ölçüm · dilim A-M | ✅ |

**Aşama 3 · dilim 9-14:** on bir `F-nnn`. Kapanış sonrası ölçüm: dört bulgu, on kusur; **dilim I-J-H** sayfaya bir şekil verdi (`notes/archive/`).

**Dilim K-M** (`notes/archive/`): sayfa referansa karşı ölçüldü (84 atom; 4/4 About · 7/7 Tech Stack · 14/14 proje · 56/56 madde, 0 yanlış pozitif); şablon referansın kendisi (`classic:v4`, `B-074`), iki ölçüm hatası kapandı; Tech Stack ilana göre süzülüyor (§ 33.4, LLM'siz); **maliyet `usage.cost`**, her istekte `data_collection: deny`.

**Anonim akış indi — beş dilim, § 35.7.2-5 ve § 51.6.1.** Hesapsız kişi profil çıkarıyor, **düzenliyor**, ilana göre CV üretiyor, okuyup indiriyor, ve hesap açınca **profilini ve ürettiklerini** götürüyor; kod yolu hesabınkiyle aynı. **Sapma:** anonim profil Redis belgesi değil, sahibi olmayan + `expires_at` taşıyan satır — beş dakikada süpürülüyor ama yedeğe yakalanırsa şifreli arşivde **altı aya kadar** kalabilir (`B-080` gizlilik metni). § 35.7'nin üç limiti *zorlanıyor*; içe aktarım ve üretim **challenge** istiyor; ön yazı hesabın.

**Ölçümler:** Faz D eşiklerine hiçbir gerçek skor ulaşmıyor (`PhaseDReachTest`, sebep aritmetik); `cover_letter` **v1** (v2 turu 169 kelime, bant 255-290); yazıyla sayı muhafızda; üç BOM override'ı hâlâ gerekli, `SecurityPatchFloorTest` tutuyor.

**`F-028`-`F-030` cevaplandı (PR #164), ve ikisi gerçek kusurdu.** Yetenek bloğunda **`canWriteCoverLetter`** var artık — ön yazı `canSaveHistory` vekilinden okunuyordu. `FEATURE_REQUIRES_ACCOUNT`'ın `feature`'ı **kapalı sözlük** (`AccountFeature`, § D.6.1) ve her değerinin blokta bir boolean karşılığı var. İki uç (`feedback`, `cover-letter/regenerate`) anonime **401 yerine 403** dönüyor — 401 "oturumunuz bitti" dedirtiyordu, oysa oturum yerindeydi. **`POST /profile/import`'un `challengeToken`'ı şemada query parametresiydi**, artık form alanı (springdoc çok parçalı uçta `@RequestParam`'ı query diye yayımlıyor — sonraki çok parçalı uç için de geçerli).

**Geliştiricide:** VPS/restore (**restore sonrası anonim satırları silmek**, § 49.4), OAuth ve Turnstile'ın gerçek uca karşı denenmesi.

**Test:** 1287 birim · 476 entegrasyon · latex 64/64 — 0 hata

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

**Sıra kimsede değil — iki kanal da boş.** `F-001`-`F-030` ve `B-001`-`B-087`
kapandı ve `handoff/resolved/`'a indi (2026-09-09). Bir sonraki madde
dağıtımdan gelir: OAuth sıçraması, sihirli bağlantının Turnstile'ı ve
`B-083`'ün challenge'ı hâlâ gerçek uca karşı denenmedi.
