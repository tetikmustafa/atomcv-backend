# AtomCV — Durum Panosu

> İki repo da okur ve kendi satırlarını günceller. **Kural: 60 satırı geçmez.**
> Ayrıntı repo-yerel `notes/current.md`'de.

**2026-09-12** · **iki tarafta da açık madde yok** — `B-097`…`B-099` geldikleri gün karşılandı

## Backend — `atomcv-backend`

| Aşama / Adım | Durum |
|---|---|
| Aşama 0-3 — hesap, MVP, anonim akış (kapanış denetimi 08-28) | ✅ |
| Aşama 4 — buradan yapılabilecek maddelerin hepsi | ✅ |

**Aşama 4'te inenler.** **Faz G** düzenleme döngüsü: elle aç/kapa (`B-088`, kotasız) ve doğal dil (`B-089`, kotalı) — düzenleme render'a değil selection state'e uygulanıyor, sayfa sınırı yirmi düzenleme sonra da duruyor. **Üç şablon** (`B-090`, `B-092`), **Katman B** slider'ları (`B-091`, V12), **başvuru takibi** (`B-093`), **DOCX indirme** (`B-094`), **yaşam döngüsü e-postaları** (`B-096`, V14). Test tarafında: **LLM eval** lane'i (`llmEval`, § 53.5), **performans bütçeleri** (§ 52.6) ve golden set'in üç şablona genişletilmesi. **Açık kaynak hazırlığı**: lisans zaten MIT'ti, iş `CONTRIBUTING.md`'de ve bayat ön kapıdaydı.

**Ekleme — § 57.7 yaşam döngüsü e-postalarını tanımladı**, çünkü inşa kılavuzu maddeyi adlandırıp bırakıyordu. Liste **kapalı**: hoş geldin (ilk başarılı girişte, tercihe tabi) ve silme onayı (işlemsel, kapatılamaz — § 57.4 söylemeyi zorunlu kılıyor). Tercih `users.lifecycle_emails`, kapatma bağlantısı satırdaki opak bir jetonla ve **bir sayfaya** iniyor: § 40.3'ün ön-getirmesi, uca inen bir bağlantıyı hiç tıklamamış kişilerin postasını kesen bir şeye çevirirdi. Spec **uygulanmadan önce iki kez düzeltildi**: satır tetikleyicisi giriş kutusuna adresi yazılan herkese posta gönderirdi (§ 40.4 satırı hemen yaratıyor), ve tercihi `PUT /profile/preferences`'a koymak bir CV çakışmasının e-posta ayarını reddetmesi olurdu.

**Frontend'in üç maddesi geldikleri gün kapandı (2026-09-12).** `GET /generations/{id}/selection` bu üretimin tarttığı satırları **metniyle** yayımlıyor ve `GenerationResponse` `supersededByGenerationId` taşıyor (`F-031` — `B-088`'in arayüzünü bekleten tek şeydi). `JobStatusResponse` `supersededGenerationId` **ve** `matchLevel` kazandı (`F-032`; ikincisi istenmemişti ve birebir aynı kusurdu — worker'ın `result`'ına yazılan anahtar akışta var, tipte yoktu). 33 ucun hepsi açık bir `operationId` aldı, `empty` iki şemadan kalktı (`F-033`) — ve muhafız isimler değil, `_<sayı>` ile biten bir `operationId` görünce düşen test. Frontend'e `B-097`-`B-099`.

**Sayfa garantisi: üçünün de tuttuğu gerçek derleyiciye karşı doğrulandı** — yedi golden profil, hepsi %3 içinde. Genişletme **beş kusur** çıkardı ve beşi de aynı cümleydi: *sayfanın dizdiği ama ölçümün hiç görmediği bir şey.* Listeden sonraki bölüm başlığı; sabit sanılan başlık bloğu (V13 `profiles.header_costs`, artık ölçülüyor); `\resumeItem`'ın iki ayrı kaçak boşluğu; compact'in aynı boşluğu iki kez yazması. **İkisi kullanıcıya ulaşmıştı** — compact'te ve modern'de birer profil ikinci sayfaya taşıyordu. `B-095` açıldı, düzeltildi, kapandı. **Şablon sürümleri yükseldi** (`classic:v6`, `compact:v2`, `modern:v3`); sürüm yalnız ölçüm anahtarlarında geçiyor, API'de değil.

**Ölçümler:** Faz D eşiklerine hiçbir gerçek skor ulaşmıyor (`PhaseDReachTest`, sebep aritmetik); `cover_letter` **v1** (v2 turu 169 kelime, bant 255-290); üç BOM override'ı hâlâ gerekli, `SecurityPatchFloorTest` tutuyor.

**Geliştiricide:** VPS/restore (**restore sonrası anonim satırları silmek**, § 49.4); OAuth, Turnstile ve `B-083`'ün challenge'ı gerçek uca karşı denenmedi; GitHub entegrasyonu (§ 31.8) gerçek bir OAuth uygulaması istiyor.

**Test:** 1722 birim · 539 entegrasyon · latex 141 — 0 hata

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

| Soru | Bekleyen taraf |
|---|---|
| Faz D eşiklerinin normalizasyonu | **veri** · `default` setli üretim biriktiğinde |

_Kapandı 09-09: model `openai/gpt-5.6-sol`; `emphasis` kalın, bedeli sıfır; anonim çalışma **profiliyle üretimleriyle** taşınıyor (hesabın profili varsa `kept_existing`, ikisi de sönüyor)._

## Sonraki senkronizasyon noktası

**Üçü de kapandı (2026-09-12).** `gen:api` yeniden koşuldu ve 26 operasyon adı yeni adlarına bağlandı (`B-099`; yol üzerinden bağlayan iki yardımcı silindi, gerekçeleri kalmadı). `JobStatus` iki yeni alanı tipli taşıyor ve mock'ta terminal yük tek yerde üretiliyor (`B-098`). **Faz G'nin elle aç/kapa arayüzü indi** (`B-097`): `GET /selection`'dan çizilen liste, yalnız yeri değişen satırları gönderen bir kaydet, ve emekliden halefe bağlantı. Sonuç rotası 219.3 → **224.4 KB** (tavan 280).

**Frontend'de kalanlar karar, kod değil:** analitik (ölçümü alacak bir dağıtım istiyor), bölüm düzeni ve dil ekseni kontrolleri, diğer diller, `docs/spec/`'in İngilizceye çevrilmesi.
