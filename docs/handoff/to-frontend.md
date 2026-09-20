# → Frontend

> **Kanal kuralları**
>
> - Backend yazar, frontend okur ve `OPEN` → `ACK` taşır.
> - Her madde bir ID taşır (`B-nnn`); numaralar tekrar kullanılmaz.
> - **Dosya 100 satırı geçerse arşivleme gecikmiştir.** `ACK` maddeleri `resolved/`'a taşınır.
> - API _şekli_ için otorite OpenAPI şemasıdır. Burası **neden değişti + ne yapman lazım** taşır.
> - Kalıcı kural niteliğindeki maddeler `spec/`'e işlenir ve buradan silinir.

---

## OPEN

**Hepsi denetimlerden (2026-09-15'ten 2026-09-20'ye, altı tur):** spec baştan
sona kodla karşılaştırıldı; "dokümanda var, kodda yok" olan her şey ya yazıldı
ya sapma olarak kaydedildi. **Önce `npm run gen:api` koş** — ilk turda altı uç
ve üç şema, ikincide `SelectionLine` (`B-108`), beşincide iki sözlük
(`B-112`), altıncıda iki sözlük daha (`B-114`, `B-116`).

> **Dosya 100 satırı geçti ve bu bir arşivleme değil koordinasyon meselesi**
> (kanal kuralı): `B-100`-`B-116`'nın hiçbiri `ACK` almadı, yani taşınabilecek
> madde yok. On yedisi de denetimlerden; okunup ACK'lendiklerinde hepsi birden
> `resolved/`'a iner.

### B-100 · CSP Turnstile'ı blokluyordu, düzeldi
**Since:** `df742fa` · `docker/nginx/nginx.conf` · § 11.2
**Neden:** politika `default-src 'self'` idi, hiçbir host adlandırmıyordu.
Turnstile bu origin'in yüklediği bir script ve gömdüğü bir iframe; ikisi de
sessizce bloklanırdı — widget çizilmez, token üretilmez, `B-050`/`B-083`'ün üç
ucu `CHALLENGE_FAILED` cevaplardı. İki reponun testi yeşilken.
**Aksiyon:** kod işi yok; dağıtım gününde widget'ın çizildiğini görün.

### B-101 · `openapi.json` repo kökünde ve commit'li
**Since:** `f849d4a` · § 47.1, § 35.8
**Neden:** `contract-check` `.../build/openapi.json` çekiyor; `build/` üretilen
ve gitignore'lu, yani o URL hep 404 verdi ve iş hep "skipping" dalına gitti —
ayrışmaya karşı tek muhafız kendini atlıyordu.
**Aksiyon:** URL'i `.../main/openapi.json` yapın. Dosya her PR'da
`OpenApiDocumentIT` ile yayımlanan şemaya karşı doğrulanıyor.

### B-102 · Arşivleme ucu indi, `archived` iki okuma şeklinde de var
**Since:** `2b4efd4` · § 35.2, § 13
**Neden:** `POST /generations/{id}/archive` kaynak haritasında baştan beri
vardı, `generations.archived` kolonu V1'den beri vardı, ve ikisi hiç
buluşmamıştı.
**Aksiyon:** İşareti `GenerationResponse.archived` ve
`GenerationSummary.archived`'dan okuyun; geçmiş listesi bu işaretin okunduğu
ekran. Aynı uç `{"archived": false}` ile işareti kaldırıyor, boş gövde
arşivliyor. **Yeni ICU anahtarı:** `AccountFeature` beşinci değerini aldı —
`archive`, `canSaveHistory`'ye eşleniyor (`feedback` gibi).
**Not:** işaret bugün saklama süresini değiştirmiyor; nesne deposu inince
§ 13'ün kuralını o okuyacak (§ 57.4'ün açık paragrafı).

