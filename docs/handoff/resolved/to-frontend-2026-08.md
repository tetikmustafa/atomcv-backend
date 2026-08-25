# Kapatılmış — → Frontend · 2026-08

Aşama 1 kapanışında `to-frontend.md`'nin `ACK` bölümünden taşındı. Maddeler
frontend tarafından uygulandı ve teyit edildi; burada yalnız "bu karar ne zaman
ve neden verilmişti?" sorusu için duruyorlar.

---

### B-025 · Media type `application/json`, `If-Match: "7"`
§ 35.6'nın `application/merge-patch+json` yazması hataydı; öyle gönderen istek artık **415**. ETag'de `v` öneki yok. 405/406/400 doğru kodla geliyor.
**Yeni ICU anahtarları:** `METHOD_NOT_ALLOWED`, `NOT_ACCEPTABLE`, `UNSUPPORTED_MEDIA_TYPE`

### B-026 · Değişiklik yapmayan yazma sürümü artırmaz
Aynı değerlerle `PATCH` → 200 + **aynı** sürüm. Autosave için taşıyıcı.

### B-027 · Atom ve varyant sürümleri bağımsız
`PATCH /atoms/{id}` atomun `version`'ını artırır, varyantlarınkine dokunmaz. Editör atom başına **iki** sürüm tutar.

### B-028 · Promote için metni geri gönderme
`PATCH …/variants/{id}` artık `content` istemiyor; `{"primary": true}` yeterli.
**Hata düzeltmesiydi:** metni geri gönderen istek `tone`'u siliyordu. `tone` üç durumlu: atlanırsa korunur, `null` gönderilirse nötr.

### B-029 · Şema artık `200`'leri ve `ETag`'i söylüyor
On operasyon başarı yanıtını, her tekil kaynak yazması `ETag`'i ilan ediyor.
`endpoints/profile.ts`'teki elle beyanlar ve `EntryPatch` null genişletmesi **geri alınabilir**. `ApiError.code`/`.status` zorunlu.

### B-031 · `?format=markdown` şemada
`/profile/export` iki media type ilan ediyor.

---

---

### B-032 · Seed profilinde iki sözcüklemeli atom var
`senior_backend_tr` artık `enabledLanguages: ["tr","en"]`; Deneyim'in ilk maddesi Türkçe birincilin yanında İngilizce alternatif taşıyor.
**Aksiyon:** Sekmeler, promote ve birincil-önce sıralama mock'suz test edilebilir. `make db-reset && make dev` gerekiyor — seeder mevcut profile dokunmuyor (P8).
**Frontend:** Doğrulandı — gerçek uca karşı, MSW kapalı, 13 kontrolün 13'ü. Sekmeler iki
sözcüklemeyi de çiziyor, sıralama birincil-önce geliyor, rozet yalnız birincide, `tone` etikette
görünüyor (`English · technical`), bayat rozeti yok (B-024 ile tutarlı). Promote `{"primary":true}`
gönderiyor, `tone` sağ çıkıyor ve karşı sözcükleme sunucuda demote ediliyor. `gen:api` yeniden
çalıştırıldı: üretilen şema commit'lideki ile **birebir aynı**, yani B-029/B-030 zaten uygulanmış.

Doğrulama **mock'ların yakalamadığı bir hata çıkardı** ve düzeltildi: promote `content`
göndermiyor, ama iyimser güncelleme onu koşulsuz yazıyordu — yani "dokunma" anlamına gelen
yokluk "temizle"ye dönüşüyor, kullanıcının okuduğu sözcükleme gidiş-dönüş boyunca ekrandan
siliniyordu. Ayrıntı `notes/current.md`.

Bir de sözleşme gözlemi: demote edilen satırın `version`'ı artmıyor. Bizi kırmıyor,
`to-backend.md` · **F-001** olarak açıldı.

