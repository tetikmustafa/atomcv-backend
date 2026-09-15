# AtomCV — Durum Panosu

> İki repo da okur ve kendi satırlarını günceller. **Kural: 60 satırı geçmez.**
> Ayrıntı repo-yerel `notes/current.md`'de.

**2026-09-16** · **frontend'e dokuz madde açık** — `B-100`…`B-108`, iki denetimden, hiçbiri henüz ACK'lenmedi

## Backend — `atomcv-backend`

| Aşama / Adım | Durum |
|---|---|
| Aşama 0-3 — hesap, MVP, anonim akış (kapanış denetimi 08-28) | ✅ |
| Aşama 4 — buradan yapılabilecek maddelerin hepsi | ✅ |

**Aşama 4 + iki denetim.** **Faz G** (`B-088`, `B-089`), **üç şablon** (`B-090`, `B-092`), **Katman B** (`B-091`), **başvuru takibi** (`B-093`), **DOCX** (`B-094`), **yaşam döngüsü e-postaları** (`B-096`). Sonra spec **iki kez** baştan sona koda karşı okundu (09-15, 09-16); ikisinin de bulduğu her şey ya yazıldı ya gerekçesiyle `spec/`'e kaydedildi. Anlatı `notes/`'ta.

**Birinci turda inenler (09-15).** Arşivleme ucu, atom etiketleri, `/api/v1/warmup`, commit'li `openapi.json`, `emphasize` + `note`, HTML renderer, `format=source`, GitHub içe aktarımı, `/customizations` + `GET /templates`, CDS, Umami, § 21.8'in çeviri adımı (`F-013`), § 32.5'in pivotu, § 30.6'nın `LISTEN/NOTIFY`'ı, § 48.3'ün metrikleri, § 48.5'in replay'i. **Üç sessiz bulgu:** nginx CSP'si Turnstile'ı blokluyordu (`B-100`), `tags`/`atom_tags`'e hiçbir şey yazmıyordu (`B-103`), çeviri fan-out'u model çağrısını `@Transactional` içinde tutuyordu.

**İkinci tur sekiz madde daha buldu (09-16), altısı kodda.** **(1)** **İlke 7 telde yoktu** — skor, eşleşen keyword ve red nedeni hesaplanıyor, hiçbiri yayımlanmıyordu; `matchedKeywords` + `heldBackReason` indi, skor § 23.3 gerekçesiyle bilerek inmedi (`B-108`, § 35.3.1). **(2)** **WAL arşivleme hiç kurulmamıştı** — § 49.5'in "~5 dakika"sı gerçekte 03:00'a kadardı; `archive_mode` + haftalık `pg_basebackup` indi, çünkü WAL `pg_dump`'a değil fiziksel bir temele oynanır. **(3)** **3-2-1'in ikinci sağlayıcısı yoktu**, saklama tek `7d` idi. **(4)** `restore.sh` § 49.4'ün anonim satır silmesini hiç yapmıyordu. **(5)** **circuit breaker yoktu** — karanlık sağlayıcıya her üretim 30 sn ödüyordu. **(6)** § 42.4'ün JSON limitleri kurulmamıştı; `.env.example`'da `AGE_PUBLIC_KEY` hiç yoktu. Ayrıca § 27.2'nin **üç eksik adaptörü** yazıldı (dağıtım değişmiyor: anahtarsız adaptör atıl).

**İki döküman bulgusu.** § 35.2.1 beş ucun "yok" olduğunu savunuyordu, bir aşamadır varken. **EK D monolitte kalmıştı** — spec ona 75 kez atıf yapıyor ve `sync-spec.sh` onu kopyalamıyordu, yani frontend'in kopyasında 75 atfın hepsi boşa düşüyordu; `spec/18-appendix-d.md`'ye taşındı.

**Ölçümler.** Faz D eşiklerine hiçbir gerçek skor ulaşmıyor (0.1259 / taban 0.40). `cover_letter` **v1**. Sayfa garantisi üç şablonda gerçek derleyiciye karşı %3 içinde. **§ 29.2'nin format dökümü XeTeX'te imkânsız** (motor sınırı, ölçüldü).

**Geliştiricide:** VPS ve restore testi (§ 49.4); OAuth, Turnstile, `B-083`'ün challenge'ı gerçek uca karşı denenmedi. **Admin teşhis ucu** (§ 41.4) ve **R2** (§ 57.4) bilerek yok; ikincisini bir tuzak tel tutuyor.

**Test:** 1869 birim · 580 entegrasyon · latex 145 — 0 hata

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

**Sırada `B-100`…`B-108` var (2026-09-16).** Dokuzu da iki denetimden ve hiçbiri ACK'lenmedi, yani `to-frontend.md` 100 satırı geçti — bu bir arşivleme değil koordinasyon meselesi. **Önce `npm run gen:api`**: birinci turda altı uç ve üç şema, ikincide `SelectionLine`. Başlıcanlar: `B-100` (CSP — kod işi yok ama dağıtımda görülmeli), `B-101` (`contract-check`'in URL'i), `B-103` (etiketler), `B-107` (`auto` ilanı takip ediyor: üretim yavaşlayabilir), `B-108` (`heldBackReason` dört ayrı cümle istiyor — `INACTIVE` profil ayarı, `EXCLUDED_BY_DIRECTIVE` bu CV'nin düzenlemesi; karıştıran bir ekran kalıcı kararı geri aldırır).

**Frontend'de kalanlar karar, kod değil:** analitik (ölçümü alacak bir dağıtım istiyor), bölüm düzeni ve dil ekseni kontrolleri, diğer diller, `docs/spec/`'in İngilizceye çevrilmesi.
