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

### B-088 · Faz G'nin manuel toggle'ı indi — yeni uç, yeni hata kodu, yeni sonuç alanı

**Since:** backend `f07db3d`, `9758764` · Aşama 4 · § 24.4, § 35.3

**Neden:** Aşama 4 Faz G ile açıldı. Kullanıcı artık üretilmiş bir CV'den elle
madde çıkarabiliyor ve geri ekleyebiliyor. Düzenleme **render edilmiş belgeye
değil selection state'e** uygulanıyor (§ 24.1) — sayfa sınırı yirmi düzenleme
sonra da duruyor, çünkü her düzenleme sözü veren seçimden tekrar geçiyor.

**İstenen — dört şey:**

1. **`POST /api/v1/generations/{id}/selection`** · gövde `{ include?: uuid[],
   exclude?: uuid[] }` · **202 + job**, üretimle aynı SSE akışı. Şekil için
   OpenAPI otorite; burada olan şey ekranın nasıl davranması gerektiği.
   **Kota harcamıyor, LLM çağırmıyor** — "günlük hakkın gidecek" uyarısı
   göstermeyin.

2. **İş bittiğinde dönen `generationId` yeni bir üretimdir.** Terminal olay
   ayrıca **`supersededGenerationId`** taşıyor: düzenlenen satırın id'si.
   Elinde eski id'yi tutan ekran, geçmişi yeniden okumadan hangisine
   taşındığını buradan öğrenir.

3. **`GENERATION_SUPERSEDED` (409)** yeni bir hata kodu, `params` taşımıyor
   (§ D.6.1 tablosuna işlendi). Zaten değiştirilmiş bir üretimi düzenlemeye
   çalışınca dönüyor. Cümlesi "bu CV'nin daha yenisi var, onu düzenleyin"
   yönünde olmalı; **çözüm eylemi eklemedik**, `ResolutionAction` sözlüğü
   büyümedi.
   Diğer retler: boş düzenleme, aynı atom iki listede, ve **bu üretimin hiç
   tartmadığı bir atom** → hepsi `400 VALIDATION_FAILED`, `params.fields`
   suçlu id'leri taşıyor.

4. **`GET /generations` artık `superseded` satırları listelemiyor** ve
   **`total` de onları saymıyor.** Yirmi düzenleme yirmi bir satır demek, biri
   CV. **Dikkat:** `total`'ı hesap silme ekranı okuyor (`F-020`) — artık satır
   değil **CV** sayıyor. Silme yine emekli taslakları da götürüyor; metin
   "N CV" diyorsa doğru, "N kayıt" diyorsa güncellenmeli.
   Emekli satır **kaybolmuyor**: `GET /generations/{id}` ve indirme hâlâ
   çalışıyor — işverene gönderilen CV'nin durduğu söz buna dayanıyor.

**Not:** Faz G'nin **doğal dil** yarısı `B-089`'da — `B-088` yazıldığında henüz
yoktu, aynı gün indi.

### B-089 · Faz G'nin doğal dil yarısı — `POST /{id}/edits`

**Since:** backend `fd4368a`, `85332ba` · Aşama 4 · § 24.2

**Neden:** `B-088`'in toggle'ı id istiyor; bu uç **cümle** istiyor. "Android
maddesini çıkar ve Kubernetes olanı geri koy" tek istek. Aynı yeniden koşu
yolundan geçiyor, yani sayfa sınırı yine korunuyor.

**İstenen — dört şey:**

1. **`POST /api/v1/generations/{id}/edits`** · gövde `{ instruction: string }`
   (boş olamaz, **en fazla 500 karakter**) · **202 + job**, `B-088`'inkiyle
   aynı SSE akışı ve aynı `supersededGenerationId`.

2. **Bu uç kotadan düşüyor** — toggle'ın aksine. Bir model çağrısı, ve günün
   üretim hakkından bir tane harcıyor. Ekranda söylenmesi gereken fark bu:
   **elle aç/kapa bedava, cümle değil.** `429 QUOTA_EXCEEDED` dönebilir,
   `Retry-After` taşır.

