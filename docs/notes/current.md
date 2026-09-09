# İnşa Notları — Aktif

> Kural: bu dosya **200 satırı geçmez**. Aşama bitince `archive/`'a taşınır, boş başlanır.
> Kayıt tipleri: **Sapma** (doküman başka diyor) · **Ekleme** (doküman sessiz) · **Düzeltme** (doküman yanlış).
> Bir sapma kalıcıysa `spec/`'e işlenir ve buradan silinir.

**Aktif aşama:** Aşama 3 — hesap ve MVP. **Plan:** `spec/14-build-guide.md`
§ XI-A.6; gerekçesi § 55. Aşama 1-2: `archive/stage-1.md`, `archive/stage-2.md`.

---

## Aşama 1-2'den taşınanlar — hâlâ açık

- **Axiom'da loglar görünüyor** (2026-08-26); telde doğrulanması dağıtımı bekliyor.
- **Anonim TTL etkinlikle kayıyor**; metin "son etkinliğinden iki saat sonra" demeli (§ 9) — frontend'in işi.
- **`UserScopedRepository`'de `findAll` yok** — § 41.2 `findByUserId` çağırıyor,
  `JpaRepository`'de yok. Alt sınıflar kendi bulucularını ekler.

## Kapanış denetimi (2026-08-28) — sekiz dilimin yedisi indi

**Tam kayıt `kapanis-denetimi.md`'de**; kalıcı olanlar `spec/`'e işlendi
(§ 47, § 57.4, § 3.2, § 51.7, atomsuz entry'nin § 20.2'si). **Canlı madde
kalmadı** — sığmayan başlık-adayı `rejectedEntries`'e giriyor (tek sebep
`BUDGET`, eski snapshot'ta boş, EK D.6.3).

## Aşama 3 · dilim 9-13 — `F-017`-`F-024` (2026-08-29/30)

Kayıtları `archive/stage-3-handoff-answers.md`'de. Canlı olanlar:
- **"Kritik uyarı" diye bir şey yok, `critical` bayrağı da yok** —
  `ExtractionWarningCode` kapalı; § 31.6'nın üçüncü kuralı **silindi**.
- **`ImportWarning.code` `String`, şeması enum** — değer JSONB'den geri
  okunuyor, enum yapmak adı değişmiş satırı düşürür. **`OpenApiSchemaIT`'in
  okuduğu altı değer de elle yazılı**: `values()`'tan türetilirse yedinciye de
  "evet" der.
- **`shared.wire` bir sonraki kapalı sözlüğün yeri**; `shared.error` retlerin.

## Aşama 3 · dilim 14 — `F-027`, `F-025`, `F-026` (2026-09-02)

Tam kayıt `archive/stage-3-slice-14.md`'de. Canlı olanlar:
- **Düzeltme — `cover_letter` v2 kapandı, iki ölçümle.** "Model 250-400'ü yok
  sayıyor" iddiası ilk beş fixture'aydı; on ikisi, 09-02 sonrası hepsi gerçek,
  255-290 diyor. v2 turu (09-09) **169** kelime verdi, eksik tamamen gövdede
  (102/181). Aktif `v1`. **Ders: tamamına karşı ölç, ölçütü önce yaz.**

## Aşama 3 kapanışından sonra · uçtan uca ölçüm (2026-09-03/07)

Dört bulgu, yedi ayrı kusur; dilim A-G ve K'nın tam kaydı
`archive/stage-3-post-closure-e2e.md`'de. **Fiyat tablosu indi** (2026-09-09;
`usage.cost` da okunuyor, slug'ın yedi endpoint'i $1-$5.50 arası).
**Geliştiricide kalan: VPS ve restore testi.**

## Kapanış sonrası · dilim I-J-H ve K — sayfanın şekli (2026-09-07)

Tam kayıt `archive/stage-3-post-closure-shape.md`'de; kalıcı kararlar § 18.4,
§ 20, § 31.3.1, § 33.4.1, § 21.2 ve § 22.4.1'de. Yalnız **canlı** olanlar:

