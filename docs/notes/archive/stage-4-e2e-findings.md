# Aşama 4 · uçtan uca ölçüm — dilim kayıtları

> Aşama 3 kapandıktan sonra yapılan ilk gerçek uçtan uca denemede dört bulgu
> çıktı ve dördü de dört ayrı fazda yaşıyordu. Teşhis dört ayrı soruşturmayla,
> tek harita halinde yapıldı; **bulgular kusurlarla bire bir eşleşmedi.**
> `current.md` yalnız canlı olanı ve dilim listesini taşıyor, ayrıntı burada.

## Aşama 4 · uçtan uca ölçüm — dilim A: Faz C (2026-09-03)

Dört bulguluk uçtan uca ölçümün ilki. **Bulgular kusurlarla bire bir eşleşmedi**:
"eksik Tech Stack" render kusuru sanılıyordu, Faz C çıktı.

**Düzeltme — iade edilen bütçe bir daha teklif edilmiyordu.** Düşürülen entry
yerini iade ediyor ama greedy pass bitmişti; `improveBySwapping` de `needed <= 0`
ile **sığan** her adayı atlıyor. Ölçümde 352 pt'nin **133'ü boş**, on atom `BUDGET`
ile reddedilmişti — etiket yazıldığında doğru, koşu bitince yanlış.
`fillUntilStable()` sabit noktaya döndürüyor, ve **`BUDGET` artık gerçekten
doğru**: yeni ret kodu gerekmedi, telde değişiklik yok.

**Düzeltme — boşalan section başlığı iade edilmiyordu.** `remove()` yalnız
`entryFurniturePt`'yi veriyordu; ölçülen koşu **beş** başlık ödeyip **dört** bastı.
`closeSectionIfEmpty` iade ediyor, `downgradeFirstEntryOf` de liste kapanınca
`upgradeFirstEntryOf`'un taşıdığını geri koyuyor.

**Düzeltme — `min_atoms`'u import körlemesine 2 yazıyordu.** `ProfileWriter` hiç
`setMinAtoms` çağırmıyordu; extraction'ın tek madde verdiği her entry (bir dil, bir
derece, bir Tech Stack kategorisi) ulaşamayacağı bir taban taşıyor ve Faz C onu
**bütün** düşürüyordu. Gerçek profilde **11 entry** — kaybolan üç bölümün sebebi
bu; `V5` onarıyor.

**Kıskaç `SelectionPhase`'e konmadı.** Oraya konunca `GoldenSelectionTest` düştü:
fikstür *bilerek* atom sayısından büyük `min_atoms` taşıyor, çünkü "kısa
basmaktansa bütün düşür" **kullanıcının** kararı (`EntryPatchRequest`). Kusur
ulaşılamaz minimum değil, **import'un o kararı kullanıcı adına vermesi**.
Migration'ın körlemesine uygulanabilmesi de buradan: `minAtoms` frontend'de
yalnız mock ve üretilmiş tipte geçiyor, hiçbir bileşende değil.

**Ekleme — `trace.C` bütçesini taşıyor** (§ 14.6 istemiyor). Kısaltılmışı
`"rejected": 13` yazıp ne kadar yerden çevrildiğini yazmıyordu; seçim kusurunu
bütçeden ayırmak `selection_state`'i elle okumak demekti. `rejectionReasons` ve
`pinnedCostPt` zaten § 14.6'da isteniyordu.

**Ders — bir muhafız düşürdüğünde önce neyi koruduğuna bak:**
`GoldenSelectionTest` kıskacın yanlış değil, **yanlış katmanda** olduğunu diyordu.

## Dilim B — çıkarım tripwire'ı (Bulgu 1) · 2026-09-04

**Belirti:** 4 sayfalık CV yüklendi, profil oluşmadı, kullanıcıya
eyleme dönüştürülebilir bir hata gösterilmedi — **ama OpenRouter'da çağrı
faturalandı.**

**Düzeltme — `MAX_ATOM_TEXT` tür başına ayrıldı.** Çağrı başarılıydı
(`outcome=success`, 10617 çıkış jetonu, kesilme yok) ve koşu `local-record`
olduğu için cevap diskteydi: `v1-d506ef8f97bb.json`, **84 atom, 6 section,
`skills` dahil**. `StructuringAudit` onu **yedi karakterle** reddetti — en uzun
`textSource` 607, sınır 600 — ve `ProfileStructuring.gate` denetimi *ilk*
çağırdığı için 84 atomun 84'ü gitti.

