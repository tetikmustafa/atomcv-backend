# Aşama 3 kapanışından sonra · sayfanın şekli — dilim I-J-H ve K

> Dilim A-G ölçümün kendisini ve dört bulgusunu indirdi
> (`stage-3-post-closure-e2e.md`). I-J-H sayfanın **şeklini** indirdi, K ise onu
> **referans belgeye karşı** doğruladı. `current.md` yalnız canlı olanı taşıyor;
> ayrıntı burada.

## Kapanış sonrası · dilim I-J-H — sayfanın şekli (2026-09-07)

Tek sayfada altı bölüm, ilana göre doldurulmuş. Üçü de ölçüme dayalı.

**Sapma — § 20 seçimi saf bütçe yarışı diye tarif ediyor, o bir CV üretmiyor.**
Ölçülen koşu sayfaya yirmi atom koydu, yirmisi de Projects'ten: deneyim yok,
yetenek yok, özet yok — her biri "hangi atom puan başına daha değerli"nin doğru
cevabıydı. **`SectionFloor`** her türe önce taban ayırıyor (About 1 paragraf ·
Education 1 · Experience 2×2 · Projects 2×3 · Tech Stack 3 · Languages 2 =
**576/708pt**), kalan **132pt** ilana göre yarışıyor. Sıra sabit, seçimde ve
sayfada aynı. **Taban bir tavandır, talep değil:** profilde yoksa basılmaz,
eksikse ne varsa girer. About'un ayrıca *tavanı* var — bir CV bir özet.

**Düzeltme — `inline_list` maliyeti.** Renderer Tech Stack'i tek blokta düz
satır basarken Faz C her satıra entry mobilyası yazıyordu: **35.43pt**, beş
satırda **169.55pt**, sayfanın dörtte biri. Prob hiç yoktu; artık
`LatexCalibrationIT` ölçüyor ve cevap "kendi sabiti gerekmiyor".

