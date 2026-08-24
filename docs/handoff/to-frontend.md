# → Frontend

> **Kanal kuralları**
> - Backend yazar, frontend okur ve `OPEN` → `ACK` taşır.
> - Her madde bir ID taşır (`B-nnn`), numaralar tekrar kullanılmaz.
> - **Dosya 100 satırı geçerse arşivleme gecikmiştir.** `ACK` maddeleri `resolved/`'a taşınır.
> - API *şekli* için otorite OpenAPI şemasıdır. Burası **neden değişti + ne yapman lazım** taşır.
> - Kalıcı kural niteliğindeki maddeler `spec/`'e işlenir ve buradan silinir.

---

## OPEN

### B-038 · İlana özel üretimin senkron yüzü + SSE indi; ilerleme metni **sizin**
**Since:** commit `<bu PR>` · Adım 2.6 · **Spec:** `spec/08-api.md` § 35.3, `spec/08b-api-contract.md` EK D.6.4

`POST /api/v1/generations` **202** + `Location: /api/v1/jobs/{jobId}` dönüyor;
gövde `{ jobId, status, streamUrl }`. `GET /api/v1/jobs/{jobId}` durumu söylüyor.

**Ön kontroller senkron.** İlan gibi okunmayan metin ve boş profil **422 ile
anında** reddediliyor, kuyruğa girmiyor (`UNPARSEABLE_JOB_DESCRIPTION`,
`INSUFFICIENT_PROFILE`). `B-037`'nin bayrağı burada: `acknowledgePreflight: true`.

**Aksiyonunuz — `label` bir çeviri anahtarı, cümle değil** (§ 30.6 düz metin
taşıyordu, § 35.4 ile çelişiyordu). `GET /jobs/{id}`:

```json
{ "jobId": "...", "status": "running",
  "phase": "B", "label": "generation.phase.SCORING", "pct": 50 }
```

Anahtarlar: `generation.phase.ANALYSING`, `.MEASURING`, `.SCORING`, `.RENDERING`.

**Terminal alanlar yalnız kendi durumlarında var:** `generationId` sadece
`completed`'da, `error` sadece `failed`'da — biri gelince yoklamayı bırakın.
**`Idempotency-Key`** onurlandırılıyor: çift tıklama tek CV.

**SSE:** `streamUrl`'i sunucu veriyor, siz kurmayın. Üç olay adı: koşarken
`phase`, sonra **tam olarak bir tane** `completed` ya da `failed`, akış kapanır.

```
event: phase       data: {"phase":"C","label":"generation.phase.RENDERING","pct":70,"detail":""}
event: completed   data: {"generationId":"...","pageCount":1}
event: failed      data: {"code":"...","params":{},"resolutions":[]}
```

**Bağlanır bağlanmaz güncel durum geliyor** — ekran boş başlamıyor, yeniden
bağlanma kendiliğinden yakalanıyor, ve **202 ile abonelik arasında biten iş de
sonucunu gönderiyor**. `Last-Event-ID` kabul ediliyor ama **oynatma yok**; `id`
tek akış içinde sıralama. Sürekliliğe değil **terminal olaya** güvenin; akış
terminal olay olmadan kapanırsa `GET /jobs/{jobId}` geri düşüş.

**`POST /generations/general` KALDIRILDI** (`B-022` kapandı). Genel CV modu
kaybolmadı, **aynı uca taşındı**: `jobDescription` opsiyonel, yokluğu genel mod.
Boş gövde (`{}`) de 202 dönüyor. Aksiyonunuz: o uca giden çağrıyı
`POST /generations` + iş takibine çevirin.

**`GET /api/v1/generations/{generationId}/download`** indi:
`application/pdf`, `Content-Disposition: attachment`, dosya adı yalnız tarih
taşıyor (indirme klasörüne kişisel veri yazmıyoruz). Belge **üretim anındaki
metinden** yeniden render ediliyor — kullanıcı sonradan bir maddeyi düzenlerse
indirdiği CV değişmez, başvurduğu belge neyse odur. Yeniden render edilecek
şeyi olmayan bir satır `410` + `GENERATION_ARTIFACT_EXPIRED` + `retry` döner.

`gen:api`'yi bu PR'dan sonra çalıştırın: `/generations/general` tipten
düşecek.

`gen:api` bu PR'dan sonra çalıştırılabilir; şema uçları taşıyor.

---

### B-037 · `resolutions[].action` sözlüğü onuncu değeri kazandı: `continue_anyway`
**Since:** commit `<bu PR>` · Adım 2.3 · **Spec:** `spec/08b-api-contract.md` EK D.6.1, `spec/05-pipeline-a-c.md` § 18.1

Ön kontrol (§ 18.1) **engelleme değil sorma**: üç çıkış yolu var, sözlükte
ikisi vardı. `retry` karşılamıyor — aynı metin aynı şekilde yine reddedilir,
dönen bir döngü olurdu.

**Aksiyon:** `errors.resolutions.continue_anyway` için ICU mesajı ve düğme.
Kod `UNPARSEABLE_JOB_DESCRIPTION` (422), `params` değişmedi (`confidence`,
`skillsFound`); ön kontrol reddinde **ikisi de 0**, makullük kapısı (§ 18.4)
reddettiğinde gerçek değerler geliyor. Sıra: `continue_anyway`,
`paste_full_posting`, `continue_as_general_cv`.

**Uç indi:** `POST /generations` gövdesinde `acknowledgePreflight: true`
(bkz. `B-038`).

---

## ACK — frontend tamamladı, backend arşivleyebilir

*(boş — `B-035` ve `B-036` `resolved/to-frontend-2026-08.md`'de)*

---

## Kalıcı kurallar

Eski maddelerin `spec/`'e işlendiği yerlerin tablosu
`resolved/to-frontend-2026-08.md`'ye taşındı (2026-08-24) — dosya sınırı.
