# İnşa Notları — Aktif

> Kural: bu dosya **200 satırı geçmez**. Aşama bitince `archive/`'a taşınır, boş başlanır.
> Kayıt tipleri: **Sapma** (doküman başka diyor) · **Ekleme** (doküman sessiz) · **Düzeltme** (doküman yanlış).
> Bir sapma kalıcıysa `spec/`'e işlenir ve buradan silinir.

**Aktif aşama:** Aşama 4 — Olgunlaşma. **Plan:** `spec/14-build-guide.md`
§ XI-A.7 (sabit sıra yok, öncelik önerisi var); gerekçesi § 55.
Aşama 1-2: `archive/stage-1.md`, `archive/stage-2.md`. **Aşama 3 ve kapanış
sonrasının yuvarlanan özetleri `archive/stage-3-closeout.md`'ye indi
(2026-09-10)** — aşağıdaki "hâlâ canlı" bölümü onlardan çıkarılanlar.

---

## Aşama 4 · e-postalar ve açık kaynak (2026-09-11)

**Ekleme — § 57.7 yaşam döngüsü e-postalarını tanımladı.** İnşa kılavuzu maddeyi
adlandırıp bırakıyordu, yani "bu ürün hangi e-postaları gönderir" sorusunun
uygulanacak bir cevabı yoktu. Liste **kapalı**: hoş geldin ve silme onayı.

**Ve spec uygulanmadan önce iki kez düzeltildi** — bu dilimin asıl kazancı bu.
(1) Tetikleyici "`users` satırı ilk yazıldığında" diyordu; § 40.4 hesap sayımını
engellemek için satırı adres yazılır yazılmaz yaratıyor, yani **giriş kutusuna
adresi yazılan herkese** posta giderdi. Doğrusu ilk başarılı giriş
(`last_seen_at` null). (2) Tercih `PUT /profile/preferences`'a konacaktı; o uç
profili yerine koyuyor ve profilin ETag'iyle korunuyor, yani bir CV çakışması
e-posta ayarını reddederdi ve alanı göndermemek onu kapatmak olurdu.
`GET`/`PATCH /api/v1/account` oldu.

**Ekleme — silme onayı işlemsel, hoş geldin değil.** Adres satır silinmeden
okunuyor ve posta commit'ten sonra çıkıyor (servis adresi döndürüyor, çağıran
gönderiyor): geri alınan bir silmenin onayı yalan olurdu. Hoş geldin için aynı
özen gereksiz — geri alınan bir girişin hoş geldini bir fazla postadır.

**Ekleme — kapatma bağlantısı uca değil sayfaya iniyor** (§ 40.3). Ağ geçitleri
mesajdaki her adresi kimse okumadan çekiyor; çekilince kapatan bir uç, hiç
tıklamamış kişilerin postasını keserdi. Jeton opak ve satırda (`unsubscribe_token`),
imzalı değil — doğrulaması sır istemesin diye. Bilinmeyen jeton da 204.

**Düzeltme — `.env.example` hiçbir şeyin okumadığı bir harcama limiti sunuyordu.**
`DAILY_BUDGET_USD`'yi spec dört yerde anıyordu ve yayın kontrol listesinde
maddesi vardı; kod `ANOMALY_DAILY_BUDGET_USD` okuyor. Kurtaran tek şey
varsayılanın daha düşük olması: kill switch geç değil erken ateşliyordu.
`EnvExampleTest` iki kuralı tutuyor — örnekte okunmayan ad olamaz, varsayılanı
olmayan ad eksik olamaz.

**Ders — görev girdisi olmayan bir dosya kimseyi bağlamaz.** Bugün ikinci kez:
`performance-budgets.yaml` gibi `.env.example` de `inputs.file` ile bildirildi.
Aksi hâlde Gradle `:test UP-TO-DATE` deyip onu denetleyen testi hiç koşturmuyor,
ki `DAILY_BUDGET_USD`'nin kayma yolu tam buydu.

**Ders — ölçüm belgesiyle sayfa arasındaki fark her zaman bir hatadır**, ve
sapma testi tek başına yetmez: bir maddedeki bir satır yedi yüz puanın içinde
kaybolur. `WordingCostIT` bir ifadenin, `EntryFurnitureIT` bir girdinin marjinal
bedelini sayfaya karşı ölçüyor. **`EntryFurnitureIT`'in sabitlediği ~2.9pt'lik
artık bir kusur değil**, aracın kendisi: test listesi *olan* belgeyi listesi
*olmayanla* karşılaştırdığı için listenin arkasında duran tek şey prob oluyor.
Girdi eklemenin marjinal bedeli üç şablonda da modelin yazdığına **tam** eşit
(43.72 / 25.47 / 51.72), her boyutta ve ardından başlık gelse de gelmese de.

---

## Aşama 1-3'ten taşınanlar — hâlâ canlı

