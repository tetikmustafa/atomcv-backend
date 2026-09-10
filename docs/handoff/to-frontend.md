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

**Henüz yok — Katman B.** § 33.1'in font boyutu / margin / satır aralığı
sliderları hâlâ çalışmıyor: her şablonun ölçülmüş kapasitesi **yalnız kendi
varsayılan ayarları** için geçerli, ve ölçülmemiş bir ayarla üretim
reddediliyor. Yani iki şablon var, altı slider yok. § 33.3'ün "yeniden
hesaplanıyor" akışı geldiğinde ayrı bir madde olarak gelir.

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