### B-033 · Doküman yapısı bölündü — aynısını sizde de kurun
**Since:** commit `221a7c1`, `02441b3`, `4f890fe` · **Spec:** `docs/INDEX.md`
Tek dosyalık `teknik-mimari-dokumani.md` erişim desenine göre bölündü: `spec/` (18 dosya,
salt-okunur kopya sizde), `notes/` (repo-yerel), `handoff/` (bu kanal), `INDEX.md`, `STATUS.md`.
**Frontend:** Kuruldu. `spec/` (18 dosya), `INDEX.md`, `STATUS.md`, `handoff/`, `notes/` yerinde;
`sync-spec.sh` alınmadı. `teknik-mimari-dokumani.md` arşivsiz silindi — kopya sizde duruyor.
CLAUDE.md **927 → 347** satır: spec'i tekrar eden bölümler işaretçiye indi, "Current Stage"
devralma reçetesine döndü, Aşama 1 inşa bilgisi `notes/current.md`'ye taşındı.
`check-doc-sizes.sh` sınırı ölçülüp **360** yapıldı (sizde 280). `rsync` kullanan betiğimiz yok;
exec biti `git update-index --chmod=+x` ile işlendi — ikinci tuzak bizde de gerçekti.
Bayat atıflar da tarandı ve düzeltildi: `DOC-SYNC-REQUEST.md` (kanal onun yerini aldı),
`EK D.x` / `D.9 · n` → ilgili `spec/` dosyası, README ve ürün konsept dokümanının
işaretçileri. Üç yorum yalnız bayat işaretçi değil **yanlış** olduğu için yeniden yazıldı:
`client.ts` "500 döner" diyordu (artık 415, B-025), `endpoints/profile.ts` "şema `2xx`
beyan etmiyor" diyordu (B-029 kapattı), export yorumu "şema yalnız JSON yarısını beyan
ediyor" diyordu (B-031 kapattı).

### B-030 · Operasyon id'leri adlandırıldı
`list_2` → `listAtoms`, `create_1` → `createEntry`, `patch` → `patchSection` …
**Aksiyon:** `gen:api` sonrası üretilen yüzeye **isimle** bağlanan yerleri ara; kırılacaklar.
**Frontend:** Kırılan yer yok — `operations` yüzeyine isimle bağlanan kod yok, `endpoints/profile.ts`
tiplerini `components['schemas']`'tan alıyor. **Devam işi:** B-029 o kısıtı kaldırdığı için artık
`operations`'tan türetmek mümkün ve yanıt *sarmalayıcısındaki* değişikliği de yakalar;
`notes/current.md`'de kayıtlı.

### B-024 · Bayat varyant düğmeleri Aşama 2'ye ait
**Spec:** `spec/09-frontend.md` § 37.6
`Variant.stale` Aşama 1'de **her zaman false**; yeniden üretim ucu yok.
**Aksiyon:** Rozeti göster, kontrolü çizme. (Mevcut kararınız doğru — teyit.)
**Frontend:** Rozet ve açıklaması var, kontrol yok (`VariantTabs.tsx`).

### B-021 · `PAGE_LIMIT_EXCEEDED` için "tekrar dene" yanlış çözüm
**Spec:** `spec/06-pipeline-d-g.md` § 23
Sunucu içeriği kendi iki kez kısaltmayı deniyor; bu hata geldiyse denemeler bitmiştir.
**Aksiyon:** Kullanıcıya sayfa sınırını artırmayı veya içerik çıkarmayı öner. Retry düğmesi koyma. `params`: `actual`, `limit`.
**Frontend:** Üç katman — katalog metni retry önermiyor (`errorCatalogue.test.ts` iki dilde
sabitliyor), `isRetriable()` her 4xx'e false diyor, `ErrorPanel`'in kendi retry'ı çağrı yeri
opsiyonel ve resolutions satırının dışında.

### B-022 · `POST /generations/general` geçicidir
**Since:** Adım 1.8 · **Spec:** `spec/08-api.md` § 35.3
Senkron, Aşama 1'e özgü. Gövde opsiyonel (`maxPages` 1-10, `language`). Yanıt `application/pdf`, **hiçbir yere kaydedilmiyor** — indirme bağlantısı, geçmiş, düzenleme döngüsü yok.
Aşama 2'de `POST /generations` + 202 + iş akışı gelecek.
**Aksiyon:** Bu uca **kalıcı ekran bağlama.** Geçici bir "önizle ve indir" akışı yeterli.
**Frontend:** Bağlı ekran yok. Uca yalnız `src/mocks/` ve üretilen tipler değiniyor;
`dev/mocks` harness'ı üretim build'inde `notFound()`.

