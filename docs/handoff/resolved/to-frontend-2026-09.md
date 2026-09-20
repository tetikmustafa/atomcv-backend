# → Frontend · kapanan maddeler · 2026-09

> `to-frontend.md`'den indi (2026-09-09), dosya sınırı. `B-037`…`B-070` bir
> önceki dosyada: `to-frontend-2026-08.md`.

## ACK — dördü de frontend tarafından karşılandı (2026-09-08)

**B-074** — şablon seçicisi de, `classic` için elde hazırlanmış bir önizleme
de yok: `templateId` hiçbir bileşende geçmiyor, `src/components/preview/` boş.
Yenilenecek bir görsel yok, iş yok.

**B-073** — düzen adını gösteren ya da seçtiren bir arayüz yok, dolayısıyla
karşılanacak bir `select` de yok. `paragraph` `npm run gen:api` ile üretilen
tipe girdi (üç şemada: `Section`, `SectionCreateRequest`, `SectionPatchRequest`)
ve birliği genişletmekten başka bir şey yapmadı. Fixture'a `about` bölümü
eklenmedi: mock'ta düzeni okuyan hiçbir şey yok, eklenen bölüm yalnızca
`sectionCount`/`atomCount` bekleyen testleri oynatırdı. Mock'un bölüm oluşturma
varsayılanı sunucununkiyle aynı (`bullet_list`) kalıyor — şema hâlâ öyle
diyor, `about` satırlarını `V9` taşıdı, kolon varsayılanı değil.

**B-072** — canlı şemada da doğrulandı: `/v3/api-docs` içinde
`no_responsibilities` hiç geçmiyor (neden sözlüğü zaten şemada değil, hata
kataloğunda). `no_responsibilities` dalı `errors.UNPARSEABLE_JOB_DESCRIPTION`
içinden kaldırıldı (en + tr), `gateRefusal`'ın kabul ettiği nedenlerden çıktı,
katalog testindeki neden listesi yediye indi. Yakalanmış yük de silindi:
`wireErrors` gerçekten gönderilmiş gövdeleri taşıyor, uydurulmuş bir tanesi
oraya konmadı. `F-016`'nın nöbeti — "sayımı suçlama" — kayıptan kurtarıldı ve
katalog testinde artık tek bir yük yerine `too_few_skills` dışındaki her
nedene karşı koşuyor.

**B-071** — yedinci dal `Onboarding.warning`'e eklendi (en + tr), ve
`ImportWarning.code` üretilen tipte artık yedi değer listeliyor. Kod zaten
**açık** okunuyordu (`B-069`), o yüzden tip tarafında iş çıkmadı; eksik olan
yalnızca cümleydi ve o gelene kadar uyarı `other`'a düşüyordu. Yeri de
çalışıyor: `sectionOrder`/`entryOrder` taşıdığı için satıra bağlanıyor,
testi `ReviewGate` içinde. Ortak fixture'a **eklenmedi** — ekrandaki
davranışı diğer altısından farklı değil, ve üçüncü bir uyarı e2e'deki
sayıları oynatırdı.

**Yanlış pozitif konusunda söylenecek bir şey henüz yok:** mock'ta bu kodu
üreten bir yol yok, yani gürültü ancak gerçek uca karşı görülür.

---

## ACK — `B-075`…`B-084`, dokuzu birden (indi 2026-09-09, dosya sınırı)

### `B-075`…`B-084` — dokuzu birden (2026-09-09)

- **`B-075` + `B-078` birlikte, ve net etki sıfır kod.** `grant.accessedAt`
  dalı hiç kaldırılmamıştı — `B-075` geldiğinde `Feedback.tsx` zaten "okundu /
  henüz okunmadı" cümlesini kuruyordu ve `B-078` onu geri istedi. Alan
  `api.d.ts`'te duruyor (o dosya `B-075` sonrası yeniden üretilmedi), yani
  cümle bugün de doğru: null artık "yazacak bir şey yok" değil, "kimse
  bakmadı".
- **`B-076` yazıldı.** Gizlilik metninin alt işleyen bölümü artık
  OpenRouter'ı broker olarak, OpenAI · Microsoft Azure (küresel/ABD/AB) ·
  Amazon Bedrock (us-east-1) · Google'ı yukarı akış olarak adlandırıyor; ne
  gittiğini sayıyor ("kişisel veri" diyerek), "eğitebilecek sağlayıcıya
  yönlendirilmiyor" ifadesini kullanıyor ve modeli **şu an kullanılan model**
  olarak yazıyor (`openai/gpt-5.6-sol`). Model değişince sayfa gözden
  geçirilecek. `ProcessorAudit` satırına karşı kontrol **yapılmadı** — backend
  ayakta değildi; dağıtım gelince ilk iş.
- **`B-077` çalışıyordu, bir yanlış anlaşılma kaldı.** `usePatchAtom` yanıtı
  zaten iki önbelleğe de yazıyor ve `TagInput` tamamen `values`'tan çiziliyor,
  yani kanonik biçim ekrana kendiliğinden geliyor. Eklenen: mock artık
  gerçekten kanonikleştiriyor (küçük harf + tire, `postgresql`→`postgres`,
  tekrar edenleri düşürüyor — yani liste **kısalabiliyor**), ve alanın ipucu
  bunu önden söylüyor, hata gibi göstermeden.
- **`B-079` + `B-082` birlikte.** Kota hiç 0 görmedi: mock'ta 5'ti, 5 kaldı,
  o yüzden "0 gören sürüm" hiç dağıtılmadı. Anonim üretim yolu açık;
  `ANONYMOUS_SESSION_EXPIRED` **iş sonucu olarak** da ele alınıyor — akıştan
  gelen refüz aynı panele düşüyor ve `sign_up` gerçekten `/login`'e götürüyor
  (birim testi var). `feedback` anonimde null: sonuç ekranı o bölümü hiç
  çizmiyor.
- **`B-080` yazıldı**, parantez içi dahil: *"anonim mod son etkinlikten 2 saat
  sonra (veritabanından beş dakika içinde silinir; şifreli yedeklerde en fazla
  altı aya kadar kalabilir)"*. Ekranda ayrıca uyarı yok, dediğiniz gibi.
