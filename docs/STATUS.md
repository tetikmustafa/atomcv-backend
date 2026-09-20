# AtomCV — Durum Panosu

> İki repo da okur ve kendi satırlarını günceller. **Kural: 60 satırı geçmez.**
> Ayrıntı repo-yerel `notes/current.md`'de.

**2026-09-20** · **frontend'e on yedi madde açık** — `B-100`…`B-116`, denetimlerden, hiçbiri henüz ACK'lenmedi

## Backend — `atomcv-backend`

| Aşama / Adım | Durum |
|---|---|
| Aşama 0-3 — hesap, MVP, anonim akış (kapanış denetimi 08-28) | ✅ |
| Aşama 4 — buradan yapılabilecek maddelerin hepsi | ✅ |

**Aşama 4 + altı denetim.** **Faz G** (`B-088`, `B-089`), **üç şablon** (`B-090`, `B-092`), **Katman B** (`B-091`), **başvuru takibi** (`B-093`), **DOCX** (`B-094`), **yaşam döngüsü e-postaları** (`B-096`). Sonra spec **altı kez** koda karşı denetlendi (09-15 → 09-20) ve her turun ekseni değişti: ad kodda var mı (1-2) · yazılmış sayılan tasarım (3) · kümeler tam mı (4) · telde bir ucu var mı (5) · **iki belge aynı şeyi mi anlatıyor, ve ertelenmiş bir işin koşulu geçti mi (6)**. Altısının da bulduğu her şey ya yazıldı ya gerekçesiyle `spec/`'e kaydedildi; anlatılar `notes/archive/denetim-*.md`'de, özet tablo `notes/current.md`'de.

**Altıncı turun yedi bulgusu:** **EK D.6'nın iki kopyası vardı** ve ikisi de "tek kaynak burasıdır" diyordu (eskisi kapanmış bir soruyu açık gösteriyordu); **EK D.7** Aşama 1'de donmuşken "frontend için tek adres" diyordu — ikisi emekliye ayrıldı. **Dört çıkarım reddi çözümsüz geliyordu** ve `switch_to_manual_form` sözlükte kullanılmadan duruyordu (`B-114`). **Dört sözlük değeri daha üretilemiyordu** ve biri **girdiydi**: `two_column` kabul edilip sessizce başka basılıyordu — `V17` dördünü de kolonlardan düşürdü (`B-116`). `trace.C.estimatedAtoms` iki bölümde vaat edilip atılıyordu, § 48.3'ün üç satırının hiç serisi yoktu. `job_analysis`'in işveren cümlesi "bir sonraki sürümde" diye ertelenmişti, v2 geldi ve cümle girmedi — **v3** yazıldı, eval'den geçti (%100 / %91.7 / %100). § 37.6 frontend'e **çalışan bir kontrolü çizmemesini** söylüyordu (`B-115`). Artı on dört bayat blok.

**Ölçümler.** **Faz D artık çalışıyor** — eşikler gerçek BGE-M3 vektörleriyle ölçülüp skordan **kanıta** taşındı (§ 21.2): kosinüs her profilde 0.63-0.84 arası dar bir bant, yani alakasız bir akademik CV hiçbir terim adlandırmadan 0.3870 alırken eşleşen CV 0.4133 alıyordu — hiçbir mutlak taban ikisini ayıramaz. Kapı `matchedTerms`, taban 0.35, `ADAPT` 4 terim; eşleşen profilde 2 aday, ötekilerin altısında 0. Ölçüm `ScoreReachIT`, `gradlew embeddingTest` (gerçek TEI ister). `cover_letter` **v1**. Sayfa garantisi üç şablonda gerçek derleyiciye karşı %3 içinde. **§ 29.2'nin format dökümü XeTeX'te imkânsız** (motor sınırı, ölçüldü).

