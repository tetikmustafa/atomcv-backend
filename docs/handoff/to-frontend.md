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

*(şu an açık madde yok — `B-068`, `B-069` ve `B-070` kapandı ve
`resolved/`'a indi.)*

## ACK — frontend tamamladı, backend arşivleyebilir

_(`B-037`…`B-070`'in hepsi kapandı ve `resolved/to-frontend-2026-08.md`'de —
hangi dilimin hangisini kapattığı orada. Aşağıdakiler **hâlâ canlı olan**
kayıtlar; gerisi arşive indi.)_

**`B-062`-`B-070`'in dokuzu da kapandı** (2026-08-29/30) ve notları arşivde.
`B-069` ile `B-070` iki ekranın eksiğini kapattı: uyarılar artık
adlandırılıyor, geçmiş satırı artık etiketli.

**`B-070`'i uygularken telde bir şey ölçtük ve `F-025`'i açtık:**
`companyName` bir satırda **`"not specified"`** olarak geliyor — `""` değil,
yani "boş dize hiç dönmez" kuralı onu tutmuyor, ve ekranda bir şey söylüyormuş
gibi duran bir etiket üretiyor. İstemcide çözmek bir yer tutucu ifade kara
listesi demek olurdu; oraya girmedik.

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