### B-034 · Demote artık sürüm artırıyor — iyimser güncellemeniz de artırmalı
**Since:** Adım 1 · F-001 kapanışı · **Spec:** `spec/08-api.md` § 35.6
`PATCH /profile/atoms/{id}/variants/{vid}` ile bir sözcükleme birincil yapıldığında
**demote edilen satırın `version`'ı da artıyor** artık. F-001'de istediğiniz buydu;
diğer seçenek sözleşmeye "iyimser kilit bu satırda çalışmıyor" istisnası yazmaktı.
**Aksiyon:** `usePatchVariant` demote'u önbelleğe kendisi uyguluyor ve `version`'a
dokunmuyordu — tesadüfen hizalıydı, artık değil. Yerel demote `version`'ı **+1**
yapmalı, yoksa invalidation gelene kadar elinizde bayat bir etag var ve o pencerede
yapılan bir yazma 412 alır.
**Değişmeyen:** Atomun promote'a karışmayan sözcüklemeleri sürümlenmiyor; onların
etag'leri geçerli kalıyor. Yani "hepsini bir artır" da doğru değil, yalnız demote edilen.
**Frontend:** Uygulandı ve doğrulandı. `usePatchVariant.onSuccess` demote edilen
satırın sürümünü **+1** yapıyor — yalnız o satırın; promote'a karışmayanlara
dokunmuyor. `version` telde opsiyonel olduğundan artış koşullu, yoksa
`If-Match: "NaN"` giderdi.

Gerçek uca karşı, MSW kapalı, iki yönde de ölçüldü — ve ilk koşum **kanıt
değildi**: `onSuccess`'in invalidation'ı koleksiyonu yeniden çekip sürümleri
seed'lediği için eksik artışı onarıyor, düzeltmesiz de geçiyordu. Ayırt etmek
için `GET /profile/atoms` tutuldu:

```
düzeltmesiz   PATCH …/variants/21f6… if-match="14" -> 412
düzeltmeli    PATCH …/variants/21f6… if-match="17" -> 200   (önbellek 16, +1)
```

Not: pencere her zaman kendini onarmıyor. Refetch yalnız koleksiyonun etkin bir
gözlemcisi varsa oluyor ve editör listesiz de çizilebiliyor — o hâlde bayat etag
kalıcı. Üç birim testi sabitliyor, MSW handler'ı da artık demote edileni
sürümlüyor.

F-002 de doğrulandı: create `400` + `endDate`, eşit tarih `201`, patch iki
yönden de saklanan yarıya göre reddediyor, ileri aralık `200`. İstemci kontrolü
kaldı — daha hızlı ve mesajı alanın yanına koyabilen taraf o.

---

### B-035 · `PUT /profile` gövdesinde `sourceLanguage` artık **zorunlu**
**Since:** commit `38993f5` · F-004 kapanışı · **Spec:** `spec/08-api.md` § 35.6
F-004'te sorduğunuz iki seçenekten "temizlensin" tarafını seçtik, ama kolon
`NOT NULL` olduğu için temizlenecek bir değer yok — `DEFAULT`'una düşürmek
Türkçe yazılmış bir profili herhangi bir baş düzenlemesinde sessizce
İngilizceye çevirirdi. Bu yüzden alan **gövdede zorunlu** oldu: eksikse
**400 `VALIDATION_FAILED`** + `params.fields: ["sourceLanguage"]`.
Artık başın **hiçbir** alanı merge edilmiyor, istisna kalmadı.
**Aksiyon:** Sizde kırılan bir ekran olmamalı — baş formu dokuz alanı da
gönderiyor. Ama şema değişti: `sourceLanguage` OpenAPI'de `required`, yani
`gen:api`'den sonra üretilen tipte opsiyonelliği kalkıyor. Alanı göndermeyen
**mock'lar ve testler** 400 almaya başlar; `POST`/`PATCH` uçları etkilenmiyor.

### B-036 · Hata `params.fields` artık isteğin gönderdiği alanı adlandırıyor
**Since:** commit `2be3bc0`, `5c5a67f` · F-005 + F-006 · **Spec:** `spec/08-api.md` § 35.2
Entry tarih kuralı (F-005) ve sözcükleme silme (F-006) için `params.fields`'ın
ne döndüğü sözleşmeye tabloyla yazıldı. Sözcükleme tarafında ölçümünüz eksikti:
ret **tek kural değil, iki** ve ikisi farklı alan döndürüyor.

```
PATCH  entries/{id} {"startDate": …}     400 fields: ["startDate"]   ← değişti
PATCH  entries/{id} {"endDate":   …}     400 fields: ["endDate"]
PATCH  entries/{id} iki uç birden        400 fields: ["startDate","endDate"]
POST   entries      ters aralık          400 fields: ["startDate","endDate"]  ← değişti
DELETE …/variants/{vid}  son sözcükleme  400 fields: ["variantId"]
DELETE …/variants/{vid}  birincil, başkası var
                                         400 fields: ["primary"]  ← siz variantId ölçmüştünüz
```

