# Arşiv — Aşama 3, dilim 9-10: frontend'in `F-nnn` cevapları

> `notes/current.md`'den taşındı (2026-08-29, dosya sınırı). Kalıcı kararlar
> `spec/`'te: EK D.6'nın tablosu ve § 48.4.2. Hâlâ canlı olan tek şey —
> springdoc'un tam sayı enum tuzağı — `current.md`'de bırakıldı.

## Aşama 3 · dilim 9 — `F-017` ve `F-021` (2026-08-29)

Frontend Aşama 3'ü kapatırken beş madde yazdı; bu dilim ikisini indirdi.
**Üç sorunun ikisinde tahminleri yanlıştı, ve yanlış olan taraf bizdi.**

**Düzeltme — `{"userEdited": true}` `500` dönüyordu.** `VariantPatch`'in
compact constructor'ı doğru davranıyor, ama `ProblemDetailAdvice`'ta
`IllegalArgumentException` işleyicisi yok: ihlal son çareye düşüp
`INTERNAL_ERROR` oluyordu. Kapı artık `VariantPatchRequest.userEdited`
üstünde bir `@AssertFalse` — `null` onun altında geçerli kalıyor ("dokunma"
trafiğin çoğu), ve reddi mevcut `MethodArgumentNotValidException` işleyicisi
yazdığı için `fields` binding result'tan geliyor, ikinci bir yerde tutulan
addan değil. Constructor'daki muhafız duruyor: o servis çağıranı için.

**Ders, ve bu oturumda dördüncü kez:** *muhafızı düşerken görmediysen ne
kanıtladığını bilmiyorsun.* `VariantSynchronizationIT` bu kuralı yıllardır
test ediyordu — **constructor'ı çağırarak**. Telden geçen hiçbir şey yoktu, ve
telde yanlış olan şey tam olarak oydu. § 51.7'nin dördüncü kuralının kardeşi:
bir kuralın doğru olması, cevabının doğru olduğunu söylemiyor.

**Ekleme — `ErrorCatalogueSpecTest`.** EK D.6'nın tablosunu okuyup
`ErrorCode`'a karşı doğruluyor: kod, HTTP durumu, parametre adları, tipleri ve
**sıraları**, iki yönde de. Bir birim testi ve `docs/`'tan dosya okuyor —
Gradle testleri proje dizininden koştuğu için yol göreli, ve yolun bozulması
`theTableWasActuallyFound`'ı düşürüyor: bulunamayan bir tablo, sessizce iki
boş haritayı karşılaştırmaya dönüşmemeli.

**Neden yazıldı:** frontend'in katalog testi `params`'ı **o tablodan** okuyor,
yani satırı olmayan bir kodun ICU mesajı yanlış argümanla yazılsa da iki repoda
da yeşil kalıyor. Bildirilen bir eksik satır vardı; muhafız **beş** buldu —
`COVER_LETTER_REJECTED`, `GENERATION_PAUSED`, ve D.6.8'de "katalogda" yazıp
EK D.6'ya hiç geçmemiş üç protokol kodu. *İki tablo bir kataloğun tamamı
değil.*

**`Retry-After` eksik değildi.** Advice onu `resetsAt`'i `Instant` taşıyan her
429'dan türetiyor. Eksik olan test ve şema girişiydi — ve **ikisinin yokluğu
başlığın yokluğundan ayırt edilemez**: bu iddianın test edilmeden yazıldığı tek
öbür yer (elle test kılavuzu) yanlış çıkmıştı, `QueuedGenerationApiIT`'in
javadoc'u onu kaydediyor.

**`CoverLetterApiIT` artık `ratelimit:*`'ı siliyor.** Yeni test on mektuplu
pencerenin tamamını harcıyor; silmeyen bir sınıfta komşu bir vaka ilgisiz bir
429'da düşerdi. `MagicLinkApiIT`'in aynı sebeple yaptığı şey — **bu dosyada
ikinci kez.**

**Açık bırakıldı — `selection_state` telde yok.** `rejected` ve
`headerOnlyEntries` yalnız `generations` satırında; hiçbir uç yayımlamıyor.
Frontend "neden bu satır çıktı" görünümünü kurmadı ve istemedi, biz de
tüketicisi olmayan bir yapıyı yayımlamadık — ilk ekran çizildiğinde yanlış
şekli olmuş bir sözleşme sürdürmek olurdu. **Görünüm gelirse yayımlanacak.**

Frontend aksiyonları: `B-062`, `B-063`, `B-064`.

## Aşama 3 · dilim 10 — `F-019` (2026-08-29)

Verilmiş bir yargı geri okunabiliyor, ve `rating` şemada sayı.

**Kalıcı kararlar `spec/11-operations.md` § 48.4.2'ye işlendi**, buraya
kopyalanmadı. Burada yalnız bir sonraki oturumun bilmesi gereken **tuzak**
duruyor:

**Springdoc'ta tam sayı enum'u üç şeyi birden istiyor.** `@Schema(type =
"integer", format = "int32", allowableValues = {"1", "-1"})`, ve **enum
bildiriminin üstünde** — özelliğin üstünde değil. Denenen sıra:

