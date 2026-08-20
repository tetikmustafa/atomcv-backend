# İnşa Notları Arşivi — Aşama 1 (D.8-D.8.10)

> Kapanmış aşama. **Rutin okunmaz.** Aşama 1 kapanışında `current.md`'den taşındı.

---

### D.8 — Adım 1.2: profil başı ve acting user

**Karar — Aşama 1'in acting user'ı.** Kimlik Aşama 3'te (XI-A.6), ama Adım
1.2'nin endpoint'leri bir `UserContext` istiyor ve `ProfileRef` onsuz
üretilemiyor. Üç seçenek vardı:

| Seçenek | Neden seçilmedi |
|---|---|
| İstek başlığından kullanıcı seçmek | Üretime sızdığı anda kimlik doğrulamayı komple atlayan bir arka kapı; test kolaylığı bu riski taşımıyor |
| Kimlik gelene kadar endpoint yazmamak | Aşama 1'in geri kalanı (ölçüm, seçim, render) profil verisine bağlı; tıkanırdı |
| **Yalnız `local` profilinde sabit bir kullanıcı** | ✅ Seçildi |

`CurrentUser` arayüzü + `LocalDevCurrentUser` (`@Profile("local")`). **Yedek bean
yok:** üretimde bir endpoint kullanıcı istediği anda uygulama açılışta gürültüyle
düşer — herkese aynı kullanıcının verisini sessizce servis etmektense. Bu
davranış kasıtlı ve gerçek implementasyon geldiğinde sınıf tek parça silinir.
`@Profile("local")` anotasyonunun varlığı testle sabitlendi.

`users` satırını JDBC ile ekliyor (`ON CONFLICT DO NOTHING`), çünkü identity
modülünün henüz entity'si yok; Flyway ile yarışmasın diye `ApplicationRunner`
olarak çalışıyor. Sabit kimlik `00000000-…-0001` — yeniden başlatmada yerel veri
ve seed'ler anlamını korusun diye.

**Profil başı.** `contact` ve `preferences` **map değil, tipli record**
(Bölüm 14.2, 14.3): her alan CV başlığına render ediliyor ve map, "hangi
anahtarlar var" sorusunu hem renderer'a hem frontend'e taşırdı. `Tone` artık
JSON'da da küçük harf — `preferences` içinde Jackson serileştiriyor, JPA
converter'ı değil.

`Contact.toString()` ve `WritingStyle.toString()` içerik basmıyor: ilki tamamen
kişisel veri, ikincisi kullanıcının yazdığı serbest metni taşıyor.

**İlk endpoint ve yayınlanan şema.** `GET /api/v1/profile`, springdoc ile
birlikte geldi.

| Konu | Tür | Karar |
|---|---|---|
| Şema üretimi | Ekleme | springdoc-openapi; `/v3/api-docs`. **Üretimde kapalı** (`springdoc.api-docs.enabled: false`): şema, frontend'in tip üretimi için bir derleme zamanı çıktısıdır (XI-B.9.1), üretimin servis etmesi gereken bir şey değil — servis etmek her endpoint'i ve gövde şeklini isteyene açar. |
| Hata gövdesinin şemaya girmesi | Ekleme | Yanıtları `ProblemDetailAdvice` üretiyor, ama bir advice şemaya görünmez. Bu yüzden `ApiErrorResponse` adında **yalnız dokümantasyon için** bir record var; iki kapalı sözlük şemaya onun üzerinden giriyor. Advice'in ürettiği gerçek gövdeyle alan alan karşılaştıran bir test, ikisinin sessizce ayrışmasını engelliyor. |
| Profil yanıtında `id` yok | Ekleme | Hiçbir endpoint profil id'si kabul etmiyor; sahiplik oturumdan geliyor (Bölüm 35.1). Şemada bir `id` alanı, geri gönderilebilecek bir şey varmış izlenimi verirdi. |
| Profil yanıtında `version` yok | Ekleme | Tekil kaynakta sürüm **ETag'de**. Gövdede de olsaydı ikisi çelişebilirdi. Koleksiyonlarda öğe başına `version` alanı kalıyor (`spec/08-api.md` § 35.6). |

**Karar — profil ilk kullanımda oluşur.** `ProfileResolver.resolve` profili
bulamazsa yaratır. `profiles.user_id` tekil, yani bir kullanıcının tam olarak
bir profili var ve yokluğu bir hata değil, hesabın yeni olması demek. 404
dönmek, her istemciyi "henüz profilin yok" durumunu aynı boş satırı yaratmaya
giden yolda bir hata hâli olarak ele almaya zorlardı.

### D.8.1 — Adım 1.3: LaTeX container

