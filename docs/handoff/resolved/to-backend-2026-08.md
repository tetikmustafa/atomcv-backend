# Kapatılmış maddeler — frontend → backend · 2026-08

> Rutin okunmaz. "Şu karar ne zaman ve neden verilmişti?" sorusunda `rg` ile aranır.
>
> Buradaki metinler **backend'in kapanış notlarıdır**. Maddelerin frontend
> tarafından yazılmış özgün hâli `git show 591d467:docs/handoff/to-backend.md`
> içinde — kanal senkronu karşı tarafın dosyasını olduğu gibi kopyaladığı için
> özgün metin `to-backend.md`'de kalmadı.

---

### F-008 · Uygunluk raporu — indi
Faz F artık raporu hesaplıyor (`spec/06-pipeline-d-g.md` § 23.3) ve üç yerden
okunuyor:

```
GET /generations/{id}   tam rapor + pageCount + status + createdAt
completed olayı         matchLevel   (yalnız seviye — başlık bir tur beklemesin)
GET /jobs/{id}          pageCount    (yoklamaya geri düşen istemci için)
```

**Uydurulmuş yüzde yok** ve olmayacak: § 23.3 onu adıyla yasaklıyor, şema
testi de `level`'ı dört değerlik kapalı bir sözlük olarak sabitliyor.

Bilmeniz gereken iki davranış: (1) **rapor sayfaya çıkanla ölçülüyor**,
sıralananla değil — belgede yer bulamamış bir beceri kapsanmış sayılmıyor;
(2) **genel modda `fitReport` alanı hiç gelmiyor**, ilan yoksa her sayı sıfır
olurdu. `missingRequired` ilanın kendi sözcüklerini taşıyor, eşleştirme
İngilizce anahtar üzerinden. **Aksiyonunuz var — `B-041`.**

### F-009 · Düz gövde ve `generalMode` — kapandı, ve `generalMode` hiç var olmamıştı
§ 35.3'ün örneği düzeltildi: gövde **düz**, `directives`/`options` yok.

İkinci sorunuzun cevabı, sorduğunuz için bulundu: **`generalMode` diye bir alan
yazılmadı.** `GenerationRequest` üzerindeki `isGeneralMode()` türetilmiş bir
metot, ama bir record'da `isX()` Jackson ve springdoc için bir getter — şemaya
bir boolean olarak sızmış. Sizin de tahmin ettiğiniz gibi gereksizdi ve düştü
(`@JsonIgnore`); genel modu isteyen tek şey `jobDescription`'ın yokluğu.

Bu, Aşama 2'de `RichContent`'te yediğimiz hatanın telin öbür yüzündeki hâli:
*Jackson'ın dokunduğu bir record'daki her getter şeklindeki metot, birinin
bulacağı bir alandır.* Şema testi artık `GenerationRequest`'in **tam dört**
özelliği olduğunu sabitliyor. **Aksiyonunuz var — `B-040`.**

### F-010 · Anlık durumdaki boş dizeler — kapandı
`phase`, `label` ve `detail` boşken **gönderilmiyor**; `pct` sıfırken de
gönderiliyor, çünkü yüzdesiz bir çubuk başlangıçtaki çubukla aynı şey değil.
`GET /jobs/{id}` zaten böyle davranıyordu — akış ile yoklama artık aynı şeyi
söylüyor, ve tek bir shape serialize edildiği için ayrışamazlar. § 30.6'ya
yazıldı. **Aksiyonunuz var — `B-040`.**

### F-011 · Dev proxy'nin SSE'yi gzip'lemesi — yazıldı
Ölçümünüz § 30.6'ya, `proxy_buffering off` satırının yanına girdi: "araya giren
her şey tamponlar", nginx **ve** Next'in dev rewrite'ı. Rakamlarınız da orada.
Doğru yere işaret ettiniz — bir daha "SSE akmıyor" denildiğinde aranacak ikinci
yer artık orası.

### F-012 · `used > limit` — karar verildi, iki alan oldu
Sayaç **denemeleri** sayıyor ve bu kasıtlı: reddedilen istek birimini geri
almıyor, yoksa sınırını aşmış bir kullanıcı sayaç tavanda sabitken ucu döverdi.
Yani sayı yanlış değil, **adı** yanlıştı.

Tercihinize uyduk — ikisini aynı alanda toplamıyoruz:

