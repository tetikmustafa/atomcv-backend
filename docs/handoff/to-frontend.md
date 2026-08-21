# → Frontend

> **Kanal kuralları**
> - Backend yazar, frontend okur ve `OPEN` → `ACK` taşır.
> - Her madde bir ID taşır (`B-nnn`), numaralar tekrar kullanılmaz.
> - **Dosya 100 satırı geçerse arşivleme gecikmiştir.** `ACK` maddeleri `resolved/`'a taşınır.
> - API *şekli* için otorite OpenAPI şemasıdır. Burası **neden değişti + ne yapman lazım** taşır.
> - Kalıcı kural niteliğindeki maddeler `spec/`'e işlenir ve buradan silinir.

---

## OPEN

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

---

## ACK — frontend tamamladı, backend arşivleyebilir

*(boş — `B-034` `resolved/to-frontend-2026-08.md`'de)*

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