| Konu | Tür | Karar |
|---|---|---|
| `ulimit`'lerin yeri | Düzeltme | Bölüm 29.4 onları container entrypoint'ine koyuyor. Orada **JVM'e de** uygulanıyorlar: `ulimit -v 512m` ile sunucu heap'ini ayıramadan ölüyor, ve `ulimit -t 20` ilk yirmi saniyeden sonra sunucunun kendisini öldürürdü. Limitler her derlemeye ait; `run-xelatex.sh` onları kurup `exec xelatex` yapıyor. |
| Bölüm 22.4'ün `\newsavebox{\mbox}`'u | Düzeltme | `\mbox` LaTeX'te zaten tanımlı: doküman `! LaTeX Error: Command \mbox already defined.` ile durur. Ölçüm dokümanı başka bir ad kullanmalı (`\measurebox`). Adım 1.5 bunu ilk denemede yaşayacaktı. |
| HTTP sarmalayıcı | Ekleme | Tek dosyalık, bağımlılıksız bir Java sunucusu (JDK'nın kendi `HttpServer`'ı). İmaj, yamalanması gerekebilecek hiçbir kütüphane taşımıyor ve sarmalayıcının tamamı bir oturumda okunuyor. |
| İki uç | Ekleme | `/compile` → PDF; `/measure` → **TeX log'u**. Ölçüm (Bölüm 26) PDF'i değil, `\typeout{ATOMCOST\|…}` satırlarını istiyor; ikisini ayırmak, ölçümün PDF üretmeye zorlanmamasını sağlıyor. Başarısız derleme **422** döner (belge hatalı, servis değil) ve gövdesi log'dur. |
| `javac -encoding UTF-8` | Ekleme | `build.gradle.kts` ile aynı sebep: container'ın platform charset'i US-ASCII, ve bir yorumdaki tire derlemeyi düşürüyor. |
| Healthcheck kabuğu | Düzeltme | `/dev/tcp` bir **bash** özelliği; `sh` (dash) "Directory nonexistent" diyor ve container bozuk gibi görünüyor. `curl`/`wget` bilerek kurulmuyor — buradan ağa uzanabilen ne kadar az araç varsa o kadar iyi. |
| Geliştirmede ağ izolasyonu | Sapma | Üretimde container `internal: true` ağında, dışarı yolu ve yayınlanmış portu yok. **Geliştirmede olamıyor:** `make dev` backend'i host'ta çalıştırıyor ve Docker, yalnız internal ağa bağlı bir container için port yayınlamıyor — host'un içeri girecek yolu kalmıyor. Yerel içerik geliştiricinin kendi içeriği; fark `docker-compose.yml`'de yazılı, keşfedilmiyor. |
| Test maliyeti | Ekleme | `LatexContainerIT` `@Tag("latex")` taşıyor ve `integrationTest`'ten **dışlanıyor**: imaj birkaç GB ve dakikalar sürüyor. `gradlew latexTest` ile, `docker/latex` değiştiğinde çalıştırılır. |
| Ne doğrulanıyor | Ekleme | xelatex'in çalışması değil, **etrafındaki çitin durması**: `\write18` denemesi log'da `runsystem(touch /tmp/pwned)...disabled` ile reddediliyor ve dosya oluşmuyor; root dosya sistemi salt-okunur; süreç uid 1000. Bunlar bayrağın kurulu olduğuna inanmakla değil, çalışan container'a sorularak doğrulanıyor. |
| Henüz yapılmayanlar | Açık | (a) Bölüm 29.2'nin **preamble format dump**'ı: gerçek preamble Adım 1.4'te doğuyor, ondan önce uydurmak olurdu. (b) CI'da **imaj taraması**: Trivy'nin misconfig taraması Dockerfile'ı artık buluyor, ama imajın kendisini taramak her koşuda birkaç GB'lık bir derleme demek — kayıt defterine push eklendiğinde oraya bağlanacak. |

**`make dev-full` imajı yeniden inşa eder (`--build`).** Aksi hâlde Compose en
son inşa ettiği imajı kullanıyor; bayat bir imaj `X-Page-Count` başlığı
göndermiyor ve istemci bunu haklı olarak `UNAVAILABLE` sayıyor — container
ayakta ve sağlıklıyken başarısız olan bir üretim. Yerel kurulumda bir kez
yaşandı; hedef artık her seferinde inşa ediyor.

### D.8.2 — Adım 1.4: klasik şablon ve renderer

| Konu | Tür | Karar |
|---|---|---|
| Ortak preamble | Ekleme | `renderFinal` ve `renderMeasurement` **aynı metodu** çağırıyor (`PreambleBuilder.build`). Adım 1.4'ün kritik testi ikisinin preamble'ını karşılaştırıyor; farklı bir geometriyle alınan ölçüm, kimsenin basmayacağı bir belgeyi ölçer ve sayfa garantisi tam buna dayanıyor. |
| `FontRegistry` sınıfı yok | Sapma | Bölüm 22.5 `FontRegistry.resolve(enum)` çağırıyor. **Enum'un kendisi whitelist**: `FontFamily` LaTeX adını taşıyor, yani araya bir eşleme tablosu koymadan da hiçbir kullanıcı dizesi `\setmainfont`'a ulaşamıyor. |
| Fontlar imajda var olmalı | Ekleme | Kurulu olmayan bir font derleme sırasında **sessizce** başkasına düşer ve ölçülmüş bütün maliyetler yanlış olur — hatasız. `FontFamily`'nin üç değeri de container imajında (TeX Gyre). |
| `HexColor` büyük harfe çevriliyor | Ekleme | `Locale.ROOT` ile. Yalnızca harf büyüklüğüyle ayrışan iki özelleştirme aksi hâlde iki ayrı ölçüm işine yol açardı. |
| `String.format` ve locale | Ekleme | `Locale.ROOT`. Türkçe locale altında `%.2f` "0,60" yazıyor ve belge derlenmiyor — mutlak kural 7'nin sayı tarafı. Test locale'i değiştirip doğruluyor. |
| URL kaçışı | Ekleme | `\href` argümanında ters bölü ve süslü parantez argümanı erkenden kapatıyor; bunlar **kaçırılmıyor, atılıyor**. Bozuk bir link, derlenmeyen bir belgeden iyidir. |
| Kaçış önce, işaretleme sonra | Ekleme | Kullanıcı metni önce escape ediliyor, sonra mark komutuna sarılıyor: aksi hâlde metnin içindeki `\textbf{...}` gerçek bir komut olurdu. Ayrı bir test bunu sabitliyor. |
| Klasik şablon | Ekleme | Tek kolon, grafiksiz (Bölüm 33.5 "ATS-güvenli"). ATS metin çıkarır; insana hoş görünüp çıkarımda dağılan bir düzen, insana hiç ulaşmayan bir CV demektir. |
| Ölçüm anahtarı karakter kümesi | Ekleme | `MeasurableItem.key` içinde `|`, `%`, `{`, `}`, boşluk ve TeX'in özel karakterleri yasak: anahtar log satırından `|` ile bölünerek geri okunuyor. Anahtarlar kod tarafından id'lerden üretiliyor, yani bu bir saldırıyı değil bir hatayı yakalıyor. |
| Doğrulama | Ekleme | Renderer'ın çıktısı **gerçekten derleniyor**: `latexTest` içindeki iki test, üretilen CV'yi container'a gönderip PDF alıyor ve ölçüm belgesinden `ATOMCOST|var-1|<pt>|<pt>` satırlarını okuyor. Birim testler bunu gösteremez. |