**Faz G'den taşınanlar** (tam gerekçe `archive/stage-4-faz-g.md`): değişiklik
seti modele **atom id'si değil satır numarası** gösteriyor — uydurulmuş bir UUID
aranana kadar gerçeğinden ayrılmaz, aralık dışı bir indeks bariz (**kalıcı,
§ 24.2'ye işlenmeli**). Yarım anlaşılmış cümle **hiç** uygulanmıyor;
`understood: false` arıza değil ve sık dönecek. Geçmişin `total`'ı satır değil
**CV** sayıyor ve onu hesap silme ekranı okuyor (`F-020`). Yeniden koşu Faz
C'den başlıyor, skorlar snapshot'tan — üretimden sonra profile eklenen atom
skorsuzdur ama adıyla istenebilir.

**Tamir etmeye kalkma — hepsi bilinçli, gerekçeleri arşivde:** `SectionFloor`
bir tavandır talep değil; `reservedByFloor` `forcedByLock`'tan ayrı bir küme;
bir inline satırın öğeleri **ve** etiketi sade diziliyor; `PARAGRAPH`
`INLINE_LIST`'e katlanmadı. `suspicious_output` telde hiç görülmedi (enjeksiyon
tripwire'ı); `bullet_rewrite` / `about_synthesis` fixture'ı olmayan girdide
anlamsız — doğrulayıcı reddeder, orijinal basılır.

**Sözlükler ve şemalar:**
- **"Kritik uyarı" diye bir şey yok, `critical` bayrağı da yok** —
  `ExtractionWarningCode` kapalı; § 31.6'nın üçüncü kuralı **silindi**.
- **`ImportWarning.code` `String`, şeması enum** — değer JSONB'den geri okunuyor,
  enum yapmak adı değişmiş satırı düşürür. **`OpenApiSchemaIT`'in okuduğu altı
  değer elle yazılı**: `values()`'tan türetilirse yedinciye de "evet" der.
- **`shared.wire` bir sonraki kapalı sözlüğün yeri**; `shared.error` retlerin.
- **Hata kataloğu tablosunun `params` sütunu düzyazı kabul etmiyor** —
  `ErrorCatalogueSpecTest` birebir ayrıştırıyor, bir hücreye eklenen açıklama
  testi düşürür.

**Ölçümler ve eşikler:**
- **Faz D eşikleri ölçüldü, teste sabitlendi (`PhaseDReachTest`), karar
  ertelendi.** Gerçek embedding'le en yüksek atom skoru 0.2741-0.4313; § 21.2'nin
  0.65'ine hiçbirinde yaklaşılmadı. **Sebep ayar değil aritmetik** (§ 28.4'e
  işlendi). **Yeni ölçüm gerekmiyor:** `default` setli üretim biriktiğinde karar
  veriye dayanır ve test o gün yeni sayılarla düşer.
- **`cover_letter` aktif `v1`.** v2 turu (09-09) 169 kelime verdi, bant 255-290.
  **Ders: tamamına karşı ölç, ölçütü önce yaz.**
- **`MeasurementDriftIT.heightOnThePage`'in `\pagetotal`'ı yalnız bulunulan
  sayfayı sayıyor** — iki sayfalık belgede sapması anlamsız, bilerek bırakıldı;
  `theRealDocumentNeverRunsPastThePage` sayfa sayısını ayrıca tutuyor.

**Test yazarken:**
- **`MagicLinkApiIT` her testten önce `ratelimit:*`'ı siliyor.** Silmeyen bir
  kimlik testi dördüncüde ilgisiz bir 429'da düşer, flake gibi okunur.