### B-103 · Atom etiketleri yazılabiliyor — ve bugüne kadar hiç yazılmıyordu
**Since:** `3470471` · § 35.2, § 13, § 19.1
**Neden:** `tags` ve `atom_tags` tablolarına **hiçbir şey** yazmıyordu; içe
aktarım modelin bulduğu etiketleri normalize edip düşürüyordu. Yani Faz B'nin
etiket örtüşmesi — ham skorun **dörtte biri** — her atom için her ilana karşı
yapısal olarak sıfırdı.
**Aksiyon:** `AtomResponse` artık `tags[]` taşıyor (`id`, `label`, `source`).
`POST /profile/atoms/{id}/tags` ekliyor, `DELETE .../tags/{tagId}` kaldırıyor;
`If-Match` istemiyor (etiket kendi satırı). `source` `auto` ise çıkarımın
tahmini, `user` ise kişinin kararı — ikisi farklı çizilmeli. § 55'in
"Etiket / önem / kilit" editörünün eksik yarısı buydu.

### B-104 · `POST /generations` `emphasize` alıyor
**Since:** `491b5f9` · § 18.7
**Neden:** § 18.7'nin yönlendirme nesnesinin dört alanından ikisi kodda yoktu.
**Aksiyon:** İsteğe bağlı `emphasize: string[]` (en çok on terim, her biri en
çok 60 karakter). İlanın kendi keyword ve etiketlerine katılıyor — § 19.1'in
formülü değişmedi, aynı dört ağırlık daha büyük bir küme okuyor. Ekranda
"ilanın söylemediği ama işin bununla ilgili olduğu terimler" diye sunulmalı.
**`freeformNote` gelmedi** ve bilerek: § 18.7 alanı adlandırıyor, hiçbir bölüm
onu kimin okuduğunu söylemiyor, ve tek makul okuyucu Faz D'nin prompt'u —
o da yeni bir prompt sürümü ve EK C.3'ün eval koşusu demek.

### B-105 · İndirmeye `html` ve `source` eklendi
**Since:** `8c00397` · § 35.2, § 22.6, § 55
**Neden:** `format=source` kaynak haritasında ilk taslaktan beri vardı ve
`VALIDATION_FAILED` dönüyordu; HTML renderer'ın paketi boştu.
**Aksiyon:** `?format=html` tek dosyalık, hiçbir şey çekmeyen bir HTML;
`?format=source` PDF'in derlendiği LaTeX. **Sayfa sınırı HTML'de hiç
geçerli değil** — DOCX'teki gibi "yaklaşık" değil, sayfa kavramı yok; düğmenin
yanındaki cümle bunu söylemeli.

### B-106 · GitHub içe aktarımı indi
**Since:** `4a49a1c` · § 31.8
**Aksiyon:** `POST /profile/github/suggestions` (yazmaz, önerir) ve
`POST /profile/github/apply` (seçilenleri yazar). **Yol `/profile/github`,
`/ingestion/github` değil** — `F-029`'un CV yüklemesi için verdiği kararın
aynısı. `username` opsiyonel; boşsa profilin iletişim bloğundaki hesap
okunuyor. Bir öneri `matchedEntryId` taşıyorsa birleştirme (beceri + bağlantı,
**cümleye dokunulmuyor**), taşımıyorsa yeni proje. Saatte beş çağrı
(`RATE_LIMITED`), hiçbir izin istemiyor, hiçbir token saklamıyor.

### B-107 · `F-013` kapandı: `auto` artık ilanı gerçekten takip ediyor
**Since:** `f518644`, `928c34d` · § 21.8, § 32.5
**Neden:** Türkçe bir profil İngilizce bir ilana Türkçe CV üretiyordu, çünkü
belgeyi tek dilde tutmanın tek yolu ilanı takip etmeyi reddetmekti. § 21.8'in
ikinci adımı indi: eksik sözcüklemeler Faz B ile Faz C arasında çevriliyor ve
kaydediliyor. **Üçüncü diller İngilizce üzerinden** (§ 32.5).
**Aksiyon — üç şey:**
1. **Bu üretim yavaş olabilir.** Profilin hedef dilde sözcüklemesi yoksa en
   çok altmış çeviri çağrısı yapılıyor; **ikinci kez ücretsiz**, çünkü
   sonuçlar profile yazılıyor. İlerleme çubuğu `SCORING` fazında beklerse
   sebebi bu.
