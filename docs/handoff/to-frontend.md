# → Frontend

> **Kanal kuralları**
> - Backend yazar, frontend okur ve `OPEN` → `ACK` taşır.
> - Her madde bir ID taşır (`B-nnn`), numaralar tekrar kullanılmaz.
> - **Dosya 100 satırı geçerse arşivleme gecikmiştir.** `ACK` maddeleri `resolved/`'a taşınır.
> - API *şekli* için otorite OpenAPI şemasıdır. Burası **neden değişti + ne yapman lazım** taşır.
> - Kalıcı kural niteliğindeki maddeler `spec/`'e işlenir ve buradan silinir.

---

## OPEN

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

---

## ACK — frontend tamamladı, backend arşivleyebilir

*(boş — Aşama 1 maddeleri `resolved/to-frontend-2026-08.md`'de)*

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
