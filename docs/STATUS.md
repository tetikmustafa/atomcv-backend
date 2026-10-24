# AtomCV — Durum Panosu

> İki repo da okur ve kendi satırlarını günceller. **Kural: 60 satırı geçmez.**
> Ayrıntı repo-yerel `notes/current.md`'de.

**2026-10-24** · **Kapanış turu bitti, ikinci denetim turu da.** Üç paralel denetim (spec↔kod, tel kapsaması, arayüz) iki repoyu baştan taradı; `F-041`…`F-045` ve `B-120`…`B-125` aynı gün karşılandı, iki kanal da boş. **Kod tarafında yapılacak iş kalmadı; kalan her şey sunucu ister** (aşağıda).

## Backend — `atomcv-backend`

| Aşama / Adım | Durum |
|---|---|
| Aşama 0-4 ve altı denetim | ✅ |
| Kapanış turu — `B-120`…`B-124`, operasyon sertleştirmesi | ✅ (PR #217-#220) |
| İkinci tur — ATS sonucunun saklanması (V18, `B-125`) | ✅ (PR #222) |

**Bu tur.** `fontFamily` beş alanda dört değerli enum (profil `book`'u reddediyordu); `CoverLetterRequest.language`; içe aktarma fazları `import.phase.*` anahtarı; `keep_top_pinned`'in cevabı `GenerationRequest.keepPinned` (önerilen `keep` bölüm başlıklarını saymadığı için **yine reddediliyordu** — düzeldi); sonuçta `roleTitle`/`companyName`; misafire giriş/bölüm kontrolleri kapalı (yalnız atomda zorlanıyordu). F-042/43/44 spec metni.

**Operasyon — bulunan gerçek kusurlar.** 13 betiğin 9'u `100644`'tü: cron'un `backup.sh`'i ve deploy'un `./scripts/deploy.sh`'i sunucuda "Permission denied" alırdı (`ScriptModeTest`). Prod compose'da log rotasyonu yoktu; `deploy.sh` frontend'i hiç health-check etmiyordu; `APP_BASE_URL` yoksa her e-posta localhost'a bağlanıyordu (`PublicBaseUrlCheck`); CSP `frame-src` PDF önizlemeyi engellerdi. Axiom'a log akışı OTLP ile eklendi — **kendi anahtarıyla** (`OTLP_LOGS_ENABLED`): metrikle paylaşınca boş log URL'li her ortam açılmıyordu.

**İkinci tur.** `AtsCheck` her PDF'i geri okuyordu ama sonuç yalnız sayaca gidiyordu (konsept § 10.1 görünmüyordu): V18 `generations.ats_check` — yalnız sayılar ve bayraklar — ve `GenerationResponse.atsCheck`. Gerçek derleyiciyle `JobSpecificCvIT` 12/12, hiçbir PDF kusur bildirmedi.

**Test:** 1953 birim · 587 entegrasyon · 0 hata; CI'ın kontrolleri (gerçek derleyici turu dahil) her PR'da geçti. İmaj JRE ile kurulup koşuldu.

## Frontend — `atomcv-frontend`

| Aşama / Adım | Durum |
|---|---|
| Aşama 0-4 | ✅ |
| Kapanış turu — tasarım sistemi, her ekranın yeniden tasarımı, telde kalan alanlar | ✅ |

**Arayüz.** Görsel yön **monokrom ve cilalı**: kırık beyaz sayfa, beyaz kartlar, tek renk yıkıcı kırmızı. Beş yeni primitive (`Card`, `Badge`, `NativeSelect`, `Sheet`, `Skeleton`), `Page`/`PageHeader`/`EmptyState`. Kabuk: `lg`'den yukarı kenar çubuğu, altında tembel yüklenen bir menü; arayüz dili seçici; çevrili 404, hata ve yükleme sayfaları. Landing, nasıl-çalışır ve yasal sayfalar yeniden çizildi (hâlâ **0 KB kendi JS'i**).

**Telde olup ekranda olmayanlar indi.** Sonuçta **PDF önizleme** (iframe, 0 KB; isteğe bağlı çünkü her indirme derliyor), **başvuruya bağlama** (`generationId` hiçbir ekrandan gönderilmiyordu), misafire indirme sonrası hesap teklifi; üretimde **sayfa sınırı**; giriş `url`/kontrolleri, bölüm anahtarları, ifade **üslubu**; kayıtlı görünüşleri yeniden adlandırma/güncelleme; **profili silme**; başvurularda filtre, düzenleme ve silme onayı; içe aktarmada sürükle-bırak, adım göstergesi, sonuç özeti. `RunEditor` artık gerçekten tembel (kural 4).

**İkinci tur.** Sonuçta **makine okunabilirliği** kartı (`B-125`; kusuru şablona yazar, CV'ye değil); atomda **"Bunu kontrol ettim"** (`verified`, misafire de açık); başvuruyu sonradan **CV'ye bağlama**; içe aktarmada **metni yapıştırma** (`.txt` olarak aynı yükleme); mektubu **`.txt` indirme**; GitHub önerilerinde yıldız/bağlantı/diller. Form id'leri örnek başına (`useId`), başlık hiyerarşisi, dokuz ölü çeviri anahtarı ve iki kullanılmayan primitive silindi.

**Test:** 1015 birim · 84 e2e (375px dahil) · axe iki temada temiz · **bundle** profil 266.2 / ayarlar 253.6 / üretim 236.0 / onboarding 232.4 / başvurular 227.1 / geçmiş 223.2 / pazarlama 169.6 KB (tavan 280 / 200) · `npm audit` sıfır.

## Açık kararlar

**Açık karar yok.** Bu turun iki sapması kayıtlı (`notes/current.md`): başvuru takibi "varsayılan açık" değil, ön dolu **tek basış**; PDF önizleme kendiliğinden değil **basınca** yükleniyor. Bilinçli olarak yapılmayanlar gerekçeleriyle aynı yerde: atom başına skor (§ 35.3.1), "yeniden hesaplanıyor" göstergesi, sürüm banner'ı, R2, admin teşhis ucu.

## Sonraki adım — yalnız sunucu ister

Sırası `vps-dagitim-plani.md` (backend) § 2-4 ve frontend `notes/current.md` § *Dağıtım günü*'nde: VPS + DNS + Origin sertifikası, `.env` (`APP_BASE_URL`, `OTLP_LOGS_*` dahil), iki cron + logrotate, GitHub secret'ları ve `deploy.yml`'ın elle ilk koşusu, restore ve rollback tatbikatı; sonra gerçek uca karşı OAuth, Turnstile, sihirli bağlantı, SPF/DKIM/DMARC, `NEXT_PUBLIC_SITE_URL`, Umami ve Sentry kararları. Frontend imajı `NEXT_PUBLIC_*`'ı **derlemede** gömüyor: `NEXT_PUBLIC_SITE_URL` ve `NEXT_PUBLIC_TURNSTILE_SITE_KEY` `--build-arg` olarak verilmeli — Dockerfile bunları almıyordu, eklendi.
