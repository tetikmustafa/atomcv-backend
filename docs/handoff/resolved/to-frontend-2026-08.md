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