- **`B-081` — üçünden ikisi kod işi çıktı.** (1) Atom kontrolleri zaten
  `canEditAtomControls` ile gizliydi. (2) **"Alternatif ekle" düğmesi henüz
  hiç yok** — `POST /atoms/{id}/variants` istemcide tanımlı, hiçbir ekran onu
  çağırmıyor; düğme çizildiği gün `canAddAlternatives` ile kapanacak, ve
  mevcut yazımı düzenlemek zaten açık. (3) `params.feature` artık cümleyi
  seçiyor: üç jetonun üçü de kendi cümlesini alıyor, jeton ekrana hiç
  çıkmıyor. Ayrıca **sessiz bir kusur düzeldi**: profil editöründeki paneller
  sunucunun `sign_up` düğmesini çiziyor ama basılınca hiçbir şey yapmıyordu.
- **`B-083` bağlandı.** Turnstile widget'ı anonim çağıranda hem CV yükleme
  hem üret ekranında; token gövdede (`challengeToken`) ve multipart'ta form
  alanı olarak. **Token yoksa alan hiç gönderilmiyor** — boş göndermek
  başarısızlık sayıldığı için. Her denemeden sonra widget sıfırlanıyor
  (yalnız refüzde değil: kabul edilen istek de token'ı harcıyor), ve iş
  ekranında da duruyor, çünkü başarısız bir işten çıkan her yol yeni bir
  istek. Hesaplı çağıranda hiç çizilmiyor. **Gerçek uca karşı denenmedi** —
  sırrı olan bir dağıtım gerekiyor.
- **`B-084` fırsatı alındı.** `upgraded` artık geçmişe düşürüyor: sihirli
  bağlantıda doğrudan, OAuth dönüşünde yalnız `next` yoksa (bir `next`
  kullanıcının gönderildiği ekran, ve o kazanıyor). `kept_existing` ve
  `unavailable` eskisi gibi. Cümle de düzeldi: profil **ve ondan üretilen
  CV'ler** deniyor.

**Üç soru `to-backend.md`'ye gitti:** `F-028` (blokta ön yazı alanı yok),
`F-029` (`challengeToken` şemada yok + uç adı tekil mi), `F-030` (`422`'nin
`resolutions`'ı ve anonim `feedback` yazısının reddi — ikisini de tahmin ettik).

_(`B-071`…`B-074`'ün dördü de karşılandı ve `resolved/to-frontend-2026-09.md`'ye
indi 2026-09-09'da, dosya sınırı; `B-037`…`B-070` bir öncekinde. Aşağıdakiler
**hâlâ canlı olan** kayıtlar.)_

**Üç yerde bir doğrulama eksik ve söylenmesi gerekiyor:** ne OAuth sıçraması
(`B-048`), ne sihirli bağlantının Turnstile'ı (`B-050`), ne de `B-083`'ün
üretim/içe aktarım challenge'ı gerçek uca karşı denendi — üçü de kendi
anahtarları yapılandırılmış bir dağıtım istiyor. Bugün doğrulanan şey
mock'a karşı.

**EK C.1'in sağlayıcı listesi yazıldı** (`B-076`, yukarıda). Yayın öncesi
kontrol listesinde kalan tek şey, yayımlanan sayfayı `ProcessorAudit`'in
açılış satırına karşı okumak — dağıtım işi, kod işi değil.

---

## ACK — `B-085`…`B-087` (indi 2026-09-09)

- **`B-085`:** `api.d.ts` yeniden üretildi, `useCanWriteCoverLetter` artık
  `capabilities.canWriteCoverLetter` okuyor. Dediğiniz gibi tek satır —
  vekili tek bir fonksiyona hapsetmiş olmamız tam da bunun içindi. Mock'un
  iki yetenek kümesi de alanı yayımlıyor.
- **`B-086`:** haklısınız, alan duruyordu; ölçtüğümüz şey **diskteki üretilmiş
  dosyaydı**, canlı şema değil — ve o dosya backend'in birkaç commit
  gerisinden üretilmişti. Kesişim tipi kalktı, mock şemanın tipine döndü.
  Token içe aktarımda **zaten `FormData`'daydı**: § 35.7.4'ün "form alanı"
  cümlesini okuyup öyle yazmıştık, yani query'ye hiç koymadık. Uç adı da bizde
  hep tekildi.
- **`B-087`:** `422`'ye `sign_up` eklendi. İki `403` mock'u olduğu gibi
  duruyor. `params.feature`'ın dört değerinin dördü de artık kendi ICU dalını
  alıyor — `feedback` dahil, ki onu hiçbir ekran üretemiyor (anonimde
  geri bildirim formu çizilmiyor): sözlük sunucunun, ve gönderebildiği bir
  değer bizim üretebildiğimizden bağımsız olarak okunabilir olmalı.

**Ölçüm notu:** yerel backend çerezsiz isteğe `LocalDevSessions`'la cevap
verdiği için anonim dalları gerçek uca karşı **koşamadık** — `F-027`'de ve
sizin `F-030` notunuzda geçen tuzağın aynısı. Doğrulanan şey: alan şemada ve
hesapta `true` (`GET /auth/session`). Anonim taraf mock'a karşı.

---

## `B-095` · kapandı 2026-09-11 — üç şablonun da sayfa garantisi doğrulandı

Madde 2026-09-10'da açıldı: golden set yalnız `classic`'i gerçek derleyiciye
karşı ölçüyordu, ve üç şablona genişletince **compact sayfayı olduğundan kısa
sanıyordu** — bir profilde de gerçekten ikinci sayfaya taşıyordu. Modern'de de
bir profil taşıyordu.

**Kapandı: classic, compact ve modern, yedi golden profilin hepsinde %3 içinde.**

**Sizden istenen bir şey kalmadı.** Ara güncellemede "compact'i öne çıkaran bir
varsayılan/öneri varsa geri alın" demiştik; **o kısıt kalktı**, compact dâhil üç
şablonun üçü de sayfa vaadini tutuyor. Uç, alan ve `templateId` sözleşmesi bu
madde boyunca hiç değişmedi (`B-090`, `B-091`, `B-092` aynen geçerli).

**Beş sebep çıktı ve beşi de aynı cümleydi: sayfanın dizdiği ama ölçümün hiç
görmediği bir şey.**

1. Compact'te listeden sonra gelen bölüm başlığı 10pt pahalı; kalibrasyon yalnız
   ilk başlığın konumunu ölçüyordu.
2. Başlık bloğu her profil için tek sabitti — oysa metin sarmalıyor; artık
   ölçülüyor ve profilde saklanıyor (geometri + dil anahtarıyla).
3. `
esumeItem`, ifadeyle ardındaki negatif `space` arasına bir kelime arası
   boşluk koyuyordu.
4. Aynı makro çok satırlı yazılmıştı, ve **makro gövdesindeki her satır sonu bir
   boşluktur** — yani (3) kaldırıldıktan sonra ikinci bir boşluk kalmıştı.
5. Compact aynı boşluğu iki kez yazıyordu: bir kez listenin ardında bıraktığı,
   bir kez altındaki başlığın primi olarak.

(3) ve (4) birlikte okunmalı: ikisi de doğal genişliği satıra bir boşluk kadar
yakın olan bir maddeyi ikinci satıra düşürüyor, **ve yalnız listenin son
maddesinde** — bu yüzden altmış böyle maddesi olan bir fixture olmadan ikisi de
görünmüyordu.

**Sizi ilgilendiren tek kalıcı sonuç:** şablon sürümleri yükseldi
(`classic:v6`, `compact:v2`, `modern:v3`). Sürüm yalnız ölçüm anahtarlarında
geçiyor, API'de değil — bir yerde sürüm dizesi sabitlemediyseniz yapacağınız bir
şey yok.

---

## ACK — frontend karşıladı

**`B-097`, `B-098`, `B-099` karşılandı (2026-09-12), geldikleri gün.**

- **`B-099`** · `npm run gen:api` koşuldu. 26 operasyon adı değişti ve
  bağlamalar yeni adlara taşındı; numaralı id'ler için yol üzerinden bağlayan
  `ReturnsAt`/`AcceptsAt` **silindi** — tek varlık sebepleri oydu, ve muhafız
  artık sizdeki test. `Appearance` okunup doğrudan geri yazılıyor, eleme kodu
  kalktı.
- **`B-098`** · `JobStatus` iki alanı da tipli taşıyor. Mock'ta terminal yük
  **tek yerde** üretiliyor artık: akış ile `GET /jobs/{id}` aynı nesneyi
  yayıyor, yani alanın birinde olup diğerinde olmaması bir daha yazılamaz.
  `matchLevel` genel modda iki taşıyıcıda da yok.
- **`B-097`** · Elle aç/kapa arayüzü indi. Liste kapalı başlıyor (uç ikinci
  bir istek), sunucunun sırasıyla çiziliyor, **yalnız yeri değişen** satırlar
  gönderiliyor, her satırın durumu switch'in yanında sözle de yazıyor ve
  hareket `aria-live`'a düşüyor. Emekli üretim halefine bağlantı veriyor.

Üçünün de testleri negatif kontrolden geçti. `B-088`…`B-094` ve `B-096`
`resolved/to-frontend-2026-09.md`'de (2026-09-11).

---

## ACK — backend'in on yedi maddesi, frontend karşıladı (2026-09-20)

**`B-100`…`B-116` ACK'lendi ve `to-frontend.md`'den indi.** On yedisi de altı
denetim turundan çıkmıştı ve hepsi tek bir kapanış sırasında karşılandı; dosya
347 satıra çıkmıştı ve taşınabilecek madde yoktu, çünkü hiçbiri
karşılanmamıştı. Niçin öyle karşılandıkları **denetim arşivlerinde**:
`notes/archive/denetim-2026-09-20-altinci.md` (altıncı tur, on yedinin çoğunun
çıktığı yer) ve kardeşleri. **`notes/archive/stage-4.md` yok** — Aşama 4 hâlâ
açık, ve kapandığı gün yazılacak olan dosya o.

**Maddelerin kendisi aşağıda, indikleri haliyle.** Arşivin işi budur: bir
sonraki oturum `B-111`'in neden yazıldığını soruyorsa okuyacağı yer burası, ve
özet bir satır onu yanıtlamaz.

> **Bunlar 2026-09-20'de `to-frontend.md`'den silindi ve buraya
> yazılmadı** — dosya `B-099`'da bitiyordu, on yedisi de hiçbir yerde
> değildi. `to-frontend.md` okuyucuyu buraya yolluyordu ve burada yoktular.
> Aynı gün `d5172d8`'den kurtarılıp eklendiler.

### B-100 · CSP Turnstile'ı blokluyordu, düzeldi
**Since:** `df742fa` · `docker/nginx/nginx.conf` · § 11.2
**Neden:** politika `default-src 'self'` idi, hiçbir host adlandırmıyordu.
Turnstile bu origin'in yüklediği bir script ve gömdüğü bir iframe; ikisi de
sessizce bloklanırdı — widget çizilmez, token üretilmez, `B-050`/`B-083`'ün üç
ucu `CHALLENGE_FAILED` cevaplardı. İki reponun testi yeşilken.
**Aksiyon:** kod işi yok; dağıtım gününde widget'ın çizildiğini görün.

### B-101 · `openapi.json` repo kökünde ve commit'li
**Since:** `f849d4a` · § 47.1, § 35.8
**Neden:** `contract-check` `.../build/openapi.json` çekiyor; `build/` üretilen
ve gitignore'lu, yani o URL hep 404 verdi ve iş hep "skipping" dalına gitti —
ayrışmaya karşı tek muhafız kendini atlıyordu.
**Aksiyon:** URL'i `.../main/openapi.json` yapın. Dosya her PR'da
`OpenApiDocumentIT` ile yayımlanan şemaya karşı doğrulanıyor.

### B-102 · Arşivleme ucu indi, `archived` iki okuma şeklinde de var
**Since:** `2b4efd4` · § 35.2, § 13
**Neden:** `POST /generations/{id}/archive` kaynak haritasında baştan beri
vardı, `generations.archived` kolonu V1'den beri vardı, ve ikisi hiç
buluşmamıştı.
**Aksiyon:** İşareti `GenerationResponse.archived` ve
`GenerationSummary.archived`'dan okuyun; geçmiş listesi bu işaretin okunduğu
ekran. Aynı uç `{"archived": false}` ile işareti kaldırıyor, boş gövde
arşivliyor. **Yeni ICU anahtarı:** `AccountFeature` beşinci değerini aldı —
`archive`, `canSaveHistory`'ye eşleniyor (`feedback` gibi).
**Not:** işaret bugün saklama süresini değiştirmiyor; nesne deposu inince
§ 13'ün kuralını o okuyacak (§ 57.4'ün açık paragrafı).

### B-103 · Atom etiketleri yazılabiliyor — ve bugüne kadar hiç yazılmıyordu
**Since:** `3470471` · § 35.2, § 13, § 19.1
**Neden:** `tags` ve `atom_tags` tablolarına **hiçbir şey** yazmıyordu; içe
aktarım modelin bulduğu etiketleri normalize edip düşürüyordu. Yani Faz B'nin
etiket örtüşmesi — ham skorun **dörtte biri** — her atom için her ilana karşı
yapısal olarak sıfırdı.
**Aksiyon:** `AtomResponse` artık `tags[]` taşıyor (`id`, `label`, `source`).
`POST /profile/atoms/{id}/tags` ekliyor, `DELETE .../tags/{tagId}` kaldırıyor;
`If-Match` istemiyor (etiket kendi satırı). `source` `auto` ise çıkarımın
tahmini, `user` ise kişinin kararı — ikisi farklı çizilmeli. § 55'in
"Etiket / önem / kilit" editörünün eksik yarısı buydu.

### B-104 · `POST /generations` `emphasize` alıyor
**Since:** `491b5f9` · § 18.7
**Neden:** § 18.7'nin yönlendirme nesnesinin dört alanından ikisi kodda yoktu.
**Aksiyon:** İsteğe bağlı `emphasize: string[]` (en çok on terim, her biri en
çok 60 karakter). İlanın kendi keyword ve etiketlerine katılıyor — § 19.1'in
formülü değişmedi, aynı dört ağırlık daha büyük bir küme okuyor. Ekranda
"ilanın söylemediği ama işin bununla ilgili olduğu terimler" diye sunulmalı.
**`freeformNote` gelmedi** ve bilerek: § 18.7 alanı adlandırıyor, hiçbir bölüm
onu kimin okuduğunu söylemiyor, ve tek makul okuyucu Faz D'nin prompt'u —
o da yeni bir prompt sürümü ve EK C.3'ün eval koşusu demek.

> **Bu son paragraf bayatladı (2026-09-20, `F-038`).** Alan sonradan geldi ve
> madde güncellenmedi: `note` (500 karakter) `POST /generations`'ta ve Faz D'ye
> gerçekten ulaşıyor — `RewriteContext`'in dördüncü alanı. Gerekçesindeki iki
> iş de yapıldı. `F-038` "ikisinden biri bayat" diye sordu; cevabı **madde**,
> uç açıklaması değil. `B-117` dördünü de adlandırıyor.

### B-105 · İndirmeye `html` ve `source` eklendi
**Since:** `8c00397` · § 35.2, § 22.6, § 55
**Neden:** `format=source` kaynak haritasında ilk taslaktan beri vardı ve
`VALIDATION_FAILED` dönüyordu; HTML renderer'ın paketi boştu.
**Aksiyon:** `?format=html` tek dosyalık, hiçbir şey çekmeyen bir HTML;
`?format=source` PDF'in derlendiği LaTeX. **Sayfa sınırı HTML'de hiç
geçerli değil** — DOCX'teki gibi "yaklaşık" değil, sayfa kavramı yok; düğmenin
yanındaki cümle bunu söylemeli.

### B-106 · GitHub içe aktarımı indi
**Since:** `4a49a1c` · § 31.8
**Aksiyon:** `POST /profile/github/suggestions` (yazmaz, önerir) ve
`POST /profile/github/apply` (seçilenleri yazar). **Yol `/profile/github`,
`/ingestion/github` değil** — `F-029`'un CV yüklemesi için verdiği kararın
aynısı. `username` opsiyonel; boşsa profilin iletişim bloğundaki hesap
okunuyor. Bir öneri `matchedEntryId` taşıyorsa birleştirme (beceri + bağlantı,
**cümleye dokunulmuyor**), taşımıyorsa yeni proje. Saatte beş çağrı
(`RATE_LIMITED`), hiçbir izin istemiyor, hiçbir token saklamıyor.

### B-107 · `F-013` kapandı: `auto` artık ilanı gerçekten takip ediyor
**Since:** `f518644`, `928c34d` · § 21.8, § 32.5
**Neden:** Türkçe bir profil İngilizce bir ilana Türkçe CV üretiyordu, çünkü
belgeyi tek dilde tutmanın tek yolu ilanı takip etmeyi reddetmekti. § 21.8'in
ikinci adımı indi: eksik sözcüklemeler Faz B ile Faz C arasında çevriliyor ve
kaydediliyor. **Üçüncü diller İngilizce üzerinden** (§ 32.5).
**Aksiyon — üç şey:**
1. **Bu üretim yavaş olabilir.** Profilin hedef dilde sözcüklemesi yoksa en
   çok altmış çeviri çağrısı yapılıyor; **ikinci kez ücretsiz**, çünkü
   sonuçlar profile yazılıyor. İlerleme çubuğu `SCORING` fazında beklerse
   sebebi bu.
2. **Hep ya hiç.** Çeviri tamamlanamazsa belge profilin kendi dilinde
   çıkıyor — yarısı bir dilde değil. `GenerationResponse` hangi dil olduğunu
   zaten söylüyor; ekran onu okumalı.
3. **Profil editöründe yeni sözcüklemeler belirecek.** `createdBy:
   llm_translate`, `userEdited: false`. Kullanıcının yazmadığı bu satırların
   gözden geçirilmesi öneriliyor (§ 32.5) — rozet için gereken alan zaten
   `Variant` şemasında.

### B-108 · `GET /generations/{id}/selection` artık gerekçe de taşıyor (İlke 7)
**Since:** § 35.3.1, § 1.2 · denetim 2026-09-16
**Neden:** İlke 7 her seçimin gerekçesinin gösterilmesini istiyor ve üç şey
adlandırıyor — skor, eşleşen keyword'ler, red nedeni. Üçü de hesaplanıyordu,
hiçbiri telde yoktu: `SelectionLine` yalnız `atomId`, `text`, `onPage`
taşıyordu, yani gerekçesi bildirilmemiş bir sıralama.
**Aksiyon — önce `npm run gen:api`**, `SelectionLine` iki alan kazandı:

1. **`matchedKeywords?: string[]`** — bu satırın taşıdığı ilan terimleri,
   alfabetik. **Yokken alan hiç gelmiyor**, boş dizi olarak değil: seçilmiş bir
   satırın yanındaki boş dizi "hiçbir şey eşleşmedi" diye okunur, ve genel CV
   modunda — ortada ilan yokken — bu içerik hakkında bir iddia olurdu. Çip
   olarak çizilmeye uygun; sayfaya girmeyen satırlarda **hiç gelmez** (anlık
   görüntüye yalnız seçilenler yazılıyor).
2. **`heldBackReason?: "BUDGET" | "INACTIVE" | "EXCLUDED_BY_DIRECTIVE" |
   "ENTRY_BELOW_MINIMUM"`** — sayfaya girmeyen satırlarda dolu, girenlerde
   **hiç gelmiyor**. Şemada kapalı enum, yani ICU `select`'i dördünü de
   yazabilir. **Dördü dört ayrı cümle istiyor**, ve bu maddenin asıl işi o:
   `BUDGET` sayfa sınırını uzatmaya davet eder, `INACTIVE` profil editörüne
   gönderir (atom kapalı), `EXCLUDED_BY_DIRECTIVE` *bu CV'de* yapılan
   düzenlemeyi geri almaya (profil ayarı değil — ikisini karıştıran bir ekran
   kişiye kalıcı bir kararı geri aldırır), `ENTRY_BELOW_MINIMUM` entry'nin
   bütün olarak düştüğünü söyler.

**Skor bilerek yayımlanmıyor** ve istenmesin: § 23.3'ün yüzdeye itirazı bir
madde yanındaki sayı için de geçerli. Sıra zaten sıralamayı söylüyor.

**Eski üretimler `matchedKeywords` taşımıyor** — Faz B onu bu denetimden önce
kaydetmiyordu. Alan yokluğu normaldir, boş durum ekranı gerektirmez.

### B-109 · Uç açıklamaları yeniden yazıldı — `gen:api` ikinci kez
**Since:** denetim 2026-09-16 · `openapi.json` 37 satır
**Neden:** Dökümanlar repodan çıkıyor, bu yüzden koddaki tüm `Bölüm/Adım/EK`
atıfları temizlendi. Bunların bir kısmı `@Schema`/`@Operation` metinlerinin
**içindeydi**, yani `openapi.json`'daki açıklamalar değişti.
**Aksiyon:** `npm run gen:api`. **Hiçbir alan, tip veya enum değişmedi** —
yalnız `description` metinleri. Üretilen `api.d.ts`'te tip farkı beklenmiyor;
çıkarsa bu bir kusurdur, haber ver.

Metinlerin anlamı korundu, yalnız işaretçi düştü: "Counts, never a percentage.
**Bölüm 23.3** forbids one by name" → "**One is forbidden by name**". Ekranda
bu metinleri gösteren bir yer varsa cümleler biraz kısaldı.

### B-110 · Hata kataloğu artık üretiliyor — ve sizin kopyanız ona bağlanabilir
**Since:** denetim 2026-09-16 · `error-catalogue.md`, § 08b
**Neden:** Katalog tablosu `08b-api-contract.md`'de elle yazılıyordu. Artık
**`ErrorCode` enum'undan üretiliyor** ve repo kökünde `error-catalogue.md`
olarak duruyor — `openapi.json`'ın yanında, `docs/` ağacının dışında.
`ErrorCatalogueDocumentTest` commit'li dosyayla enum ayrıştığı an düşüyor, ve
spec senkronu dosyayı `docs/error-catalogue.md` olarak size de bırakıyor.

**Düzeltme — burada sizin hakkınızda yanlış bir kayıt vardı.** Spec,
*"frontend'in katalog testi `params`'ı bu tablodan okuyor"* diyordu. Okumuyor:
`tests/unit/i18n/errorCatalogue.test.ts` kendi `PARAMS` sabitini elle tutuyor
ve tabloyu yalnızca yorumda kaynak olarak anıyor. Yani 41 kodun parametre
adları ve tipleri iki yerde elle yazılıydı ve **hiçbir şey ikisini
karşılaştırmıyordu**.

**Aksiyon — karar sizin, bugün kod işi yok:**
1. **Hiçbir şey kırılmıyor.** `PARAMS` bugün doğru; bu bir fırsat maddesi.
2. `error-catalogue.md` artık üretilen ve biçimi sabit bir dosya, yani
   `errorCatalogue.test.ts` onu ayrıştırıp kendi `PARAMS`'ıyla
   karşılaştırabilir. O zaman zincirin ikinci halkası da bağlanır: backend'e
   bir kod eklendiğinde **sizin** testiniz de düşer, mesaj yazılmadan önce.
   Biçimi sabit tutmayı üstleniyoruz; değişirse `B-nnn` ile haber veririz.
3. İstemezseniz de olur — `openapi.json` tipleri zaten tutuyor. Bu yalnız
   `params` adlarını ve ICU tiplerini kapsardı, ki `openapi.json` onları
   taşımıyor.


### B-111 · `en.json`/`tr.json` yanlış tablodan yazılmış olabilir
**Since:** denetim 2026-09-16 · `docs/spec/08-api.md` § 35.4, `18-appendix-d.md` EK D.6.1
**Neden:** § 35.4 size **"tam katalog EK D.6.1'de: 27 kod… `en.json` ve
`tr.json` artık buradan yazılabilir"** diyordu. O tablo elle yazılmıştı ve
katalog `ErrorCode`'dan üretilen `error-catalogue.md`'ye taşındığında geride
kaldı. Ölçüldü: **27 koda karşı enum'da 41**, ve iki somut yanlış —
`NO_ANONYMOUS_PROFILE` **hiçbir şeyin üretemediği** bir kod (09-15 denetiminde
kaldırıldı), ve `UNPARSEABLE_JOB_DESCRIPTION`'da **`params.reason` yok**, ki
§ 18.1 ile § 18.4'ün **yedi** değerli kapalı sözlüğü kullanıcıyı onunla dört ayrı
ekrana gönderiyor (metni düzelt / tam ilanı yapıştır / genel CV / tekrar dene).
Yedi, sekiz değil: `no_responsibilities` `B-072`'de sizden de kalkmıştı,
spec bunu 2026-09-16'ya kadar sekiz saymaya devam etti — sizde aksiyon yok.

Tablo kaldırıldı, § 35.4'ün işaretçisi `error-catalogue.md`'yi gösteriyor.
Backend tarafında kod değişmedi — **kodlar hep 41'di**, yanlış olan tabloydu.

**Aksiyon:** çeviri dosyalarınızı `error-catalogue.md`'ye karşı okuyun.
Muhtemel bulgular: **on beş kod için mesaj yok** (`AUTHENTICATION_REQUIRED`,
`RATE_LIMITED`, `MAGIC_LINK_INVALID`, `OAUTH_FAILED`, `EDIT_NOT_UNDERSTOOD`,
`COVER_LETTER_REJECTED`, `GENERATION_SUPERSEDED`, `GENERATION_PAUSED`,
`CHALLENGE_FAILED`, `TRANSLATION_FAILED`, `UNSUPPORTED_DOCUMENT`,
`DOCUMENT_TOO_LARGE`, `UNSUPPORTED_MEDIA_TYPE`, `METHOD_NOT_ALLOWED`,
`NOT_ACCEPTABLE`) ve **bir mesaj hiç görünmeyecek** (`NO_ANONYMOUS_PROFILE` —
silin). Bunların bir kısmı zaten yazılmış olabilir; madde "eksik" demiyor,
**"kaynağınız yanlıştı, kontrol edin"** diyor.

Bu `B-110`'un ta kendisi değil ama onunla aynı zinciri kapatıyor: `B-110`
testi bağlamayı öneriyor, bu madde **bugünkü** dosyaların durumunu soruyor.

### B-112 · İki şema sözlüğü daraldı — `gen:api` üçüncü kez

**Since:** `2975584` · beşinci denetim · `04-data-model.md` § 13.2, `error-catalogue.md`

**Neden:** ikisi de **hiçbir şeyin üretemediği** değerler taşıyordu, yani
sizin tarafta hiç girilmeyecek bir dalın tipi vardı.

- `ApiError.code`'dan **`REWRITE_VALIDATION_FAILED` kalktı.** Doğrulayıcının
  reddettiği bir madde kullanılmıyor ve kişinin kendi cümlesi basılıyor —
  yani bu kodu hiçbir yol üretemiyordu. Ürün ömrü boyunca da üretmedi.
- `Variant.createdBy` **dört değerden ikiye indi**: `user`, `llm_translate`.
  `llm_extract` yazılmadı çünkü içe aktarım kişinin kendi cümlelerini tutuyor
  ve bilerek `user` işaretliyor; `llm_rewrite` yazılmadı çünkü Faz D'nin
  yeniden yazımı varyant değil, `generations.rewritten_content` (V11).
  V16 kolona bu iki değeri zorlayan CHECK'i verdi.

**Aksiyon:** `npm run gen:api`. Sonra `REWRITE_VALIDATION_FAILED` için bir
çeviriniz varsa silin (`B-111`'in listesini okurken denk gelirsiniz), ve
`createdBy`'ın dört değerini ayıran bir arayüz varsa ikiye indirin.

### B-113 · `EXTRACTION_TIMEOUT` artık gerçekten dönüyor — cümlesi lazım

**Since:** `2975584` · beşinci denetim · `08b-api-contract.md` § 177

**Neden:** kod katalogda duruyordu, 504'ü seçilmişti, ve **hiçbir yol onu
üretemiyordu.** Sağlayıcı zinciri tükendiğinde çıkarım her durumda
`ALL_PROVIDERS_UNAVAILABLE` (503) diyordu — vendorlar gerçekten kapalı olsa da,
uzun bir CV sadece yavaş kaldığı için zaman aşımına uğrasa da. Zincir artık
hangisi olduğunu taşıyor: **her başarısızlık bir zaman aşımıysa** 504
`EXTRACTION_TIMEOUT`, değilse eskisi gibi 503.

**Aksiyon:** `errors.EXTRACTION_TIMEOUT` için bir mesaj yazın, ve 503'ünkinden
**farklı** olsun — bu ikisi kullanıcıdan zıt şeyler istiyor. 504: *aynı
dosyayla tekrar deneyin, belge uzun olabilir.* 503: *tekrar denemek şu an
yardımcı olmaz.* Parametresiz (mutlak kural 4: hangi belgenin yavaş kaldığı
bir log satırı değil).

### B-114 · Dört çıkarım reddi artık çıkış yolu taşıyor — iki yeni eylem

**Since:** altıncı denetim · `08b-api-contract.md` D.6.1 · § 31.10

**Neden:** dördü de boş bir `resolutions` dizisiyle geliyordu — ekranda bir
cümle, hiçbir düğme — ve `switch_to_manual_form` sözlükte **kullanılmadan**
duruyordu. Üstelik `ErrorPresenter`'ın o noktadaki yorumu *"çıkış yolu manuel
form, ki sözlükte böyle bir eylem yok"* diyordu; vardı. P4 her problemli
durumda somut seçenek istiyor.

**Aksiyon — `npm run gen:api`, sonra iki yeni ICU anahtarı.** Sözlük 12'den
14'e çıktı:

| Kod | Çözüm | Ekran ne yapmalı |
|---|---|---|
| `PDF_NOT_TEXT_BASED` (422) | `switch_to_manual_form` | Manuel profil formuna götür |
| `EXTRACTION_EMPTY` (422) | `switch_to_manual_form` | Aynısı |
| `PDF_ENCRYPTED` (422) | **`upload_another_file`** | Dosya seçiciyi yeniden aç — **"tekrar dene" değil**: aynı şifreli dosya her seferinde aynı yerde düşer, ve kişinin elinde zaten açık bir kopyası olabilir |
| `LANGUAGE_UNDETECTED` (422) | **`choose_language`** | `params.detectedCandidates`'ı seçenek olarak sun. Aday listesi en fazla tek elemanlı (model bir sıralama değil bir dil döndürüyor), yani "şu mu, yoksa başka bir dil mi" şeklinde bir soru |

**Ve bir tane daha, sözlüğe dokunmadan:** `EXTRACTION_TIMEOUT` (504) artık
`retry` taşıyor. `B-113` onu 503'ten ayırmıştı ki ikisi kullanıcıdan **zıt**
şeyler istesin; ikisi de boş çözüm listesiyle çıktığı sürece aynı hiçbir şeyi
istiyorlardı.

**Muhafız:** `ErrorCatalogueTest.everyActionIsOfferedSomewhere` — üreteni
olmayan bir eylem artık CI'yı düşürüyor. Bu maddenin kendisi o testin ilk
bulgusudur.

### B-115 · § 37.6'nın iki düğmesi çalışıyor — "çizmeyin" kaydı bayatmış

**Since:** altıncı denetim · `09-frontend.md` § 37.6, EK D.9 · 24

**Neden:** spec size **bayat varyant kontrollerini çizmemenizi** söylüyordu:
*"`Variant.stale` Aşama 1'de her zaman false ve bir varyantı yeniden üreten uç
yok."* İkisi de Aşama 3'te değişti, **satır değişmedi.** Yani doküman, işleyen
bir özelliği bir aşama boyunca yok saydırdı.

**Aksiyon — kod işi sizde, telde değişen bir şey yok:**

1. **Rozet gerçek.** Bir sözcüklemeyi düzenlemek ondan türeyen her şeyi
   `stale: true` yapıyor, düzenlemeyle **aynı transaction'da**.
2. **"İngilizceyi yeniden üret"** bir uç değil, bir yama:
   `PATCH /profile/atoms/{id}/variants/{vid}` gövdesi `{"userEdited": false}`.
   Yazarlık iddiası geri veriliyor ve bayat sözcükleme **hemen** çeviri
   kuyruğuna giriyor — kaynağı aylarca düzenlenmeyebilir, çeviri şimdi bayat.
3. **"Benim halimi koru"** hiçbir şey göndermemek. `userEdited: true`
   **reddediliyor** (400): bir makine çevirisini insan adının arkasına
   saklayabilecek tek yön o.
4. **Anonim oturumda çeviri kuyruğa girmiyor** — o oturumun ne işi
   sahiplenecek bir id'si var ne ikinci bir dili. Bozulma değil, kısa yol;
   ekranın anonimde bu düğmeyi hiç göstermemesi doğru.

### B-116 · Üç sözlük daraldı, biri **girdi** — `gen:api` dördüncü kez

**Since:** altıncı denetim · `V17`, `04-data-model.md` § 13.2

**Neden:** `B-112`'nin kapattığı kusurun dört kopyası daha bulundu — hiçbir
şeyin üretemediği değerler. Üçü telde:

- **`GenerationResponse.status` ve `GenerationSummary.status`'tan `failed`
  kalktı.** `selection_state` `NOT NULL`, yani seçimden önce düşen bir koşunun
  yazacak satırı yok ve arıza **işin** üstünde yaşıyor. Enum'un kendi javadoc'u
  bunu zaten söylüyordu ("Reserved. Nothing writes it today"), şema değeri yine
  de yayımlıyordu.
- **`JobStatusResponse.status`'tan `cancelled` kalktı.** Onu yazabilecek tek
  metodun tek çağıranı kendi testiydi; iptal eden bir uç yok, kaynak haritası
  da böyle bir şey adlandırmıyor. **İptal bir özellik**, ve indiği gün değer
  onunla birlikte geri gelir.
- **`Section.layout`'tan `two_column` kalktı — ve bu ötekilerden farklı, çünkü
  bir girdiydi.** Uç kabul ediyordu, CHECK izin veriyordu, şema yayımlıyordu,
  ve renderer onu **bilerek** entry list olarak basıyordu: üç şablon da tek
  kolon, gerekçesi ATS çıkarımı (§ 33.5). Yani kişi bir düzen seçiyor, hiçbir
  şey söylenmiyor, belgesi başkasını basıyordu. Ötekiler size boş bir dala mal
  oluyordu; bu, kullanıcıya verdiğini sandığı bir seçime.

**Aksiyon:** `npm run gen:api`. Sonra: `status === 'failed'` ve
`status === 'cancelled'` dallarınız varsa silin (hiç girilmediler), ve **düzen
seçicisinde `two_column` sunuluyorsa kaldırın** — veritabanındaki satırlar
`entry_list`'e çevrildi, yani seçmiş bir kullanıcı varsa bugün gördüğü düzen
değişmiyor, yalnız seçenek kayboluyor.

*(Dördüncüsü `JobType.EMAIL` ve telde hiç yoktu — kuyruk tipi, sizi
ilgilendirmiyor.)*

---

## ACK — `B-117`…`B-119`, frontend karşıladı (2026-09-21)

**Üçü de geldikleri günün ertesi karşılandı ve `to-frontend.md`'den indi.**
`B-117` kod yazdırmadı — dört ucun dördü de D6/D12'de inmişti, eksik olan
maddenin kendisiydi. `B-118`: sonuç ekranı ve geçmiş satırı `pageCount`'u **o
üretimin** `maxPages`'ine karşı okuyor (alan yoksa not yok), bayat bir
`customizationId`'nin `404`'ünde ölü seçim düşüyor ve liste tazeleniyor.
`B-119`: `choose_language` çiziliyor ve cevap bir sonraki yüklemenin
`language` alanında gidiyor; `language`'ın kapı sırasındaki yeri üç sondayla
ölçüldü — **413 → `400` → 409 → 415** — ve mock ona göre düzeltildi.

> **Bunlar 2026-09-21'de `to-frontend.md`'den silindi ve buraya yazılmadı** —
> dosya okuyucuyu buraya yolluyordu, üçü de burada yoktu. `B-100`…`B-116` ile
> **aynı kusur, bir gün arayla**; o sefer `d5172d8`'den, bu sefer `3187700`'den
> kurtarıldılar.

**Maddeler aşağıda, indikleri haliyle.**

### B-117 · `F-038`'in dört şeyi, ve `B-104`'ün bayat olan yarısı

**Since:** `feat/frontend-f037-f040` · `08-api.md` § 35.3, § 35.8 · `F-038`

**Neden:** `F-038` haklı ve kapsam sorusu değil muhafız sorusu olduğunu da
doğru söylüyor. Dördü de **kodda var ve çalışıyor**; hiçbirinin maddesi
yoktu, yani `gen:api` koşmayan biri bunları göremezdi.

| Ne | Nerede | Ne yapmanız lazım |
|---|---|---|
| `POST /generations` **`note`** (500 karakter) | `JobSpecificGenerationService` → `RewriteContext`'in dördüncü alanı | Faz D'ye **gerçekten ulaşıyor.** Kullanın. |
| `POST /generations` **`customizationId`** | aynı uç; artık kapıda doğrulanıyor (`B-118`) | Kayıtlı set seçiciyi bağlayın |
| **`GET /templates`** (`TemplateSummary`) | `CustomizationController.listTemplates` | Şablon listesi buradan, sabit listeden değil |
| **`/customizations`** (liste/oluştur/yama/sil) | `CustomizationController`, profil başına 20 | 20 tavanı `400 VALIDATION_FAILED` + `fields: ["name"]` döner — ekranda söyleyin |

**`B-104` bayattı, `08-api.md` değil.** Maddenin *"`freeformNote` gelmedi ve
bilerek"* cümlesi yazıldığı gün doğruydu; sonra alan geldi ve madde
güncellenmedi. Gerekçesi de ("yeni prompt sürümü + EK C.3 eval koşusu") o iş
yapıldığı için artık bir engel değil. **İkisinden biri bayat sorusunun cevabı:
madde.**

**Action:** `npm run gen:api`; `note`, `customizationId`, `GET /templates` ve
dört `/customizations` ucu şemada. D6 ve D12'de telden okuyacağınız şey bu.
`B-101`'in `contract-check`'i hâlâ ikimizde de yapılmadı ve tam olarak bunu
yakalardı — **bu maddeyle kapanmıyor**, sadece bu turun dört boşluğu kapanıyor.

### B-118 · Bayat bir `customizationId` artık `404`, ve `maxPages` yayımlanıyor

**Since:** `feat/frontend-f037-f040` · `F-039`, `F-040`

**Neden:** ikisi de sizin ölçümünüz.

- **`F-040`** — set kontrolü worker'ın içindeydi, yani `202`'den sonra. Artık
  `POST /generations` kuyruğa girmeden bakıyor: bilinmeyen ya da başkasına ait
  bir `customizationId` **`404 RESOURCE_NOT_FOUND`**. Kotanın önünde, yani
  **bir üretim harcanmıyor** — sizin harcadığınız gibi.
- **`F-039`** — `GenerationResponse` **ve** `GenerationSummary` artık
  `maxPages` taşıyor. Sayıyı seçtiniz, sayı verdik: `pageCount < maxPages` o
  belgenin izin verilenden kısa olduğunu söyler, ve **o üretimin kullandığı**
  sınırdır — profilin bugünkü tercihi değil. İki yıl önceki bir CV'yi bugünkü
  ayara göre "kısa" ilan etme sorununuz yok.

**Action:** `npm run gen:api`. `maxPages` **eski satırlarda yok** (`null`) —
seçenek kaydedilmeden önce yazılmış üretimler sınırlarını bilmiyor ve makul bir
varsayılan, sunucunun olguyu uydurması olurdu. Notu `maxPages` yokken
çizmeyin.

### B-119 · `POST /profile/import` artık `language` alıyor — `choose_language` çizilebilir

**Since:** `feat/frontend-f037-f040` · `F-037` · `08b-api-contract.md` Adım 3.4

**Neden:** `F-037`'nin üç seçeneğinden **birincisi**. `choose_language`'ın
gideceği yer yoktu, siz de `ErrorPanel` politikası gereği düğmeyi çizmiyordunuz
— yani sunucunun gönderdiği bir çözüm düşüyordu.

Multipart gövdeye **`language`** alanı eklendi (ISO 639-1, opsiyonel).
Gönderildiğinde **tespit atlanır** ve profilin dili o olur; gönderilmezse
bugünkü davranış. Atlaması önemli: yalnız "eşik düşükse kullan" olsaydı ikinci
yükleme aynı reddi üretebilirdi, ki `F-037`'nin şikayet ettiği döngü bu.

Beyan modelin güvenli tahminini de geçer. Kişi kendi CV'si hakkında bir soruya
cevap veriyor; anlaşmazlıkta modele sessizce yenilen bir alan, birinin fark
ettiği durumda hiçbir şey yapmayan bir alan olurdu.

**Action:** `LANGUAGE_UNDETECTED` ekranında `choose_language` düğmesini
**çizin**; cevabı bir sonraki `POST /profile/import`'un `language` alanında
gönderin. `detectedCandidates` en fazla tek elemanlı, yani "şu mu, yoksa
başka bir dil mi" sorusu doğru soru.

**Bir uyarı:** tanımadığımız bir kod **`400 VALIDATION_FAILED`** +
`fields: ["language"]`. Sessizce yoksaymıyoruz — yoksayılsa profile yazılır ve
sonraki her üretim var olmayan bir dilde yapılırdı, kişiye söylenmeden.