**Tamir etmeye kalkma — hepsi bilinçli, gerekçeleri arşivde:** `SectionFloor`
bir tavandır talep değil; `reservedByFloor` `forcedByLock`'tan ayrı bir küme;
bir inline satırın öğeleri **ve** etiketi sade diziliyor (karar render'da,
içerikte değil); `PARAGRAPH` `INLINE_LIST`'e katlanmadı.

**Canlı kalan:**
- **Faz D eşikleri ölçüldü, teste sabitlendi (`PhaseDReachTest`), karar
  ertelendi (2026-09-09).** Gerçek embedding'le en yüksek atom skoru
  0.2741-0.4313; vektörsüz 0.0959, DEFAULT ağırlıklarla vektörsüz 0.2578.
  § 21.2'nin tam uyarlama eşiği **0.65'e hiçbirinde yaklaşılmadı**, 0.40 tabanı
  yalnız bir profilde aşıldı. **Sebep ayar değil aritmetik:** § 19.2'nin yetenek
  terimi ilanın *tüm* isteklerine bölünüyor (bu ilanda on yedi; en isabetli madde
  üçünü taşıyor = 0.176), etiketlenmemiş profilde etiket terimi 0.0, ve vektörsüz
  koşuda nötr 0.5 her atoma sabit 0.20 ekliyor (§ 28.4'e işlendi).
  **Normalizasyon ertelendi:** üç aday da yer değiştirmeli bir çarpıklık taşıyor
  ve veri olmadan seçilemiyor. **Yeni ölçüm gerekmiyor:** `engine_version`
  ağırlık setini, `selection_state` atom skorlarını, `trace.D.calls` Faz D'nin
  ateşleyip ateşlemediğini zaten yazıyor — `default` setli üretim biriktiğinde
  karar veriye dayanır ve test o gün yeni sayılarla düşer.

**Ders — golden fixture yazılmaz, okunur.** `master_cv_en` beş elle yazılmış
profilin taşımadığını taşıyor: **ilanla ilgisi olmayan çok fazla içerik.** İlk
koşusunda Faz C'de belirlenimsizlik yakaladı (İlke 2).

## Kapanış sonrası · dilim L — referans belgenin kendisi (2026-09-08)

**Düzeltme — dilim F'nin üç kararı geri alındı.** `\small`'ın ve negatif aralığın
kaldırılması, Termes: üçü de o günkü maliyet modeli için doğruydu, ama
**taşınması gereken modeldi** — referansa *yakın* bir CV başka bir CV. Preamble
artık referansın kendisi; şablon sürümü **v4**, yedi fixture'ın maliyetleri
yeniden ölçüldü.

**Düzeltme — ölçüm kutusu sayfanın satır kırma kuralını kullanmıyordu.**
`\parbox` girişte `\@parboxrestore` çalıştırıp `\rightskip`'i sıfırlıyor: sayfa
`\raggedright` iken kutu **yaslı** diziliyor, on sekiz kelime arasını üçte bir
daraltıyor, marjinal madde tek satır ölçülüp iki satır dizildi ve kırk tanesi
tek sayfa sözünü iki sayfaya çevirdi. Yalnız **kalın** metinde göründü.

**Ekleme — `SECTION_LIST_CLOSE` (12.0pt).** Birinci seviye madde listesinden
sonraki bölüm başlığı tam bir küçük satır daha pahalı: `\resumeItemListEnd`'in
`\vspace{-5pt}`'ini başlığın `\addvspace`'i yutuyor, `\topsep` kalıyor. Bir
maddede de üçünde de aynı, paragraf ve inline listede sıfır. **Başlığa değil
listeye yazıldı** ve açık bölümler üzerinden yeniden hesaplanıyor (seçim puana,
sayfa okuma sırasına göre diziyor). **Bilinçli fazla ücret:** son bölüm madde
listesiyse o 12pt boşa gidiyor; alternatifi her başlığa yazmak, beş bölümde 60pt.

**Ekleme — `TechStackEditor`, ilana göre madde süzme (§ 33.4, kural B).** Bir öğe
ya ilanın andığı ya da sayfanın kalanının zaten konuştuğu şeyse kalıyor;
kategori boşalırsa düşüyor. **LLM yok** — kategorinin uydurulamayacağını garanti
etmenin yolu uyduracak kodun olmaması. Üç okuma kusuru: `.tex`'ten gelen Tech
Stack her kategoriyi kendi entry'sine asıyor (serbest atomları okumak hiçbir şeyi
süzmedi); `Spring Cloud (Gateway, Eureka)` tek öğe ve düz virgül bölmesi ikiye
ayırıyordu; `RewriteContext.postingSkills` `SkillNames`'den geçmiyordu ve
burada yerel düzeltildi (süpürmenin kalanı aşağıda). `LANGUAGES` süzülmüyor
(karar (c)).

