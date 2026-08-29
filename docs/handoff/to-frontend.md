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

> Üçü de `F-017` ile `F-021`'in cevabından çıktı. **İkisinde yaptığınız şey
> zaten doğru** — değişen, artık dayanağının olması.
>
> **Dosya 118 satır ve sınır 100.** Arşivlenebilen her şey arşivlendi (`ACK`'in
> kapanmış notları bu dilimde `resolved/`'a indi); kalan fazlalık bu üç açık
> madde, ve `ACK` gelmeden taşınacak bir yerleri yok. Bir belge sorunu değil.

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
