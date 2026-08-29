# → Frontend

> **Kanal kuralları**
>
> - Backend yazar, frontend okur ve `OPEN` → `ACK` taşır.
> - Her madde bir ID taşır (`B-nnn`), numaralar tekrar kullanılmaz.
> - **Dosya 100 satırı geçerse arşivleme gecikmiştir.** `ACK` maddeleri `resolved/`'a taşınır.
> - API _şekli_ için otorite OpenAPI şemasıdır. Burası **neden değişti + ne yapman lazım** taşır.
> - Kalıcı kural niteliğindeki maddeler `spec/`'e işlenir ve buradan silinir.

---

## OPEN

> Altısı da `F-017`-`F-021`'in cevaplarından çıktı — **beş maddenizin beşi de
> kapandı.** Birkaçında yaptığınız şey zaten doğruydu; değişen, artık
> dayanağının olması. **`B-066` bir soru taşıyor ve cevabını bekliyor.**
>
> **Dosya sınırın üstünde.** Arşivlenebilen her şey arşivlendi; kalan fazlalık
> bu altı açık madde, ve `ACK` gelmeden taşınacak bir yerleri yok. Bir belge
> sorunu değil.

### B-067 · İçe aktarmanın sonucu telde, uyarılar yerleriyle — ve bir kural silindi
**Since:** commit `d73fca1` · `F-018` · **Spec:** `spec/07-subsystems.md` § 31.6.4

**Aksiyon:** `gen:api` — `contracts.ts`'teki elle tip artık kalkabilir. § 31.6
geçidini "sorunlu bölümler otomatik açık" kuralıyla kurun. **"Kritik uyarı"
mantığı yazmayın** ve içe aktarma için faz çevirisi yazmayın.

**1. `GET /jobs/{id}` içe aktarmanın sonucunu söylüyor:** `profileId`,
`sectionCount`, `atomCount`, `warningCount`, `detectedLanguage`, `warnings[]`.
SSE yükünü artık tipsiz taşımanız gerekmiyor.

**2. `warnings[]` her uyarının yerini taşıyor:** `code`, `sectionOrder`,
`entryOrder`. **Konum id değil, `displayOrder`** — `GET /profile`'ın hem
`sections[]` hem `entries[]` üstünde zaten yayımladığı alan. Yani uyarıyı
**elinizdeki** profile karşı çözüyorsunuz; uç, satırları adlandırmak için geri
okumuyor. `detail` gitmiyor (operatör İngilizcesi, çevrilemez); mesajı `code`
üstünden kurun. **Hiçbir entry adlamayan uyarı** konumsuz geliyor — modelin
belge düzeyinde kaldırdıkları; onları bölüm açmadan sayın.

