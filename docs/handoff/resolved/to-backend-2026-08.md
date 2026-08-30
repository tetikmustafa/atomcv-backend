# Kapatılmış maddeler — frontend → backend · 2026-08

> Rutin okunmaz. "Şu karar ne zaman ve neden verilmişti?" sorusunda `rg` ile aranır.
>
> Buradaki metinler **backend'in kapanış notlarıdır**. Maddelerin frontend
> tarafından yazılmış özgün hâli `git show 591d467:docs/handoff/to-backend.md`
> içinde — kanal senkronu karşı tarafın dosyasını olduğu gibi kopyaladığı için
> özgün metin `to-backend.md`'de kalmadı.

---

### F-008 · Uygunluk raporu — indi
Faz F artık raporu hesaplıyor (`spec/06-pipeline-d-g.md` § 23.3) ve üç yerden
okunuyor:

```
GET /generations/{id}   tam rapor + pageCount + status + createdAt
completed olayı         matchLevel   (yalnız seviye — başlık bir tur beklemesin)
GET /jobs/{id}          pageCount    (yoklamaya geri düşen istemci için)
```

**Uydurulmuş yüzde yok** ve olmayacak: § 23.3 onu adıyla yasaklıyor, şema
testi de `level`'ı dört değerlik kapalı bir sözlük olarak sabitliyor.

Bilmeniz gereken iki davranış: (1) **rapor sayfaya çıkanla ölçülüyor**,
sıralananla değil — belgede yer bulamamış bir beceri kapsanmış sayılmıyor;
(2) **genel modda `fitReport` alanı hiç gelmiyor**, ilan yoksa her sayı sıfır
olurdu. `missingRequired` ilanın kendi sözcüklerini taşıyor, eşleştirme
İngilizce anahtar üzerinden. **Aksiyonunuz var — `B-041`.**

### F-009 · Düz gövde ve `generalMode` — kapandı, ve `generalMode` hiç var olmamıştı
§ 35.3'ün örneği düzeltildi: gövde **düz**, `directives`/`options` yok.

İkinci sorunuzun cevabı, sorduğunuz için bulundu: **`generalMode` diye bir alan
yazılmadı.** `GenerationRequest` üzerindeki `isGeneralMode()` türetilmiş bir
metot, ama bir record'da `isX()` Jackson ve springdoc için bir getter — şemaya
bir boolean olarak sızmış. Sizin de tahmin ettiğiniz gibi gereksizdi ve düştü
(`@JsonIgnore`); genel modu isteyen tek şey `jobDescription`'ın yokluğu.

Bu, Aşama 2'de `RichContent`'te yediğimiz hatanın telin öbür yüzündeki hâli:
*Jackson'ın dokunduğu bir record'daki her getter şeklindeki metot, birinin
bulacağı bir alandır.* Şema testi artık `GenerationRequest`'in **tam dört**
özelliği olduğunu sabitliyor. **Aksiyonunuz var — `B-040`.**

### F-010 · Anlık durumdaki boş dizeler — kapandı
`phase`, `label` ve `detail` boşken **gönderilmiyor**; `pct` sıfırken de
gönderiliyor, çünkü yüzdesiz bir çubuk başlangıçtaki çubukla aynı şey değil.
`GET /jobs/{id}` zaten böyle davranıyordu — akış ile yoklama artık aynı şeyi
söylüyor, ve tek bir shape serialize edildiği için ayrışamazlar. § 30.6'ya
yazıldı. **Aksiyonunuz var — `B-040`.**

### F-011 · Dev proxy'nin SSE'yi gzip'lemesi — yazıldı
Ölçümünüz § 30.6'ya, `proxy_buffering off` satırının yanına girdi: "araya giren
her şey tamponlar", nginx **ve** Next'in dev rewrite'ı. Rakamlarınız da orada.
Doğru yere işaret ettiniz — bir daha "SSE akmıyor" denildiğinde aranacak ikinci
yer artık orası.

### F-012 · `used > limit` — karar verildi, iki alan oldu
Sayaç **denemeleri** sayıyor ve bu kasıtlı: reddedilen istek birimini geri
almıyor, yoksa sınırını aşmış bir kullanıcı sayaç tavanda sabitken ucu döverdi.
Yani sayı yanlış değil, **adı** yanlıştı.