**Aksiyon:** İki yerde. (1) `params.fields`'ı input'a çeviren eşlemede
`startDate` artık gerçek bir değer ve create iki alan birden döndürüyor.
(2) Mock handler'larınız her iki silme reddi için de `variantId` döndürüyor
olmalı — `primary` durumu ayrı, ve sözcükleme silme kontrolünü çizerken
ayırmanız gereken şey tam olarak bu: `variantId` "atomu sil", `primary`
"önce başkasını varsayılan yap" demek.

**Değişmeyen:** hiçbir tarihe dokunmayan bir `PATCH` artık **hiç
denetlenmiyor**. F-002'den önce ters kaydedilmiş bir satırın başlığı bu
sayede düzenlenebiliyor; o satırların tarihini düzeltmek yine ayrı bir yama.
**Frontend (B-035):** Baş formu `sourceLanguage`'ı koşullu gönderiyordu
(`profile.sourceLanguage ? {…} : {}`) — yani alanı olmayan bir profilde onu
düşürürdü ve artık 400 alırdı. Koşul kaldırıldı, dokuz alan da her seferinde
gidiyor. Mock `PUT` de ikisini birden zorunlu tutuyor ve eksik olan(lar)ı
`params.fields`'ta adlandırıyor; `sourceLanguage` merge'ü kaldırıldı.
**`gen:api` henüz çalıştırılamadı** — sunucu ayakta değil. Şema `required`
olduğunda üretilen tipte `ProfileUpdate['sourceLanguage']` zorunlu olacak ve
bizim `toUpdate`'imiz `string | undefined` verdiği için typecheck kırılacak;
o kırılma doğru yerde ve orada karşılanacak.

**Frontend (B-036):** İki yer de yapıldı. Entry create mock'u artık
`["startDate","endDate"]`, sözcükleme silme iki ayrı ret üretiyor
(`variantId` / `primary`). Entry `PATCH` mock'u da yazıldı ve kuralı
**yamanın sonucuna** uyguluyor; hiçbir tarihe dokunmayan bir yama
denetlenmiyor. Üçü de testli, üçü de negatif kontrolden geçti.

Ölçüm hatası bizde: gerçek uca vurduk ama o vakada yalnız `status`'ü
logladık, `params`'ı hiç basmadık — `variantId`'yi *son sözcükleme*
vakasından ölçüp ikisine genelledik. Mock'tan ölçülmedi; tek ölçülmüş
vakadan iki vakaya genelleme yapıldı. Sonda artık iki reddi de ayrı basıyor.

Bunun somut karşılığı: entry düzenleme formu `toEntryPatch` ile **gördüğü
her alanı** gönderiyor, boşları `null` olarak. Yalnız değişeni göndermek
`params.fields`'ın kullanıcının ekranda görmediği bir alanı adlandırmasına
yol açardı.

---

> `to-frontend.md`'den taşındı (2026-08-24): dosya 100 satır sınırını aştı ve
> aşağıdaki tablo zaten yalnız `spec/`'e işaret eden bir indeksti.

## Kalıcı kurallar — `spec/`'e işlendi, burada tutulmuyor

| Eski # | Konu | Nerede |
|---|---|---|
| 1-4 | Run/mark kuralları (`href` zorunluluğu, bilinmeyen mark koruması, `v` sunucuya ait, `m` daima dizi) | `spec/04-data-model.md` § 14.1 |
| 5 | `content_hash` düz metnin hash'i | `spec/04-data-model.md` § 16.2 |
| 6 | Sözlükler küçük harf, hata kodu büyük harf | `spec/08b-api-contract.md` |
| 7, 10-12 | Hata kataloğu, `params` disiplini, göreli `type` | `spec/08b-api-contract.md` |
| 8 | ETag kapsamı (`generations` ETag taşımaz) | `spec/08-api.md` § 35.6 |
| 9 | Anonim TTL kayar — "son etkinliğinden iki saat sonra" | `spec/08-api.md` § 35.7 |
| 13-20, 23 | Profil/bölüm/entry/atom/varyant uçları, export, `completeness`, `complete_profile` | `spec/08-api.md` |

---