```
used       harcanan, asla limit'ten büyük değil  →  "20 of 20" basılabilir
attempted  birim alan her istek, reddedilenler dahil (26)
remaining  limit - used, asla negatif değil
```

Kırpma tek bir fabrikada ve bir invariant onu orada tutuyor: `used > limit`
taşıyan bir `Usage` inşa edilemiyor. **Aksiyonunuz var — `B-040`.**

### F-002 · Ters tarih aralığı — kapandı
`endDate >= startDate` artık `EntryService`'te, hem `POST /profile/entries` hem
`PATCH /profile/entries/{id}` için. İhlal **400 `VALIDATION_FAILED`** +
`params.fields: ["endDate"]` — istediğiniz şekil.

`PATCH` karşılaştırmayı **yamanın sonucu** üzerinde yapıyor, gövdesi üzerinde değil:
`{"endDate": "2020-01-01"}` saklı `startDate`'e karşı, `{"startDate": "2026-01-01"}`
saklı `endDate`'e karşı ölçülüyor. İkisi de reddediliyor. `{"endDate": null}` hâlâ
geçiyor — "sürüyor"da karşılaştıracak ikinci tarih yok — ve eşit tarihler geçerli
(tek günlük sertifika/hackathon gerçek bir entry, `>` değil `>=`).

Kural `spec/08-api.md` § 35.2'ye yazıldı, yani sözleşmenin parçası.
**Sizde bir şey değişmiyor:** istemci kontrolünüz kalsın, artık tek savunma değil.
Üç entegrasyon testi: yaratmada ters aralık, yamayla iki uçtan ters çevirme, aynı gün.

### F-001 · Demote'ta sürüm artışı — kapandı, davranış değişti
Sürüm artıyor tarafını seçtik. Diğer seçenek `spec`'e "iyimser kilit bu satırda
çalışmıyor" istisnası yazmaktı; tarif ettiğiniz kayıp-yazma penceresi gerçekti.

Kök neden bulduğunuz yerdeydi: demote tek satırlık bir toplu JPQL `update` ve toplu
update `@Version`'ın yanından geçiyor. `update versioned` oldu, ayrıca yalnız
**gerçekten birincil olan satırı** hedefliyor.

```
başlangıç   tr primary=true  v=0   |  en primary=false v=2
{"primary":true} → en          tr primary=false v=1   |  en primary=true  v=3
```

**Aksiyonunuz var — `B-034` olarak açıldı.** Kendi cümlenizle: yerel demote'un
sürümü de artmalı, yoksa `usePatchVariant` sunucuyla artık hizalı değil.

Atomun promote'a karışmayan diğer sözcüklemeleri **sürümlenmiyor**; etag'leri
geçerli kalıyor. Bunu ayrıca test ettik, çünkü "hepsini artır" düzeltmesi promote'u
tamamen kırıyor. Kural `spec/08-api.md` § 35.6'da.

---

## Frontend tarafında ne yapıldı

**F-002** — istemci kontrolü (`lib/forms/profileSchemas.ts`) **kaldı**; artık tek
savunma değil ama round trip'ten önce ve alanın yanında cevap veren taraf o. Mock
handler'ı da reddedecek şekilde güncellendi, yoksa dev/test sunucudan sapardı.
Doğrulandı: create `400` + `endDate`, eşit tarih `201`, patch iki yönden de
saklanan yarıya göre, ileri aralık `200`.

**F-001** — `B-034` olarak geri geldi ve uygulandı; ayrıntı `to-frontend.md` ·
`B-034` altında.

---

### F-003 · Yazma yanıtındaki `completeness` — kapandı
Ölçümünüz birebir doğruydu ve sebebi tam olarak tarif ettiğiniz yerdeydi:
`ProfileService.replace()` rakamı hiç hesaplamıyordu, yalnız `readOwn()`
hesaplıyor. `PUT /profile` ve `PUT /profile/preferences` artık kaydetmeden
önce yeniden hesaplıyor, yani yanıt **yazmadan sonrasını** taşıyor.

Kural `spec/08-api.md` § 35.6'da: **`completeness` taşıyan bir yanıt güncel bir
değer taşır** — kolonun her yazmadan sonra güncel olduğu değil; bölüm/entry/atom
uçları başı döndürmüyor ve rakamı bir sonraki okumaya bırakıyor.
**Aksiyonunuz:** `PUT` sonrası yeniden okuma kaldırılabilir.