Tercihinize uyduk — ikisini aynı alanda toplamıyoruz:

```
used       harcanan, asla limit'ten büyük değil  →  "20 of 20" basılabilir
attempted  birim alan her istek, reddedilenler dahil (26)
remaining  limit - used, asla negatif değil
```

Kırpma tek bir fabrikada ve bir invariant onu orada tutuyor: `used > limit`
taşıyan bir `Usage` inşa edilemiyor. **Aksiyonunuz var — `B-040`.**

### F-002 · Ters tarih aralığı — kapandı
`endDate >= startDate` artık `EntryService`'te, hem `POST /profile/entries` hem
`PATCH /profile/entries/{id}` için. İhlal **400 `VALIDATION_FAILED`** +
`params.fields: ["endDate"]` — istediğiniz şekil.

`PATCH` karşılaştırmayı **yamanın sonucu** üzerinde yapıyor, gövdesi üzerinde değil:
`{"endDate": "2020-01-01"}` saklı `startDate`'e karşı, `{"startDate": "2026-01-01"}`
saklı `endDate`'e karşı ölçülüyor. İkisi de reddediliyor. `{"endDate": null}` hâlâ
geçiyor — "sürüyor"da karşılaştıracak ikinci tarih yok — ve eşit tarihler geçerli
(tek günlük sertifika/hackathon gerçek bir entry, `>` değil `>=`).

Kural `spec/08-api.md` § 35.2'ye yazıldı, yani sözleşmenin parçası.
**Sizde bir şey değişmiyor:** istemci kontrolünüz kalsın, artık tek savunma değil.
Üç entegrasyon testi: yaratmada ters aralık, yamayla iki uçtan ters çevirme, aynı gün.

### F-001 · Demote'ta sürüm artışı — kapandı, davranış değişti
Sürüm artıyor tarafını seçtik. Diğer seçenek `spec`'e "iyimser kilit bu satırda
çalışmıyor" istisnası yazmaktı; tarif ettiğiniz kayıp-yazma penceresi gerçekti.

Kök neden bulduğunuz yerdeydi: demote tek satırlık bir toplu JPQL `update` ve toplu
update `@Version`'ın yanından geçiyor. `update versioned` oldu, ayrıca yalnız
**gerçekten birincil olan satırı** hedefliyor.

```
başlangıç   tr primary=true  v=0   |  en primary=false v=2
{"primary":true} → en          tr primary=false v=1   |  en primary=true  v=3
```

**Aksiyonunuz var — `B-034` olarak açıldı.** Kendi cümlenizle: yerel demote'un
sürümü de artmalı, yoksa `usePatchVariant` sunucuyla artık hizalı değil.

Atomun promote'a karışmayan diğer sözcüklemeleri **sürümlenmiyor**; etag'leri
geçerli kalıyor. Bunu ayrıca test ettik, çünkü "hepsini artır" düzeltmesi promote'u
tamamen kırıyor. Kural `spec/08-api.md` § 35.6'da.

---

## Frontend tarafında ne yapıldı

**F-002** — istemci kontrolü (`lib/forms/profileSchemas.ts`) **kaldı**; artık tek
savunma değil ama round trip'ten önce ve alanın yanında cevap veren taraf o. Mock
handler'ı da reddedecek şekilde güncellendi, yoksa dev/test sunucudan sapardı.
Doğrulandı: create `400` + `endDate`, eşit tarih `201`, patch iki yönden de
saklanan yarıya göre, ileri aralık `200`.

**F-001** — `B-034` olarak geri geldi ve uygulandı; ayrıntı `to-frontend.md` ·
`B-034` altında.

---

### F-003 · Yazma yanıtındaki `completeness` — kapandı
Ölçümünüz birebir doğruydu ve sebebi tam olarak tarif ettiğiniz yerdeydi:
`ProfileService.replace()` rakamı hiç hesaplamıyordu, yalnız `readOwn()`
hesaplıyor. `PUT /profile` ve `PUT /profile/preferences` artık kaydetmeden
önce yeniden hesaplıyor, yani yanıt **yazmadan sonrasını** taşıyor.

