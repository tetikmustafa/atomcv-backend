# Denetim — üçüncü tur, spec'in tamamı koda karşı (2026-09-16)

> Kapanmış kayıt. `current.md` 200 satır sınırına dayandığında buraya taşındı
> (2026-09-16, **dördüncü** turun kaydı için). Birinci ve ikinci tur
> `denetim-2026-09-15.md` ile `denetim-2026-09-16.md`'de. Bu turun kalıcı
> kararları o gün `spec/`'e işlendi ve orada duruyor; burası ne bulunduğunun
> ve ne inildiğinin anlatısıdır.
>
> **Canlı olanlar `current.md`'de kaldı:** bilinçli boşluklar, test yazarken
> öğrenilenler, ölçümler, Aşama 1-3'ten taşınanlar ve dersler.


Birinci ve ikinci tur `archive/denetim-2026-09-15.md` ile
`denetim-2026-09-16.md`'de. Bu turu ikincinin kendi dersi istedi — *"bir
denetim kapanmış bir denetimi tekrarlamaya değer: ikincisi sekiz madde
buldu"* — ve haklı çıktı: **üç gerçek boşluk, bir kayıtsız karar, on iki
doküman sapması.** Öncekilerden farkı şekli: ilk ikisi çoğunlukla *yazılmamış
kod* buldu, bu tur *yazılmamış olduğu hâlde yazılmış sayılan tasarım* buldu.

**En ağırı, ve mutlak kural 4'ün yarısıydı: `ContentShape` hiç yazılmamıştı.**
§ 48.2 onu on alanlı bir kayıt olarak tanımlıyor, EK A sözlükte listeliyor,
§ XI-B.9 "içerik yerine bunu logla" diyor, `CLAUDE.md`'nin kural 4'ü adını
veriyor — ve **`noContentInLogs` muhafızının kendi javadoc'u** okuyanı ona
yönlendiriyordu. `src/` içinde yoktu; on alanının hiçbiri yoktu. Yani kuralı
çiğnemek üzere olan geliştirici, import edemeyeceği bir tipe gönderiliyordu.

> Fiilen olan şey kuralın kaçış şıkkıydı ("*or a stage's own*") ve
> `ExtractedText.shape()` o davayı iyi savunuyor: alanları bir **dosyanın**,
> ötekininkiler bir **atomun**. İtiraz geçerli, ve kaydın javadoc'unda
> cevaplanıyor — çıkarım aşaması hakkında, atomlar hakkında değil.
>
> **Bağlı olarak indi**, çünkü çağıranı olmayan bir kayıt aynı boşluğun başka
> bir şekli: reddedilen yeniden yazım (`TOO_LONG`, 190 tavanına karşı,
> orijinal 186 karakterken bir şey söyler, 60 karakterken başka), ölçüm
> dönmeyen sözcükleme (sessizdi), ve ölçülmüş bir sözcüklemenin yüksekliğinin
> yanındaki şekli (`debug`).

**Sapma — § 17'nin faz sözleşmesi hiç kurulmamıştı, ve iki tur bunu geçti.**
`PipelinePhase<I, O>`, `PipelineContext` ve § 17.2'nin yedi girdi/çıktı tipi;
hiçbiri yok. Kayıtlı da değildi. Ortak arayüz yazılmadı çünkü fazlar gerçekten
heterojen — tek ortak şey `Result`, yani arayüz `execute`'un adını birleştirir
imzasını değil; ve somut kazancı "sırayı konfigüre etmek" olurdu, ki sıra bir
konfigürasyon değil bir veri bağımlılığı. § 17.1 ile § 6 bunu artık yazıyor.

**Format bağımsızlığı sınırın yanlış tarafındaydı.** `DocumentRenderer`
`formatId()` ve `supportedTemplates()` taşıyordu, javadoc'u üç format
adlandırıyordu, **ve repoda ikisinin de tek çağıranı yoktu** — ilan edilen
soyutlama hiçbir yerde yaşamıyordu, `generation` öteki iki formata somut
sınıflarıyla uzanıyordu. § 10.2 kural 3, § 9.2 ve § 1.2'nin dördüncü iddiası
birlikte çiğneniyordu, ve kontrol eden hiçbir şey yoktu.

> **Arayüz genişletilmedi, daraltıldı** — `DocxDocumentWriter`'ın javadoc'u
> neden `DocumentRenderer` olamayacağını zaten yazmıştı ve haklıydı: o
> sözleşme derleyiciye kaynak ve **ölçülmüş** bir kapasite istiyor, HTML'in
> sayfası yok, DOCX'i POI yazıyor. Format soyutlaması `DocumentWriter`'a
> taşındı, `DocumentWriters` § 6'nın hiç yazılmamış Factory'si oldu.

**Ekleme — `MAX_ABOUT_TEXT = 1500` kaydı yalnız javadoc'taydı.** § 43.1
tavanları sayıyor ve dördüncüsü yoktu. Bedeli ölçülmüş: **84 atomu temiz
çıkarılmış dört sayfalık bir CV, özeti 607 karakter olduğu için bütünüyle
çöpe gitti** — tavanın yedi karakter üstünde, ve o profilin en uzun dört alanı
da About'tu. § 43.1'e işlendi.

**Ve bir kusur denetimin konusu bile değildi: gitleaks bir süredir
koşmuyordu.** `pre-commit` hook'unu `.git/hooks`'a kurar; `core.hooksPath`
ayarlanınca git o dizine hiç bakmaz; `.githooks/` yalnız `post-commit`
taşıyordu. Hatasız, çıktısız, ve commit'ler korunmuş olanlarla birebir aynı
görünüyordu. **Yalnız `CLAUDE.md` bir koşunun neye benzediğini yazdığı için
fark edildi.** Hook artık commit'li ve `pre-commit` yoksa commit'i durduruyor.

**Bilinçli istisna — migration yorumları düzenlendi (mutlak kural 2).**
Checksum'ı koruyan kural, dosyayı çoktan uygulamış bir veritabanını korur;
öyle tek bir veritabanı var (yerel dev), çünkü VPS yok. Testcontainers her
koşuda sıfırdan kuruyor, yani entegrasyon paketi karşılaştırma bile yapmıyor.
**Bedeli bir daha bu kadar düşük olmayacak.** Yerelde `make db-reset`
gerekiyor.
