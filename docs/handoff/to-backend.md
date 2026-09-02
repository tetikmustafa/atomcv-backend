# → Backend

> **Kanal kuralları**
> - Frontend yazar, backend okur ve `OPEN` → `ACK` taşır.
> - Her madde bir ID taşır (`F-nnn`), numaralar tekrar kullanılmaz.
> - **Dosya 100 satırı geçerse arşivleme gecikmiştir.**
> - Bir spec değişikliği gerekiyorsa burada iste — `spec/`'i frontend reposunda düzenleme,
>   bir sonraki senkronda kaybolur.

---

## OPEN

> İkisi de **gerçek uca karşı ölçümden** çıktı (2026-08-30, `make record`),
> mock'a karşı değil, ve ikisi de bir aşama boyunca durmuştu. Hiçbiri bir
> ekranı bloke etmiyor. **`F-027` cevaplandı ve aşağıya, `ACK`'e indi.**
>
> **Dosya sınırın üstünde**, ve arşivlenebilen her şey arşivlendi: fazlalık bu
> açık maddeler, ve `ACK` gelmeden taşınacak bir yerleri yok.

### F-025 · `companyName` telde `"not specified"` olabiliyor — boş dize kuralı bunu tutmuyor
**Since:** frontend commit `1c63e27` · gerçek uca karşı ölçüm, 2026-08-30
**Neden:** `B-070` "boş dize hiç dönmüyor, alan ya doludur ya yoktur" diyor ve
`""` için doğru. Ama telde duran 45 satırın birinde `companyName` **`"not
specified"`** — modelin, şirketin adı geçmediğini söylemek için yazdığı bir
cümle. `""` değil, o yüzden çeviren kural onu yakalamıyor, ve satır ekranda
*"Business Intelligence Specialist (SQL Developer) · not specified"* diye
çıkıyor: § 57.6'nın "bir şey söylüyormuş gibi duran etiket" diye tarif ettiği
şeyin ta kendisi.

**Bizde çözülemez, ve denemeyeceğiz.** İstemci tarafında bu, bir yer tutucu
ifade kara listesi demek — `"not specified"`, `"belirtilmemiş"`, `"N/A"`,
`"unknown"`, ve modelin yarın yazacağı yedincisi. Dilden ve modelden bağımlı
bir tahmin, ve yanlış tarafta.

**İstenen:** ikisinden biri. (a) `JobAnalysis` "yok"u tek bir biçimde
söylesin — prompt'ta şirket yoksa alanı boş bırakma talimatı, ve mevcut
`""` → yok çevirisi işini görsün; ya da (b) çevirici, boş dizeye ek olarak
modelin "yok" demek için kullandığı kalıpları da yok sayar — hangisi
sizin tarafınızda daha az kırılgansa. Kararı sizinki, çünkü hangi kalıpların
çıktığını **prompt'u yazan** taraf görebiliyor.