### D.8.3 — Adım 1.5: ölçüm sistemi (ilk yarı)

| Konu | Tür | Karar |
|---|---|---|
| Sabit maliyetler **ölçüldü** | Ekleme | Bölüm 26.4'ün sayıları örnek; klasik şablonun kendi değerleri derleyiciden alındı. Varsayılan özelleştirmede: `pageTextHeight` **708.245pt**, `baselineSkip` **12.0pt**, bölüm başlığı **24.0pt**, entry başlığı **22.76pt**, madde listesi ek yükü **7.0pt**, madde satırı **13.0pt**. |
| Nasıl ölçüldü | Ekleme | Renderer bir **kalibrasyon belgesi** üretiyor: sonda `\the\pagetotal` yazan probe'lar; iki konum arasındaki fark, o mobilyanın maliyeti. Aynı preamble, aynı sebep. |
| Kalibrasyon bir test | Ekleme | `LatexCalibrationIT` her çalıştığında sayıları yeniden türetip saklananlarla karşılaştırıyor (0.01pt tolerans). Preamble değiştiğinde bu test düşer — şablon sürümünü yükseltme anı budur (Bölüm 16.3), saklanmış maliyetlerin sessizce yalan söylemeye başladığı an değil. |
| İlk çalıştırmada bir yanlış sabit yakalandı | Düzeltme | Entry başlığını elle ölçtüğüm belgede satır sonu (`\\`) kaybolmuştu; **10.87pt** okundu ve tamamen makul göründü. Gerçek değer **22.76pt** — iki satır. Bir sayfada altı entry'de bu 71 punto, yani neredeyse altı satırlık sessiz taşma demekti. |
| `capacity()` `Optional` döner | Sapma | Bölüm 22.2 koşulsuz bir model döndürüyor. Döndüremez: **ölçülmemiş bir özelleştirmenin kapasitesi yoktur**, ve uydurmak sayfa garantisini sessizce bozar — sistemin var olma sebebi olan tek hata. Boş optional "önce ölç" demek. Bölüm 33.1'in B katmanı (font, boyut, margin, aralık) bu yüzden ölçüm gerektiriyor. |
| Log ayrıştırma | Ekleme | `ATOMCOST\|key\|<pt>\|<pt>` deseni; yarım yazılmış bir satır (TeX log'u 79 karakterde sarar) **yok sayılır**, yarım okunmaz. Maliyet = yükseklik + derinlik + `baselineSkip`: aradaki boşluğu saymamak, on altı atomun kâğıtta teoride sığıp pratikte taşması demek. |

### D.8.4 — Adım 1.5: ölçümün veriye yazılması

| Konu | Tür | Karar |
|---|---|---|
| Bölüm 22.4'ün ölçüm belgesi derlenmiyor (ikinci kusur) | Düzeltme | `\begin{itemize}` açılıp **hiç `\item` konmadan** kapanıyor: LaTeX "Something's wrong--perhaps a missing `\item`" ile duruyor ve `-halt-on-error` altında koşu bitiyor. İlk atomun `ATOMCOST` satırı hatadan **önce** basıldığı için tek atomlu bir test geçiyor, iki atomlu düşüyor. Düzeltilmiş hâl: `\item\savebox{…}\usebox{…}`. |
| Ölçüm genişliği | Düzeltme | `\parbox{\measurewidth}` (yani `\textwidth`) hiçbir maddenin sahip olmadığı bir genişlikte ölçüyor. Madde `itemize` içinde ve orada gerçek genişlik **`\linewidth`** — girintiyle azaltılmış hâli. |
| Kendi testim de zayıftı | Düzeltme | "ATOMCOST satırı var mı" diye bakıyordu, "belge geçerli mi" diye değil; geçersiz bir belgeyle **geçiyordu**. Artık log'da LaTeX hatası olmadığını ve **on iki maddenin on ikisinin de** raporlandığını doğruluyor. |
| Derleyici istemcisi | Ekleme | `LatexCompilerClient` uygulamanın container'a uzanan tek yolu; hata dört türe ayrılıyor: **belge hatalı** (422, log'uyla), **meşgul** (503, tekrar denemeye değer), **zaman aşımı**, **ulaşılamıyor**. Kuyruk ve yeniden deneme kararları bunlara bakıyor. TeX log'u yanıta ve geliştiriciye gider, **log satırına asla** — kullanıcının kendi içeriğinden türetilmiştir (mutlak kural 4). |
| `RenderCostService` neden `rendering` içinde | Ekleme | Maliyet varyanta ait, ama servisi `profile`'a koymak **modül döngüsü** yaratırdı: `rendering` zaten içeriği tanıyor (`RichContent`), `profile` da rendering'e uzanırsa ikisi birbirine bağlanır ve ArchUnit'in döngü kuralı düşer. Yazma yine profilin kendi kapsamlı repository'sinden geçiyor. |
| Ölçüm anahtarı | Sapma | Bölüm 22.4 `{variantId}:{customizationId}:{templateVersion}` diyor. Uygulanan: **yalnız `variantId`**. Özelleştirme entity'si henüz yok, ve anahtarın tek işi log satırından geri okunmak; sürüm bilgisi zaten `render_costs` anahtarında (`classic:v1`). Özelleştirmeler geldiğinde genişler. |
| Eksik ölçüm | Ekleme | Bir varyantın maliyeti log'da yoksa **diğerleri yine yazılır**. Tek bir eksik ölçüm için tüm profili ölçümsüz bırakmak, seçimin tahmine düşeceği tek atom yerine hepsini tahmine düşürürdü (Bölüm 26.5). |
| `FontMetricEstimator` ertelendi | Açık | Bölüm 26.2'nin 1. katmanı font dosyalarını **backend tarafında** okumayı gerektiriyor; fontlar container imajında. Tahminin tek tüketicisi henüz olmayan bir arayüz önizlemesi (Bölüm 33.3) ve ölçümsüz üretim yolu. Fontları ikinci bir yere kopyalamadan önce tüketicisi olsun. |

