# Asama 3 ve kapanis sonrasi — kapanmis kayitlar

> `current.md`'den 2026-09-10'da indi: Asama 4 acildi ve bu kayitlarin
> tamami kapali. **Canli kalan maddeler burada degil**, `current.md`'nin
> "hala canli olanlar" bolumunde — bir kaydin arsive inmesi, ondan hala
> gecerli olan cumlelerin de inmesi demek degil.
>
> Her bolumun kendi tam kaydi zaten ayri bir dosyada; asagidakiler o
> dosyalara isaret eden yuvarlanan ozetlerdi.

---

## Kapanış denetimi (2026-08-28) — sekiz dilimin yedisi indi

**Tam kayıt `kapanis-denetimi.md`'de**; kalıcı olanlar `spec/`'e işlendi
(§ 47, § 57.4, § 3.2, § 51.7, atomsuz entry'nin § 20.2'si). **Canlı madde
kalmadı** — sığmayan başlık-adayı `rejectedEntries`'e giriyor (tek sebep
`BUDGET`, eski snapshot'ta boş, EK D.6.3).

## Aşama 3 · dilim 9-13 — `F-017`-`F-024` (2026-08-29/30)

Kayıtları `archive/stage-3-handoff-answers.md`'de. Canlı olanlar:
- **"Kritik uyarı" diye bir şey yok, `critical` bayrağı da yok** —
  `ExtractionWarningCode` kapalı; § 31.6'nın üçüncü kuralı **silindi**.
- **`ImportWarning.code` `String`, şeması enum** — değer JSONB'den geri okunuyor,
  enum yapmak adı değişmiş satırı düşürür. **`OpenApiSchemaIT`'in okuduğu altı
  değer elle yazılı**: `values()`'tan türetilirse yedinciye de "evet" der.
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

**Yedinci golden profil `stress_long_career`** — bu **yazıldı**, okunmadı: iki
sayfa hatasını üreten şekil, elle yazılmış beşi yakalayamıyordu. **Canlı:**
`MeasurementDriftIT.heightOnThePage`'in `\pagetotal`'ı yalnız bulunulan sayfayı
sayıyor, iki sayfalık belgede sapması anlamsız — bilerek bırakıldı,
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

## Kapanış sonrası · `F-028`-`F-030` (2026-09-09)

**Düzeltme — springdoc çok parçalı bir uçta `@RequestParam`'ı *query
parametresi* diye yayımlıyor.** Gövdeyi yalnız `@RequestPart`'lardan kuruyor,
yanındaki her `@RequestParam` `parameters`'a düşüyor. `POST /profile/import`'un
`challengeToken`'ı böyle URL'e taşınmıştı — § 35.7.4 "form alanı" diyor, ve bir
challenge token'ının erişim/vekil kayıtlarına ve tarayıcı geçmişine yazılması
var olma sebebinin çoğunu siliyor. Gövde şeması elle yazıldı (`@RequestBody`
+ `schemaProperties`); bağlama `@RequestParam`'da kaldı, ikisini de okuyor.
**Sonraki çok parçalı uçta aynısı olur:** dosya olmayan her parça elle
bildirilmezse query parametresi olur. `mode` bilerek query'de bırakıldı.

**Ders (ikinci kez, `F-027`'den sonra) — dev stub ölçümü yiyor.**
`SessionCurrentUser.resolve` çerez *yoksa* `LocalDevSessions`'a düşüyor ve dev
kullanıcısı gibi cevap veriyor. Yani **çerezsiz istekle yazılmış hiçbir test
kimlik davranışını ölçmüyor** — 401 bekleyen testim stub yüzünden 404 aldı.
Doğru kurgu **çözülmeyen bir çerez**: çerez dalına giriyor, boşa filtreleniyor,
ve gerçekten olan bir tarayıcı durumu (iptal edilmiş/süresi geçmiş oturum).

**Ekleme — hata kataloğu tablosunun `params` sütunu düzyazı kabul etmiyor.**
`ErrorCatalogueSpecTest` onu birebir ayrıştırıyor (virgülle bölüp `ad: tip`
okuyor), yani bir hücreye eklenen açıklama testi düşürüyor. `F-030`'un iki
notu bu yüzden § D.6.1'in düzyazısına girdi, tabloya değil.

**`feature` sözlüğü ve blok eşleşmesi `spec/08b` § D.6.1'e işlendi**, burada
tekrarlanmıyor. Frontend aksiyonları: `B-085`-`B-087`.

---

---