**Kayıt için doğru çıkanlar:** iki alan gerçekten bağımsız (45 satırın 28'inde
rol var, 19'unda şirket), ve genel modda ikisi de yok. Bir de şunu ölçtük:
`roleTitle` **kendi içinde tire taşıyabiliyor** (`"Integration Engineer —
Legacy Systems"`), o yüzden satırda rolü ve şirketi bir tire ile birleştirmiyor,
iki ayrı öğe olarak çiziyoruz.

**Spec:** `spec/16-cost-legal.md` § 57.6, `spec/08-api.md` EK D.8.7

### F-026 · Dört mektup taslağının dördü de reddedildi — `retry` tek çıkış yolu
**Since:** frontend commit `5df4d42` · gerçek uca karşı ölçüm, 2026-08-30
(`make record`, yani gerçek LLM çağrıları)
**Neden:** Altın profile (`senior_backend_tr`) karşı arka arkaya **dört**
taslak istedik ve dördü de `422 COVER_LETTER_REJECTED` döndü:

| # | `style` | `issues` |
|---|---|---|
| 1 | `default` | `number_invented`, `length_out_of_range` |
| 2 | `shorter` | `number_invented`, `length_out_of_range` |
| 3 | `shorter` | `number_invented`, `length_out_of_range` |
| 4 | `more_formal` + `companyNote` | `number_invented`, `length_out_of_range`, `cliche` |

Dördüncüsünün farklı çıkması, bunların **tekrar oynatılan tek bir fixture
olmadığını** gösteriyor: gerçekten dört ayrı taslak yazıldı ve dördü de
geçemedi.

**Ekranın söylediği cümle bu ölçüme göre yanlış.** `COVER_LETTER_REJECTED`'ın
tek çözümü `retry`, ve bizim metnimiz "tekrar istemek genelde geçen bir taslak
üretir" diyor. Dört denemede geçmedi; yani kullanıcının önündeki tek düğme onu
hiçbir yere götürmüyor olabilir. Metni yumuşatmadık — bir profil ve bir model
yapılandırmasına bakarak ürün metnini değiştirmek, ölçtüğümüzden fazlasını
iddia etmek olurdu.

**İstenen:** bu iki ihlalin sistematik olup olmadığına bakın. `number_invented`
altın profilin metriklerinden (`300 bin satır`) geliyor olabilir — mektuba
girmesi yasaksa prompt bunu söylemeli; `length_out_of_range` ise `shorter`
istendiğinde bile çıkıyor, ki bu bir aralık/prompt uyuşmazlığına benziyor.
**Sistematikse çıkışsız bir ekran var demektir**, ve o zaman ya guard'ın ya
prompt'un değişmesi gerekiyor — istemcinin ekleyebileceği bir şey yok.

**Doğru çalışan yarısı:** gövde tam beklediğimiz şekilde geldi ve ekran onu
doğru çiziyor — iki değer `errorValues` üzerinden adlandırılıp
`Intl.ListFormat` ile birleşiyor, panel kırmızı değil, ve `retry` düğmesi
zaten ekranda duran düğme. Mock'un tek elemanlı `issues`'u da ölçülen çiftle
değiştirildi: tek elemanlı bir liste `ListFormat`'ı hiç sınamıyordu.

**Spec:** `spec/07-subsystems.md` § 34, `spec/08b-api-contract.md` EK D.6.5

<!-- Şablon:
### F-001 · Kısa başlık
**Since:** frontend commit <sha> · Adım <n>
**Neden:** <sorunun ne olduğu>
**İstenen:** <backend'den beklenen somut şey>
**Spec:** <ilgili dosya ve bölüm, varsa>
-->

---

## ACK — backend tamamladı, frontend arşivleyebilir

*(`F-001`…`F-024` `resolved/to-backend-2026-08.md`'de — üçünün de cevabı
oraya indi 2026-08-30'da, dosya sınırı.)*

### F-027 · İndi, ve düzeltme uçlarda değil oturumda

**Haklıydınız, ve "muhtemelen iki yerde" tahmininiz yarı yarıya doğruydu.**
Kusur oturum doğrulamasındaydı; profil çözümleyicisinde değil.

**Ne oluyordu:** `ProfileResolver` profil satırını **ilk kullanımda
yaratıyor**, ve silinmiş bir `user_id` ile yapılan `INSERT`
`profiles.user_id`'nin yabancı anahtarını ihlal edip `500` oluyordu. Listelediğiniz
uçların hangilerinin `500` hangilerinin `200` döndüğü tam olarak bunu
söylüyor: satır yazan uçlar düşüyordu, yazmayanlar (`/generations`,
`/account/usage`) hesap yerindeymiş gibi cevap veriyordu — ki o da kendi
başına yanlıştı.

**Ne yaptık:** kontrol `SessionCurrentUser`'ın oturum çözümüne kondu. Hesabı
olmayan bir oturum artık **oturum değil**: `find()` boş dönüyor, `require()`
istediğiniz `401 AUTHENTICATION_REQUIRED`'ı üretiyor, ve bu **her uç için aynı
anda** oluyor — yazan uç için değil. Oturum aynı anda iptal de ediliyor
(§ 40.1). Maliyeti istek başına tek birincil anahtar okuması, ve zaten
memoize edilmiş.

**§ 35.6 kırılmıyordu, ve artık kanıtı var.** "Hesabı olan ama profili
olmayan çağırana boş profil dönülür" iddiası doğru çalışıyor; o yolu `500`
yapan şey profilin yokluğu değil **hesabın** yokluğuydu. İkisi ayrı ayrı test
edildi.

**Bir de sizin göremeyeceğiniz yarısı vardı:** `AccountDeletionService`
oturumları satırdan önce siliyor, ama `revokeAllFor` Redis hatasını `warn`
edip `0` dönüyordu — "hiç oturumu yoktu"dan ayırt edilemez bir cevap — ve
hesap onun üstüne siliniyordu. Yani sizin ölçtüğünüz durum yerelde dev
stub'ından, **üretimde bu yoldan** doğuyordu. Artık fırlatıyor: oturumları
silinemeyen hesap silinmiyor.

**Sizde iş yok, ama bir davranış değişikliği var ve söylenmesi gerekiyor:**
`DELETE /api/v1/account` **iki kez** basılırsa ikincisi artık `204` değil
`401`. Uç hâlâ idempotent; değişen, ikinci basışın uca ulaşamaması — ilk yanıt
çerezi zaten sildiği için telde de böyleydi. Eski `204`'ü üreten şey dev
stub'ıydı ve testimiz onu ölçüyormuş.

**Ölçümünüz bir şey daha çıkardı.** Bu `401` inince kendi entegrasyon
sınıflarımızdan biri düştü: `SecondImportIT` `409`'unu, üç sınıf önce
`AccountDeletionIT`'in **sildiği** kullanıcıdan alıyormuş. O sınıf tam da bu
kusur sayesinde geçiyordu.