**Bunu yayımlamak önce bir kusuru düzeltmeyi gerektirdi**, ve sormasaydınız
kusur bir ekran kurulana kadar dururdu: `path` ne bölümü söylüyordu (her bölüm
sıfırdan başlıyordu) ne de doğru satırı (indeks sıralama öncesiydi, oysa
`newestFirst` bir satır sonra entry'leri yeniden diziyor).

**3. "Kritik uyarılar çözülmeden Onayla aktif olmaz" kuralı silindi.**
İkinci seçeneğinizi tam almadık — kural sayıya inmedi, **kalktı.** Kapalı
sözlükteki altı kodun altısı da ekranda düzeltilebilir bir alanı tarif ediyor;
engelleyici bir uyarı sınıfı hiç var olmadı. **Onayla hep aktif — bugün
yaptığınız şey doğruydu**, ve artık dayanağı var.

**4. İçe aktarma `phase`/`label` göndermiyor**, yalnız `pct`. Ekranın kendi
cümlesini yazması doğru davranış. **Çeviri yazmayın** — uydurulmuş anahtarlar,
çevrilecek ama hiç değişmeyecek altı satır olurdu.

### B-066 · `GET /api/v1/generations` indi — ve satırda başlık yok, kasten
**Since:** commit `b776047` · `F-020` · **Spec:** `spec/08-api.md` § 35.3, EK D.8.7

**Aksiyon:** `gen:api`, geçmiş ekranını kurun; **ve aşağıdaki soruya cevap
verin** — satırı neyle etiketleyeceğiz.

`capabilities.canSaveHistory` artık karşılıksız değil. Gövde
`{ items, nextCursor, total }`:

- **Cursor, offset değil.** `nextCursor`'ı `cursor` olarak geri verin;
  **yokluğu geçmişin sonu** (boş bir `items` bir sayfa geç kalmış olurdu).
  Opak — bizim okuyacağımız, sizin yankılayacağınız bir değer; parçalamayın,
  sıralama bizim değiştirebileceğimiz bir şey. `limit` varsayılan 20, tavan
  100, ve **aşan istek kırpılıyor, reddedilmiyor.**
- **`total` sayfanın değil hesabın sayısı.** İstediğiniz ikinci şey buydu:
  **hesap silme onayındaki sayıyı buradan alın** (`GET /generations?limit=1`
  yeter). Sayfaları yürüyerek sayılan bir sayı, yürüyüş bitene kadar başka bir
  sayı olurdu.
- **Satır:** `generationId`, `status`, `createdAt`, `pageCount`, `matchLevel`,
  `contentLanguage`, `hasCoverLetter`. **İlan yok, mektup metni yok.**
- **Bozuk cursor `400 VALIDATION_FAILED`**, `fields: ["cursor"]`.

**Soru — satırı neyle etiketleyeceğiz?** Bugün bir satır "1 sayfa · 29 Ağustos
· strong" diyor ve **başka hiçbir şey demiyor**; on üretimi olan biri için bu
liste neredeyse okunmaz. Bir geçmiş ekranının isteyeceği etiket — rol adı,
şirket — **ilandan** okunuyor, ve `GenerationResponse` ilanı baştan beri geri
döndürmüyor (mutlak kural 4). Buraya `jdAnalysis.role.title` koymak o kuralın
sınırını **kazara** çizmek olurdu, o yüzden koymadık.

Cevabınıza göre üçünden biri olacak: (a) etiket gerekmiyor, tarih yeter;
(b) rol/şirket yayımlansın — o zaman bunu § 57'de açık bir karar olarak
yazarız; (c) kullanıcının kendi verdiği bir ad. **Bir `F-nnn` ile söyleyin**,
biz spec sorusu olarak kapatalım.

### B-065 · `rating` artık sayı, ve verilmiş yargı geri okunuyor
**Since:** commit `608655a` · `F-019` · **Spec:** `spec/11-operations.md` § 48.4.2

**Aksiyon:** `gen:api`, sonra `rating`'in üstündeki `Omit` daraltmasını silin;
ve `GET /generations/{id}`'nin `feedback` alanını okuyup ekranı ondan kurun.

**1. `FeedbackRequest.rating` artık `enum: [1, -1]`, tırnaksız.** Haklıydınız
ve sebebini de doğru bulmuşsunuz: swagger'ın `allowableValues`'ı bir
`String[]` ve özelliğin tipi ne olursa olsun tırnaklı basıyor. `rating` artık
yanındaki `Category` ile aynı kalıp — kapalı bir enum, tel biçimi bir
`@JsonValue` — farkı tel biçiminin sayı olması. **Gönderdiğiniz şey
değişmiyor**, yalnız tip artık onu tarif ediyor; daraltma silinebilir.

Elle yazılmış aralık kontrolü de kalktı: `0` artık ayrıştırmada reddediliyor.
**Cevabı değişmedi** — hâlâ `400 VALIDATION_FAILED`, `fields: ["rating"]`.

**2. `GET /generations/{id}` gövdesinde `feedback` var.** Yargı verilmemişse
alan **hiç yok** — boş bir yargı, tarafsız bir yargı değil. İçinde `rating`,
`category` ve `contentGrant` (`open`, `expiresAt`, `accessedAt`, `revokedAt`);
**yorum yok**, o POST yanıtında da gitmiyordu.

**Alan `FeedbackResponse`'un kendisi**, ona benzeyen ikinci bir tip değil —
POST'tan dönen nesneyi doğrudan aynı slota koyabilirsiniz. İçindeki
`generationId` burada gereksiz ve bilerek duruyor: bedeli tekrarlanan bir
uuid, kaldırmanın bedeli ikinci bir şema.

Asıl kazandığınız şey ikinci yarısı: **`accessedAt` artık ertesi gün de
görülebiliyor.** Pencere kırk sekiz saat, yani ona bakması gereken kişi
zaten ilk oturumun dışındaki kişi.

### B-062 · Cover letter'ın 429'u `Retry-After` gönderiyor, hep gönderiyordu
**Since:** commit `495f577` · `F-021` (1) · **Spec:** `spec/08b-api-contract.md` § EK D.6.5

**Aksiyon:** `POST …/cover-letter/regenerate`'in 429'unda "birazdan tekrar
dene" dalını bırakıp süreyi yazan dala geçin — iki kota kapınızda yaptığınızın
aynısı.

Başlık eksik değildi. `ProblemDetailAdvice` onu **`params.resetsAt`'i bir
`Instant` olarak taşıyan her 429'dan** türetiyor ve bu uç da öteki ikisiyle
aynı advice'tan geçiyor. Eksik olan iki şey vardı ve ikisi de sizin
gördüğünüz yerdeydi: uçta başlığı yayımlayan bir `@ApiResponse` yoktu, ve onu
telde gördüğünü söyleyen bir test yoktu. **İkisinin yokluğu, başlığın
yokluğundan ayırt edilemez** — nitekim bu iddianın test edilmeden yazıldığı tek
öbür yer, elle test kılavuzu, yanlış çıkmıştı.

Şimdi ikisi de var: `CoverLetterApiIT.arefusedLetterCarriesRetryAfterAsWellAsResetsAt`
pencereyi harcayıp başlığı telde görüyor, ve şema onu belgeliyor. **`gen:api`
gerekiyor** — yeni bir başlık tanımı iniyor, şekil değişikliği yok. Üretim
ucunun `429`'u da (`QUOTA_EXCEEDED`) aynı sebeple şemaya yazıldı; davranışı
değişmedi.

### B-063 · Hata tablosunda beş yeni satır — dördünü siz bildirmediniz
**Since:** commit `0dc5365` · `F-017` · **Spec:** `spec/08b-api-contract.md` § EK D.6

**Aksiyon:** kataloğunuzda bu beş kodun mesajı var mı, bakın; ve
`COVER_LETTER_REJECTED`'ın altı `issues` değerini ICU'da adlandırın.

Haklıydınız, ve **gerekçeniz bulduğunuzdan fazlasını buldu.** Tabloyu
`ErrorCode`'a karşı okuyan bir muhafız yazdık; bildirdiğiniz
`COVER_LETTER_REJECTED` ile birlikte dört tane daha düştü:

| Kod | HTTP | Nereden geliyor |
|---|---|---|
| `COVER_LETTER_REJECTED` | 422 | `issues: string[]` — sizin bildirdiğiniz |
| `GENERATION_PAUSED` | 503 | § 44.3'ün acil freni, parametresiz |
| `METHOD_NOT_ALLOWED` | 405 | D.6.8'in kendi tablosunda duruyordu |
| `NOT_ACCEPTABLE` | 406 | aynı |
| `UNSUPPORTED_MEDIA_TYPE` | 415 | aynı |

Son üçü "katalogda var" diye D.6.8'de yazılıydı ama **EK D.6'ya hiç
geçmemişti**, ve testinizin okuduğu tablo o. `GENERATION_PAUSED` yalnız
`10-security.md`'de tarif ediliyordu.

**`issues` kapalı** — `CoverLetterIssue` bir enum ve tam altı değeri var:
`unsupported_claim`, `number_invented`, `experience_overstated`,
`wrong_company`, `length_out_of_range`, `cliche`. Yedincisi ancak bu belge de
değişerek gelebilir, yani altısını adlandırıp kullanıcıya okunur bir cümle
verebilirsiniz.

Tablo artık `ErrorCatalogueSpecTest`'in okuduğu şey: kod, durum, parametre
adları, tipleri **ve sıraları**, iki yönde de. Bir daha sessizce ayrışmaz.

### B-064 · `{"userEdited": true}` artık `400` — `500` dönüyordu
**Since:** commit `495f577` · `F-021` (2) · **Spec:** `spec/08b-api-contract.md` § EK D.6

**Aksiyon:** yok — mock'unuz doğru. Kaydı için: tahmin ettiğiniz şekil
(`400 VALIDATION_FAILED`, `fields: ["userEdited"]`) artık gerçekten dönen şey.

**Ama sorduğunuz için bir kusur çıktı.** `VariantPatch`'in constructor'ı bu
ihlalde `IllegalArgumentException` atıyor, advice'ta o istisnanın işleyicisi
yok, ve istek son çareye düşüp **`500 INTERNAL_ERROR`** oluyordu — sunucunun
kullanıcıya "isteğin beni bozdu" demesi. Kural doğruydu, cevabı yanlıştı.

Testi vardı ve yakalamamıştı: `VariantSynchronizationIT` **constructor'ı**
çağırıp fırlattığını görüyordu, telden geçen hiçbir şey yoktu. Kapı artık
`VariantPatchRequest.userEdited` üstünde bir `@AssertFalse`, yani reddi mevcut
doğrulama işleyicisi yazıyor ve `fields`'i binding result'tan alıyor — adın
ikinci bir yerde tutulması gerekmiyor. Constructor'daki muhafız duruyor; o
servis katmanı çağıranı için.

## ACK — frontend tamamladı, backend arşivleyebilir

_(`B-037`…`B-061`'in hepsi kapandı ve `resolved/to-frontend-2026-08.md`'de —
hangi dilimin hangisini kapattığı orada. Aşağıdakiler **hâlâ canlı olan**
üç kayıt; gerisi arşive indi.)_

**İki maddede bir doğrulama eksik ve söylenmesi gerekiyor:** ne OAuth
sıçraması (`B-048`) ne de Turnstile (`B-050`) gerçek uca karşı denendi —
ikisi de kendi anahtarları yapılandırılmış bir dağıtım istiyor. Bugün
doğrulanan şey mock'a karşı: `403` widget'ı sıfırlatıyor, `429` cümlesini
`Retry-After`'dan kuruyor, `/auth/complete` oturumu okuyup yoluna gidiyor.

**`B-051` kapandı ama § 31.6'nın gözden geçirme ekranı yarım.** "Sorunlu
bölümler otomatik açık" ve "kritik uyarılar Onayla'yı kapalı tutar"
uygulanamadı, çünkü telde hangi bölümün sorunlu olduğunu söyleyen bir alan
yok — yalnız bir sayı var. Uydurmadık; ne yapılabildiği ve ne istediğimiz
**`F-018`**'de. O maddeye kadar geçit "şu kadar konuda emin olamadık" notuyla
duruyor.

**`B-059` kapandı ama EK C.1'in maddesi kapanmadı.** Alt işleyen listesi artık
doğru — e-posta yolu adıyla ve bölgesiyle yazılı. Eksik olan şey **sağlayıcı
listesinin kendisi**: hangi model, ona ne gidiyor, ücretsiz katman eğitimde
kullanıyor mu. Model seçimi bir ürün kararı olarak bekliyor, o paragraf da
onunla birlikte yazılacak. **Yayın öncesi kontrol listesi hâlâ açık.**

---

## Kalıcı kurallar

Eski maddelerin `spec/`'e işlendiği yerlerin tablosu
`resolved/to-frontend-2026-08.md`'ye taşındı (2026-08-24) — dosya sınırı.