**Düzeltme — § 18.4'ün `no_responsibilities`'i kalktı.** Gerçek ilanların çoğu
başlıksız nitelik listesi; ölçülen ilan 0.92 güven ve 20 yetenekle reddedildi.
`job_analysis` **v2** sorumlulukları metinde ne varsa ondan türetiyor; yetenek
kuralı sıkı kaldı (`postingSkills` § 21.6'nın sözlüğü). İş tarif etmeyen metin
zaten `LOW_CONFIDENCE`; ikisi aynı ilanı iki kez reddediyordu. `B-072`.

**Ekleme — üç import kararı ikinci yazıcıyla paylaşıldı:** `layoutFor`
(Languages da `INLINE_LIST`, `V6`), `reachableMinimumFor` (About entry'si 1,
`V7`), `hangsOffItsSection` (özet bölüme asılır, `V8` — uydurulan "Professional
Summary" başlığı gitti). `EphemeralProfileWriter` üçünü de kaçırmıştı.

**Tamir etmeye kalkma — testlerin öğrettiği üçü:**
- **`improveBySwapping()` tabanı yerleştirir yerleştirmez takas ediyordu.**
  `reservedByFloor` ayrı bir küme: `forcedByLock` "kullanıcı seçti" demek ve
  `pinnedCostPt` onu sayar; taban kullanıcının seçimi değil.
- **Golden, tabanın bir entry'yi 3'lük asgarisinin 2'siyle açtığını yakaladı**
  (§ 20.3). Entry artık açılmadan önce fiyatlanıyor.
- **Taban "nerede" değil "ne kadar" demeli.** İlk hali About ve Languages'ı
  serbest atom sanıyordu; gerçek profil ikisini de entry'de tutuyor.

**Canlı kalanlar:**
- **Aynı 4-sayfa belge iki kez faturalandı**, tek `profile_extract` satırı var;
  `job_id` indiği için bir sonraki tekrar kendini gösterir.
- **Faz D eşikleri ölçüldü, ikisi de doğru çıktı:** bir profilde en yüksek skor
  0.3577 (Faz D koşmadı), diğerinde 0.4313 (koştu). § 21.2'nin sayıları profile
  göre kenarda; **ayarlamak yeni ölçüm ister**, karar spec'in.
- **Altı bölümün *sert* tabanı sığmazsa sayfa limiti kazanır**, en alttaki
  düşer. Ölçülen sayfada olmuyor (386/708pt); kendi testi var.

## Kapanış sonrası · dilim K — referans belgeye karşı (2026-09-07)

Gerçek `master_cv.tex` içe aktarıldı, gerçek `ilan.txt`'ye karşı üretildi ve
çıkan sayfa **referans şablonun kendisiyle** karşılaştırıldı. Kalıcı kararlar
`spec/07-subsystems.md` § 31.3.1 ve § 33.4.1, `spec/06-pipeline-d-g.md` § 21.2
ve § 22.4.1'de.

### Ölçülen — içe aktarım (Bulgu 1, aynı dosyayla tekrar)

`master_cv.tex`, `mode=replace`, gerçek çağrı (`openai/gpt-5.6-sol`, 4611 girdi
/ 10373 çıktı jetonu, 122 sn, `outcome=success`). **`completed`, 6 bölüm, 84
atom, 20 uyarı** — hiçbiri `unsupported_by_source` değil, yani çıkarım sadakat
muhafızının **84 atomda 0 yanlış pozitifi**. Dilim B'nin tripwire'ı bir daha
tetiklenmedi.

Çıkan profil `master_cv.tex`'e karşı satır satır karşılaştırıldı: **4/4** About
varyantı, **7/7** Tech Stack satırı, **14/14** proje ve **56/56** madde,
**15/15** deneyim maddesi — hepsi birebir. Languages iki satır. Tek kusur
Education'daki bitişik argümanlardı (aşağıda).

### Ölçülen — üretim (`ilan.txt`, gerçek embedding)

`trace.B.weights = default` · `C`: 22 seçili, 63 `BUDGET` reddi, 4.99pt boş ·
`D`: `bullet_rewrite` **0 çağrı**, `about_synthesis` 2 çağrı ve ikisi de
`NUMBER_INVENTED` ile reddedildi (orijinal basıldı) · `F`: **1 sayfa, 1 deneme**.

Sayfa: About (bir paragraf) · Education · Experience (2 rol) · **Projects
(Order Management System + Syllabus Creator App, tam ikisi)** · Tech Stack
(7'nin 4'ü) · Languages. Elle yapılmış sürümün seçtiği iki projenin aynısı, ve
**uydurulmuş `Software Engineering Practices` kategorisi yok**.

### Düzeltmeler

**R1 — About artık `esumeParagraphListStart` + tek `esumeItem`.** Beşinci
düzen `PARAGRAPH`, `V9` eski satırları taşıyor, `B-073` frontend'e gitti.

**R2 — Tech Stack `	extbf{Kategori}{: öğe, öğe} \` satırları.** `InlineRow`
satırı ilk iki noktadan ayırıyor; ölçüm de aynı şekli görüyor
(`MeasurableItem.layout`), yoksa basılan satır ölçülenden geniş olurdu.

**Faz D'ye üç tür gönderilmiyor** (`SKILL`, `LANGUAGE`, `ABOUT_PARAGRAPH`).
Bir Tech Stack satırı ilana karşı en iyi puanı alan atom — çünkü ilanın kendi
kelimelerinden oluşan bir liste — ve § 21.4'ün prompt'u ona uygulanınca
kategoriyi yeniden adlandırmaya davet ediyor. About ayrıca iki kez isteniyordu.

**`.tex` çıkarıcısı bitişik argümanları ayırıyor.** `{...GPA: 3.21}{2022 -- 2026}`
→ `3.212022 -- 2026` idi; ayraç yalnız ikinci öbek noktalama ile başlamıyorsa
giriyor, yoksa `	extbf{Kategori}{: öğe}` bozulurdu.

**`SectionFloor` eşit puanlı entry'leri sözcüklemeye göre sıralıyor.** Golden
fixture'ın ilk koşusu yakaladı; aşağıda.

### Canlı olanlar

**Tamir etmeye kalkma — üçü de bilinçli:**
- **Bir inline satırın öğeleri sade diziliyor, işaretleri ne derse desin.**
  Çıkarım not bulduğunu işaretliyor ve her öğesi teknoloji olan bir listede bu
  satırın %70'i demek. Etiket de sade: kalın zaten vurgunun kendisi. Karar
  render'da, içerikte değil — `contentHash` düz metin üzerinden hesaplandığı
  için işaretleyerek kalın yapmak ölçülmüş maliyeti **geçersizleştirmezdi**.
- **`PARAGRAPH` `INLINE_LIST`'e katlanmadı.** İkisi de etiketsiz liste açıyor,
  ama inline satırın ilk iki noktası kalın diziliyor; iki noktalı bir özet
  yanlış yerden kalınlaşırdı. Geometri aynı, o yüzden şablon sürümü **yükselmedi**.
- **Maddelerdeki `emphasis` italik kalıyor.** Referans belge teknolojileri
  **kalın** yazıyor; bizde çıkarım hepsini `EMPHASIS` veriyor (§ 31.5.1: onlar
  hakkında bilinen şey bu) ve § 22.3 `emphasis → 	extit` diyor. Şablonda
  `	extbf`'e çevirmek bütün ölçümleri yeniden ister. **Karar spec'in.**

**Canlı kalanlar:**
- **`.env`'in `LLM_CHAIN_*`'i `application-local-fake.yml`'i eziyor.** Ortam
  değişkeni profil YAML'ının üstünde; `make dev` .env'i export ettiği için
  `chain: [fake]` hiç uygulanmıyor ve **`make dev` gerçek, ücretli çağrı
  yapıyor**. Ölçüldü: bir `local-fake` içe aktarımı 127 saniyelik bir OpenRouter
  çağrısı koştu. `CLAUDE.md`'ye yazıldı; kalıcı çözüm ya `.env`'den çıkarmak ya
  da fake profilinde `LLM_CHAIN_*` adlarını okumamak.
- **`mode=replace` `headline`'ı silmiyor.** Yeni CV'nin taşımadığı eski bir
  başlık sayfaya çıkıyor — ölçülen koşuda İngilizce bir CV'nin üstünde Türkçe
  bir unvan. Çıkarım şeması `headline` üretmiyor, yani "koru" da savunulabilir;
  **ürün kararı**, o yüzden dokunulmadı.
- **`.tex` fixture'ı artık ulaşılamaz.** Bitişik-argüman düzeltmesi çıkarılan
  metni değiştirdi, yani `v1-75f61110da11` anahtarı bir daha tutmayacak. Aynı
  dosyanın bir sonraki `.tex` içe aktarımı `make record` ister.

**Ders — golden fixture yazılmaz, okunur.** `master_cv_en` beş elle yazılmış
profilin hiçbirinin taşımadığı şeyi taşıyor: **ilanla ilgisi olmayan çok fazla
içerik.** İlk koşusunda Faz C'de bir belirlenimsizlik yakaladı — `SectionFloor`
iki entry'yi eşit puanda **UUID sırasıyla** seçiyordu, ve on dört tarihsiz
proje genel modda tam olarak eşit puanlı. Aynı CV iki kez okununca iki farklı
sayfa çıkıyordu (İlke 2). Sıra artık sözcüklemenin özetinden.