2. **Hep ya hiç.** Çeviri tamamlanamazsa belge profilin kendi dilinde
   çıkıyor — yarısı bir dilde değil. `GenerationResponse` hangi dil olduğunu
   zaten söylüyor; ekran onu okumalı.
3. **Profil editöründe yeni sözcüklemeler belirecek.** `createdBy:
   llm_translate`, `userEdited: false`. Kullanıcının yazmadığı bu satırların
   gözden geçirilmesi öneriliyor (§ 32.5) — rozet için gereken alan zaten
   `Variant` şemasında.

### B-108 · `GET /generations/{id}/selection` artık gerekçe de taşıyor (İlke 7)
**Since:** § 35.3.1, § 1.2 · denetim 2026-09-16
**Neden:** İlke 7 her seçimin gerekçesinin gösterilmesini istiyor ve üç şey
adlandırıyor — skor, eşleşen keyword'ler, red nedeni. Üçü de hesaplanıyordu,
hiçbiri telde yoktu: `SelectionLine` yalnız `atomId`, `text`, `onPage`
taşıyordu, yani gerekçesi bildirilmemiş bir sıralama.
**Aksiyon — önce `npm run gen:api`**, `SelectionLine` iki alan kazandı:

1. **`matchedKeywords?: string[]`** — bu satırın taşıdığı ilan terimleri,
   alfabetik. **Yokken alan hiç gelmiyor**, boş dizi olarak değil: seçilmiş bir
   satırın yanındaki boş dizi "hiçbir şey eşleşmedi" diye okunur, ve genel CV
   modunda — ortada ilan yokken — bu içerik hakkında bir iddia olurdu. Çip
   olarak çizilmeye uygun; sayfaya girmeyen satırlarda **hiç gelmez** (anlık
   görüntüye yalnız seçilenler yazılıyor).
2. **`heldBackReason?: "BUDGET" | "INACTIVE" | "EXCLUDED_BY_DIRECTIVE" |
   "ENTRY_BELOW_MINIMUM"`** — sayfaya girmeyen satırlarda dolu, girenlerde
   **hiç gelmiyor**. Şemada kapalı enum, yani ICU `select`'i dördünü de
   yazabilir. **Dördü dört ayrı cümle istiyor**, ve bu maddenin asıl işi o:
   `BUDGET` sayfa sınırını uzatmaya davet eder, `INACTIVE` profil editörüne
   gönderir (atom kapalı), `EXCLUDED_BY_DIRECTIVE` *bu CV'de* yapılan
   düzenlemeyi geri almaya (profil ayarı değil — ikisini karıştıran bir ekran
   kişiye kalıcı bir kararı geri aldırır), `ENTRY_BELOW_MINIMUM` entry'nin
   bütün olarak düştüğünü söyler.

**Skor bilerek yayımlanmıyor** ve istenmesin: § 23.3'ün yüzdeye itirazı bir
madde yanındaki sayı için de geçerli. Sıra zaten sıralamayı söylüyor.

**Eski üretimler `matchedKeywords` taşımıyor** — Faz B onu bu denetimden önce
kaydetmiyordu. Alan yokluğu normaldir, boş durum ekranı gerektirmez.

