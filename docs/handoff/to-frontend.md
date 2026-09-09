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

_Beş madde açık ve dosya sınırın üstünde: sebep arşivleme değil, ACK bekleyen
backlog. Hepsi ACK'lendiğinde `resolved/`'a iner._

### B-080 · Gizlilik metni: anonim veri artık veritabanına yazılıyor

**Since:** commit `<bu PR>` · **Spec:** § 51.6.1 (sapma), § 57.4 · `retention/RetentionSweeper`

**Ne oldu:** anonim oturumun profili Redis belgesi değil, `profiles` tablosunda
**sahibi olmayan ve süresi olan** bir satır. Veritabanından **beş dakikada bir**
süpürülüyor; ama § 49.2'nin yedek saklaması 7 gün + 4 hafta + 6 ay olduğu için
bir yedeğe yakalanan anonim CV **şifreli arşivde en fazla altı aya kadar**
kalabilir.

**Neden:** anonim kişi artık profilini düzenleyip ilana göre CV üretecek, ve bu
hesabın kullandığı kod yolunun aynısı. İkinci bir depo her adımın ikinci bir
uygulaması demekti — ve zaten sapmıştı: eski efemer yazıcı bir bölüm başlığını
iki kez basıyordu. Bedeli bilerek kabul edildi ve küçültülmedi.

**Action:** § 57'nin gizlilik metnindeki **"NE KADAR SAKLIYORUZ"** maddesini
güncelleyin. Backend'in yazdığı hâli: *"anonim mod son etkinlikten 2 saat sonra
(veritabanından beş dakika içinde silinir; şifreli yedeklerde en fazla altı aya
kadar kalabilir)"*. Parantez içi **atlanmamalı** — kaldırıldığında metin
tutmayan bir söz verir. Ekranda ayrıca bir uyarı gerekmiyor; bu, politika
metninin cümlesi.

### B-079 · Anonim `dailyGenerationQuota` artık 0 — tutulamayan bir sözdü

**Since:** commit `<bu PR>` · **Spec:** § 35.7 (sapma) · `identity/service/Capabilities`

**Ne oldu:** oturumsuz çağıranın `capabilities` bloğunda
`dailyGenerationQuota` **5 yerine 0** dönüyor. `dailyProfileQuota` **3 olarak
kalıyor**, `maxAtoms` 60 olarak kalıyor.

**Neden:** anonim üretim kurulmadı. `POST /generations` hesap istiyor ve
oturumsuz çağırana `AUTHENTICATION_REQUIRED` dönüyor; blok ise "bugün beş
hakkın var" diyordu. Blokta bunu söyleyen başka bir alan da yok —
`canSaveHistory` üretmekle değil, üretileni saklamakla ilgili. Yani ekran
doğru okuyup yanlış şey gösteriyordu ve kullanıcı ilk tıklamada 401 alıyordu.
Profil tarafı böyle değil: `POST /profiles/import` anonim oturumu kabul ediyor
ve çıkan profil giriş anında hesaba geçiyor — o yüzden o sayı duruyor.

**Action:** anonim ziyaretçiye "üret" yolunu **kotaya bakarak** açıyorsanız
artık kendiliğinden kapanır; ayrı bir bayrak beklemeyin, `canGenerate` diye bir
alan yok. Kotayı okumayıp butonu her zaman gösteriyorsanız, 0 gördüğünüzde
kayıt/giriş çağrısına çevirin. Anonim üretim indiği gün bu sayı 5'e döner ve
size yeni bir madde gelir.

### B-077 · Beceriler artık yazılırken kanonikleşiyor: yankı gönderileni tutmaz

**Since:** commit `<bu PR>` · **Spec:** § 31.5 · `profile/service/AtomService`