### D.8.5 — Adım 1.6: Faz C, seçim

| Konu | Tür | Karar |
|---|---|---|
| Başlık bloğu ölçüldü | Ekleme | Bölüm 20.1 bütçeye `capacity.fixedCost("heading")` koyuyor ama kalibrasyonda yoktu. Ölçüldü: ad + iki ortalanmış satır = **52.0pt**. Onsuz her CV yarım satır fazla sığıyor sanılırdı. |
| `minAtoms` her entry için zorlanmıyor | Sapma | Bölüm 20.3'ün 1. aşaması **her görünür entry** için minimumu zorluyor. Bu, uzun bir profili "sığmıyor" hatasına düşürürdü — oysa doğru davranış zayıf entry'leri bırakmak. Uygulanan: minimum yalnız **kilitli bir atomun zaten açtığı** entry'lerde zorlanır; diğerlerinde greedy'den sonra **ya hepsi ya hiçbiri** olarak uygulanır (yeni red sebebi: `ENTRY_BELOW_MINIMUM`). |
| Öncelik kuyruğu yerine her turda yeniden hesap | Sapma | Bölüm 20.3 bir `PriorityQueue` kuruyor. Bir atomu almak kardeşlerinin **hem maliyetini** (entry başlığı artık ödendi) **hem değerini** (aynı entry'den beşinci madde daha az değerli) değiştiriyor; önceden sıralanmış bir kuyruk bayat sayıları sıralar. 200 atom için her turda yeniden taramak birkaç milisaniye, ve tamamen deterministik. |
| Swap tek-için-tek | Sapma | Bölüm 20.3 bir **küme** çıkarıp bir aday koymayı öneriyor. Bu boyutta kazanç küçük, alt küme araması pahalı, ve her ek serbestlik derecesi iki koşunun ayrışması için bir yol daha. |
| Etkin maliyet | Ekleme | Bir atom, açtığı mobilyayı da ödüyor: bölüm başlığı, entry başlığı ve madde listesi ek yükü. Kısıt (5) bu; problemin saf knapsack olmamasının sebebi de. |
| Model tutarlılığı kodda | Düzeltme | `EntryPlan` altındaki bir atomun `entryId`'si o entry'yi göstermek **zorunda**. Test yazarken tam bu hatayı yaptım: entry içindeki atom `entryId = null` taşıyınca seçim entry başlığını hiç ödemedi ve bütçe entry başına **22.76 punto** kazandı — görünür sebebi olmayan bir taşma. Artık kurulumda patlıyor. |
| `Result` ve `PipelineError` | Ekleme | Bölüm 25.1/25.2'nin biçimiyle, ama **yalnız bugün üretilebilen hata** ile: `ConflictingPreferences`. Sealed arayüz, hata sunumunu exhaustive switch yapıyor — yeni bir hata türü, kullanıcıya ne söyleneceği kararlaştırılmadan derlenmiyor (P4, dille zorlanmış). Diğer durumlar kendi fazlarıyla gelecek; erken eklemek parametrelerini tahmin etmek olurdu, ve frontend'in mesajlarının ihtiyacı tam olarak o parametreler. |

### D.8.6 — Adım 1.7: Faz E ve Faz F

| Konu | Tür | Karar |
|---|---|---|
| Sayfa sayısını **derleyici bildiriyor** | Ekleme | Bölüm 23.1 `pdfAnalyzer.pageCount(pdf)` diyor ama böyle bir bileşen tanımlı değil. PDF baytlarında `/Type /Page` saymak modern xelatex çıktısında güvenilir değil (sayfa ağacı object stream içinde sıkıştırılıyor) ve bunun için bir PDF kütüphanesi eklemek, container'ın "bağımlılıksız" olma gerekçesiyle çelişirdi. Container `/compile` yanıtına **`X-Page-Count`** başlığı koyuyor; değeri TeX'in kendi `Output written on ... (N pages)` satırından. |
| Sayfa sayısı **gelmezse belge reddedilir** | Ekleme | Başlıksız bir 200, "uzunluğu bilinmeyen bir CV" demek. Faz F ölçemediği bir sınırı garanti edemez, o yüzden `LatexCompilerClient` bunu belge hatası değil **`UNAVAILABLE`** (yanlış derleyici) sayar. P4'ün doğrudan uygulaması; testi var. |
| `SelectionRequest.withBudgetFactor` | Ekleme | Bölüm 23.1'in `input.withBudgetFactor(0.95)` çağrısının karşılığı. Faktör bileşen olarak eklendi (0 < f ≤ 1; **büyütülemez**), üç argümanlı kurucu 1.0 ile delege ediyor. |
| Geri besleme döngüsü | Uygulama | Seç → render et → derle → say. Sığmıyorsa bütçe %5 kısılır ve **Faz C tekrar koşar**; en çok iki tekrar, sonra `PageLimitExceeded`. LLM'e dönülmüyor — Faz F asla yeni metin istemez. `generation.budget.overshoot` sayacı Bölüm 23.1'in istediği oranı besliyor. |
| `PipelineError` iki yeni durum | Ekleme | `PageLimitExceeded(actualPages, maxPages)` — kataloğun `PAGE_LIMIT_EXCEEDED` (422) koduyla birebir. `CompilationFailed(kind, texLog)` — derleyici istisnası hattın dışına **fırlamıyor**, `Result.err` olarak taşınıyor; sunum yine exhaustive switch. |
| Sıra profilden gelir, seçimden değil | Uygulama | Seçim skora göre sıralar. Madde işaretleri profil sırasında basılıyor; aksi halde CV karıştırılmış gibi okunurdu. |
| Boş başlık basılmaz | Uygulama | Altında seçilmiş içeriği kalmayan bölüm ve entry render edilmez. Seçim yalnız **açtığı** mobilyayı ödediği için, boş bir başlık bütçede karşılığı olmayan punto harcardı. |
| "Halen" / "Present" | Sapma (geçici) | Bitiş tarihi olmayan entry için dilde bir kelime gerekiyor. Bölüm 32 çok dilli render'a kendi sözlüğünü getirene kadar iki dil `RenderPhase` içinde sabit; bilinmeyen dil İngilizce'ye düşer. Tarih biçimi `MMM yyyy`, içerik diliyle. |
| İndirme ucu Adım 1.8'e taşındı | Kapsam | XI-A.3 Adım 1.7'nin beşinci maddesi. Hattın girdisi **skorlanmış ve maliyeti bilinen** bir `SelectionRequest`; bir profili ona çeviren şey genel mod skorlaması, o da Adım 1.8. Uç orada tek parça yazılacak (`generations` tablosuna yazan kalıcı üretim kaydı ve `GET /generations/{id}/download` ise Aşama 2, `spec/08b-api-contract.md` § D.6.3). Aşama 1 kontrol listesindeki "PDF indiriliyor ve gerçekten 1 sayfa" maddesi 1.8'de kapanır. |
| `ATS` raporu ve `FitReport` yok | Kapsam | Bölüm 23.2/23.3 metin çıkarma (PDF → text) ve ilan analizi istiyor; ikincisi Faz A'ya, birincisi bir PDF kütüphanesine bağlı. Aşama 1'in dört kritik testinde ikisi de yok, Aşama 2'ye bırakıldı. |
| `RenderableSection.toString` | Düzeltme | Kardeş record'lar (`ProfileHeader`, `RenderableEntry`) içerik basmıyordu, bu basıyordu — bölüm başlığı kullanıcının kendi metni (mutlak kural 4). |

**Doğrulama.** Birim testler döngünün aritmetiğini sahte derleyiciyle kanıtlıyor
(bir denemede sığar, iki denemede sığar, üç denemede sığmazsa reddedilir, kilitli
içerik derleyiciye **hiç ulaşmaz** — P5). Gerçek container'a karşı iki test:
üç bölümlük bir kariyer gerçekten tek sayfalık bir PDF oluyor, ve **her atomun
maliyeti bilerek beşte bir bildirildiğinde** seçim sığdığını sanıyor, derleyici
aksini söylüyor, sonuç sessiz bir üç sayfalık CV değil bir hata oluyor.

### D.8.7 — Adım 1.8: genel mod skorlaması ve seçim isteği

| Konu | Tür | Karar |
|---|---|---|
| Yarılanma süresi **5 yıl** | Ekleme | Bölüm 19.4 "üstel azalma" diyor, hızını vermiyor. On yıl önceki bir iş güncelin dörtte biri ediyor; **sıfır etmiyor**, çünkü içinde metrik olan on yıllık bir madde hâlâ sayfanın en iyi şeyi olabilir. |
| Tarihsiz atom cezalandırılmıyor | Ekleme | Entry'si olmayan atom (beceri, sertifika) için recency **1.0**. Bölüm 19.4 bu durumdan söz etmiyor; 0 vermek her beceriyi CV'nin dışına iterdi. |
| Skor sonda kırpılıyor | Düzeltme | Ağırlıklar bire tamamlanıyor ama dört double `1.0000000000000002` edebiliyor ve `AtomCandidate` birden büyük skoru reddediyor — yalnız kusursuz bir atomda ortaya çıkacak bir kusur. |
| Bugünün tarihi **parametre** | Ekleme | Saati okuyan bir skorlayıcı Bölüm 51.2'nin "aynı girdi → aynı çıktı" testini geçemez. |
| Ölçümsüz atom: `RenderCostEstimator` | Sapma | Bölüm 26.5 ölçüm yoksa **font-metrik tahmini + %8 pay** istiyor; Bölüm 26.2 bunu FontBox ile gerçek font metriklerinden kuruyor. Burada PDF kütüphanesi eklemek yerine bağımlılıksız ve **daha kötümser** bir tahmin var: ortalama karakter genişliği (0.46em, Termes'in gerçek ortalamasının altında — kasten), satır doluluğu %92, ve ölçümün biçimini taklit eden `(satır + 1) × baselineSkip`, üstüne %8. Tek sözü: **asla TeX'ten az yazmaz**, ve bu gerçek derleyiciye karşı altı farklı uzunlukta test ediliyor. |
| `CapacityModel.textWidthPt` | Ekleme | Kalibrasyon `\textwidth`'i zaten ölçüyordu ve atıyordu. Tahmin bu genişliğe bölüyor; yanlışsa her ölçümsüz atom yanlış sayıda satır ödüyor. Ölçülen değer **527.571pt** ve kalibrasyon testi artık onu da doğruluyor. |
| Entry kilidi atom kilidine çevriliyor | Ekleme | `entries.always_include` "bu iş CV'de kalsın" demek; `SelectionPhase` yalnız atom kilidi biliyor. Bütçedeki karşılıkları aynı: entry başlığı + `min_atoms` kadar madde. Kurucu, kilitli entry'nin **en yüksek skorlu** `min_atoms` atomunu kilitliyor (eşitlikte id ile, Bölüm 19.6). Kilitli bölüm için aynısı bir atomla. Bunu yapmamak, kullanıcının koyduğu kilidi sessizce yok saymak olurdu. |
| Pasif bölüm/entry hiç aday olmuyor | Karar | Pasif **atom** aday listesinde kalıyor ve `INACTIVE` sebebiyle reddediliyor (Bölüm 19.5), ama pasif bir bölüm ya da entry CV'nin parçası değil: altındaki atomlar için "neden yok" sorusu da doğmuyor. |
| Sözü olmayan atom sayılıyor | Ekleme | Hiçbir dilde varyantı olmayan atom render edilemez; sessizce düşürmek yerine `withoutWording` sayacına yazılıyor — yukarıda bir kusur olduğunun işareti. |

### D.8.8 — Adım 1.8: üretim servisi ve PDF ucu

| Konu | Tür | Karar |
|---|---|---|
| **`POST /api/v1/generations/general`** | Ekleme | Bölüm 35.3'ün `POST /generations`'ı 202 + iş döndürüyor, çünkü içinde LLM var. Genel modda LLM de kuyruk da yok: bu uç belgeyi **doğrudan** döndürüyor (`application/pdf`, `Content-Disposition: attachment`, `Cache-Control: no-store`). Aşama 1'e özgü ve öyle işaretli; kuyruklu sözleşme üretim kaydıyla birlikte Aşama 2'de gelecek. Gövde **isteğe bağlı**; `maxPages` ve `language` verilmezse profilin kendi varsayılanları geçerli. |
| Hiçbir şey saklanmıyor | Kapsam | `generations` tablosuna satır yazılmıyor, `selection_state` saklanmıyor, indirme bağlantısı yok. Saklama, saklama süresi (`spec/08b-api-contract.md` § D.6.3'teki 14 gün ve 410) ve düzenleme döngüsü (Faz G) hep aynı kaydı gerektiriyor; biri olmadan diğerini yazmak yarım bir sözleşme olurdu. Bir test `generations`'ın boş kaldığını doğruluyor. |
| Ön kontrol **yapısal**, yüzde değil | Karar | Bölüm 25.2 `INSUFFICIENT_PROFILE(completeness, missing)` diyor, eşik vermiyor. Yüzde eşiği gayet iyi render edilecek profilleri reddederdi; üretimi durduran şey **basılacak bir şeyin olmaması**. Tamamlanma yüzdesi mesajda taşınıyor, kararı vermiyor. |
| `complete_profile` sözlüğe eklendi | Ekleme | Bölüm 25.3'ün örneği bu adı kullanıyor ama `spec/08b-api-contract.md` § D.6.1'in sekiz eylemlik kümesinde yoktu. Dokuzuncu eylem; frontend'in buton davranışı yazması gerekiyor (`spec/08-api.md`). |
| `ErrorPresenter` | Uygulama | Bölüm 25.3'ün biçimiyle, dört durumun **tamamı** için. `UserFacingError` parametreleri katalogla doğruladığı için her sunum aynı zamanda "ICU mesajının beklediği alanları yayınlıyor mu" testi. |
| `PAGE_LIMIT_EXCEEDED`'in çözümü | Karar | `increase_page_limit`, `maxPages` = **derleyicinin gerçekten ürettiği sayfa sayısı**. Yeterli olduğu bilinen tek sayı o. |
| `COMPILATION_FAILED.detail` **log değil** | Karar | Katalog `detail: string` istiyor ve bu dize ICU mesajına giriyor. TeX logu kullanıcının kendi içeriğinden türüyor, oraya konamaz: `detail` yalnız hatanın türü (`invalid_document`, `busy`, `timeout`, `unavailable`). `retry` çözümü TeX'in reddettiği belge dışında sunuluyor — o belge tekrar denenince yine reddedilir. |
| `PipelineError.Resolution` silindi | Düzeltme | Aynı kavramın iki tipi vardı; `generation` artık `shared.error.Resolution`'ı kullanıyor. Eylem adı artık `String` değil enum: yazım hatası derlenmiyor. |
| `Clock` bean'i | Ekleme | Skorlama bugünün tarihini parametre olarak alıyor (Bölüm 19.6); onu üreten yer bir bean, UTC. Kotanın gün sınırı ayrı bir karar olarak duruyor. |
| `ProfileResolver.owned()` | Ekleme | Üretim hem profilin kendi alanlarını (başlık, tercihler) hem de altındaki kapsamı istiyor. `ProfileRef`'in tek üretim yeri kuralını bozmamak için ikisini birlikte döndüren bir metot eklendi — satırı iki kez okumak yerine. |

**Doğrulama.** `GenerationApiIT` yedi test: PDF eki, isteğe bağlı gövde ve
`maxPages` geçersiz kılma, boş profilin **derleyiciye hiç gitmeden** reddi,
sayfa aşımının çözümüyle birlikte sunumu, derleyici çöküşünün 502'si,
`maxPages: 99`'un 400'ü, ve `generations` tablosunun boş kalması.
`GeneralCvIT` (latex etiketli) aynı ucu gerçek container'a karşı çalıştırıyor:
veritabanındaki bir profil ölçülüyor, seçiliyor, derleniyor ve **gerçekten tek
sayfalık** bir PDF olarak dönüyor — XI-A.3'ün Aşama 1 kontrol listesindeki
madde budur.

### D.8.9 — Adım 1.9: golden set, seeder ve dört kritik test

| Konu | Tür | Karar |
|---|---|---|
| Fixture formatı **export formatı değil** | Sapma | Bölüm 51.3 dosyaları adlandırıyor, biçimini vermiyor. Export biçimi her satır için `id` ve `version` taşıyor; elle yazılan bir fixture'da bunlar altmış kez uydurulurdu ve ikinci bir veritabanına yüklendiğinde yanlış olurdu. `GoldenProfileDocument`'te kimlik **ağaçtaki yer**; id'leri okuyucu üretiyor. Metinden başka her alan isteğe bağlı, yani bir fixture yalnız ilginç olan şeyi söylüyor. |
| Fixture'lar `src/main/resources` altında | Sapma | Bölüm 51.3 `src/test/resources` diyor. `DevSeeder` üretim kodu ve aynı dosyaları okuyor; test kaynakları onun sınıf yolunda değil. Kopyalamak iki formatın zamanla ayrışması demekti. Dosyalar jar'a giriyor (birkaç KB) ama onları okuyan tek şey `local` profiline bağlı seeder. |
| Maliyetler **içerik hash'iyle** anahtarlanıyor | Ekleme | `*.costs.json` Bölüm 51.3'ün istediği dosya. Varyant id'si her okumada değişiyor, içerik hash'i içeriğin kendisi — metin değişince anahtar da değişiyor, yani bayat bir maliyet sessizce eşleşemiyor. |
| `-Dgolden.record=true` | Ekleme | `GoldenCostsIT` normalde **doğruluyor**: saklanan her sayıyı gerçek derleyiciden yeniden ölçüyor ve 0.01 punto sapmada düşüyor. Kayıt modunda aynı test dosyaları yazıyor. Bir fixture'ın metni ya da şablonun geometrisi değişince yeniden kaydedilir. |
| **`max_print_line`** | Düzeltme | TeX logunu 79 sütunda katlıyor. Ölçüm anahtarı 64 karakterlik bir hash olunca `ATOMCOST` satırı ikiye bölündü ve parser hiçbir şey bulamadı — beş profilin **hepsi sıfır ölçümle** döndü. Container artık xelatex'e `max_print_line=10000` veriyor. Varyant id'siyle (36 karakter) hiç görünmeyecek, ama sınıra iki karakter kalmıştı. |
| Beraberlikler id ile çözülüyor, ve id kalıcı değil | Bulgu | Aynı puanı **ve** aynı maliyeti taşıyan iki atom arasında Bölüm 19.6'nın tie-break'i id'ye bakıyor. Veritabanındaki bir profil için id sabit, dolayısıyla çıktı sabit; ama aynı içerik yeniden içe aktarılırsa (Aşama 3'ün anonim profil devralması) ikisinden diğeri seçilebilir. Golden test bu yüzden "aynı atomlar" değil **"aynı sayıda atom ve aynı punto"** diyor. İçerikten türeyen bir tie-break Aşama 2'de bilinçli olarak kararlaştırılmalı. |
| Atomsuz entry hiç görünmüyor | Bulgu | Seçim atom üzerinden çalışıyor; hiç atomu olmayan bir entry (yalnız derece satırı olan bir eğitim kaydı) aday bile olmuyor. Fixture'larda her eğitim kaydına bir atom verildi. Gerçek çözüm — entry'nin kendisinin aday olması — Bölüm 20.2'nin modelini değiştirir ve Aşama 2'ye ait. |
| `DevSeeder` var olan profile dokunmuyor | Karar | `local` profiline bağlı, `@Order(100)` ile kullanıcı satırından sonra çalışıyor, ve profil zaten varsa **hiçbir şey yapmıyor**: yerel olarak denemek için girilen bir CV'nin üzerine yazmak tam olarak P8'in engellediği şey. Hangi fixture'ın ekileceği `atomcv.dev.seed-profile` ile seçiliyor. |

**Dört kritik test (Bölüm 51.2), nerede.**

| # | Test | Nerede | Kapsam |
|---|---|---|---|
| 1 | Sayfa sınırı aşılmıyor | `GoldenSelectionTest` | 5 profil × 2 dil × {1,2} sayfa |
| 2 | Determinizm | `GoldenSelectionTest` | Her profil için 50 koşu |
| 3 | Çok-kiracılı izolasyon | `MultiTenantIsolationIT` | Kimlik taşıyan **sekiz** uç + reorder + listeler + üretim ucu |
| 4 | Kilitler ve yapısal kısıtlar | `GoldenSelectionTest` | Kilitli atom seçiliyor, pasif olan seçilmiyor, entry ya minimumuna ulaşıyor ya da bütün olarak düşüyor |

Üçü Docker'sız koşuyor — maliyetler dosyada olduğu için. İzolasyon testi
kasıtlı bir ihlale karşı doğrulandı: `ProfileScopedRepository.findById`'nin
profil filtresi kaldırıldığında sekiz ucun hepsi düştü, geri konunca geçti.

### D.8.10 — Aşama 1'in son maddesi: ölçüm ile gerçek sayfa arasındaki sapma

XI-A.3'ün tamamlanma kontrolü "**ölçüm ile gerçek sayfa arasında sapma
<%3**" diyor. Bu maddeyi kapatan test (`MeasurementDriftIT`) yazıldığında sapma
**%15-32** çıktı — beş golden profilin hepsinde, hep aynı yönde: model sayfayı
gerçekte olduğundan **dolu** sanıyordu. Yönü güvenliydi (sayfa taşmıyor) ama
sonucu değildi: kullanıcının içeriğinin üçte biri sebepsiz yere dışarıda
kalıyordu.

Test, render edilen **gerçek belgeye** `\typeout{...\the\pagetotal}` ekleyip
TeX'e "bu sayfada ne kadar yer kapladın" diye soruyor ve seçimin harcadığını
sandığı puntoyla karşılaştırıyor. Üç ayrı hata buldu:

| Hata | Neydi | Ne oldu |
|---|---|---|
| **Atom maliyeti** | `height + depth + baselineSkip` (Bölüm 26.2'nin formülü) | Bir madde listesindeki kutu, sayfayı kendi yüksekliği kadar değil **satır sayısı kadar baseline** ilerletiyor. Doğrusu `satır × baselineSkip + itemsep`. Madde başına ~8 punto, yirmi maddelik bir sayfada üçte bir sayfa. |
| **Başlık bloğu** | 52.0pt | Kalibrasyon belgesi ölçümden önce `\null` koyuyordu; o boş kutu, gerçek belgede olmayan bir baseline boşluğu satın alıyordu. `\null` kaldırıldı: **45.68pt**. |
| **Entry başlığı tek sayı değil** | Her entry 22.76pt | Bölüm başlığından sonra gelen entry 22.76pt, **üstündeki işin madde listesinden sonra gelen** entry 32.0pt — arada paragraf boşluğu var. Dört işlik bir CV bunu üç kez ödüyor. Yeni sabit: `ENTRY_HEADER_AFTER_LIST`. Seçim, entry'yi açarken hangisinin geçerli olduğunu biliyor ve **ne ödediğini kaydediyor**, çünkü swap turunda geri alırken aynı sayıyı düşmesi gerekiyor. |

**Bölüm 26.3'e düzeltme.** O bölüm "satıra yuvarlama, puntoyla topla" diyor ve
gerekçesi doğru — ama satıra yuvarlamak burada bir yaklaşım değil, TeX'in
kendi aritmetiği: ardışık baseline'lar tam olarak `\baselineskip` uzaklıkta,
dolayısıyla n satırın yüksekliği tam olarak n baseline. Uyarı, *ölçümü satıra
çevirip artığı kaybetmek* için geçerli; toplama hâlâ puntoyla yapılıyor.

**Sonuç.** Sapma beş profilde de **%3'ün altında** ve hepsinde **fazla tahmin**
yönünde (senior %0.65, minimal_edge %2.4) — yani sayfa hâlâ taşmıyor, ama artık
neredeyse dolu. Kalan pay çoğunlukla başlık bloğunun sabit sayılmasından
geliyor: iletişim satırı kısa olan bir profil kalibre edilenden az yer kaplıyor.

**Kalıcı guard'lar:** `MeasurementDriftIT` (beş profil, %3), ve
`LatexCalibrationIT` artık ikinci bir bölümü, ikinci bir entry'yi, ikinci bir
listeyi **ve** listeden sonra gelen bir entry'yi de ölçüyor — tekrarlanan
mobilyanın maliyeti değişirse test düşer.

---

## Aşama 1 ne bıraktı

Kapanış envanteri. Kod değişince bayatlar — **otorite repodur**, burası
Aşama 2'ye başlayan birinin haritasıdır.

| Paket | Sınıflar |
|---|---|
| `profile.domain.content` | `RichContent`, `Run`, `Mark`, `ContentMigrator`, `RichContentConverter` |
| `profile.domain` | `Profile` (+ `Contact`, `Preferences`), `Section`, `Entry`, `Atom`, `AtomVariant`, `ProfileTree`, altı enum |
| `profile.repository` | Paket-özel Spring Data arayüzleri, public kapsamlı cepheler arkasında |
| `profile.service` | `ProfileResolver`, `ProfileService`, `SectionService`, `EntryService`, `AtomService`, `CompletenessCalculator`, `ProfileExporter`, `ProfileAssembler` |
| `profile.api` | `ProfileController`, `SectionController`, `EntryController`, `AtomController` + DTO'lar |
| `shared.security` | `UserContext`, `UserRole`, `UserOwned`, `ProfileOwned`, `ProfileRef`, iki kapsam bazı, `CurrentUser`, `LocalDevCurrentUser` |
| `shared.error` | `ErrorCode` (30 kod, tipli params), `ResolutionAction`, `Resolution`, `UserFacingError`, `ApiException`, `ProblemDetailAdvice` |
| `shared.util` | `LowercaseEnumConverter`, `EntityTags` |
| `rendering` | `DocumentRenderer`, `latex/*` (escaper, satır içi renderer, preamble, `LatexDocumentRenderer`), `model/*`, `template/*`, `measurement/*` |
| `compilation` | `LatexCompilerClient`, `CompiledDocument`, `CompilationException`, `CompilationProperties` |
| `generation` | `pipeline`, `selection`, `scoring` (`GeneralModeScorer`), `render` (`RenderPhase`), `service` (`CvGenerationService`, `GenerationOptions`), `api` (`GenerationController`) |
| `profile.seed` | `GoldenProfileDocument`, `GoldenProfileReader`, `GoldenProfile`, `DevSeeder`; beş fixture ve ölçülmüş render maliyetleri |

**Testler:** 312 birim · 132 entegrasyon · 44 latex-etiketli.
`gradlew latexTest` `integrationTest`'ten hariç — imaj dakikalar alıyor.

Aşama 1'in dokuz adımının hepsi ✅ (`STATUS.md`). Bu dosyadaki D.8-D.8.10
kayıtları her adımın *neden* öyle yapıldığını taşır; sınıf listesi yalnız *ne*
olduğunu söyler.
