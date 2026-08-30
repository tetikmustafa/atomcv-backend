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

> Üçü de `F-022`-`F-024`'ün cevabı. **İkisi ekranınızdaki bir eksiği kapatıyor,
> biri sizde iş çıkarmıyor.** `gen:api` üçü için de gerekiyor.

### B-070 · Geçmiş satırı artık etiketli — ve sınır § 57.6'ya yazıldı
**Since:** commit `b76e5a7` · `F-022` · **Spec:** `spec/16-cost-legal.md` § 57.6, `spec/08-api.md` § 35.3

**Aksiyon:** `gen:api`, sonra satıra `roleTitle` ile `companyName`'i koyun.

Seçenek **(b)** uygulandı: `GenerationSummary` üstünde iki alan var, ikisi de
Faz A'nın ilandan **çıkardığı** adlar. **İkisi bağımsız** — bir ilan işi
adlandırıp şirketi adlandırmayabiliyor, o durumda `companyName` gelmiyor ve
`roleTitle` geliyor. Genel modda ikisi de yok. **Boş dize hiç dönmüyor**;
`JobAnalysis` ikisini de `""` yaptığı için bunu ayrıca çeviren bir kural var,
yani `""` kontrolü yazmanız gerekmiyor — alan ya doludur ya yoktur.

**İstediğiniz cümleyi yazdık ve istediğinizden geniş yazdık.** § 57.6 yalnız
"satırı adlandıracak kadarı" demiyor; istisnanın **üç ölçütünü** ve bir
listesini tutuyor — amaç adlandırmaksa, model çıkarımıysa, ve bir satıra
sığıyorsa. Listede olmayan alan istisna değil. Yani bir sonraki alan aynı
gerekçeyle giremez, ve girmek isterse önce o bölüm değişir.

### B-069 · `ImportWarning.code` artık enum — altı değer telde
**Since:** commit `b8876ca` · `F-023` · **Spec:** `spec/07-subsystems.md` § 31.6.5

**Aksiyon:** `gen:api`, sonra altı ICU mesajını yazın:
`ambiguous_date`, `missing_organization`, `unclear_section`, `scrambled_text`,
`overlapping_dates`, `untranslatable_atom`.

Haklıydınız ve gerekçeniz de doğruydu: "tahminle yazılan bir anahtar kümesi
hiç eşleşmeyecek altı satır olurdu" cümlesi, `B-067`'de faz çevirileri için
verdiğimiz gerekçenin aynısı — ve o gerekçe bu yönde de geçerli.

**Alanın tipi telde hâlâ `String`, ve bu bilinçli.** Şema enum, çalışma zamanı
hoşgörülü: değer JSONB'den geri okunuyor ve adı sonradan değişmiş bir kod
taşıyan eski bir satır, tipi enum olsaydı ya isteği düşürürdü ya uyarıyı yok
ederdi — ve kaybolan bir uyarı `warningCount == warnings.length` iddiasını
bozardı. Yani **`ResolutionAction`'da yaptığınızın aynısını yapın**: kapalı
olduğunu bilerek açık okuyun, tanımadığınız bir yedinci kod genel cümlenize
düşsün. Yedincisi gelirse bir `B-nnn` ile duyarsınız; muhafızımız tam bunun
için elle yazılmış altı değer okuyor.

### B-068 · `file` parçasız içe aktarma artık `400` — sizde iş yok
**Since:** commit `cb3f652` · `F-024` · **Spec:** `spec/08b-api-contract.md` EK D.6.9

**Aksiyon:** yok — mock'unuz doğruydu. `400 VALIDATION_FAILED`,
`fields: ["file"]`, ve artık gerçekten dönen şey o.

Teşhisiniz kelimesi kelimesine doğru çıktı:
`MissingServletRequestPartException`'ın advice'ta işleyicisi yoktu ve istek
son çareye düşüyordu. `B-064` ile aynı sınıf, ve bir istisna kadar yakın:
`handleBadParameter` **zaten** query parametresinin eksiğini tam bu gerekçeyle
yakalıyordu; bir parça, aynı kusurun başka bir zarfı.

**Ölçümünüzün asıl değeri bu maddede değil, ölçümü yapmış olmanızda.** Bu uca
kendi formunuzdan bakan hiçbir test buraya ulaşamazdı — form dosyasız
göndermiyor. Bir aşama boyunca durmasının sebebi o, ve EK D.6.9'a ders olarak
öyle yazıldı: *bir uca yalnız kendi arayüzünden bakan, o ucun başka
istemcilere ne dediğini hiç görmez.*

## ACK — frontend tamamladı, backend arşivleyebilir

_(`B-037`…`B-067`'nin hepsi kapandı ve `resolved/to-frontend-2026-08.md`'de —
hangi dilimin hangisini kapattığı orada. Aşağıdakiler **hâlâ canlı olan**
kayıtlar; gerisi arşive indi.)_

**`B-062`-`B-067`'nin altısı da kapandı** (2026-08-29/30) ve notları
arşivde. İkisinin bıraktığı eksik `B-069` ile `B-070`'te karşılandı:
uyarılar artık adlandırılabilir, geçmiş satırı artık etiketli.

**İki maddede bir doğrulama eksik ve söylenmesi gerekiyor:** ne OAuth
sıçraması (`B-048`) ne de Turnstile (`B-050`) gerçek uca karşı denendi —
ikisi de kendi anahtarları yapılandırılmış bir dağıtım istiyor. Bugün
doğrulanan şey mock'a karşı.

**`B-059` kapandı ama EK C.1'in maddesi kapanmadı.** Alt işleyen listesi artık
doğru — e-posta yolu adıyla ve bölgesiyle yazılı. Eksik olan şey **sağlayıcı
listesinin kendisi**: hangi model, ona ne gidiyor, ücretsiz katman eğitimde
kullanıyor mu. Model seçimi bir ürün kararı olarak bekliyor, o paragraf da
onunla birlikte yazılacak. **Yayın öncesi kontrol listesi hâlâ açık.**

---

## Kalıcı kurallar

Eski maddelerin `spec/`'e işlendiği yerlerin tablosu
`resolved/to-frontend-2026-08.md`'ye taşındı (2026-08-24) — dosya sınırı.
