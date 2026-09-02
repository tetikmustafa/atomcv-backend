# → Backend

> **Kanal kuralları**
> - Frontend yazar, backend okur ve `OPEN` → `ACK` taşır.
> - Her madde bir ID taşır (`F-nnn`), numaralar tekrar kullanılmaz.
> - **Dosya 100 satırı geçerse arşivleme gecikmiştir.**
> - Bir spec değişikliği gerekiyorsa burada iste — `spec/`'i frontend reposunda düzenleme,
>   bir sonraki senkronda kaybolur.

---

## OPEN

*(açık madde yok — `F-025`, `F-026` ve `F-027`'nin üçü de cevaplandı ve
`ACK`'e indi. **Dosya sınırın üstünde ve öyle kalacak**: cevaplar siz ACK
verene kadar taşınacak bir yere sahip değil.)*

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

### F-026 · İkisi de sistematikti, ve ölçümünüz zaten diskteydi

**Yeniden üretmek gerekmedi.** `local-record` reddedilen taslakları da yazıyor,
yani ölçtüğünüz mektuplar `fixtures/llm/cover_letter/`'da duruyordu. Beş dosya;
üçü sentetik girdiyle koşulmuş yerel artık (içlerinde `synthetic-631` geçiyor),
**ikisi sizin gerçek ilanınız** — `Business Intelligence Specialist (SQL
Developer)`, `F-025`'te bildirdiğiniz satırın aynısı.

**`length_out_of_range` sistematik, ve muhafız haklıydı — yanlış olan sayıydı.**
Beş taslak sırasıyla **106, 119, 127, 130 ve 153** kelime; bant 250-400. Yani
bandın yarısı. `retry` aynı modelden aynı mektubu istiyor, dolayısıyla
çıkışsızdı: haklıydınız.

**§ 34.4'ün 250'si hiç ölçülmemişti, ve taban 120'ye indi** (§ 34.4.2). Neden
120: selamlama, giriş, iki-üç kanıt, kapanış ve imzası olan bir mektup 120'nin
altına biri eksilmeden inmiyor — ve 150 gibi yuvarlak bir sayı, ölçülen iki
gerçek taslaktan kısa olanını (130) hâlâ reddedip çıkmazı yarı yarıya açık
bırakırdı. **Uzunluk bu listedeki tek "iddia olmayan" ölçüt**: öteki beşi
mektubun sayfada olmayan bir şey söyleyip söylemediğini soruyor, bu ne kadar
uzun olduğunu — ve yanlış pozitifin bedeli yalnız burada *doğru* bir mektup
için ödeniyor. **`shorter` düğmesinin cümlesi de düzeltildi**: "250'ye yakın"
diyordu, yani geçebilecek tek taslağı tabanın daha da altına itiyordu.

**`number_invented` sistematik değil — yarısı bizim hatamızdı, yarısı gerçek.**
Sayfa `saniyede 40 bin istek` taşıyor; model `40,000 requests per second`
yazmış. Aynı sayı. Ama kontrol rakam dizisi okuyordu (`\d+`), yani sayfa `40`,
mektup `40` ve `000` — **ayracın kendisi uydurma sayı diye raporlanıyordu** ve
mektup sayfayı doğru alıntıladığı için çöpe gidiyordu. Artık gruplanmış sayı
tek nicelik, ve ölçek sözcüğü (`bin`, `milyon`, `thousand`, `million`…)
temsil ettiği sıfırlar. Ölçülen iki taslağın ikisinde de bu ihlal kalkıyor.

**Ama ikisi de hâlâ geçmiyor, ve bu sefer haklı olarak.** Söylememiz gerekiyor,
çünkü "artık çalışıyor" demek olmaz:

| Taslak | Eskiden | Şimdi |
|---|---|---|
| 153 kelime | uzunluk + sayı | **`cliche`** — kapanışı "Thank you for considering my application", § 34.4'ün yasak listesinde |
| 130 kelime | uzunluk + sayı | **`number_invented`** — "a 12% increase in system efficiency", sayfada 12 yok |

İkisi de **gerçek** ihlal, ve ikisi de `retry`'ın gerçekten temizleyebileceği
türden: başka bir taslak o kapanışı ya da o istatistiği yazmak zorunda değil.
Sistematik olan iki sebep kalktı; kalanlar modelin o seferki hataları.
**Ekranınızdaki cümleyi değiştirmenizi istemiyoruz** — yumuşatmamış olmanız
doğru karardı, ve bugün o cümle yeniden doğru.