- **`profiles` entegrasyon paketinde hiç boş değil** (`DevSeeder`) — anonim
  gizlilik iddiası "satır sayısı değişmedi" diye kuruluyor, ve dev kullanıcısına
  ikinci bir profil eklenemez (tek-profil unique index'i).
- **`local` profilinde LLM sağlayıcısı yok** — entegrasyon lane'inde her çağrı
  `ALL_PROVIDERS_UNAVAILABLE` alır; `CoverLetterApiIT` kasten öyle kuruyor.
  **`AccountDeletionIT` tablo listesini `information_schema`'dan okuyor.**
- **Dev stub ölçümü yiyor** (ikinci kez, `F-027` ve `F-030`'da).
  `SessionCurrentUser.resolve` çerez *yoksa* `LocalDevSessions`'a düşüyor, yani
  **çerezsiz istekle yazılmış hiçbir test kimlik davranışını ölçmüyor.** Doğru
  kurgu **çözülmeyen bir çerez**.

**Sınırlar ve açıklar:**
- **Kalibrasyon belgesi ~0.6 inç'ten ferah bir geometriye sığmıyor** (2026-09-10).
  § 33.2 margin'i 1.0'a kadar açıyor; o aralık **kalıcı olarak tahminde**, yani
  sayfanın %92'si harcanıyor ve tahminin güvenliği o ayarlarda **doğrulanamıyor**
  (karşılaştırılacak ölçüm yok). Güvenli, çünkü tahmin yalnız cimri olabiliyor.
  **Modern'in ilk taslağı bunu böyle buldu**: 0.6in/1.05'te belge on bir punto
  taştı, `\pagetotal` sıfırlandı, bir proje başlığı **−646.7pt** ölçüldü.
  `CalibrationService` artık geriye giden okumayı reddediyor — o olmasa Katman
  B'nin ölçüm işi bu sayıyı `template_capacities`'e yazacaktı ve o ayardaki her
  sayfa sessizce taşacaktı.
  **Gerçek çözüm belgeyi sayfalara bölmek**, ama probe çiftlerinin hiçbiri bir
  sayfa kırığını aşmamalı ve `SECTION_HEADER` kasten üstündeki bloğa bağlı
  (`asectionHeadingCostsTheSameAfterAsectionOfEntries`) — yani klasik ve
  kompakt'ın ölçülmüş sabitlerini de değiştirebilir. Yapılmadan önce ölçülmeli.
- **Axiom'da loglar görünüyor** (2026-08-26); telde doğrulanması dağıtımı bekliyor.
- **`UserScopedRepository`'de `findAll` yok** — alt sınıflar kendi bulucularını ekler.
- **Faz D sekize kadar eşzamanlı çağrı yapıyor**, havuz 10, işçi eşzamanlılığı 2
  → tepede 16 kısa ödünç. **Havuz büyütülmeden işçi eşzamanlılığı artırılmamalı.**
- **R2'deki PDF'ler** § 57.4'ün silme listesinde ama R2 istemcisi yok
  (7. karar: MVP'ye girmiyor; `pdf_key`'i hiçbir şey yazmıyor).
- **`ExtractedContact`, `Contact` ve şema aynı şekli üç yerde taşıyor**
  (§ 31.4.1). **`Contact.linkedin`** CV alanı, LinkedIn *girişiyle* ilgisiz.
- **`accessedAt` (`B-078`):** çevrimdışı okuyucu grant'i kimlik belgesi sayıp
  sahibinin bağlamında okuyor; **tek kapsanmamış adım grant aramasıdır**
  (`SupportGrantLookup`, API'den erişilemez — ArchUnit kuralı var).
- **Geliştiricide kalan: VPS ve restore testi** (§ 49.4: restore sonrası anonim
  satırları silmek), OAuth ve Turnstile'ın gerçek uca karşı denenmesi.
- **springdoc çok parçalı bir uçta `@RequestParam`'ı query parametresi diye
  yayımlıyor.** Sonraki çok parçalı uçta aynısı olur: dosya olmayan her parça
  elle bildirilmezse query'ye düşer.

**Dersler:** *bir javadoc ne zaman çalıştığını söylüyorsa çağıranı da ara* (3.4)
· *doğru davranan kod, korunan değildir* (3.5) · *bir ölçüm belgesi, sayfayla
aynı preamble'ı paylaştığı için aynı belge olmuyor* (dilim L) · *golden fixture
yazılmaz, okunur* — ama `stress_long_career` yazıldı, çünkü iki sayfa hatasını
üreten şekli elle yazılmış beşi yakalayamıyordu · *flake demeden önce dalına
bak* · *bir redaksiyon, durduğu kayıt yenilenince bayatlar* · **§ 51.7: bir
muhafızın düştüğünü görmeden yazıldı sayma.**

---

## Kapanan adımların arşiv haritası

| Adım | İnşa kaydı | Kalıcı kararlar |
|---|---|---|
| 3.3 kimlik | `stage-3-identity.md` | § 40.4.1, § 40.5.1, § 40.6.1, § 46.5 |
| 3.4 çıkarım | `stage-3-ingestion.md` | § 31.3.1, § 31.4.1, § 31.5.1, § 31.6.1-2, § 43.1, § 53.1 |
| 3.5 çok dillilik | `stage-3-multilingual.md` | § 32.2.1, § 32.3.1 |
| 3.6 anonim | `stage-3-anonymous.md` | § 35.7.1, § 41.3.1-3, § 44.1.1, § 44.2, § 31.6.3, § 51.6.1 |
| 3.8 Faz D | `stage-3-faz-d.md` | § 21.1 notu, § 21.3.1, § 21.5.1-7.1, § 34.4.1 |
| 3.9 hukuki | — | § 57.4.1, § 48.4.1 |
| dilim 9-14 · `F-017`-`F-027` | `stage-3-handoff-answers.md`, `stage-3-slice-14.md` | — |
| kapanış sonrası A-M | `stage-3-post-closure-e2e.md`, `stage-3-post-closure-shape.md` | § 18.4, § 20, § 31.3.1, § 33.4.1, § 21.2, § 22.4.1 |
| kapanış denetimi + yuvarlanan özetler | `kapanis-denetimi.md`, `stage-3-closeout.md` | § 47, § 57.4, § 3.2, § 51.7, § 20.2 |
| Aşama 4 · Faz G | `stage-4-faz-g.md` | § 24.2 (bekliyor) |
| Aşama 4 · sayfa garantisi | `stage-4-page-guarantee.md` | § 26.4, § 33.1 (sabitler) |

Frontend aksiyonları: `B-055`-`B-096`.
