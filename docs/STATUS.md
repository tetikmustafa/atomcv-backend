# AtomCV — Durum Panosu

> İki repo da okur ve kendi satırlarını günceller. **Kural: 60 satırı geçmez.**
> Ayrıntı repo-yerel `notes/current.md`'de.

**2026-09-16** · **frontend'e on iki madde açık** — `B-100`…`B-111`, üç denetimden, hiçbiri henüz ACK'lenmedi

## Backend — `atomcv-backend`

| Aşama / Adım | Durum |
|---|---|
| Aşama 0-3 — hesap, MVP, anonim akış (kapanış denetimi 08-28) | ✅ |
| Aşama 4 — buradan yapılabilecek maddelerin hepsi | ✅ |

**Aşama 4 + üç denetim.** **Faz G** (`B-088`, `B-089`), **üç şablon** (`B-090`, `B-092`), **Katman B** (`B-091`), **başvuru takibi** (`B-093`), **DOCX** (`B-094`), **yaşam döngüsü e-postaları** (`B-096`). Sonra spec **üç kez** baştan sona koda karşı okundu (09-15, 09-16 ×2); üçünün de bulduğu her şey ya yazıldı ya gerekçesiyle `spec/`'e kaydedildi. Anlatı `notes/`'ta ve `notes/archive/denetim-*.md`'de.

**Birinci tur (09-15)** on altı eksik indirdi (arşivleme ucu, atom etiketleri, `/warmup`, HTML renderer, `format=source`, GitHub içe aktarımı, `/customizations`, CDS, Umami, çeviri adımı, `LISTEN/NOTIFY`, replay) ve **üç sessiz kusur** buldu: nginx CSP'si Turnstile'ı blokluyordu (`B-100`), `tags`/`atom_tags`'e hiçbir şey yazmıyordu (`B-103`), çeviri fan-out'u model çağrısını `@Transactional` içinde tutuyordu. **İkinci tur (09-16) sekiz madde buldu, altısı kodda:** İlke 7 telde yoktu (`B-108`); WAL arşivleme, ikinci yedek sağlayıcısı, `restore.sh`'ın anonim silmesi, circuit breaker ve § 42.4'ün JSON limitleri hiç kurulmamıştı. Detay `notes/archive/denetim-*.md`'de.

**Üçüncü tur (09-16) üç gerçek boşluk buldu, ve şekli farklıydı: yazılmamış olduğu hâlde yazılmış sayılan tasarım.** **(1)** **`ContentShape` hiç yazılmamıştı** — § 48.2 tanımlıyor, EK A sözlükte listeliyor, `CLAUDE.md`'nin mutlak kural 4'ü ve `noContentInLogs`'un javadoc'u adını veriyor, `src/`'de yoktu. Yazıldı ve üç log satırına bağlandı. **(2)** **§ 17'nin faz sözleşmesi** (`PipelinePhase`, `PipelineContext`, yedi tip) hiç kurulmamıştı ve kayıtlı değildi; spec gerçeğe göre düzeltildi. **(3)** **Format bağımsızlığı sınırın yanlış tarafındaydı** — `DocumentRenderer`'ın iki metodunun tek çağıranı yoktu, `generation` DOCX/HTML'e somut uzanıyordu; `DocumentWriter` + `DocumentWriters` indi, **ArchUnit kuralı** tutuyor. Ayrıca **gitleaks bir süredir koşmuyordu** (`core.hooksPath` `.git/hooks`'u iptal ediyor) ve on iki doküman sapması kapandı — en önemlisi **iki otoriteli hata kataloğu** (`B-111`).

**Ölçümler.** Faz D eşiklerine hiçbir gerçek skor ulaşmıyor (0.1259 / taban 0.40). `cover_letter` **v1**. Sayfa garantisi üç şablonda gerçek derleyiciye karşı %3 içinde. **§ 29.2'nin format dökümü XeTeX'te imkânsız** (motor sınırı, ölçüldü).

**Geliştiricide:** VPS ve restore testi (§ 49.4); OAuth, Turnstile, `B-083`'ün challenge'ı gerçek uca karşı denenmedi. **Admin teşhis ucu** (§ 41.4) ve **R2** (§ 57.4) bilerek yok; ikincisini bir tuzak tel tutuyor. **Yerelde `make db-reset` bekliyor:** migration yorumları düzenlendi (bilinçli istisna, `notes/`), checksum'lar değişti.

**Test:** 1875 birim · 580 entegrasyon · latex 145 — 0 hata

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

**Sırada `B-100`…`B-111` var (2026-09-16).** On ikisi de üç denetimden ve hiçbiri ACK'lenmedi, yani `to-frontend.md` 100 satırı geçti — bu bir arşivleme değil koordinasyon meselesi. **Önce `npm run gen:api`**: birinci turda altı uç ve üç şema, ikincide `SelectionLine`. Başlıcanlar: `B-100` (CSP — kod işi yok ama dağıtımda görülmeli), `B-101` (`contract-check`'in URL'i), `B-103` (etiketler), `B-107` (`auto` ilanı takip ediyor: üretim yavaşlayabilir), `B-108` (`heldBackReason` dört ayrı cümle istiyor — `INACTIVE` profil ayarı, `EXCLUDED_BY_DIRECTIVE` bu CV'nin düzenlemesi), **`B-111`** (çeviri dosyalarınız yanlış tablodan yazılmış olabilir: 27 koda karşı 41).

**Frontend'de kalanlar karar, kod değil:** analitik (ölçümü alacak bir dağıtım istiyor), bölüm düzeni ve dil ekseni kontrolleri, diğer diller, `docs/spec/`'in İngilizceye çevrilmesi.