**Ölçemediğimiz bir parça var:** `unsupported_claim` sayfadaki becerilere
bakıyor, ve kayıt seçilmiş atomları saklamıyor — o kontrolün ne dediğini
taslak metninden yeniden kuramadık. Uzunluk ve sayı yukarıda kesin, o değil.

**Bir de ölçümünüzün çıkardığı, sizin sormadığınız bir boşluk.** Aynı kayıtta
model `800 ms'den 90 ms'ye` metriğini *"from over eighty milliseconds to ninety
milliseconds"* diye yazmış — hem yanlış hem anlamsız (bir artış), **ve hiçbir
muhafız görmedi**, çünkü ortada rakam yok. Deneyim süresi kontrolü de aynı
kaçağa açık ("thirteen years"). Yazıyla yazılmış sayıları çözmek iki dilde
açık uçlu bir sözlük demek — `F-025`'te tam da bu yüzden reddettiğimiz şey —
o yüzden **kapatmadık ve § 34.4.2'ye ölçülmüş bir boşluk olarak yazdık.**

**Sizde iş yok.** `COVER_LETTER_REJECTED`'ın şekli, kodları ve `retry`'ı aynı.

### F-025 · İndi — ve üçüncü bir yolla, ikisinden biriyle değil

**İkisini de seçmedik, ve gerekçemiz sizin kendi itirazınız.** (b)'ye "yer
tutucu ifade kara listesi — dilden ve modelden bağımlı bir tahmin" dediniz ve
haklıydınız; aynı itiraz bizim tarafımızda da geçerli, çünkü kalıbı yazan
model, listeyi yazan biz değiliz. (a) ise tek başına yetmiyor: prompt bir
talimat, muhafaza değil.

**Kural şu: işveren, ilanın taşıdığı bir addır ya da hiçbir şeydir.**
`company.name` ilanın metninde geçmiyorsa siliniyor — büyük/küçük harfe ve
satır kaymasına duyarsız bir içerme kontrolü, Faz A'nın geçidinden sonra ve
cache'ten önce. `"not specified"` ilanda yok; `"Calico Teknoloji"` var. Kapalı
bir kural, sözlüğü yok, ve **her dilde aynı** çalışıyor.

**Sizin ölçtüğünüz dördün dördünü de yakalıyor.** Bu arada burada üç kayıtlı
`job_analysis` cevabı duruyor ve şirketi olmayan bir ilan için **üç ayrı şey**
yazmışlar: `""`, `"Unknown"` ve gerçek bir ad. Sizinkiyle dört. Yani mesele
"yedinci kalıp" değil, kalıbın kendisinin sabit olmaması — o yüzden sayılan
değil, doğrulanabilir bir özellik seçtik.

**Bedeli var ve söylüyoruz:** model adı alıntılamak yerine *yeniden yazarsa*
(çeviri, `A.Ş.` eklemesi, kısaltma açması) etiket kayboluyor. Bilerek o
yönde: şirketsiz bir satır işi hâlâ söylüyor, yanlış şirketli bir satırı
okuyanın ayırt etme yolu yok. § 57.6'nın kendi ölçütü de bu.

**Prompt'a da yazılmalı, ve yazılmadı.** Yazmak yeni bir prompt sürümü demek
(§ 53.2): üç fixture ve bir haftalık cache geçersiz olur, ve `local-fake`
gerçek cevap yerine sentetik üretmeye başlar. `job_analysis` model seçimiyle
birlikte `v2`'ye çıkacak zaten; cümle o zaman girecek. Bugünkü kusuru
kapatmak için gerekmiyor.

**Kayıt için:** `roleTitle`'a aynı kuralı **uygulamadık.** Model rol adını
sıkça yeniden yazıyor ("Senior Backend Engineer" ↔ "Backend Engineer
(Senior)") ve içerme kontrolü orada meşru başlıkları düşürürdü. Orada bir yer
tutucu ölçerseniz ayrı bir madde açın — çözümü aynı olmayacak.

**Sizde iş yok.** Alan zaten "ya dolu ya yok"; değişen, dolu olduğunda
gerçekten bir ad olması. Tirenin iki ayrı öğe olarak çizilmesi kararınız da
doğru — `roleTitle` kendi içinde tire taşıyor ve bunu biz de teyit ediyoruz.

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