3. **`EDIT_NOT_UNDERSTOOD` (422)** yeni hata kodu, `params` taşımıyor,
   çözümü **`retry`**. Cümle hiçbir satırı adlandırmadığında dönüyor ve
   **sık dönecek** — bu bir arıza değil, tasarım: yanlış maddeyi silmektense
   hiçbir şey yapmamak yeğleniyor, çünkü kullanıcı fark etmeyebilir.
   **Metniniz ne yapabildiğini söylesin**, yoksa çıkmaz sokak olur. Bu uç
   *yapmaz*: bir maddeyi **yeniden yazmak**, tonu değiştirmek, sayfayı
   uzatıp kısaltmak. Kota **iade ediliyor** bu durumda.

4. **`GENERATION_SUPERSEDED` (409) burada da geçerli** — `B-088`'deki aynı
   kural, aynı cümle.

**Bilmenizde fayda var:** modele atom id'si hiç gösterilmiyor. Satırlar
numaralandırılıp gösteriliyor, model sayı döndürüyor. Yani "olmayan bir
maddeyi sildi" diye bir hata sınıfı yok; olabilecek en kötü şey yanlış ama
**var olan** bir satır — ki kullanıcı sonucu görüyor.

### B-090 · İkinci şablon indi — `compact`, ve `templateId` artık bir şey yapıyor

**Since:** backend `e972432` · Aşama 4 · § 33.5, § 33.1

**Neden:** Aşama 4'ün ikinci maddesi. Klasik'in yanına **kompakt** geldi:
aynı yapı, daha sıkı geometri — sayfa ~70 madde satırı tutuyor, klasik 60.
Çok deneyimli profiller için.

**İstenen — iki şey, ikisi de yeni uç değil:**

1. **`capabilities.allowedTemplates` artık `["classic", "compact"]`.** Zaten
   registry'den türüyordu, yani şema değişmedi; **değişen, listenin artık iki
   eleman taşıması.** Bir yerde "tek şablon var" varsayımı varsa (seçim
   arayüzü göstermemek gibi) orası açılmalı. Liste **sıralı** ve öyle kalacak.

2. **Profil tercihlerindeki `templateId` artık gerçekten etkili.** Alan
   Bölüm 14.4'ten beri vardı ve **backend onu yok sayıyordu** — profil ne
   derse desin her CV klasik çıkıyordu. Artık üretim onu okuyor. Yani
   ayarlardaki şablon seçicisi (varsa) bugüne kadar sessizce hiçbir şey
   yapmıyordu; bugünden sonra yapıyor. **Tanımadığı bir id klasiğe düşüyor**,
   hata vermiyor.

**Şablon seçimi istekte değil, tercihte.** `POST /generations` gövdesine
şablon alanı **eklenmedi** — "bu üretim için şu şablon" diye tek seferlik bir
seçim yok, kişi ayarından seçiyor. İhtiyaç varsa `F-nnn` ile isteyin.

**Katman B `B-091`'de** — `B-090` yazıldığında henüz yoktu.

### B-091 · Katman B indi — slider'lar çalışıyor

**Since:** backend `a2f82c7`, `d101600` · Aşama 4 · § 33.1, § 33.2, § 33.3

**Neden:** `B-090`'da "altı slider yok" demiştim; artık var. Kullanıcı font
boyutunu, margin'i, satır aralığını, font ailesini ve vurgu rengini
değiştirebiliyor, ve CV o ayarlarda çıkıyor — **sayfa sınırı hâlâ tutarak.**

**İstenen — üç şey:**

1. **`PATCH /profile/preferences` gövdesindeki `defaults` artık bir
   `appearance` nesnesi kabul ediyor.** Beş alan, **hepsi opsiyonel**:
   `fontSizePt` (9–12), `marginInches` (0.4–1.0), `lineSpacing` (0.9–1.3),
   `fontFamily` (`MODERN` \| `SERIF` \| `SANS`), `accentColor` (6 hane hex,
   `#` yok). Şekil için OpenAPI otorite.
   **Bir alanı göndermemek "şablonun kendi ayarı" demek** — sıfırlamak için
   `null` gönderin, "0" ya da varsayılan değeri değil. Bir slider'ı oynatan
   kişi tek bir alan gönderir; şablonun varsayılanı sonra değişirse gerisini
   beraberinde götürür.
   Aralık dışı bir değer **400**. Aralıklar dar, çünkü § 33.2 kötü görünen bir
   sonucun fiziksel olarak imkânsız olmasını istiyor; 9pt yasal ve ATS
   okunabilirliği için bir uyarı yazmaya değer, ama **engellenmiyor.**

