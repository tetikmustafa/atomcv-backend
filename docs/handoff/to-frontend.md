# → Frontend

> **Kanal kuralları**
> - Backend yazar, frontend okur ve `OPEN` → `ACK` taşır.
> - Her madde bir ID taşır (`B-nnn`), numaralar tekrar kullanılmaz.
> - **Dosya 100 satırı geçerse arşivleme gecikmiştir.** `ACK` maddeleri `resolved/`'a taşınır.
> - API *şekli* için otorite OpenAPI şemasıdır. Burası **neden değişti + ne yapman lazım** taşır.
> - Kalıcı kural niteliğindeki maddeler `spec/`'e işlenir ve buradan silinir.

---

## OPEN

### B-039 · Kota indi: iki yeni durum, bir yeni hata kodu
**Since:** commit `<bu PR>` · Adım 2.7 · **Spec:** `spec/10-security.md` § 44, EK D.6.5

**`GET /api/v1/account/usage`**: her metrik için `{ metric, used, limit,
resetsAt }`. **İki metrik** (`generation`, `profile_extract`) ve ikisi de her
zaman dönüyor — eksik giriş "sıfır" demek değil, hiç olmuyor. `resetsAt` mutlak
bir an (UTC gece yarısı, Türkiye'de 03:00); metni siz yazıyorsunuz.

**`QUOTA_EXCEEDED` (429)** artık gerçekten dönüyor: `params` içinde `metric`
(string) ve `resetsAt` (timestamp), **başlıkta `Retry-After`** (saniye).
İkisi birden, ve gereksiz tekrar değil: `resetsAt` kullanıcının yerelinde
yazacağınız mutlak an, `Retry-After` ise süre — **istemcinin saati yanlışsa
doğru olan tek değer o**, ve zaten hemen tekrar deneyecek istemci tam olarak
odur. **`resolutions` boş**: kapalı sözlükte "yarın tekrar dene" yok ve `retry`
bunun tersini söylüyor.

**Yeni kod: `GENERATION_PAUSED` (503), parametresiz, `retry` ile.** § 44.3'ün
acil freni. **Veri erişimi durmaz** — profil görülebilir, düzenlenebilir, dışa
aktarılabilir; UI bunu böyle anlatmalı, "hesabınız kapandı" gibi değil.

Kota kuyruğa alırken düşüyor ve **başarısız her işte geri veriliyor**.

---

### B-038 · İlana özel üretimin uçları + SSE indi; ilerleme metni **sizin**
**Since:** Adım 2.6 · **Spec:** `spec/08-api.md` § 35.3, `spec/08b-api-contract.md` EK D.6.4

`POST /api/v1/generations` **202** + `Location` + gövdede `{ jobId, status,
streamUrl }`. `GET /api/v1/jobs/{jobId}` durum, `.../stream` SSE,
`GET /api/v1/generations/{id}/download` PDF. Şekiller OpenAPI'de.

**`POST /generations/general` KALDIRILDI** (`B-022` kapandı). Genel CV modu
kaybolmadı: `jobDescription` opsiyonel, yokluğu genel mod. Boş gövde (`{}`) 202.

**Ön kontroller senkron:** okunmayan ilan ve boş profil **422 ile anında**,
kuyruğa girmeden. `B-037`'nin bayrağı: `acknowledgePreflight: true`.

**Aksiyonunuz — `label` bir çeviri anahtarı, cümle değil** (§ 30.6 düz metin
taşıyordu, § 35.4 ile çelişiyordu). Anahtarlar: `generation.phase.ANALYSING`,
`.MEASURING`, `.SCORING`, `.RENDERING`.

**SSE:** üç olay adı — koşarken `phase`, sonra **tam olarak bir tane**
`completed` ya da `failed`, akış kapanır. Bağlanır bağlanmaz güncel durum
geliyor, yani ekran boş başlamıyor ve **202 ile abonelik arasında biten iş de
sonucunu gönderiyor**. `Last-Event-ID` kabul ediliyor ama **oynatma yok**;
sürekliliğe değil terminal olaya güvenin. Akış terminal olay olmadan kapanırsa
`GET /jobs/{jobId}` geri düşüş.

**Terminal alanlar yalnız kendi durumlarında:** `generationId` sadece
`completed`'da, `error` sadece `failed`'da. **`Idempotency-Key`** onurlandırılıyor.
**Download üretim anındaki metinden** render ediliyor — sonradan bir madde
düzenlenirse indirilen CV değişmez; yeniden render edilecek şeyi olmayan satır
`410` + `GENERATION_ARTIFACT_EXPIRED` + `retry`.

**`gen:api` çalıştırılmalı.**

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