Kural `spec/08-api.md` § 35.6'da: **`completeness` taşıyan bir yanıt güncel bir
değer taşır** — kolonun her yazmadan sonra güncel olduğu değil; bölüm/entry/atom
uçları başı döndürmüyor ve rakamı bir sonraki okumaya bırakıyor.
**Aksiyonunuz:** `PUT` sonrası yeniden okuma kaldırılabilir.

Bir not, çünkü sizde de aynı şekilde saklanır: "değişim olmayan iki tur uyuşuyor"
dediğiniz maskeleme testte de çıktı. Tercihleri ölçen testimiz düzeltmesiz de
geçti — etag'i almak için yaptığı `GET` saklı rakamı tazeliyor, yani iki yazma
arasındaki her okuma bayatlığı onarıyor. ETag'i önceki yazmanın **yanıtından**
alınca düştü.

### F-004 · Omitted alanların tekdüze temizlenmesi — kapandı, davranış değişti
İki seçeneğinizden "temizlensin" tarafını seçtik ama uygulaması farklı oldu:
`source_language` kolonu `NOT NULL DEFAULT 'en'`, yani temizlenecek bir değer
yok ve `DEFAULT`'a düşürmek Türkçe yazılmış bir profili herhangi bir baş
düzenlemesinde sessizce İngilizceye çevirirdi. Alan **gövdede zorunlu** oldu.
Artık başın hiçbir alanı merge edilmiyor; `preferences` haklı olarak
beklediğiniz gibi kendi ucunda kalıyor.

**Aksiyonunuz var — `B-035`.** Şema değişti, `gen:api` sonrası tip de.

### F-005 · Entry `PATCH`'te `params.fields` — kapandı
Kural artık isteğin gönderdiği ucu adlandırıyor; tam tablo `spec/08-api.md`
§ 35.2'de. Kontrol yine **yamanın sonucu** üzerinde, çünkü aralığı bozan tek
uç da olabilir — değişen yalnız hangi alanın raporlandığı.

Yanına, sormadığınız ama sizi ilgilendiren bir davranış: hiçbir tarihe
dokunmayan bir `PATCH` artık hiç denetlenmiyor. Aksi hâlde F-002'den önce
ters kaydedilmiş bir satır, ilgisiz bir başlık düzenlemesini düzeltilecek
alanı adlandıramadan reddederdi.

**Aksiyonunuz var — `B-036`**, create'in iki alan birden döndürmesiyle birlikte.

### F-006 · Birincil sözcükleme kuralı — kapandı, ve ölçümünüz eksikti
Kural `spec/08-api.md` § 35.2'ye yazıldı. Sorduğunuz ayrımın cevabı: **iki ayrı
kural**, ve ikisi zaten farklı `params.fields` döndürüyor.

```
son sözcükleme            400 fields: ["variantId"]   → atomu sil
birincil, başkası var     400 fields: ["primary"]     → önce başkasını birincil yap
```

İkincisini `variantId` ölçmüşsünüz; gerçek uçta `primary` dönüyor ve bunun
Aşama 1'den beri entegrasyon testi var. Muhtemelen mock'unuzdan ölçüldü.
Bizim tarafta eksik olan şuydu: **birinci durumun testi yalnız 400'ü kontrol
ediyordu**, yani ayrımın kendisi test edilmemişti — artık ikisi de sabit.
Sözcükleme silme kontrolünü çizerken ayırmanız gereken şey tam olarak bu.

### F-007 · Kota gün dönümü — karar verildi
**Gün sınırı UTC**; Türkiye'de sayaç 03:00'te döner. `usage_counters.period`
zaten saat dilimsiz bir `DATE` ve UTC onu tek anlamlı kılan okuma: sunucunun
dilimi değişse de aynı satır aynı günü gösterir, yaz saati sınırı yok. Gömülü
bir `Europe/Istanbul` o dilimin dışına ilk çıkan kullanıcıda sessizce yanlış
olurdu; istemcinin bildirdiği dilim ise kota kaçırmak için ayarlanabilirdi.

**Tercihiniz kabul:** `resetsAt` telde her zaman offset taşıyan bir ISO-8601
**anı** (`2026-08-22T00:00:00Z`), yalnız saat değil — `capabilities.quotaResetsAt`
ve `QUOTA_EXCEEDED` / `PROFILE_QUOTA_EXCEEDED` `params`'ı için de aynı. Metni
kullanıcının yerelinde yazacak taraf sizsiniz; `Retry-After` yanında saniye
cinsinden kalıyor, istemci saati yanlışsa doğru olan tek değer o.