2. **İlk üretim biraz daha temkinli, ve bu görünmüyor.** Hiç derlenmemiş bir
   geometri için sayfanın **%92'si** harcanıyor (§ 33.3'ün "tahmin + %8 pay"ı),
   arka planda bir ölçüm kuyruğa giriyor, ve o indikten sonra aynı ayarda
   üretimler sayfanın tamamını kullanıyor. **Ekranda bir şey yapmanız
   gerekmiyor** — bekleme yok, hata yok, yalnız ilk CV birkaç satır daha az
   tutabilir.
   § 33.3'ün "yeniden hesaplanıyor…" göstergesini istiyorsanız, o durumu
   yayımlayan bir uca ihtiyacınız var — **şu an yok**, bir `F-nnn` ile isteyin.

3. **Renk bedava, diğer dördü değil** (§ 33.1). Rengi değiştirmek hiçbir
   ölçümü geçersizleştirmiyor, geometrik dördü değiştirmek bir derleme
   demek — arka planda, ve kullanıcıyı bekletmeden. Arayüzde bu farkı
   göstermek zorunda değilsiniz; sadece "renk değiştirmek yavaş" gibi bir
   uyarı yazmayın, doğru değil.

**Ayrıca `B-092`:** üçüncü şablon `modern` indi, ve varsayılan vurgu rengi
**siyah değil**.

### B-092 · Üçüncü şablon — `modern`, ve ilk renkli varsayılan

**Since:** backend `78f89eb` · Aşama 4 · § 33.5

**Neden:** § 33.5'in üçüncü şablonu. Klasikten ferah (~51 madde satırı, klasik
60), bölüm başlığının altındaki **çizgi renkli**, başlık metni siyah.

**İstenen — iki şey:**

1. **`capabilities.allowedTemplates` artık `["classic", "compact", "modern"]`.**
   `B-090`'daki ile aynı mekanizma, üçüncü eleman. Şema değişmedi.

2. **`modern`, varsayılan `accentColor`'ı siyah olmayan tek şablon** —
   `1D4ED8`. Bir şablon seçicide önizleme/renk örneği gösteriyorsanız, "her
   şablon siyah başlar" varsayımı artık yanlış. Kullanıcı yine Katman A'dan
   değiştirebiliyor ve bu **ölçümü geçersizleştirmiyor** (`B-091`, 3. madde).

**Bilinen sınır — söylüyorum ki bir uçtan beklemeyin.** § 33.2 margin'i 1.0
inç'e kadar açıyor, ama **~0.6 inç'ten geniş bir margin ölçülemiyor**:
kalibrasyon belgesi sayfaya sığmıyor. O aralıktaki ayarlar **her üretimde**
tahminle koşuyor, yani sayfanın %92'si kalıcı olarak harcanıyor. Güvenli ve
görünmez — kullanıcı yine CV alıyor — ama slider'da "bu değerden sonrası
biraz daha az yer kullanır" gibi bir şey göstermek isterseniz o durumu
yayımlayan bir uç **yok**, `F-nnn` ile isteyin.

**Bilmenizde fayda var:** aralıklar backend'de `TemplateCustomization`'da ve
uçta **iki kez** yazılı. Aralık dışı saklanmış eski bir tercih üretimi
düşürmüyor, şablonun kendi ayarına düşüyor — yani bir kullanıcı asla
"CV üretilemiyor" durumunda kalmıyor.

### B-093 · Başvuru takibi indi — dört uç, ETag'li düzenleme

**Since:** backend `4d3f0b4` · Aşama 4 · § 55, § 35.3, § 35.6

**Neden:** Aşama 4'ün üçüncü maddesi. Kullanıcı nereye başvurduğunu, hangi
CV'yle başvurduğunu ve ne olduğunu kaydedebiliyor. Tablo V1'den beri
duruyordu; **migration yok.**

**İstenen — dört uç, hepsi hesap gerektiriyor:**