**Yedinci golden profil `stress_long_career`** — bu **yazıldı**, okunmadı: altı
işin onar maddesi, iki sayfa hatasını üreten şekil. Elle yazılmış beşi
yakalayamıyordu; hepsi sistematik bir az-ücretlendirmeyi sapma bütçesinin içinde
tutacak kadar kısa. **Canlı:** `MeasurementDriftIT.heightOnThePage`'in okuduğu
`\pagetotal` yalnız içinde bulunulan sayfayı sayıyor, yani iki sayfalık bir
belgede verdiği sapma anlamsız — bilerek bırakıldı,
`theRealDocumentNeverRunsPastThePage` sayfa sayısını ayrıca tutuyor.

## Kapanış sonrası · P3'ün yanlış pozitifleri ölçüldü (2026-09-08)

**Düzeltme — teşhis yanlıştı; sebep alias sözlüğü değil, tire.** Kelime sınırı
tireyi kelime karakteri sayıyordu: on altı kayıtlı `about_synthesis` cevabında
**on token, dört özet, bir verdict** (tam ölçüm `5ce0d94` ve `a4c5736`'nın
gövdesinde). Ayırıcılar iki tarafta katlanıyor artık; gerçek ret kalkmadı.

**Ekleme — `postingSkillNames`: kaynak evet, sözlük ve prompt hayır.** İlan
`Scrum`'ı yalnız `name`'inde adlandırıyor; yazımlarını ayrı liste taşıyor ve
yalnız "bu adı bir kaynak taşıyor mu?" okuyor, yani prompt da muhafızın sözlüğü
de aynı kaldı (§ 53.2). **Bilerek açık, ölçülmedi:** kaynağın kısalttığını
cevabın açtığı yön. **Ders:** bir redaksiyon, durduğu kayıt yenilenince bayatlar.

---

## Kapanan adımların arşiv haritası

| Adım | İnşa kaydı | Kalıcı kararlar |
|---|---|---|
| 3.3 kimlik | `stage-3-identity.md` | § 40.4.1, § 40.5.1, § 40.6.1, § 46.5 |
| 3.4 çıkarım | `stage-3-ingestion.md` | § 31.3.1, § 31.4.1, § 31.5.1, § 31.6.1-2, § 43.1, § 53.1 |
| 3.5 çok dillilik | `stage-3-multilingual.md` | § 32.2.1, § 32.3.1 |
| 3.6 anonim | `stage-3-anonymous.md` | § 35.7.1, § 41.3.1-3, § 44.1.1, § 44.2, § 31.6.3 |
| 3.8 Faz D | `stage-3-faz-d.md` | § 21.1 notu, § 21.3.1, § 21.5.1-7.1, § 34.4.1 |
| 3.9 hukuki | — | § 57.4.1, § 48.4.1 |

Hepsi `archive/` altında. Frontend aksiyonları: `B-055`-`B-058`.

## Kapanan adımlardan hâlâ canlı olanlar