**Geliştiricide:** VPS ve restore testi (§ 49.4); OAuth, Turnstile, `B-083`'ün challenge'ı gerçek uca karşı denenmedi. **Admin teşhis ucu** (§ 41.4) ve **R2** (§ 57.4) bilerek yok; ikincisini bir tuzak tel tutuyor. **Yerelde iki şey bekliyor:** `make db-reset` (migration yorumları düzenlendi, checksum'lar değişti; üstelik `V17` indi) ve **`make record`** — `job_analysis` v3'e çıktığı için o prompt'un fixture'ları ıskalanıyor ve `local-fake` sentetik cevaba düşüyor. `scripts/dev-record.sh` ilanı kendi taşıyor; senden istediği tek şey bir CV dosyası.

**Test:** 1917 birim · 580 entegrasyon · latex 145 — 0 hata; artı elle koşulan `embeddingTest` ve `llmEval` (para harcar)

## Frontend — `atomcv-frontend`

| Aşama / Adım | Durum |
|---|---|
| Aşama 0-2 — iskelet, profil editörü, üretim akışı + SSE | ✅ |
| Aşama 3 — **bütün dilimler** | ✅ |
| Aşama 4 — `B-071`-`B-074`, `B-085`-`B-087`, `B-088`-`B-094`, `B-096` | ✅ |
| Aşama 4 — SEO, a11y denetimi, tema, `canAddAlternatives`, bağımlılıklar | ✅ |

**Aşama 4'ün sekizi karşılandı (2026-09-11).** Faz G'nin cümle kutusu, üç şablon + Katman B, `/applications`, DOCX, `/unsubscribe` — satır satır `handoff/resolved/to-frontend-2026-09.md`'de. **Tek eksik bilerek:** `B-088`'in elle aç/kapa arayüzü çizilmedi, çünkü hangi atomların tartıldığını söyleyen uç yok (`F-031`); istemci fonksiyonu ve `GENERATION_SUPERSEDED` indi. **`gen:api` bir sessiz kusur açığa çıkardı:** springdoc `DELETE /account`'u `delete_2`'ye kaydırdı ve `delete_1` başvuru silmeye geçti; ikisi de 204 döndüğü için typecheck sustu — numaralı id'li her uç artık **yoluyla** bağlanıyor (`F-033`).

**Backend beklemeyen beş iş de indi (2026-09-12).** **SEO** (`robots.txt`, `sitemap.xml`, canonical + hreflang, `noindex`) — alan adı yok, `NEXT_PUBLIC_SITE_URL` dağıtımda ayarlanacak. **axe taraması** on bir ekranda, açık ve koyu; ilk koşuşta iki gerçek kontrast hatası buldu. **Tema** üç durumlu, flash yok, landing hâlâ 0.0 KB kendi JS'i. **`canAddAlternatives`** üç aşama sonra bir kontrole kavuştu.

**⚠ axe paleti görmüyor.** Token'lar `oklch`, Tailwind'in alfası `oklab(… / α)`'ya derleniyor, ve axe böyle bir arka planlı düğümü **ne ihlal ne `incomplete`** sayar — düşürür. Koyu temada 3.16'da duran bir düğme taramayı sessizce geçti. `palette.test.ts` artık çiftleri hesapla ölçüyor, hover dahil. Backend'in kendi a11y/kontrast denetimi varsa aynı tuzağa bakmaya değer.

**Güvenlik:** `next` 16.3.0 iki **kritik** RCE uyarısının aralığındaydı (Windows sunucu; AVIF/görüntü optimizasyonu). 16.3.5'e çıkıldı, kalan yedisi geliştirme zinciriydi, **sıfır açık**. CI action'ları `@v5` — yalnız push'ta doğrulanabilir.

**Test:** 805 birim · 75 e2e · **bundle** profil 254.7 / ayarlar 241.0 / üretim 223.3 / onboarding 220.8 / başvurular 216.0 / geçmiş 214.8 / landing 168.8 KB.

## Açık kararlar

**Açık karar yok** (2026-09-20). Altıncı denetimin gerektirdiği sekiz karar verildi ve aynı gün uygulandı (kayıt: `notes/current.md`). Ondan önceki ikisi ölçümle kapanmıştı: **`ADAPT` barajı 4'te kaldı** — 7 profil 218 atoma karşı dağılım `{0→206, 1→4, 2→2, 3→5, 5→1}`, yani baraj *ulaşılabilir* (0.65'in aritmetik kapalılığı yok) ve tam 4'te hiçbir şey olmadığı için 3'e inmek kümeyi 1'den 6'ya çıkarırdı; tek analiz edilmiş ilanla ayar yapılmadı, muhafızı `AdaptBarReachTest`, kayıt § 21.2. **Ölçek tavanı 3.0'da kaldı** — bozuk olan tahmin ediciydi, düzeltildikten sonra doğrusal 1.94/2.02'ye karşı ekili karesel 3.86/3.63 (`performance-budgets.yaml`). Ayrıca **VPS'in beş kararı** verildi ve **doküman çevirisi kapsam dışı** bırakıldı: `docs/vps-dagitim-plani.md` § 0.

_Daha önce kapandı 09-09: model `openai/gpt-5.6-sol`; `emphasis` kalın, bedeli sıfır; anonim çalışma **profiliyle üretimleriyle** taşınıyor (hesabın profili varsa `kept_existing`, ikisi de sönüyor)._

## Sonraki senkronizasyon noktası

**Sırada `B-100`…`B-116` var (2026-09-20).** On yedisi de denetimlerden ve hiçbiri ACK'lenmedi, yani `to-frontend.md` sınırı üç kattan fazla geçti — bu bir arşivleme değil koordinasyon meselesi. **Önce `npm run gen:api`**: dört turda şema değişti, sonuncusunda `Resolution.action` iki değer kazandı ve üç enum daraldı. Başlıcaları: `B-100` (CSP — dağıtımda görülmeli), `B-101` (`contract-check`'in URL'i), `B-103` (etiketler), `B-108` (`heldBackReason` dört ayrı cümle istiyor), **`B-111`** (çeviri dosyalarınız yanlış tablodan yazılmış olabilir), **`B-114`** (dört çıkarım reddi artık çözüm taşıyor — iki yeni ICU anahtarı), **`B-115`** (§ 37.6'nın iki düğmesi çalışıyor, spec "çizmeyin" diyordu), **`B-116`** (`failed`, `cancelled`, `two_column` telden kalktı).

**Frontend'de kalanlar karar, kod değil:** analitik (ölçümü alacak bir dağıtım istiyor), bölüm düzeni ve dil ekseni kontrolleri, diğer diller.