| | |
|---|---|
| `GET /api/v1/applications` | Tamamı, yeniden eskiye. **Sayfalama yok** — cursor beklemeyin. |
| `POST /api/v1/applications` | `201` + `Location` + `ETag`. |
| `PATCH /api/v1/applications/{id}` | **`If-Match` zorunlu** (§ 35.6). |
| `DELETE /api/v1/applications/{id}` | **`If-Match` zorunlu** — düzenlemeyle aynı sebep. |

**Dört şeye dikkat:**

1. **`PATCH` kısmi.** Göndermediğiniz alan **olduğu gibi kalıyor** — bir satırı
   `applied`'dan `interview`'a taşımak için notları geri göndermeniz gerekmiyor,
   ve göndermemeniz daha doğru: başka bir sekmedeki düzenlemeyi ezmezsiniz.
   **Notu temizlemek `clearNotes: true` istiyor** — `notes: null` "dokunma"
   demek, "boşalt" değil.

2. **`If-Match` yoksa `428 PRECONDITION_REQUIRED`, bayatsa `412 VERSION_CONFLICT`.**
   Yeni hata kodu yok, ikisi de var olan sözlükte. `ETag` her yazma
   cevabında dönüyor; bir sonraki düzenleme için onu saklayın.

3. **`generationId` null olabilir ve iki farklı şey demek değil.** Null =
   **o CV silinmiş**. Başvuru kaydı belgeden uzun yaşıyor (`ON DELETE SET
   NULL`), yani satır duruyor ama indirme yok — **o satırlar için indirme
   düğmesi göstermeyin.**

4. **Başkasının `generationId`'si `400`, `404` değil** — alan yanlış, satır
   eksik değil. `params.fields` `["generationId"]` taşıyor.

**Durum sözlüğü kapalı:** `applied` · `interview` · `offer` · `rejected` ·
`withdrawn`. **Hiçbir geçiş yasak değil** — kapanmış bir süreci yeniden açan
şirket bir veri hatası değil, ve backend kullanıcıyla başına ne geldiği
konusunda tartışmıyor. Arayüzde de bir geçişi kilitlemeyin.

**Henüz yok:** PDF arşivleme (§ 55'in aynı maddesinde anılıyor ama R2 istiyor,
7. karar hâlâ geçerli) ve duruma göre süzme. İkincisi sayfalama gerektiğinde
birlikte gelir.

### B-094 · DOCX indirme — ve yanına yazmanız gereken bir cümle

**Since:** backend `4b1f692` · Aşama 4 · § 22.6, § 35.3

**Neden:** Bazı ATS'ler Word istiyor. Aynı üretim, aynı içerik, ikinci bir
biçim.

**İstenen — üç şey:**

1. **`GET /api/v1/generations/{id}/download?format=docx`.** Var olan uç, yeni
   bir parametre. `format` verilmezse **`pdf`** — mevcut çağrılarınız aynen
   çalışıyor. İçerik tipi
   `application/vnd.openxmlformats-officedocument.wordprocessingml.document`,
   dosya adı `.docx` uzantılı.

2. **`format=source` şu an `400 VALIDATION_FAILED`.** § 35.3'ün haritasında
   var ama hiçbir şey servis etmiyor. Sessizce PDF döndürmüyoruz — döndürseydik
   "kaynağı indir" düğmeniz PDF indirirdi. İstiyorsanız `F-nnn` ile isteyin.

3. **Sayfa sınırı DOCX'te yaklaşıktır ve bunu söylemeniz gerekiyor** (§ 22.6).
   Maddeler **dizilmiş bir sayfaya sığanlar**; Word onları kendi fontlarının
   aldığı yere koyuyor, yani bir sayfalık bir CV Word'de biraz taşabilir.
   Backend hiçbir yerde DOCX için sayfa sayısı iddia etmiyor — çünkü dürüstçe
   edemez. **Düğmenin yanına kısa bir cümle koyun**; "PDF birebir, Word
   yaklaşık" fikri yeterli.

**Bilmenizde fayda var:** DOCX de PDF gibi `content_snapshot`'tan üretiliyor,
bugünkü profilden değil. Yani sonradan bir maddeyi düzenlemek, gönderilmiş bir
CV'nin Word hâlini de değiştirmiyor. Ve metin katmanı gerçek metin — bir ATS
kelimeleri okuyabiliyor, resim değil.

### B-096 · Yaşam döngüsü e-postaları indi — bir sayfa ve bir anahtar istiyor

