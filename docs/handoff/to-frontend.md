# → Frontend

> **Kanal kuralları**
> - Backend yazar, frontend okur ve `OPEN` → `ACK` taşır.
> - Her madde bir ID taşır (`B-nnn`), numaralar tekrar kullanılmaz.
> - **Dosya 100 satırı geçerse arşivleme gecikmiştir.** `ACK` maddeleri `resolved/`'a taşınır.
> - API *şekli* için otorite OpenAPI şemasıdır. Burası **neden değişti + ne yapman lazım** taşır.
> - Kalıcı kural niteliğindeki maddeler `spec/`'e işlenir ve buradan silinir.

---

## OPEN

### B-022 · `POST /generations/general` geçicidir
**Since:** Adım 1.8 · **Spec:** `spec/08-api.md` § 35.3
Senkron, Aşama 1'e özgü. Gövde opsiyonel (`maxPages` 1-10, `language`). Yanıt `application/pdf`, **hiçbir yere kaydedilmiyor** — indirme bağlantısı, geçmiş, düzenleme döngüsü yok.
Aşama 2'de `POST /generations` + 202 + iş akışı gelecek.
**Aksiyon:** Bu uca **kalıcı ekran bağlama.** Geçici bir "önizle ve indir" akışı yeterli.
Hatalar: 422 `INSUFFICIENT_PROFILE` · 422 `PAGE_LIMIT_EXCEEDED` · 409 `CONFLICTING_PREFERENCES` · 502 `COMPILATION_FAILED` · 400 `VALIDATION_FAILED`

### B-021 · `PAGE_LIMIT_EXCEEDED` için "tekrar dene" yanlış çözüm
**Spec:** `spec/06-pipeline-d-g.md` § 23
Sunucu içeriği kendi iki kez kısaltmayı deniyor; bu hata geldiyse denemeler bitmiştir.
**Aksiyon:** Kullanıcıya sayfa sınırını artırmayı veya içerik çıkarmayı öner. Retry düğmesi koyma. `params`: `actual`, `limit`.

### B-024 · Bayat varyant düğmeleri Aşama 2'ye ait
**Spec:** `spec/09-frontend.md` § 37.6
`Variant.stale` Aşama 1'de **her zaman false**; yeniden üretim ucu yok.
**Aksiyon:** Rozeti göster, kontrolü çizme. (Mevcut kararınız doğru — teyit.)

### B-030 · Operasyon id'leri adlandırıldı
`list_2` → `listAtoms`, `create_1` → `createEntry`, `patch` → `patchSection` …
**Aksiyon:** `gen:api` sonrası üretilen yüzeye **isimle** bağlanan yerleri ara; kırılacaklar.

### B-032 · Seed profilinde iki sözcüklemeli atom var
`senior_backend_tr` artık `enabledLanguages: ["tr","en"]`; Deneyim'in ilk maddesi Türkçe birincilin yanında İngilizce alternatif taşıyor.
**Aksiyon:** Sekmeler, promote ve birincil-önce sıralama mock'suz test edilebilir. `make db-reset && make dev` gerekiyor — seeder mevcut profile dokunmuyor (P8).

---

## ACK — frontend tamamladı, backend arşivleyebilir

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