**Tamir etmeye kalkma — ikisi de beklenen davranış:** `suspicious_output` telde
hiç görülmedi (enjeksiyon tripwire'ı, uslu bir modelle açılmaması doğru sonuç);
`bullet_rewrite` / `about_synthesis` fixture'ı olmayan girdide anlamsız —
`SyntheticAnswer` şema şeklinde bir cümle üretir, doğrulayıcı reddeder, orijinal
basılır.

**Test yazarken:**
- **`MagicLinkApiIT` her testten önce `ratelimit:*`'ı siliyor.** Silmeyen bir
  kimlik testi dördüncüde ilgisiz bir 429'da düşer, flake gibi okunur.
- **`profiles` entegrasyon paketinde hiç boş değil** (`DevSeeder`) — anonim
  gizlilik iddiası "satır sayısı değişmedi" diye kuruluyor.
- **`local` profilinde LLM sağlayıcısı yok** — entegrasyon lane'inde her çağrı
  `ALL_PROVIDERS_UNAVAILABLE` alır; `CoverLetterApiIT` kasten öyle kuruyor.
  **`AccountDeletionIT` tablo listesini `information_schema`'dan okuyor** — elle
  yazılmış liste sonradan eklenen tabloda sonsuza kadar geçerdi.

**Sınırlar ve açıklar:**
- **Faz D sekize kadar eşzamanlı çağrı yapıyor**, her biri `REQUIRES_NEW` ile
  bağlantı alıyor. Havuz 10, işçi eşzamanlılığı 2 → tepede 16 kısa ödünç.
  **Havuz büyütülmeden işçi eşzamanlılığı artırılmamalı.**
- **R2'deki PDF'ler** § 57.4'ün silme listesinde ama R2 istemcisi yok
  (7. karar: MVP'ye girmiyor; `pdf_key`'i hiçbir şey yazmıyor).
- **Anonim akış inşa ediliyor** (karar 2026-09-09: CV'den profil, düzenleme,
  ilana göre üretim; veri saklanmaz, kota düşük, ön yazı yok). Dilim (i) indi:
  **anonim profil artık Redis belgesi değil, sahibi olmayan + `expires_at`
  taşıyan satır.** Kalıcı sapma, altı yere işlendi — gerekçe, yedek bedeli ve
  zorlanan altı iddia **§ 51.6.1**'de. `dailyGenerationQuota` hâlâ 0
  (`B-079`); üretim dilimi inince 5'e döner. Sıradaki: (ii) profil uçlarının
  anonim oturumu kabul etmesi, (iii) üretim, (iv) challenge, (v) hesap açmada
  üretimlerin de taşınması, (vi) kota + `B-nnn`.
- **`ExtractedContact`, `Contact` ve şema aynı şekli üç yerde taşıyor**
  (§ 31.4.1). **`Contact.linkedin`** CV alanı, LinkedIn *girişiyle* ilgisiz.
- **`SkillNames` süpürmesi bitti:** tek taraflı uygulayan tek çağıran
  `RewriteValidator`'dı; saklama da kanonikleşti (`AtomService`, `B-077`).
- **`accessedAt` geri döndü (`B-078`):** çevrimdışı okuyucu (`SupportRead`,
  `support` profili) grant'i kimlik belgesi sayıp sahibinin bağlamında okuyor ve
  damgalıyor; **tek kapsanmamış adım grant aramasıdır** (`SupportGrantLookup`,
  `JobQueue` emsali, API'den erişilemez — ArchUnit kuralı var). Uç açmadık.
- **Düzeltme — `PGVectorTypeContributor`'ın `NoClassDefFoundError`'ı flake değildi:**
  koşu `main` değil **PR #86'nın dalıydı**. **Ders: flake demeden önce dalına bak.**

**Ders (3.4, dilim 1'de ikinci kez):** *bir javadoc ne zaman çalıştığını
söylüyorsa çağıranı da ara.* **(3.5):** *doğru davranan kod, korunan değildir.*
**(dilim L):** *bir ölçüm belgesi, sayfayla aynı preamble'ı paylaştığı için aynı
belge olmuyor.*

Sekiz dilimin kaydı **`kapanis-denetimi.md` § 6**'da; oradan çıkan tek kural
§ 51.7'de: *bir muhafızın düştüğünü görmeden yazıldı sayma.* Dilim 14, kapanış
sonrası A, G, J, K ve L'de yine gerekti.
