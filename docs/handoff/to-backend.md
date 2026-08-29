# → Backend

> **Kanal kuralları**
> - Frontend yazar, backend okur ve `OPEN` → `ACK` taşır.
> - Her madde bir ID taşır (`F-nnn`), numaralar tekrar kullanılmaz.
> - **Dosya 100 satırı geçerse arşivleme gecikmiştir.**
> - Bir spec değişikliği gerekiyorsa burada iste — `spec/`'i frontend reposunda düzenleme,
>   bir sonraki senkronda kaybolur.

---

## OPEN

*(şu an açık madde yok — `F-017`-`F-021`'in beşi de `ACK`'te)*

### F-001 · Kısa başlık
**Since:** frontend commit <sha> · Adım <n>
**Neden:** <sorunun ne olduğu>
**İstenen:** <backend'den beklenen somut şey>
**Spec:** <ilgili dosya ve bölüm, varsa>
-->

---

## ACK — backend tamamladı, frontend arşivleyebilir

### F-018 · İkisi de indi, ve biri "yayımla" değil "önce düzelt" çıktı
**1. Terminal olayın alanları `GET /jobs/{id}`'de.** `profileId`,
`sectionCount`, `atomCount`, `warningCount`, `detectedLanguage`, ve
`warnings[]`. Teşhisiniz doğruydu ve şu haliyle de doğru: bu, `pageCount`'ın
`B-041` öncesi hâlinin **aynısı** — sonucu yalnız akış taşıyordu.

**2. Uyarıların yeri yayımlanıyor. Ama önce yanlıştı.** İstediğiniz şeyi
yayımlamaya kalkınca `path`'in iki ayrı hatası çıktı: **bölüm indeksi hiç
yoktu** (her bölüm sıfırdan başlıyor, Eğitim'in ilk satırıyla Deneyim'in ilk
satırı aynı yer diye kaydediliyordu) ve taşıdığı indeks **sıralama
öncesiydi** — `newestFirst` bir satır sonra entry'leri yeniden diziyor. Yani
tek bölümlü bir CV'de bile yanlış satırı gösterebiliyordu. Sormasaydınız
kusur, üstüne bir ekran kurulana kadar duracaktı.

**Konum id değil `display_order`** — ve bu bilinçli: `sectionOrder` ile
`entryOrder`, `GET /profile`'ın ikisinde de yayımladığı `displayOrder`. Yani
uyarıyı **elinizdeki** profile karşı çözüyorsunuz, bu uç satırları adlandırmak
için geri okumuyor. `detail` gitmiyor: operatör İngilizcesi, çevrilemez, ve
mesajı kurduğunuz şey `code`.

**3. "Kritik uyarı" kuralını kaldırdık, uygulamadık.** İkinci seçeneğinizi de
tam olarak almadık — kural sayı-tabanlı bir nota inmedi, **silindi.** Sebebi:
kapalı sözlükteki altı kodun altısı da ekranda düzeltilebilir bir alanı tarif
ediyor, ve kodun kendi belgesi *"bir uyarı bir ret değildir; düzeltilemeyen
şey çıkarımı bitirir"* diyor. Yani engelleyici bir uyarı sınıfı **hiç var
olmadı** ve kural yazıldığı gün de boştu. Bir `critical` bayrağı uydurmak, bir
vakayı değil bir cümleyi tatmin etmek olurdu. **Onayla hep aktif — bugün
yaptığınız şey doğruydu.**

**4. Küçük sorunuz: hayır, içe aktarma `phase`/`label` göndermiyor.** Yalnız
`pct`. Ekranın kendi cümlesini yazması doğru davranış; uydurulmuş bir anahtar
kümesi, çevrilecek ama hiç değişmeyecek altı satır olurdu. Çeviri yazmayın.

Kalıcı kararlar `spec/07-subsystems.md` § 31.6.4'te. **Aksiyonunuz var —
`B-067`.**

### F-020 · `GET /api/v1/generations` indi, ve `total` ile birlikte
İkisini birden aldınız çünkü ikisi de gerekiyordu: liste
`canSaveHistory`'yi anlamlı kılıyor, `total` da silme ekranını doğruluyor.
**İkinciyi yürüyerek elde edilebilecek bir sayı sanmayın** — sayfaları
yürüyerek sayılan bir sayı, yürüyüş bitene kadar başka bir sayı olurdu; bu bir
`count`. `GET /generations?limit=1` yeter.

Uç kaynak haritasında (§ 35.3) baştan beri varmış ve hiç yazılmamış — yani
"yayımlanmış yeteneği karşılıksız bırakıyor" teşhisiniz tam olarak doğruydu.

Sayfalama EK D.8.7'nin şekli: `{ items, nextCursor, total }`, cursor'lı.
Cursor **sıralama anahtarının tamamını** taşıyor (`created_at` + `id`), çünkü
aynı zaman damgasını paylaşan satırlar id'ye göre sıralı ve yalnız zamanı
taşıyan bir cursor eşit grubun kalanını ya atlar ya iki kez verir. Testte var.

**Bir şeyi kasten yapmadık ve sizden cevap bekliyor: satırda başlık yok.**
Bugün bir satır "1 sayfa · tarih · strong" diyor ve okunması güç. Etiket
ilandan okunur, ilan ise mutlak kural 4 gereği geri dönmüyor; buraya
`role.title` koymak o kuralın sınırını kazara çizmek olurdu.
**Aksiyonunuz var — `B-066`, ve içinde bir soru var.**

### F-019 · İkisi de indi, ve ikincisi asıl olanmış
**1. `rating` artık `enum: [1, -1]`.** Teşhisiniz tam isabetti: swagger'ın
`allowableValues`'ı bir `String[]` ve özelliğin tipi ne olursa olsun tırnaklı
basıyor. `rating` artık yanındaki `Category` ile aynı kalıp — kapalı bir enum,
tel biçimi bir `@JsonValue`, farkı tel biçiminin sayı olması. Gönderdiğiniz
şey değişmiyor; `Omit` daraltması silinebilir. Elle yazılmış aralık kontrolü
de kalktı, cevabı değişmeden.

**2. `GET /generations/{id}` `feedback` taşıyor.** İstediğiniz alanlar, yorum
hariç. Yargı verilmemişse alan hiç yok.

**`contentGrant`'in daha önemli olduğunu söylemiştiniz ve öyleymiş.** Dilim
2'nin § 48.4 kaydı "kontrol edilemeyen bir onay kutudan ibarettir" diyordu, ve
grant'i **yalnız yazma yanıtında** döndürüyordu — yani o cümle yalnız oturum
boyunca doğruydu. Pencere kırk sekiz saat, yani `accessedAt`'e bakması gereken
kişi zaten ertesi gün dönen kişi. Bu bir eksik değil, § 48.4'ün kendi
iddiasının yarısıydı; `spec/11-operations.md` § 48.4.2'ye **Düzeltme** olarak
yazıldı.

Alan `FeedbackResponse`'un kendisi — POST'tan döneni doğrudan aynı slota
koyabilirsiniz. **Aksiyonunuz var — `B-065`.**

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
