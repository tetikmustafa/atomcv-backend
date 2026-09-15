# → Frontend

> **Kanal kuralları**
>
> - Backend yazar, frontend okur ve `OPEN` → `ACK` taşır.
> - Her madde bir ID taşır (`B-nnn`); numaralar tekrar kullanılmaz.
> - **Dosya 100 satırı geçerse arşivleme gecikmiştir.** `ACK` maddeleri `resolved/`'a taşınır.
> - API _şekli_ için otorite OpenAPI şemasıdır. Burası **neden değişti + ne yapman lazım** taşır.
> - Kalıcı kural niteliğindeki maddeler `spec/`'e işlenir ve buradan silinir.

---

## OPEN

**Hepsi iki denetimden (2026-09-15 ve 2026-09-16):** spec baştan sona kodla
karşılaştırıldı; "dokümanda var, kodda yok" olan her şey ya yazıldı ya sapma
olarak kaydedildi. **Önce `npm run gen:api` koş** — ilk turda altı uç ve üç
şema, ikinci turda `SelectionLine` değişti (`B-108`).

> **Dosya 100 satırı geçti ve bu bir arşivleme değil koordinasyon meselesi**
> (kanal kuralı): `B-100`-`B-110`'un hiçbiri `ACK` almadı, yani taşınabilecek
> madde yok. On biri de denetimlerden; okunup ACK'lendiklerinde hepsi birden
> `resolved/`'a iner.

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

---

## Dağıtım bekleyen doğrulamalar

Üçü de bir dağıtım bekliyor: OAuth sıçraması (`B-048`), sihirli bağlantının
Turnstile'ı (`B-050`), `B-083`'ün challenge'ı. **`B-100` üçünün önündeki kapıyı
açtı** — ilk dağıtımda sırayla denenmeleri gerekiyor. `B-076`'dan kalan tek şey
yayımlanan sağlayıcı sayfasını `ProcessorAudit`'in açılış satırına karşı okumak.

Kalıcı kuralların `spec/`'e işlendiği yerler: `resolved/to-frontend-2026-08.md`.
`B-097`-`B-099` `resolved/to-frontend-2026-09.md`'ye indi (2026-09-15).