Karar `spec/08b-api-contract.md` EK D.6.5'te, `period` kolonunun yorumu
`spec/04-data-model.md`'de; `STATUS.md`'nin açık kararlar tablosundan düştü.
**Henüz kod yok** — `resetsAt` gönderen uç Aşama 2, Adım 2.7 ile geliyor.

---

### F-016 · Tek kodun arkasındaki sekiz sebep — kapandı
İkinci seçeneğiniz, ama **dörde değil sekize**. Şikâyetiniz § 18.4'ün kapısı
üzerineydi; ön kontrol de dört verdict'ini aynı koda düşürüyor ve `(0, 0)`
gönderiyordu, yani "hiç yetkinlik çıkmadı" cümlesi kazara doğruydu. Yalnız
bildirdiğiniz yarıyı düzeltmek aynı maddeyi ikinci kez açtırırdı.

`params.reason` sekiz değerli kapalı bir sözlük ve hangi kapının reddettiğini
söylüyor. `confidence` ile `skillsFound` gitmeye devam ediyor — katalog onları
bildiriyor — ama cümle artık önce `reason`'dan seçilir.

Birinci seçeneğinizi almadık, ama **asıl gördüğünüz şeyi** aldık:
`suspicious_output` `retry` alıyor. Onbirinci bir hata kodu açmadan, çünkü API
açısından sonuç aynı — değişen, kullanıcıya söylenen şey.

Aramadığınız bir şey de çıktı: **`continue_anyway` kapı reddinde `retry` ile
birebir aynı işi yapıyordu.** Onay yalnız ön kontrolü atlıyor, ön kontrol zaten
geçilmişti. Kaldırdık. **Aksiyonunuz var — `B-043`.**

### F-013 · Tek CV iki dil taşıyor — kapandı, üçüncü bir yolla
İkisinden birini değil, ortasını seçtik: **bir belge tek dilde yazılır ve o dil
profilin taşıdığından seçilir.** `auto`, ilanın diline yalnızca profil o dilde
gerçekten yazılabiliyorsa çözülüyor — sayfaya çıkabilecek her atomun hedef
dilde varyantı varsa. Yoksa `sourceLanguage`'de kalıyor, ve tarih ile "Halen"
tek bir `contentLanguage` okuduğu için ayrışamıyorlar.

2. seçeneğiniz § 21.8'in **çalışan** yarısını kapatırdı (tüm atomları çevrilmiş
bir profil bugün gerçek bir İngilizce CV alıyor, maliyeti sıfır); 1. seçeneğiniz
tarihi düzeltir, atom atom geri düşen gövdeyi düzeltmezdi.

İstediğiniz sinyal telde: `contentLanguage` ve `postingLanguage`.
**Aksiyonunuz var — `B-042`.**

### F-014 · Sessiz sağlayıcı hataları — kapandı
Adaptörden çıkışın **tek** yolu var ve WARN'ı orada basıyor: `promptRef`,
`kind`, `detail`. Dört yolun dördü de kapsandı, ve iki mükerrer satır düştü —
bir başarısızlık artık tam olarak bir satır. Gövde ve prompt asla
(mutlak kural 4); teşhisi zincirin yan etkilerinden çıkarmanız gerekmeyecek.
§ 27.2'ye yazıldı.

### F-015 · Fiyat tablosundaki ölü model — kapandı
Haklıydınız, ve alıntıladığınız cümle sonucu tam olarak söylüyordu. Tablo artık
kullanılan modeli kapsıyor; **ücretsiz model açıkça sıfır** yazılıyor, çünkü
rakam aynı olsa da iddia değil — biri "sağlayıcı ücret almıyor" der, öteki
"bilmiyoruz". Asıl eklenen `LlmPricingAudit`: `ApplicationReadyEvent`'te
tabloyu `atomcv.llm.models` ile karşılaştırıyor ve fiyatı olmayan her modeli
adıyla WARN'lıyor. § 27.4'e yazıldı.

---

## Aşama 3 · dilim 9-12'nin cevapları — arşivlendi 2026-08-29

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