### B-109 · Uç açıklamaları yeniden yazıldı — `gen:api` ikinci kez
**Since:** denetim 2026-09-16 · `openapi.json` 37 satır
**Neden:** Dökümanlar repodan çıkıyor, bu yüzden koddaki tüm `Bölüm/Adım/EK`
atıfları temizlendi. Bunların bir kısmı `@Schema`/`@Operation` metinlerinin
**içindeydi**, yani `openapi.json`'daki açıklamalar değişti.
**Aksiyon:** `npm run gen:api`. **Hiçbir alan, tip veya enum değişmedi** —
yalnız `description` metinleri. Üretilen `api.d.ts`'te tip farkı beklenmiyor;
çıkarsa bu bir kusurdur, haber ver.

Metinlerin anlamı korundu, yalnız işaretçi düştü: "Counts, never a percentage.
**Bölüm 23.3** forbids one by name" → "**One is forbidden by name**". Ekranda
bu metinleri gösteren bir yer varsa cümleler biraz kısaldı.

### B-110 · Hata kataloğu artık üretiliyor — ve sizin kopyanız ona bağlanabilir
**Since:** denetim 2026-09-16 · `error-catalogue.md`, § 08b
**Neden:** Katalog tablosu `08b-api-contract.md`'de elle yazılıyordu. Artık
**`ErrorCode` enum'undan üretiliyor** ve repo kökünde `error-catalogue.md`
olarak duruyor — `openapi.json`'ın yanında, `docs/` ağacının dışında.
`ErrorCatalogueDocumentTest` commit'li dosyayla enum ayrıştığı an düşüyor, ve
spec senkronu dosyayı `docs/error-catalogue.md` olarak size de bırakıyor.

**Düzeltme — burada sizin hakkınızda yanlış bir kayıt vardı.** Spec,
*"frontend'in katalog testi `params`'ı bu tablodan okuyor"* diyordu. Okumuyor:
`tests/unit/i18n/errorCatalogue.test.ts` kendi `PARAMS` sabitini elle tutuyor
ve tabloyu yalnızca yorumda kaynak olarak anıyor. Yani 41 kodun parametre
adları ve tipleri iki yerde elle yazılıydı ve **hiçbir şey ikisini
karşılaştırmıyordu**.

**Aksiyon — karar sizin, bugün kod işi yok:**
1. **Hiçbir şey kırılmıyor.** `PARAMS` bugün doğru; bu bir fırsat maddesi.
2. `error-catalogue.md` artık üretilen ve biçimi sabit bir dosya, yani
   `errorCatalogue.test.ts` onu ayrıştırıp kendi `PARAMS`'ıyla
   karşılaştırabilir. O zaman zincirin ikinci halkası da bağlanır: backend'e
   bir kod eklendiğinde **sizin** testiniz de düşer, mesaj yazılmadan önce.
   Biçimi sabit tutmayı üstleniyoruz; değişirse `B-nnn` ile haber veririz.
3. İstemezseniz de olur — `openapi.json` tipleri zaten tutuyor. Bu yalnız
   `params` adlarını ve ICU tiplerini kapsardı, ki `openapi.json` onları
   taşımıyor.


### B-111 · `en.json`/`tr.json` yanlış tablodan yazılmış olabilir
**Since:** denetim 2026-09-16 · `docs/spec/08-api.md` § 35.4, `18-appendix-d.md` EK D.6.1
**Neden:** § 35.4 size **"tam katalog EK D.6.1'de: 27 kod… `en.json` ve
`tr.json` artık buradan yazılabilir"** diyordu. O tablo elle yazılmıştı ve
katalog `ErrorCode`'dan üretilen `error-catalogue.md`'ye taşındığında geride
kaldı. Ölçüldü: **27 koda karşı enum'da 41**, ve iki somut yanlış —
`NO_ANONYMOUS_PROFILE` **hiçbir şeyin üretemediği** bir kod (09-15 denetiminde
kaldırıldı), ve `UNPARSEABLE_JOB_DESCRIPTION`'da **`params.reason` yok**, ki
§ 18.1 ile § 18.4'ün **yedi** değerli kapalı sözlüğü kullanıcıyı onunla dört ayrı
ekrana gönderiyor (metni düzelt / tam ilanı yapıştır / genel CV / tekrar dene).
Yedi, sekiz değil: `no_responsibilities` `B-072`'de sizden de kalkmıştı,
spec bunu 2026-09-16'ya kadar sekiz saymaya devam etti — sizde aksiyon yok.