Kusur sınırın kendisinde değil, **gerekçesinin taşınmasındaydı**: javadoc 600'ü
"bir insanın **tek bir maddeye** yazacağının ötesi" diye savunuyor, ama aynı
sayı `about_paragraph`'a da uygulanıyordu. Kayıtlı üç extraction fikstürünün
hepsi ölçüldü: **about dışındaki her tür en fazla 219 karakter** (600 onlar için
üç kat paylı ve doğru), about ise 607/541/519/506 — yani sistematik bir
kıl payı kaçırma, tesadüf değil. About tavanı 1500 (~250 kelime, ~20 satır);
tripwire'ın kendi ölçütü — "hiçbir CV alanının olamayacağı bir değer" —
paragraf şeklindeki bir alana uygulanmış hali.

**Ölçüm testi olarak yazıldı:** `RecordedExtractionAuditTest` reddedilen
**gerçek fikstürü** diske karşı okuyor, 84 atomu doğruluyor ve denetimden
geçmesini bekliyor. Düzeltme olmadan düşüyor.

**§ 43.2 zayıflatılmadı, ve bilerek.** `EXTRACTION_EMPTY`'nin belirsizliği
saldırgana "fark edildin" dememek için; meşru vakanın artık hiç tetiklenmemesi
doğru çözüm, mesajı yumuşatmak değil.

**SSE boşluğu yok — `B-nnn` açılmadı.** İki taraf da doğrulandı: `SseRegistry`
`"failed"` yayınlıyor (`terminalEvent`, geç abone olana da), `useJob.ts`
dinliyor, `ImportScreen` onu kullanıyor. Olay gitti; yanlış olan taşıdığı
cümleydi, ve o cümlenin sebebi yukarıdaki yanlış pozitifti.

## Dilim C — P3'ün muhafızları (Bulgu 4) · 2026-09-04

**Belirti:** üretilen CV'de iki uydurma — About'ta "message queues (Redis,
**Kafka**)" ve bir maddede "advanced **SQL Server** queries", oysa kaynak atom
yalnız "SQL" diyor.

**Teşhis — ikisi de Faz D'de doğmadı.** Atomlar 14:08:29'da yazılmış
(`created_by=user`, `derived_from_variant_id=NULL`), üretim 14:10:41'de koşmuş,
ve basılan metin atomlarla **bayt bayt aynı**. Üstelik `bullet_rewrite`
invocation sayısı o koşuda **sıfır**: `embedding` işi `EMBEDDING_UNAVAILABLE`
ile düşünce `trace.B.weights = "without-embedding"`, skorlar 0.0-0.061,
`RewritePlanner` eşiği 0.40 → **`RewriteValidator.validate()` bir kez bile
çağrılmadı.** `atoms.skills` de zaten `mssql` taşıyor ve `aliases.txt:42`
`sql-server = mssql` diyor: **normalizasyon doğru**, "SQL → SQL Server" diye
bir eşleme kayması yok.

**Düzeltme — sözlük cevap tarafında açıldı.** İki muhafız da
`ClaimVocabulary.of()`'u gezip cevapta arıyordu, yani **tanımadığı bir uydurma
görünmüyordu**. `aliases.txt` 74 satır ve `kafka` içinde yok; posting'de de
yok. Yani Redis kontrol edilip serbest bırakıldı, Kafka **hiç sorulmadı**.
`introducedNames()` soruyu kapalı olan yönden soruyor: "cevap tanıdığım bir şey
mi anıyor" değil, **"kaynaklarının hiçbirinde olmayan bir ad mı veriyor"**.
Kaynak = orijinal metin + atom skills + kişinin kendi sözleri + ilan.

İsim şekli: içeride büyük harf (`PyTorch`), karakterler arası `.`/`+`/`#`
(`Node.js`, `C++`), harfe bitişik rakam (`S3`), ya da cümle başı **olmayan** bir
büyük harf. Yanlış pozitifin bedeli reddedilen bir yeniden yazım ve kişinin
kendi cümlesinin kalması — P3'ün istediği yön.

**İlk denemede dört test düştü ve ikisi gerçek hataydı** (bendeydi): token
regex'i cümle sonundaki noktayı yutuyordu, yani `"Postgres."` kaynaklarda
aranıyor ve bulunamıyordu; ve `300.000` gibi salt sayısal token'lar "karakterler
arası nokta" kuralına takılıyordu — oysa o bir binlik ayracı ve sayıların kendi
kuralı var. İkisi de düzeltildi.

