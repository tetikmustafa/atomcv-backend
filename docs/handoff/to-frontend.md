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

İlana özel üretimin senkron yüzü hazır. `POST /api/v1/generations` **202** ve
`Location: /api/v1/jobs/{jobId}` dönüyor; gövde `{ jobId, status }`.
`GET /api/v1/jobs/{jobId}` işin nerede olduğunu söylüyor.

**Ön kontroller senkron.** İlan gibi okunmayan bir metin ve içi boş bir profil
**422 ile anında** reddediliyor, kuyruğa hiç girmiyor: `UNPARSEABLE_JOB_DESCRIPTION`
ve `INSUFFICIENT_PROFILE`. Yani "kabul edildi, otuz saniye izlendi, sonra düştü"
diye bir akış yok — `B-037`'nin `continue_anyway` bayrağı da burada işe yarıyor,
istekte `acknowledgePreflight: true`.

**Aksiyonunuz — `label` bir çeviri anahtarı, cümle değil.** § 30.6'nın örneği
düz metin taşıyor; § 35.4 ile çelişiyordu ve **anahtar tarafını seçtik**.
`GET /jobs/{id}` şunu döndürüyor:

```json
{ "jobId": "...", "status": "running",
  "phase": "B", "label": "generation.phase.SCORING", "pct": 50 }
```

Gereken anahtarlar: `generation.phase.ANALYSING`, `.MEASURING`, `.SCORING`,
`.RENDERING`. Metni siz yazıyorsunuz; sunucu tek dilde cümle göndermiyor.

**Terminal alanlar yalnız kendi durumlarında var:** `generationId` sadece
`completed`'da, `error` sadece `failed`'da. İkisinden biri gelince yoklamayı
bırakabilirsiniz.

**`Idempotency-Key` onurlandırılıyor** (`POST /generations`): aynı anahtar aynı
işi döndürüyor, çift tıklama tek CV üretiyor.

**SSE indi.** 202 yanıtı artık `streamUrl` de taşıyor
(`/api/v1/jobs/{jobId}/stream`) — yolu siz kurmayın, sunucu veriyor.
`GET /api/v1/jobs/{jobId}/stream` üç olay adı kullanıyor: koşarken `phase`,
sonra **tam olarak bir tane** `completed` veya `failed`, ardından akış kapanıyor.

```
event: phase       data: {"phase":"C","label":"generation.phase.RENDERING","pct":70,"detail":""}
event: completed   data: {"generationId":"...","pageCount":1}
event: failed      data: {"code":"...","params":{},"resolutions":[]}
```

**Bağlanır bağlanmaz güncel durum geliyor**, yani ekran hiç boş başlamıyor ve
yeniden bağlanma kendiliğinden yakalanıyor. **202 ile abonelik arasında biten
bir iş de sonucunu gönderiyor** — o yüzden "önce abone ol sonra oku" yarışını
kovalamanıza gerek yok.

`Last-Event-ID` kabul ediliyor ama **oynatma yapılmıyor**; `id` yalnız tek bir
akış içinde sıralama. Sürekliliğe değil **terminal olaya** güvenin. Akış terminal
olay olmadan kapanırsa `GET /jobs/{jobId}` desteklenen geri düşüş.

**Henüz gelmeyen:** `GET /generations/{id}/download` bir sonraki dilimde.
`POST /generations/general` **hâlâ duruyor**, o dilimde kalkacak (`B-022`).

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