Tablo kaldırıldı, § 35.4'ün işaretçisi `error-catalogue.md`'yi gösteriyor.
Backend tarafında kod değişmedi — **kodlar hep 41'di**, yanlış olan tabloydu.

**Aksiyon:** çeviri dosyalarınızı `error-catalogue.md`'ye karşı okuyun.
Muhtemel bulgular: **on beş kod için mesaj yok** (`AUTHENTICATION_REQUIRED`,
`RATE_LIMITED`, `MAGIC_LINK_INVALID`, `OAUTH_FAILED`, `EDIT_NOT_UNDERSTOOD`,
`COVER_LETTER_REJECTED`, `GENERATION_SUPERSEDED`, `GENERATION_PAUSED`,
`CHALLENGE_FAILED`, `TRANSLATION_FAILED`, `UNSUPPORTED_DOCUMENT`,
`DOCUMENT_TOO_LARGE`, `UNSUPPORTED_MEDIA_TYPE`, `METHOD_NOT_ALLOWED`,
`NOT_ACCEPTABLE`) ve **bir mesaj hiç görünmeyecek** (`NO_ANONYMOUS_PROFILE` —
silin). Bunların bir kısmı zaten yazılmış olabilir; madde "eksik" demiyor,
**"kaynağınız yanlıştı, kontrol edin"** diyor.

Bu `B-110`'un ta kendisi değil ama onunla aynı zinciri kapatıyor: `B-110`
testi bağlamayı öneriyor, bu madde **bugünkü** dosyaların durumunu soruyor.

### B-112 · İki şema sözlüğü daraldı — `gen:api` üçüncü kez

**Since:** `2975584` · beşinci denetim · `04-data-model.md` § 13.2, `error-catalogue.md`

**Neden:** ikisi de **hiçbir şeyin üretemediği** değerler taşıyordu, yani
sizin tarafta hiç girilmeyecek bir dalın tipi vardı.

- `ApiError.code`'dan **`REWRITE_VALIDATION_FAILED` kalktı.** Doğrulayıcının
  reddettiği bir madde kullanılmıyor ve kişinin kendi cümlesi basılıyor —
  yani bu kodu hiçbir yol üretemiyordu. Ürün ömrü boyunca da üretmedi.
- `Variant.createdBy` **dört değerden ikiye indi**: `user`, `llm_translate`.
  `llm_extract` yazılmadı çünkü içe aktarım kişinin kendi cümlelerini tutuyor
  ve bilerek `user` işaretliyor; `llm_rewrite` yazılmadı çünkü Faz D'nin
  yeniden yazımı varyant değil, `generations.rewritten_content` (V11).
  V16 kolona bu iki değeri zorlayan CHECK'i verdi.