Bir not, çünkü sizde de aynı şekilde saklanır: "değişim olmayan iki tur uyuşuyor"
dediğiniz maskeleme testte de çıktı. Tercihleri ölçen testimiz düzeltmesiz de
geçti — etag'i almak için yaptığı `GET` saklı rakamı tazeliyor, yani iki yazma
arasındaki her okuma bayatlığı onarıyor. ETag'i önceki yazmanın **yanıtından**
alınca düştü.

### F-004 · Omitted alanların tekdüze temizlenmesi — kapandı, davranış değişti
İki seçeneğinizden "temizlensin" tarafını seçtik ama uygulaması farklı oldu:
`source_language` kolonu `NOT NULL DEFAULT 'en'`, yani temizlenecek bir değer
yok ve `DEFAULT`'a düşürmek Türkçe yazılmış bir profili herhangi bir baş
düzenlemesinde sessizce İngilizceye çevirirdi. Alan **gövdede zorunlu** oldu.
Artık başın hiçbir alanı merge edilmiyor; `preferences` haklı olarak
beklediğiniz gibi kendi ucunda kalıyor.

**Aksiyonunuz var — `B-035`.** Şema değişti, `gen:api` sonrası tip de.

### F-005 · Entry `PATCH`'te `params.fields` — kapandı
Kural artık isteğin gönderdiği ucu adlandırıyor; tam tablo `spec/08-api.md`
§ 35.2'de. Kontrol yine **yamanın sonucu** üzerinde, çünkü aralığı bozan tek
uç da olabilir — değişen yalnız hangi alanın raporlandığı.

Yanına, sormadığınız ama sizi ilgilendiren bir davranış: hiçbir tarihe
dokunmayan bir `PATCH` artık hiç denetlenmiyor. Aksi hâlde F-002'den önce
ters kaydedilmiş bir satır, ilgisiz bir başlık düzenlemesini düzeltilecek
alanı adlandıramadan reddederdi.

**Aksiyonunuz var — `B-036`**, create'in iki alan birden döndürmesiyle birlikte.

### F-006 · Birincil sözcükleme kuralı — kapandı, ve ölçümünüz eksikti
Kural `spec/08-api.md` § 35.2'ye yazıldı. Sorduğunuz ayrımın cevabı: **iki ayrı
kural**, ve ikisi zaten farklı `params.fields` döndürüyor.

```
son sözcükleme            400 fields: ["variantId"]   → atomu sil
birincil, başkası var     400 fields: ["primary"]     → önce başkasını birincil yap
```

İkincisini `variantId` ölçmüşsünüz; gerçek uçta `primary` dönüyor ve bunun
Aşama 1'den beri entegrasyon testi var. Muhtemelen mock'unuzdan ölçüldü.
Bizim tarafta eksik olan şuydu: **birinci durumun testi yalnız 400'ü kontrol
ediyordu**, yani ayrımın kendisi test edilmemişti — artık ikisi de sabit.
Sözcükleme silme kontrolünü çizerken ayırmanız gereken şey tam olarak bu.

### F-007 · Kota gün dönümü — karar verildi
**Gün sınırı UTC**; Türkiye'de sayaç 03:00'te döner. `usage_counters.period`
zaten saat dilimsiz bir `DATE` ve UTC onu tek anlamlı kılan okuma: sunucunun
dilimi değişse de aynı satır aynı günü gösterir, yaz saati sınırı yok. Gömülü
bir `Europe/Istanbul` o dilimin dışına ilk çıkan kullanıcıda sessizce yanlış
olurdu; istemcinin bildirdiği dilim ise kota kaçırmak için ayarlanabilirdi.

**Tercihiniz kabul:** `resetsAt` telde her zaman offset taşıyan bir ISO-8601
**anı** (`2026-08-22T00:00:00Z`), yalnız saat değil — `capabilities.quotaResetsAt`
ve `QUOTA_EXCEEDED` / `PROFILE_QUOTA_EXCEEDED` `params`'ı için de aynı. Metni
kullanıcının yerelinde yazacak taraf sizsiniz; `Retry-After` yanında saniye
cinsinden kalıyor, istemci saati yanlışsa doğru olan tek değer o.

Karar `spec/08b-api-contract.md` EK D.6.5'te, `period` kolonunun yorumu
`spec/04-data-model.md`'de; `STATUS.md`'nin açık kararlar tablosundan düştü.
**Henüz kod yok** — `resetsAt` gönderen uç Aşama 2, Adım 2.7 ile geliyor.
