# → Frontend

> **Kanal kuralları**
> - Backend yazar, frontend okur ve `OPEN` → `ACK` taşır.
> - Her madde bir ID taşır (`B-nnn`), numaralar tekrar kullanılmaz.
> - **Dosya 100 satırı geçerse arşivleme gecikmiştir.** `ACK` maddeleri `resolved/`'a taşınır.
> - API *şekli* için otorite OpenAPI şemasıdır. Burası **neden değişti + ne yapman lazım** taşır.
> - Kalıcı kural niteliğindeki maddeler `spec/`'e işlenir ve buradan silinir.

---

## OPEN

### B-037 · `resolutions[].action` sözlüğü onuncu değeri kazandı: `continue_anyway`
**Since:** commit `<bu PR>` · Adım 2.3 · **Spec:** `spec/08b-api-contract.md` EK D.6.1, `spec/05-pipeline-a-c.md` § 18.1

Faz A'nın ön kontrolü (§ 18.1) **engelleme değil, sorma** olarak tanımlı ve üç
çıkış yolu sunuyor: `[Yine de devam et] [Metni düzenle] [Genel CV oluştur]`.
Sözlükte son ikisi vardı (`paste_full_posting`, `continue_as_general_cv`),
**birincisi yoktu**. `retry` de karşılamıyor: `retry` "geçici hata, aynen
yeniden gönder" demek, oysa ön kontrol birebir aynı metni birebir aynı şekilde
yine reddeder — dönen bir döngü olurdu.

**Aksiyon:** `errors.resolutions.continue_anyway` için ICU mesajı ve düğme
gerekiyor. Kod `UNPARSEABLE_JOB_DESCRIPTION` (422), `params` değişmedi:
`confidence: number`, `skillsFound: integer`. Ön kontrol reddinde **ikisi de 0**
— hiçbir şey analiz edilmedi ve sıfır bunu dürüstçe söylüyor; makullük kapısı
(§ 18.4) reddettiğinde gerçek değerler geliyor. Mesajınız iki durumu ayırmak
isterse `skillsFound == 0 && confidence == 0` ayırt edici.

Üç resolution sunucudan **bu sırayla** geliyor: `continue_anyway`,
`paste_full_posting`, `continue_as_general_cv`.

**Henüz uç yok.** `continue_anyway`'in isteğe koyacağı onay bayrağı
`POST /generations` ile, Adım 2.6'da geliyor — bugün bağlayacağınız bir çağrı
yok, ama sözlük büyüdü ve `gen:api` sonrası tipte görünecek.

---

## ACK — frontend tamamladı, backend arşivleyebilir

*(boş — `B-035` ve `B-036` `resolved/to-frontend-2026-08.md`'de)*

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