| Ne denendi | Ne çıktı |
|---|---|
| `Short` + `allowableValues` | `type: integer`, `enum: ["1","-1"]` — tırnaklı |
| `Short` + `type` + `allowableValues` (özellikte) | aynı, tırnaklı |
| enum + `@JsonValue short` | `type: string`, enum düştü |
| enum + `@Schema(type=integer)` | `type: integer`, **enum tamamen yok** |
| enum + `type` + `allowableValues` | ✅ `enum: [1,-1]` |

`allowableValues` bir `String[]` ve swagger onu ancak şemanın tipi **açıkça**
integer yazılmışsa sayıya çeviriyor. Tipi yazıp `allowableValues` yazmamak
enum'u tamamen düşürüyor — yani ikisi de gerekiyor, ve `@JsonValue` tek başına
yetmiyor. `OpenApiSchemaIT` üçünü de tutuyor.

**Yan kazanç:** elle yazılmış `hasValidRating()` kalktı. `0` artık
ayrıştırmada reddediliyor, yani uca hiç ulaşamıyor, ve cevabı değişmedi —
`HttpMessageNotReadableException` işleyicisi `fields`'i Jackson'ın yolundan
okuyor ve yine `["rating"]` yazıyor. Mevcut test değişmeden geçti.

Frontend aksiyonu: `B-065`.


---

## Aşama 3 · dilim 11 — `F-020` (2026-08-29)

`GET /api/v1/generations`. Sözleşme `spec/08-api.md` § 35.3'e işlendi; burada
yalnız iki tuzak.

**`limit + 1` çekip fazlasını atmak, ikinci bir sorgudan iyi.** "Sonraki sayfa
var mı" sorusunu sayarak cevaplamak, bu sorgunun zaten bildiği bir şeyi ikinci
kez ve **başka bir anda** sormaktı. `total` ayrı bir `count` olarak duruyor
çünkü o başka bir soru — sayfanın değil hesabın sayısı.

**Cursor sıralama anahtarının tamamını taşımak zorunda.** `created_at DESC,
id DESC` sıralı bir listede yalnız zaman damgası taşıyan bir cursor, eşit
damgalı grubun kalanını ya atlıyor ya iki kez veriyor — karşılaştırmanın hangi
yöne yaslandığına göre. Üçüncü seçenek yok: **bir satırı tanımlamayan anahtar
bir satırdan devam edemez.** `rowsSharingATimestampAreNotSkipped` bunu tutuyor.

**Bilinçli boşluk — satırda başlık yok.** Bir geçmiş satırı bugün "1 sayfa ·
tarih · strong" diyor ve on üretimi olan biri için okunması güç. Etiket
(rol, şirket) **ilandan** okunur ve `GenerationResponse` ilanı baştan beri
döndürmüyor (mutlak kural 4). Buraya `jdAnalysis.role.title` koymak o kuralın
sınırını **kazara** çizmek olurdu. **Tamir etmeye kalkma** — frontend'e soruldu
(`B-066`), cevabı bir spec kararı olacak.

**`spotlessCheck` yerelde CRLF'e düşüyor** — editör aracı ve python'un metin
modu Windows'ta CRLF yazıyor; git commit'te normalleştirdiği için CI geçiyor,
yerel kontrol düşüyor. **Yeni dosya yazdıktan sonra `sh ./gradlew
spotlessApply`.** (CLAUDE.md'nin heredoc uyarısının kardeşi: bu notun kendisi
`\r\n` yazmaya çalışırken heredoc'ta gerçek bir satır sonuna dönüştü.)

Frontend aksiyonu: `B-066` — ve içinde cevaplanacak bir soru var.

## Aşama 3 · dilim 12 — `F-018` (2026-08-29)

Kalıcı kararlar `spec/07-subsystems.md` § 31.6.4'te. Burada iki şey.

**Ders — "yayımla" isteği önce bir kusur buldurdu.** Frontend uyarıların
*yerini* istedi; yayımlamaya kalkınca yerin **yanlış** olduğu çıktı, iki ayrı
biçimde. `path` ne bölümü taşıyordu ne de sıralama sonrası satırı, ve düzeltme
`ProfileNormalizer`'ın içindeydi. **Bir alanı yayımlamak, onu ilk kez
okumaktır** — dilim 9'un `Retry-After`'ının tersi: orada iddia doğruydu ve
kanıtı yoktu, burada alan vardı ve yanlıştı.

**Tamir etmeye kalkma — "kritik uyarı" yok, ve `critical` bayrağı da yok.**
`ExtractionWarningCode` kapalı ve altı değerinin altısı da düzeltilebilir bir
alanı tarif ediyor. § 31.6'nın üçüncü tasarım kuralı **silindi**, sayıya
indirilmedi: kural yazıldığı gün de boştu ve bir bayrak uydurmak bir vakayı
değil bir cümleyi tatmin etmek olurdu. Yedinci bir kod gerçekten engelleyici
olursa karar § 31.6.4'te açıkça verilecek.

**Konum `display_order`, id değil** — `GET /profile` ikisini de yayımlıyor, o
yüzden uç satırları adlandırmak için geri okumuyor ve writer imzaları
değişmedi. İd'ye çözmek gerekseydi iki writer'ın da dönüş tipi değişecekti.

Frontend aksiyonu: `B-067`.