**Ne oldu:** `POST`/`PATCH` ile gönderilen `skills`, saklanmadan önce
`SkillNames`'den geçiyor — ve yanıt **saklanan biçimi** döndürüyor. Yani
`"Spring Boot"` gönderirsiniz, `"spring-boot"` okursunuz; `"postgresql"`
gönderirsiniz, sözlük onu `"postgres"`'e çevirdiği için o gelir. İki farklı
yazım tek beceriye düşerse liste kısalır.

**Neden:** o kolon bir **anahtar** olarak okunuyor — Faz B puanlaması ve
`RunMarking`'in bir vurgunun teknoloji olup olmadığına karar vermesi ona bakıyor.
İçe aktarım bunu zaten yapıyordu, editör yapmıyordu: aynı kolonun bir satırı
anahtar, öteki satırı düz yazıydı, ve ham saklanan bir beceri kalın yazımını
kaybediyordu.

**Action:** iki şey. (1) Kaydettikten sonra ekrandaki listeyi **yanıttan**
tazeleyin, gönderdiğinizden değil — yoksa kullanıcı yazdığını görür, sunucu
başkasını saklar. (2) Kullanıcı "Spring Boot" yazıp `spring-boot` görecek;
bunu bir hata gibi göstermeyin. İçe aktarılmış profillerde zaten böyle
görünüyordu, yani ekran açısından yeni bir şekil değil.

### B-076 · EK C.1'in AI sağlayıcı listesi — yayımlanacak gerçekler

**Since:** commit `<bu PR>` · **Spec:** EK C.1 (*"AI sağlayıcı listesi güncel ve
açık"*) · `llm/telemetry/ProcessorAudit`

**Neden:** madde model seçimini bekliyordu; model belli. Aşağıdakiler ölçüldü —
sağlayıcının endpoint API'sinden ve gönderdiğimiz gövdeden.

**Kimler.** Biz → **OpenRouter** (broker) → yukarı akış. Bu modelin **yedi
endpoint'i** var: **OpenAI** (üç varyant), **Microsoft Azure** (`azure`,
`azure/us`, `azure/eu`), **Amazon Bedrock** (`us-east-1`) — istek yalnız modeli
adlandırdığı için dördü de listede olmalı, hangisinin karşıladığı yanıtta yazmaz.
Zincirin ikinci halkası **Gemini**, yani anahtarı olan dağıtımda **Google** da
listede.

**Ne gidiyor.** CV metni, ilan metni, madde metinleri, özet ve ön yazı girdileri
(`profile_extraction`, `job_analysis`, `bullet_rewrite`, …) — **kişisel veri**.

**Eğitim.** Her istekte `provider.data_collection: "deny"` gidiyor. Metinde
*"sağlayıcılar eğitmiyor"* değil **"eğitebilecek sağlayıcıya yönlendirilmiyor"**
demek doğru: OpenRouter'ın kendi loglama politikası ayrı, hesap ayarında.

**Liste yapılandırmadan türüyor, sabit değil** (karar 2026-09-09: model
kısıtlanmıyor — yarın Claude ya da DeepSeek olabilir). Metni **"şu an kullanılan
model"** diye yazın ve model değişince sayfayı gözden geçirin. Backend açılışta
kendi listesini logluyor (`ProcessorAudit`: *"Content may be sent to […]"*) —
**yayımlanan sayfayı o satıra karşı kontrol edin.**