> `to-frontend.md`'den taşındı (2026-08-25): frontend üçünü de
> kapattı ve Aşama 2 iki repoda da bitti.

### B-037 · `continue_anyway` — kapandı

ICU mesajı yazıldı; Türkçesi § 18.1'in birebir kopyası ("Yine de devam et"),
İngilizcesi ona uyduruldu. Düğme `ErrorPanel`'den geliyor, koda gömülü değil.
Davranış: **yeniden gönderme değil**, aynı metin `acknowledgePreflight: true`
ile — ayrımı sabitleyen bir birim testi ve bir e2e var. Gerçek uçta
doğrulandı: kısa metin **422**, üç çıkış yolu spec sırasında, "Yine de devam
et" sonrası **202**.

### B-038 · Üretim uçları + SSE — kapandı

`gen:api` çalıştı; `/generations` 202, `/jobs/{id}`, akış ve `/download`
bağlandı. `label` çeviri anahtarı olarak işleniyor (`generation.phase.*`),
biten iş `pct: 100` ve fazsız, düşen iş nerede durduysa orada duruyor.
Akış terminal olay olmadan kapanırsa `GET /jobs/{id}`'ye geri düşülüyor.

**İki ölçüm sizin için:** (1) `completed` olayı `matchLevel` **taşımıyor** —
§ 30.6'nın örneği taşıyor; uygunluk raporu bu yüzden yazılamadı (`F-008`).
(2) Bağlanıştaki anlık durum boş dize taşıyor, alan düşürmüyor (`F-010`).

### B-039 · Kota — kapandı

`GET /account/usage` ekrana bağlandı (`/generate` başlığında, harcanmadan
önce), `QUOTA_EXCEEDED` ve `GENERATION_PAUSED` mesajları yazıldı. 503,
"hesabınız kapandı" demiyor: profilin okunur ve dışa aktarılabilir kaldığını
söylüyor.

**`Retry-After` okunmuyor**, ve bu bilinçli: onu tüketecek otomatik bir
yeniden deneme yok ve 429 `retry` resolution'ı taşımıyor — okunmayan bir
başlığı `ApiError`'a koymak kullanıcısı olmayan bir alan olurdu. Otomatik
deneme geldiği gün doğru olan tek değer o olacak; şimdilik metin `resetsAt`
üzerinden yazılıyor.

**Bir ölçüm sizin için:** `used`, `limit`'i **geçiyor** — reddedilen istek de
sayılıyor (`F-012`).

---

---

## Frontend'in ACK'ledikleri (arşivlendi 2026-08-25)

### B-040 · Üç şema düzeltmesi — kapandı

`gen:api` çalıştı. `generalMode` hiç gönderilmiyordu, yani soruyu sormak tek
düzeltmeydi. İlerleme alanlarının düşürülmesi `F-010`'u kaynağında kapattı;
`toProgress`'teki savunma **duruyor** ama artık son hat, tek hat değil.
Kotadaki yara bandı kalktı: `remaining` sunucunun, karşılaştırma bizim değil.

**Bir ölçüm mock'umuzu düzeltti:** kotanın reddettiği istek (429) birim
alıyor, **ön kontrolün reddettiği (422) almıyor**. "Reddedilenler dahil"
cümlesi iki türlü uygulanabilirdi; sonda hangisi olduğunu söyledi.

### B-041 · Uygunluk raporu — kapandı

Sonuç ekranı üretimi okuyor, işi değil: `GET /generations/{id}` geldiği için
iş cache'ini tarayan geçici çözüm silindi ve **yeniden yükleme gerçek uca
karşı doğrulandı** — MSW bunu kanıtlayamıyor, durumu sayfayla ölüyor.

Ekranda yüzde yok ve testler bunu iki yönden sabitliyor: `%` yok **ve**
ondalık sayı yok. Eksik beceriler **iki ayrı liste** — sayılar hangi tarafta
kaç eksik olduğunu söylüyor, adları birleştirmek hangi boşluğun mülakata mal
olduğunu gizlerdi. Öneri cümlesi § 23.3'ün kendi cümlesi, önce eksik zorunlu
beceriden yazılıyor. Genel modda rapor hiç çizilmiyor.

**`level` kapalı sözlük olarak bırakıldı**, `ResolutionAction` gibi
açılmadı: tanınmayan bir seviyenin basılacak düğmesi yok, ve yanındaki sayılar
zaten doğruyu söylüyor.
