# → Frontend

> **Kanal kuralları**
> - Backend yazar, frontend okur ve `OPEN` → `ACK` taşır.
> - Her madde bir ID taşır (`B-nnn`), numaralar tekrar kullanılmaz.
> - **Dosya 100 satırı geçerse arşivleme gecikmiştir.** `ACK` maddeleri `resolved/`'a taşınır.
> - API *şekli* için otorite OpenAPI şemasıdır. Burası **neden değişti + ne yapman lazım** taşır.
> - Kalıcı kural niteliğindeki maddeler `spec/`'e işlenir ve buradan silinir.

---

## OPEN

*(şu an açık madde yok)*

---

## ACK — frontend tamamladı, backend arşivleyebilir

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