**Aksiyon:** `npm run gen:api`. Sonra `REWRITE_VALIDATION_FAILED` için bir
çeviriniz varsa silin (`B-111`'in listesini okurken denk gelirsiniz), ve
`createdBy`'ın dört değerini ayıran bir arayüz varsa ikiye indirin.

### B-113 · `EXTRACTION_TIMEOUT` artık gerçekten dönüyor — cümlesi lazım

**Since:** `2975584` · beşinci denetim · `08b-api-contract.md` § 177

**Neden:** kod katalogda duruyordu, 504'ü seçilmişti, ve **hiçbir yol onu
üretemiyordu.** Sağlayıcı zinciri tükendiğinde çıkarım her durumda
`ALL_PROVIDERS_UNAVAILABLE` (503) diyordu — vendorlar gerçekten kapalı olsa da,
uzun bir CV sadece yavaş kaldığı için zaman aşımına uğrasa da. Zincir artık
hangisi olduğunu taşıyor: **her başarısızlık bir zaman aşımıysa** 504
`EXTRACTION_TIMEOUT`, değilse eskisi gibi 503.

**Aksiyon:** `errors.EXTRACTION_TIMEOUT` için bir mesaj yazın, ve 503'ünkinden
**farklı** olsun — bu ikisi kullanıcıdan zıt şeyler istiyor. 504: *aynı
dosyayla tekrar deneyin, belge uzun olabilir.* 503: *tekrar denemek şu an
yardımcı olmaz.* Parametresiz (mutlak kural 4: hangi belgenin yavaş kaldığı
bir log satırı değil).

### B-114 · Dört çıkarım reddi artık çıkış yolu taşıyor — iki yeni eylem

**Since:** altıncı denetim · `08b-api-contract.md` D.6.1 · § 31.10

**Neden:** dördü de boş bir `resolutions` dizisiyle geliyordu — ekranda bir
cümle, hiçbir düğme — ve `switch_to_manual_form` sözlükte **kullanılmadan**
duruyordu. Üstelik `ErrorPresenter`'ın o noktadaki yorumu *"çıkış yolu manuel
form, ki sözlükte böyle bir eylem yok"* diyordu; vardı. P4 her problemli
durumda somut seçenek istiyor.

**Aksiyon — `npm run gen:api`, sonra iki yeni ICU anahtarı.** Sözlük 12'den
14'e çıktı:

| Kod | Çözüm | Ekran ne yapmalı |
|---|---|---|
| `PDF_NOT_TEXT_BASED` (422) | `switch_to_manual_form` | Manuel profil formuna götür |
| `EXTRACTION_EMPTY` (422) | `switch_to_manual_form` | Aynısı |
| `PDF_ENCRYPTED` (422) | **`upload_another_file`** | Dosya seçiciyi yeniden aç — **"tekrar dene" değil**: aynı şifreli dosya her seferinde aynı yerde düşer, ve kişinin elinde zaten açık bir kopyası olabilir |
| `LANGUAGE_UNDETECTED` (422) | **`choose_language`** | `params.detectedCandidates`'ı seçenek olarak sun. Aday listesi en fazla tek elemanlı (model bir sıralama değil bir dil döndürüyor), yani "şu mu, yoksa başka bir dil mi" şeklinde bir soru |

**Ve bir tane daha, sözlüğe dokunmadan:** `EXTRACTION_TIMEOUT` (504) artık
`retry` taşıyor. `B-113` onu 503'ten ayırmıştı ki ikisi kullanıcıdan **zıt**
şeyler istesin; ikisi de boş çözüm listesiyle çıktığı sürece aynı hiçbir şeyi
istiyorlardı.

**Muhafız:** `ErrorCatalogueTest.everyActionIsOfferedSomewhere` — üreteni
olmayan bir eylem artık CI'yı düşürüyor. Bu maddenin kendisi o testin ilk
bulgusudur.

### B-115 · § 37.6'nın iki düğmesi çalışıyor — "çizmeyin" kaydı bayatmış

**Since:** altıncı denetim · `09-frontend.md` § 37.6, EK D.9 · 24

**Neden:** spec size **bayat varyant kontrollerini çizmemenizi** söylüyordu:
*"`Variant.stale` Aşama 1'de her zaman false ve bir varyantı yeniden üreten uç
yok."* İkisi de Aşama 3'te değişti, **satır değişmedi.** Yani doküman, işleyen
bir özelliği bir aşama boyunca yok saydırdı.

**Aksiyon — kod işi sizde, telde değişen bir şey yok:**

1. **Rozet gerçek.** Bir sözcüklemeyi düzenlemek ondan türeyen her şeyi
   `stale: true` yapıyor, düzenlemeyle **aynı transaction'da**.
2. **"İngilizceyi yeniden üret"** bir uç değil, bir yama:
   `PATCH /profile/atoms/{id}/variants/{vid}` gövdesi `{"userEdited": false}`.
   Yazarlık iddiası geri veriliyor ve bayat sözcükleme **hemen** çeviri
   kuyruğuna giriyor — kaynağı aylarca düzenlenmeyebilir, çeviri şimdi bayat.
3. **"Benim halimi koru"** hiçbir şey göndermemek. `userEdited: true`
   **reddediliyor** (400): bir makine çevirisini insan adının arkasına
   saklayabilecek tek yön o.
4. **Anonim oturumda çeviri kuyruğa girmiyor** — o oturumun ne işi
   sahiplenecek bir id'si var ne ikinci bir dili. Bozulma değil, kısa yol;
   ekranın anonimde bu düğmeyi hiç göstermemesi doğru.

### B-116 · Üç sözlük daraldı, biri **girdi** — `gen:api` dördüncü kez

**Since:** altıncı denetim · `V17`, `04-data-model.md` § 13.2

**Neden:** `B-112`'nin kapattığı kusurun dört kopyası daha bulundu — hiçbir
şeyin üretemediği değerler. Üçü telde:

- **`GenerationResponse.status` ve `GenerationSummary.status`'tan `failed`
  kalktı.** `selection_state` `NOT NULL`, yani seçimden önce düşen bir koşunun
  yazacak satırı yok ve arıza **işin** üstünde yaşıyor. Enum'un kendi javadoc'u
  bunu zaten söylüyordu ("Reserved. Nothing writes it today"), şema değeri yine
  de yayımlıyordu.
- **`JobStatusResponse.status`'tan `cancelled` kalktı.** Onu yazabilecek tek
  metodun tek çağıranı kendi testiydi; iptal eden bir uç yok, kaynak haritası
  da böyle bir şey adlandırmıyor. **İptal bir özellik**, ve indiği gün değer
  onunla birlikte geri gelir.
- **`Section.layout`'tan `two_column` kalktı — ve bu ötekilerden farklı, çünkü
  bir girdiydi.** Uç kabul ediyordu, CHECK izin veriyordu, şema yayımlıyordu,
  ve renderer onu **bilerek** entry list olarak basıyordu: üç şablon da tek
  kolon, gerekçesi ATS çıkarımı (§ 33.5). Yani kişi bir düzen seçiyor, hiçbir
  şey söylenmiyor, belgesi başkasını basıyordu. Ötekiler size boş bir dala mal
  oluyordu; bu, kullanıcıya verdiğini sandığı bir seçime.

**Aksiyon:** `npm run gen:api`. Sonra: `status === 'failed'` ve
`status === 'cancelled'` dallarınız varsa silin (hiç girilmediler), ve **düzen
seçicisinde `two_column` sunuluyorsa kaldırın** — veritabanındaki satırlar
`entry_list`'e çevrildi, yani seçmiş bir kullanıcı varsa bugün gördüğü düzen
değişmiyor, yalnız seçenek kayboluyor.

*(Dördüncüsü `JobType.EMAIL` ve telde hiç yoktu — kuyruk tipi, sizi
ilgilendirmiyor.)*

---

## Dağıtım bekleyen doğrulamalar

Üçü de bir dağıtım bekliyor: OAuth sıçraması (`B-048`), sihirli bağlantının
Turnstile'ı (`B-050`), `B-083`'ün challenge'ı. **`B-100` üçünün önündeki kapıyı
açtı** — ilk dağıtımda sırayla denenmeleri gerekiyor. `B-076`'dan kalan tek şey
yayımlanan sağlayıcı sayfasını `ProcessorAudit`'in açılış satırına karşı okumak.

Kalıcı kuralların `spec/`'e işlendiği yerler: `resolved/to-frontend-2026-08.md`.
`B-097`-`B-099` `resolved/to-frontend-2026-09.md`'ye indi (2026-09-15).