**Bugünkü model:** `openai/gpt-5.6-sol`, bağlam 1.050.000; fiyat (standart OpenAI
endpoint'i, %50 kampanyalı) milyon token başına 2 / 10 / 0.2 USD.

**Action:** gizlilik politikasının alt işleyen bölümünü bu adlarla ve "ne
gidiyor" listesiyle yazın.

### B-078 · `contentGrant.accessedAt` geri döndü — artık yazan bir şey var

**Since:** commit `<bu PR>` · **Spec:** § 48.4 · `generation/support/SupportRead`

**Ne oldu:** `B-075` alanı telden kaldırmıştı çünkü onu **yazacak hiçbir şey**
yoktu. Şimdi var: **çevrimdışı destek okuyucusu** (karar 2026-09-09) grant
açıkken üretimi sahibinin bağlamında okuyor ve `accessed_at`'i damgalıyor. Uç
değil, sunucuda elle çalıştırılan bir komut — mutlak kural 3 çapraz-kullanıcı
okuma yolu bırakmıyor ve yılda birkaç kez olan bir şey için kalıcı bir delik
açmaya değmez.

**Action:** `B-075`'te kaldırmanızı istediğim dalı **geri getirin** —
`grant.accessedAt` varsa "şu tarihte okundu", yoksa "henüz okunmadı". Bu kez
cümle doğru: alan artık yapısal olarak null değil, gerçekten okunmadığı için
null. `npm run gen:api` alanı tipe geri koyacak.

**İlk okuma sabittir:** kolon tek bir an tutuyor, yani "bakıldı mı, ve ne
zamandan beri" sorusunu cevaplıyor. İkinci bir okuma damgayı **oynatmıyor**.

_(`B-075` bu maddeyle kapandı sayılır — ikisini birlikte ACK'leyin.)_

### B-075 · `contentGrant.accessedAt` telden kalktı — tutulamayan bir sözdü

**Since:** commit `<bu PR>` · **Spec:** `spec/…` § 48.4 ·
`generation/api/dto/FeedbackResponse.Grant`

**Ne oldu:** `contentGrant` artık `open`, `expiresAt` ve `revokedAt` taşıyor;
**`accessedAt` yok.** Kolon (`support_grants.accessed_at`) yerinde duruyor.

**Neden:** o alanı **hiçbir şey yazmıyor** ve yazamaz — başka bir kullanıcının
içeriğini okumak destek tarafına bakan bir yol ister, mutlak kural 3 böyle bir yol
bırakmıyor. Alan her zaman null, yani ekran gerçekten bakılmış olsa da **"kimse
bakmadı"** diyor: denetim izi değil, denetim izi kılığında bir varsayım.

**Action:** `Feedback.tsx`'te `grant.accessedAt`'ten üretilen dalı ve
`feedbackGrantRead` metnini **kaldırın** (mock'takini de). Kullanıcıya izin
hakkında söylenebilecek doğru şeyler duruyor: açık mı, ne zaman doluyor, geri
çekildi mi. "Okundu / okunmadı" bunlardan biri değil — ve "okunmadı" demek,
söyleyemediğimiz şeyi söylemek. `npm run gen:api` alanı tipten de düşürecek.

**Söz geri gelecek:** çevrimdışı destek okuyucusu (karar 2026-09-09) `accessed_at`'i
damgaladığı gün alan aynı adla döner.

## ACK — frontend tamamladı, backend arşivleyebilir

_(`B-071`…`B-074`'ün dördü de karşılandı ve `resolved/to-frontend-2026-09.md`'ye
indi 2026-09-09'da, dosya sınırı; `B-037`…`B-070` bir öncekinde. Aşağıdakiler
**hâlâ canlı olan** kayıtlar.)_

**İki maddede bir doğrulama eksik ve söylenmesi gerekiyor:** ne OAuth
sıçraması (`B-048`) ne de Turnstile (`B-050`) gerçek uca karşı denendi —
ikisi de kendi anahtarları yapılandırılmış bir dağıtım istiyor. Bugün
doğrulanan şey mock'a karşı.

**EK C.1'in sağlayıcı listesi maddesi artık `B-076`'da** — model seçildi ve
gerekler orada yazılı. Yayın öncesi kontrol listesi o madde kapanınca kapanıyor.

---

## Kalıcı kurallar

Eski maddelerin `spec/`'e işlendiği yerlerin tablosu
`resolved/to-frontend-2026-08.md`'ye taşındı (2026-08-24) — dosya sınırı.
