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

### B-072 · `no_responsibilities` telden kalktı

**Since:** commit <bu PR> · kapanış sonrası dilim H · **Spec:**
`spec/05-pipeline-a-c.md` § 18.4 · `shared/error/UnreadablePostingReason`

**Ne oldu:** `UNPARSEABLE_JOB_DESCRIPTION`'ın `reason` parametresi artık sekiz
değil **yedi** değer alıyor. `no_responsibilities` üretilmiyor ve enum'dan
kaldırıldı.

**Neden:** kural "Faz B eşleştirecek bir sorumluluk bulamazsa reddet" diyordu
ve mantığı doğruydu; dünya hakkında yanlıştı. Gerçek ilanların çoğu başlıksız
bir nitelik listesi. Bunu çıkaran ilan aynen şöyleydi:

> At least 5 years of hands-on software development experience in Java, Java EE

Hiçbir yerinde "Responsibilities" geçmiyor. **0.92 güvenle ve yirmi yetenek
okunmuş halde reddedildi** — model ilanı anlamıştı, kapı cevabı çöpe attı.
`job_analysis` **v2** sorumlulukları metinde ne varsa ondan türetiyor
(niteliklerden, düz yazıdan, ekip tarifinden); hiç iş tarif etmeyen bir metin
zaten `low_confidence`'a düşüyor, yani aynı ilan iki kez reddediliyordu.

**Action:** `no_responsibilities` için yazılmış ICU `select` dalını ve varsa
çeviri anahtarını **kaldırın**. Bir `other` dalınız varsa bu değer artık ona
düşmez — hiç gelmez. Ekranda başka bir iş yok: kalan yedi değer aynı, sunulan
çözüm yolları (`paste_full_posting`, `continue_as_general_cv`) aynı.

### B-071 · Yedinci `ExtractionWarningCode`: `unsupported_by_source`

**Since:** commit <bu PR> · Aşama 4 dilim D · **Spec:** `spec/07-subsystems.md`
§ 31.6 · `shared/wire/ExtractionWarningCode`

**Neden:** çıkarım, belgede olmayan bir teknoloji yazabiliyor ve bugüne kadar
bunu kimse kontrol etmiyordu. Gerçek bir CV `utilizing **SQL** queries to model
complex business reporting logic` diyor; yazılan atom `utilizing advanced **SQL
Server** queries, optimizing analytic data layers` oldu — başka ve daha özgül
bir ürün, arkasında `skills`'e giren `mssql` ile. Aynı yükleme, belgede hiç
geçmeyen `Kafka`'yı About paragrafına yazdı. P3 yalnız Faz D'de, yani modelin
*yeniden yazdığı* yerde uygulanıyordu; *çıkardığı* şeyin sayfada olup olmadığını
soran hiçbir şey yoktu.

**Action:** `ImportWarning.code` için ICU `select`'ine yedinci dal ekleyin.
Önerilen anlam: *"Bu satırda, yüklediğiniz belgede geçmeyen bir ad var —
kontrol edin."* Kod `unsupported_by_source`, ve diğer altısı gibi
`sectionOrder`/`entryOrder` taşıyor, yani gözden geçirme ekranında satıra
bağlanıyor. Şemada da yayımlandı (`GET /v3/api-docs`).

**Dikkat — bu kodu model üretmiyor.** Extraction şeması hâlâ altı değer
listeliyor; yedincisini pipeline belgeye karşı üretiyor. Yani prompt sürümü
değişmedi, fikstürler ve cache geçerli.

**Ölçülmemiş bir yanı var ve söylüyoruz:** yanlış pozitif oranını
ölçemedik. Kayıtlı hiçbir fixture'ın **kaynak belgesi diskte değil**
(`local-record` cevabı saklıyor, girdiyi değil), o yüzden kontrolü ancak
uyuşmayan bir belgeye karşı koşturabildik. Uyarı engelleyici değil; ekranda
gürültü yaparsa duymak istiyoruz.

## ACK — frontend tamamladı, backend arşivleyebilir

_(`B-037`…`B-070`'in hepsi kapandı ve `resolved/to-frontend-2026-08.md`'de —
hangi dilimin hangisini kapattığı orada. Aşağıdakiler **hâlâ canlı olan**
kayıtlar; gerisi arşive indi.)_

**`B-062`-`B-070`'in dokuzu da kapandı** (2026-08-29/30), notları arşivde.

**`B-070`'i uygularken telde `F-025`'i açtık:** `companyName` bir satırda
**`"not specified"`** geliyor — `""` değil, yani "boş dize hiç dönmez" kuralı
onu tutmuyor ve ekranda bir şey söylüyormuş gibi duran bir etiket üretiyor.
İstemcide çözmek yer tutucu kara listesi demek olurdu; oraya girmedik.

**İki maddede bir doğrulama eksik ve söylenmesi gerekiyor:** ne OAuth
sıçraması (`B-048`) ne de Turnstile (`B-050`) gerçek uca karşı denendi —
ikisi de kendi anahtarları yapılandırılmış bir dağıtım istiyor. Bugün
doğrulanan şey mock'a karşı.

**`B-059` kapandı ama EK C.1'in maddesi kapanmadı.** Alt işleyen listesi doğru;
eksik olan **sağlayıcı listesinin kendisi** — hangi model, ona ne gidiyor,
ücretsiz katman eğitimde kullanıyor mu. Model seçimini bekliyor. **Yayın
öncesi kontrol listesi hâlâ açık.**

---

## Kalıcı kurallar

Eski maddelerin `spec/`'e işlendiği yerlerin tablosu
`resolved/to-frontend-2026-08.md`'ye taşındı (2026-08-24) — dosya sınırı.