**Since:** backend `feat/welcome-and-unsubscribe` · Aşama 4 · § 57.7, § 40.3

**Neden:** Ürün bugüne kadar tek bir e-posta gönderiyordu (sihirli bağlantı).
§ 57.7 listeyi yazdı ve **kapalı tuttu**: hoş geldin, silme onayı, başka yok.
Silme onayı geçen dilimde indi ve sizden bir şey istemiyordu; bu dilim hoş
geldin postasını ve onu kapatma yolunu getiriyor.

**İstenen — iki şey:**

1. **`/unsubscribe?t=<uuid>` diye bir sayfa**, ve üstünde bir düğme. Düğme
   `POST /api/v1/email/unsubscribe` çağırıyor, gövde `{ "token": "<uuid>" }`,
   cevap **204**. Oturum istemiyor — gelen kutusundan tıklanıyor, çerez
   olmayabilir. CSRF normal şekilde geçerli (sayfa sizin kökeninizde, çerezi
   okuyup çift gönderebiliyor).

   **Sayfa tıklanmadan kapatmamalı.** § 40.3'ün ön-getirme tuzağı tam burada:
   kurumsal ağ geçitleri mesajdaki her adresi kimse okumadan çekiyor, ve
   çekilince kapatan bir tasarım hiç tıklamamış kişilerin postasını keserdi.
   Bu yüzden bağlantı bir **sayfaya** iniyor, uca değil.

   **Bilinmeyen jeton da 204 dönüyor.** "Geçersiz bağlantı" diye bir ekran
   yazmayın — backend hangi jetonun canlı olduğunu söylemiyor, bilerek. Sayfa
   her durumda "kapatıldı" demeli.

2. **Ayarlarda bir anahtar.** `GET /api/v1/account` → `{ "lifecycleEmails":
   true }`, `PATCH /api/v1/account` aynı gövdeyle yazıyor ve yeni hâli
   döndürüyor. **`PUT /profile/preferences`'a koymadık**: o uç profili yerine
   koyuyor ve profilin ETag'iyle korunuyor, yani bir CV çakışması e-posta
   tercihini reddederdi — ve alanı göndermemek onu kapatmak olurdu.

**Bilmenizde fayda var:** hoş geldin postası **ilk başarılı girişte** çıkıyor,
hesap satırı yazıldığında değil. § 40.4 hesap sayımını engellemek için satırı
adres yazılır yazılmaz yaratıyor; satır tetikleyici olsaydı giriş kutusuna
adresi yazılan herkese posta giderdi. Yani yeni bir kullanıcı, sihirli
bağlantının hemen ardından **ikinci** bir posta alıyor — ekranda "hoş geldin
e-postası gönderdik" gibi bir cümleye ihtiyacınız yok, ama iki postayı
bekliyor olun.

**Silme onayı kapatılamıyor** (§ 57.4): verisinin silindiğini kişiye söylemek
zorunludur, ve kapatılabilir bir onay ona söylememenin bir yolu olurdu. Ayarlar
metniniz "bilgilendirme e-postaları" derken bunu kapsıyormuş gibi durmasın.

---

## Dağıtım bekleyen doğrulamalar

*(`B-085`…`B-087` de `resolved/to-frontend-2026-09.md`'ye indi 2026-09-09'da.
Aşağıdaki ikisi bir maddenin kapanışı değil, **bir dağıtım bekleyen doğrulama** —
o yüzden arşive inmiyorlar.)*

**Üç yerde bir doğrulama eksik ve söylenmesi gerekiyor:** ne OAuth sıçraması
(`B-048`), ne sihirli bağlantının Turnstile'ı (`B-050`), ne de `B-083`'ün
üretim/içe aktarım challenge'ı gerçek uca karşı denendi — üçü de kendi
anahtarları yapılandırılmış bir dağıtım istiyor.

**EK C.1'in sağlayıcı listesi yazıldı** (`B-076`). Yayın öncesi kontrol
listesinde kalan tek şey, yayımlanan sayfayı `ProcessorAudit`'in açılış
satırına karşı okumak — dağıtım işi, kod işi değil.

---

## Kalıcı kurallar

Eski maddelerin `spec/`'e işlendiği yerlerin tablosu
`resolved/to-frontend-2026-08.md`'ye taşındı (2026-08-24) — dosya sınırı.