**Düzeltme — About birleşimi About atomlarını dışlıyordu.** § 21.7 "girdi
**seçilmiş atomların** skills + metrics birleşimi" diyor; kod `isAbout` dalında
`continue` edip onları hiç eklemiyordu. Dört About paragrafı olan gerçek
profilde üçü birleşimin dışında kalıyordu.

**Regresyon testi § 51.7'ye göre:** `FabricatedTechnologyTest` teknolojinin
sözlükte **olmadığını önce kendisi doğruluyor** — yoksa eski testler gibi
Kubernetes'i ölçer. İki "uydurma" testi düzeltmesiz düşüyor, iki "serbest"
testi her iki halde de geçiyor.

**Hâlâ açık, ve Faz D'nin işi değil:**
- **Çıkarım çıktısını kaynak belgeye karşı doğrulayan hiçbir şey yok.** P3
  yalnız *yeniden yazma* anında uygulanıyor; `is_primary` varyant olarak yazılan
  LLM nesri hiçbir kapıdan geçmeden sayfaya basılıyor. Ham yükleme hiçbir yerde
  saklanmıyor, yani cümlenin CV'den mi geldiği modelden mi doğrulanamıyor.
  **Geliştiriciye soruldu.**
- **`trace.D` yazılmıyor** (`rewriteAccepted`/`rejected`/`rejectReasons` →
  `src/main/java`'da sıfır eşleşme) ve `promptVersions` koşmayan
  `bullet_rewrite`'ı koşmuş gösteriyor. `RewrittenContent` yalnız kabul edilenleri
  taşıyor; sayaçları yukarı geçirmek ayrı bir dilim.
- **`EMBEDDING_UNAVAILABLE` Faz D'yi sessizce kapatıyor.** Eşikler embedding'li
  skorlara göre ayarlı; onlarsız hiçbir aday geçmiyor ve bunu söyleyen bir şey yok.

## Dilim D — çıkarım uydurma yapıyor (Bulgu 4'ün asıl kökü) · 2026-09-04

**Geliştirici cevapladı ve öncül düştü: `Kafka` CV'de geçmiyor.** Örnek olarak
konan `cv.tex` de doğruluyor — `Kafka` sıfır eşleşme, `SQL Server` sıfır
eşleşme, yalnız çıplak `SQL` altı kez. Satır 166:

> `Integrated structured enterprise data utilizing 	extbf{SQL} queries to model complex business reporting logic.`

Veritabanındaki atom:

> `Integrated structured enterprise data utilizing advanced **SQL Server** queries, optimizing analytic data layers.`

Yani model `SQL` → `SQL Server` yapmış (başka ve daha özgül bir ürün, arkasında
`skills`'e giren `mssql`), "advanced" eklemiş, cümlenin sonunu değiştirmiş.
**Bu çıkarım değil, yeniden yazım.**

**Kök neden: P3 yalnız Faz D'de uygulanıyordu.** Modelin *yeniden yazdığı*
metin altı kontrolden geçiyor; *çıkardığı* metin hiçbirinden geçmiyor ve
`is_primary` varyant olarak doğrudan sayfaya gidiyor. Bütün çıkarım yolu
garantinin dışındaydı.

**Düzeltme — `ExtractionFidelity`.** Faz D'nin `introducedNames()` sorusu, tek
kaynak olarak belgenin kendisiyle: bu madde, dosyanın taşımadığı bir ad veriyor
mu? Sözlük yok, yani duyulmamış bir teknoloji ünlü olanla aynı şartlarda
yakalanıyor. Normalizasyonda, `normalizeEntry` içinde — uyarı orada doğuyor ve
`path`'ini sıralamadan sonra otomatik alıyor (F-018'in kuralı).

**Ret değil uyarı, ve bilerek.** Atom onun dışında kişinin kendi içeriği,
§ 31.6'nın gözden geçirme ekranı tam bunun için var, ve tek uydurma kelime için
içe aktarımı çöpe atmak **bu kod tabanının zaten bir kez yaptığı hata** (dilim B).

**`normalize(extracted)` aşırı yüklemesi kaldırıldı.** Kontrolü atlayan ikinci
bir yol bırakmak, gelecekteki bir çağıranın garantiyi sessizce kaybetmesi
demekti; tek imza, belge parametresi nullable ve ne anlama geldiği yazılı.

**Yedinci `ExtractionWarningCode`, ve şema değişmedi.** `UNSUPPORTED_BY_SOURCE`
kodu **model üretmiyor**, pipeline üretiyor — o yüzden extraction şeması hâlâ
altı değer listeliyor, prompt sürümü değişmedi, fikstürler ve cache geçerli.
Enum artık `raisedByModel()` ile bu ayrımı taşıyor ve iki test onu koruyor.

**`OpenApiSchemaIT` tam tasarlandığı gibi çalıştı.** Elle yazılmış altı değer
düştü, javadoc'u "bu liste düşerse handoff maddesini yaz" diyordu, ve `B-071`
o yüzden yazıldı. Notlarda "yedincisi karşı reponun duyması gereken bir tel
değişikliği" diye duran öngörü karşılandı.

**Ölçemediğimiz şey — ve tahmin etmedik.** Yanlış pozitif oranı **bilinmiyor**.
Üç kayıtlı fixture'ı `cv.tex`'e karşı koşturmak 16/84, 6/28 ve 5/30 bayrak
verdi, ama **fikstürlerin hiçbirinin kaynağı `cv.tex` değil** (`Cloudflare`,
`TensorFlow`, `SoapUI` gerçek teknolojiler ve asıl belgelerde varlar). Yani o
sayılar belge uyuşmazlığı artefaktı, ölçüm değil. Modelin kendi etiketlerine
(`emphasisSource`/`skills`/`properNouns`) daraltmak da denendi: 18→16, kayda
değer bir fayda yok, çünkü model teknolojileri zaten vurguluyor.

**Ve bunun asıl sebebi kayda değer: `local-record` cevabı saklıyor, girdiyi
saklamıyor.** Bir çıkarımın sadakati ancak kaynak belgeyle ölçülebilir, ve o
belge hiçbir yerde yok. **P3'ün çıkarım tarafını denetlemenin önündeki tek
engel bu.** Bir sonraki `make record`'da kaynak metnin de yazılması gerekiyor.

## Dilim E — Faz D'nin sessizliği · 2026-09-04

**Geliştirici `make dev-full` koşmuş**, yani embedding container'ı ayaktaydı;
`embedding` işi yine de **üç denemenin üçünde de** `EMBEDDING_UNAVAILABLE` ile
düştü (08-28'deki üç koşu tamamlanmıştı). Yani geçici bir kesinti, yerel bir
eksiklik değil — ve üretimde de olabilir.

**Sonucu Faz D'nin sessizce kapanması.** `trace.B.weights =
"without-embedding"` → skorlar 0.0-0.061 → `RewritePlanner`'ın eşikleri
(`FLOOR_SCORE` 0.40, `FULL_ADAPTATION_SCORE` 0.65, § 21.2'den *verbatim*) hiçbir
adayı geçirmiyor → `bullet_rewrite` sıfır çağrı. Muhafızlar koşmadı, ve bunu
söyleyen hiçbir kayıt yoktu.

**Eşiklere dokunulmadı, ve bilerek.** § 21.2 onları harfiyen veriyor;
embedding'siz skor dağılımına göre yeniden ayarlamak **ölçüm ister** ve elimde
o ölçüm yok. Yanlış ayarlanmış bir eşik, sessizce hiç yazmamaktan daha kötü:
alakasız atomları yeniden yazdırır.

**Yapılan: sessizliği görünür kılmak.** `trace.D` artık yazılıyor
(`{"rewritten": n}`), ve **sıfır gerçek bir cevap**: Faz D koştu ve hiçbir şey
değiştirmedi. Genel modda blok zaten yok — orada yazacak bir ilan yok. Testin
javadoc'u bu ayrımı taşıyor, çünkü "ölçülmemiş" ile "ölçüldü ve sıfır" aynı
şey değil.

**§ 14.6'nın `rejectReasons`'ı hâlâ yok** ve sebebi yapısal:
`RewrittenContent` yalnız **kabul edilenleri** taşıyor (§ 21.5 gereği — Faz E
için "yok" ile "reddedildi" aynı şey). Reddedilenlerin sayımını yukarı geçirmek
`ContentRewriter` cephesini değiştirmek demek; ayrı bir dilim, ve bu blok onun
yerini tutmuyor.

**`promptVersions` hâlâ `bullet_rewrite: v1` diyebiliyor koşmamışken** —
`rewritten.isEmpty()` üzerinden karar veriyor ve About sentezi tuttuğunda liste
dolu oluyor. Aynı cepheyi istiyor, aynı dilime bırakıldı.

## Dilim F — gerçek Klasik şablonu (Bulgu 3) · 2026-09-04

`resumeItem`, `resumeSubheading` ve `resumeProjectHeading` bu repoda **hiç
yoktu**; "Klasik" üç uydurma komuttu (`tomcvName`/`tomcvContact`/
`tomcvEntry`), `templates.yaml` spec'te ve CI yol filtresinde adı geçiyordu
ama dosya yoktu, iletişim bloğu çıplak değerleri `·` ile birleştiriyordu, ve
`RenderableSection`'ın **`layout` alanı yoktu** — yani `INLINE_LIST` enum'da,
şemada ve CHECK kısıtında vardı, sayfaya çıkması imkânsızdı.

**Üç mühendislik kararı, üçü de bilinçli:**
- Referans pdfLaTeX ve kendi sayfa kurulumunu varsayıyor; biz XeLaTeX'iz ve
  kenar boşluğu kullanıcı ayarı. **Komutlar ve bölüm biçimi alındı**,
  `glyphtounicode` ve `ddtolength` geometrisi alınmadı.
- **`TWO_COLUMN` bilerek uygulanmadı.** § 33.5 Klasik'i ATS için tek sütun
  tutuyor; renderer'ın bunu sessizce ezmesi render kararı değil.
- **Referansın negatif `space`'leri, sahibi olduğu öğenin dışına sızdığı her
  yerde çıkarıldı.** Listeden sonraki bir `space`, sonraki section başlığını
  ilkinden ucuza getiriyor; öğe başına tek sayı taşıyan bir maliyet modeli bunu
  ifade edemez. `	itlespacing` ve enumitem aynı işi konum bağımsız yapıyor.

**`\small` maddeden kalktı.** Bölüm 26 belge için **tek** bir baselineskip
ölçüyor; tek bir öğeyi küçültmek her tahmini sayfanın tuttuğunun %24-43 üstüne
çıkardı. Yoğunluk katman B'nin işi (`fontSizePt`), şablonun değil.

**Asıl bulgu, ve kalıcı olan bu: `ITEM_LINE` bir ölçüm artefaktıydı.**
Kalibrasyon üç maddelik listeyi **bir maddelik listeden sonra** ölçüyordu, oysa
tek maddelik olan **entry başlığından sonra** ölçülüyor — yani çıkarma iki ayrı
overhead'in farkını alıp "bir madde" diyordu. 13.0 (eski şablon) ve 11.585 (yeni)
böyle çıktı. İkisi de yanlış: **marjinal bir madde tam bir baselineskip, 12.0** —
TeX'in garantisi, ve `RenderCost.totalPt` zaten onun üstüne yazılmış. Aradaki
0.415 her atomun **saklanmış maliyetine** madde başına bir düzeltme olarak
giriyordu, oysa listeye ait; hata madde sayısıyla büyüyordu ve toplam hatanın
işareti profilin maddelerini entry listeleriyle section listeleri arasında nasıl
dağıttığına bağlıydı. Kalibrasyon artık benzeri benzerle ölçüyor ve
`itemSpacingPt` sıfır.

**Ekleme — `SECTION_LIST_OVERHEAD`.** Section başlığının **hemen altında**
açılan liste ile entry başlığının altında açılan liste aynı maliyette değil:
TeX boşluğu `ddvspace` ile ekliyor, yani istenenle mevcut olanın **büyüğünü**
alıyor, toplamını değil. Başlık kendi boşluğunu yeni bırakmış olduğu için
listenin ekleyeceği neredeyse yok (−3.17, bir düzeltme terimi, fiziksel
yükseklik değil); entry başlığı ise paragrafı bitirmiş, paragraf boşluğu hâlâ
ödenecek (6.83). `ENTRY_HEADER` / `ENTRY_HEADER_AFTER_LIST` çiftinin birebir
aynısı, ve aynı sebeple.

**Döngü — maliyet dosyası artık hangi şablonu ölçtüğünü kendisi söylüyor.**
Anahtarı `TemplateCustomization`'dan okumak `profile.seed`'i `rendering`'e
bağlıyordu, oysa `rendering` zaten `profile`'a bağlı. Literal yazmak da seçenek
değildi: `classic:v1` sürüm atlayınca **sessizce** ıskaladı, seçim tahminciye ve
%8 payına düştü, ve drift testi maliyet modelinde %30 hata bildirdi — oysa
fikstür yanlış rafa bakıyordu. Dosya kendi anahtarını taşıyınca bayat bir kayıt
**gürültüyle** düşüyor, ki bu tam da buraya getiren hataydı.

**Ders — bir kalibrasyon, ölçtüğü şeyin bağlamını da ölçer.** Üç sayıdan ikisi
(`ITEM_LINE`, `ITEMIZE_OVERHEAD`) yıllardır yanlıştı ve kimse fark etmedi,
çünkü eski şablonda birbirlerini götürüyorlardı. Şablon değişince ortaya çıktı.

