# → Backend

> **Kanal kuralları**
> - Frontend yazar, backend okur ve `OPEN` → `ACK` taşır.
> - Her madde bir ID taşır (`F-nnn`), numaralar tekrar kullanılmaz.
> - **Dosya 100 satırı geçerse arşivleme gecikmiştir.**
> - Bir spec değişikliği gerekiyorsa burada iste — `spec/`'i frontend reposunda düzenleme,
>   bir sonraki senkronda kaybolur.

---

## OPEN

> **Beş maddenin ikisi indi** (`F-017`, `F-021` — `ACK`'te), üçü açık.
> Kalanların aciliyet sırası: **`F-018`** § 31.6'nın yarısını bloke ediyor,
> **`F-020`** yayımlanmış bir yeteneği karşılıksız bırakıyor, **`F-019`** iki
> küçük düzeltme.

### F-020 · `canSaveHistory` var, geçmişi okuyacak uç yok
**Since:** frontend, Aşama 3 dilim 7 · **Spec:** `spec/08-api.md` § 35.7

**Neden:** `capabilities.canSaveHistory` yayımlanmış bir yetenek ve hesapta
`true` — yani ürün kullanıcıya üretimlerinin saklandığını söylüyor. Ama
`GET /api/v1/generations` yok: tekil `GET /generations/{id}` var, liste yok,
sayı yok. Kullanıcı kendi geçmişine hiçbir yoldan bakamıyor, ve biz de
yeteneği doğrulayan bir ekran çizemiyoruz.

**Somut olarak nerede ısırdı:** hesap silme onayı (`B-057`) "neyin gittiğini
saymalı" diyor. Bölüm ve madde sayısını profilden alıyoruz; **üretim sayısını
veremiyoruz** ve tahmin de etmiyoruz — geri alınamayan tek yerde yanlış bir
sayı, hiç sayı olmamasından kötü. Bugün cümle onları saymadan adlandırıyor.

**İstenen:** `GET /api/v1/generations` (sayfalı olabilir), ya da en azından
hesapta bir toplam. İlki `canSaveHistory`'yi anlamlı kılar; ikincisi yalnız
silme ekranını doğrular.

### F-019 · Geri bildirim: `rating` metin geliyor, ve geri okunamıyor
**Since:** frontend, Aşama 3 dilim 7a · **Spec:** `spec/11-operations.md` § 48.4

**İki şey, aynı uç.**

**1. `FeedbackRequest.rating` üretilen tipte `"1" | "-1"` — metin.** Aynı
şemada `format: int32` yazıyor, açıklama "1 for good, -1 for bad" diyor, ve
`FeedbackResponse.rating` `number` olarak dönüyor. openapi-typescript bir
tam sayı enum'unu ancak değerler şemada tırnaklıysa böyle basar. Biz **sayı**
gönderiyoruz ve tipi `Omit` ile daraltıyoruz; bugün bir şey bozulmuyor, ama
daraltma tam olarak "şemayı düzeltmeyi bekleyen kod" ve öyle işaretli.
**İstenen:** `enum` değerleri tırnaksız olsun — `[1, -1]`.

**2. Verilmiş bir yargı geri okunamıyor.** `GET /generations/{id}` geri
bildirimi taşımıyor ve başka bir uç da vermiyor. Maddeniz "geri bildirimini
gönderdin yerine **mevcut seçimi** göstermek doğru davranış" diyor — bunu
yalnız oturum boyunca yapabiliyoruz; sayfa yenilenince ekran hangi başparmağın
basıldığını bilmiyor ve boş başlıyor. Aynı şey `contentGrant` için de geçerli,
ve orası daha önemli: **`accessedAt` gösterilmeli** diyorsunuz, ama izni
verdikten bir gün sonra dönen kullanıcı ona bakamıyor.
**İstenen:** `GET /generations/{id}` gövdesinde `feedback` (rating, category,
`contentGrant`) — yorum hariç, o zaten geri yollanmıyor.

### F-018 · İçe aktarma işinin sonucu yalnız akışta var, ve uyarılar sayılabiliyor ama gösterilemiyor
**Since:** frontend, Aşama 3 dilim 3a · **Spec:** `spec/07-subsystems.md` § 31.6, `spec/08-api.md`

**İki ayrı şey, ikisi de aynı yerden çıkıyor: `JobStatusResponse`.**

**1. Terminal olayın alanları şemada yok.** `B-051` içe aktarma işinin
`profileId`, `sectionCount`, `atomCount`, `warningCount` ve `detectedLanguage`
taşıdığını söylüyor; `JobStatusResponse` ise yalnız `generationId` ve
`pageCount` yayımlıyor. Yani **`GET /jobs/{id}` bir içe aktarma işinin sonucunu
hiç söyleyemiyor** — sayfa yenilenirse sonuç yok. Bu `pageCount`'ın `B-041`
öncesi hâlinin aynısı ve çözümü de aynı olabilir: alanları status yanıtına da
koymak, ya da `JobStatusResponse`'a iş tipine göre dolan bir `result` nesnesi
eklemek. **Bugün SSE yükünü tipsiz taşıyoruz** ve `contracts.ts`'te elle bir
tip duruyor — `gen:api` onu kaldıramıyor, çünkü karşılığı yayımlanmamış.

**2. Ve asıl engelleyen bu: § 31.6'nın iki tasarım kuralı uygulanamıyor.**
Bölüm "sorunlu olanlar otomatik açık" ve "kritik uyarılar çözülmeden Onayla
aktif olmaz" diyor. İkisi de **hangi** bölümün sorunlu olduğunu bilmeyi
gerektiriyor; telde yalnız bir **sayı** var. § 31.4.1 zaten "bu yapı hiçbir
zaman frontend'e çıkmıyor" diyor — yani `warnings[]` bilinçli olarak
gizleniyor, ama o zaman § 31.6'nın iki kuralı yazıldıkları hâliyle
uygulanamaz.

**İstenen:** ya uyarıların **yeri** yayımlansın (en az `path` ya da bir
`sectionId`, ve kritik olup olmadığı), ya da § 31.6 bu iki kuralı
sayı-tabanlı bir nota indirsin. İkincisi de kabul edilebilir bir cevap —
bugün yaptığımız şey o: bölümler kapalı, "şu kadar konuda emin olamadık"
notu, ve Onayla hep aktif.

**Bir de küçük bir soru:** içe aktarma işi `phase`/`label` gönderiyor mu?
Gönderiyorsa anahtarlar ne? Kataloğumuzda `generation.phase.*` var; içe
aktarma için bir şey uydurmadık, mock yalnız `pct` gönderiyor ve ekran kendi
cümlesini yazıyor. Anahtar gönderiyorsanız çeviriyi yazalım.

### F-001 · Kısa başlık
**Since:** frontend commit <sha> · Adım <n>
**Neden:** <sorunun ne olduğu>
**İstenen:** <backend'den beklenen somut şey>
**Spec:** <ilgili dosya ve bölüm, varsa>
-->

---

## ACK — backend tamamladı, frontend arşivleyebilir

### F-021 · Üç sorunun üçü de cevaplandı, ve ikisinde tahmininiz yanlıştı
**1. `Retry-After` eksik değildi — hep gidiyordu.** `ProblemDetailAdvice` onu
`params.resetsAt`'i bir `Instant` olarak taşıyan **her** 429'dan türetiyor ve
bu uç da öteki ikisiyle aynı advice'tan geçiyor. Eksik olan, başlığı telde
gördüğünü söyleyen bir test ve onu yayımlayan bir `@ApiResponse`'tu — ve
ikisinin yokluğu başlığın yokluğundan ayırt edilemez. İkisi de indi.
**Aksiyonunuz var — `B-062`.**

**2. `{"userEdited": true}` şeklini doğru tahmin ettiniz** — ama o şekli
dönmüyordu. Constructor `IllegalArgumentException` atıyor, advice'ın o istisna
için işleyicisi yok, ve istek **`500`** oluyordu. Artık gerçekten
`400 VALIDATION_FAILED` + `fields: ["userEdited"]`. Mock'unuz gerçeğinden
doğruydu; sormasaydınız kusur duracaktı. **`B-064`.**

**3. `selection_state` telde yok, ve şimdilik yayımlamıyoruz.** Doğru
gördünüz: `GenerationResponse` onu taşımıyor, başka bir uç da vermiyor,
`rejected` ile `headerOnlyEntries` yalnız `generations` satırında duruyor.
Tüketicisi olmayan bir yapıyı yayımlamak, ilk ekran çizildiğinde yanlış şekli
olmuş bir sözleşmeyi sürdürmek olurdu. **Görünümü kurmaya karar verirseniz bir
`F-nnn` açın** — atom id'si, sebebi ve `headerOnlyEntries` ile birlikte
gelir; kaydı `notes/current.md`'de duruyor.

### F-017 · Haklıydınız, ve tabloda bir değil beş satır eksikti
Bildirdiğiniz `COVER_LETTER_REJECTED` girdi. Gerekçeniz — "tablosuz bir kod
mesajı yanlış argümanla yazılsa da testten geçer" — asıl işi yapan şey oldu:
tabloyu `ErrorCode`'a karşı okuyan bir muhafız yazdık ve **dört tane daha**
düştü. `GENERATION_PAUSED` yalnız `10-security.md`'de tarif ediliyordu;
`METHOD_NOT_ALLOWED`, `NOT_ACCEPTABLE` ve `UNSUPPORTED_MEDIA_TYPE` ise
D.6.8'de "katalogda" yazıyordu ama EK D.6'ya hiç geçmemişti — ve testinizin
okuduğu tablo o.

**`issues` kapalı:** `CoverLetterIssue` bir enum, tam altı değer, saydığınız
altının aynısı. ICU'da adlandırabilirsiniz.

Tablo artık `ErrorCatalogueSpecTest`'in okuduğu şey — kod, durum, parametre
adları, tipleri ve sıraları, iki yönde de. **Aksiyonunuz var — `B-063`.**

*(`F-001`…`F-016` `resolved/to-backend-2026-08.md`'de)*
