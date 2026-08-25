# → Frontend

> **Kanal kuralları**
>
> - Backend yazar, frontend okur ve `OPEN` → `ACK` taşır.
> - Her madde bir ID taşır (`B-nnn`), numaralar tekrar kullanılmaz.
> - **Dosya 100 satırı geçerse arşivleme gecikmiştir.** `ACK` maddeleri `resolved/`'a taşınır.
> - API _şekli_ için otorite OpenAPI şemasıdır. Burası **neden değişti + ne yapman lazım** taşır.
> - Kalıcı kural niteliğindeki maddeler `spec/`'e işlenir ve buradan silinir.

---

## OPEN

_(şu an açık madde yok)_

---

## ACK — frontend tamamladı, backend arşivleyebilir

### B-043 · Sekiz sebep — yazıldı, ve telde doğrulandı

`gen:api` çalıştı: **fark yok**. Beklediğimiz buydu (`params` şemada
`Record<string, unknown>`, `code` ve `Resolution.action` enum'ları aynı) ama
artık ölçüldü.

Sekiz cümle tek `errors.*` anahtarında, ICU `select` ile — `Fit.level` ile
aynı kalıp. Dokuzuncu bir dal daha var: `other`, tanımadığı bir sebep için.

**Ölçüp koda yazdığımız şey:** next-intl'de **eksik** bir `select` argümanı
mesajı kendi anahtar yoluna çeviriyor (`errors.UNPARSEABLE_JOB_DESCRIPTION`
ekranda), **bilinmeyen bir değer** ise `other`'a düşüyor. Katalog testimizin
süslü parantez kontrolü ilkini kaçırıyordu — anahtar yolunda parantez yok.
Artık `rendered !== code` de sınanıyor ve `useErrorMessage` `reason`'ı
garanti ediyor.

**Gerçek uca karşı üç red görüldü** ve üçü de tarif ettiğiniz gibi geldi:

```
422     too_short           conf 0     skills 0    3 resolution
stream  too_few_skills      conf 0.9   skills 0    2 resolution
stream  no_responsibilities conf 1     skills 18   2 resolution
```

`continue_anyway` kapı reddinde gerçekten yok. Üçüncüsü `F-016`'nın
şikâyetinin ta kendisi: **%100 güven, 18 beceri, yine de red** — eski tek
cümle sayıyı okuyup kendini yalanlardı.

**`suspicious_output` tetiklenemedi.** `gpt-4.1-nano` uzun beceri adlarını
normalleştiriyor; üç ayrı ilan denedik, üçü de geçti. Yani o sebebin `retry`
satırı **mock'ta ve testte var, telde görülmedi** — sizin tarifinize
dayanıyor. Aksi bir şey varsa haber verin.

**Backend cevabı: tetikleyememeniz doğru sonuç, eksik değil.**
`suspicious_output` bir *incelik* değil *şekil* denetimi — § 18.4'ün uzunluk
tavanları: 60'ı aşan beceri adı, 100'ü aşan anahtar kelime, 120'yi aşan unvan,
300'ü aşan sorumluluk. Tavanlar gerçek bir ilanın ürettiğinin çok üstünde
duruyor, çünkü uzun ama gerçek bir sorumluluğu reddeden bir kapı hiç kapı
olmamasından kötü. Uslu bir modelle ilan yazarak açılması **beklenmiyor**;
kapıyı açan şey enjeksiyon, ve o da modelin fence'e inanmayı bırakmasını
gerektiriyor. `PlausibilityGateTest` onu kurgulanmış analizle doğrudan sınıyor.

Bir ayrıntı sizin tarafınızı ilgilendirebilir: kapı **sırayla** bakıyor —
`low_confidence` → `too_few_skills` → `no_responsibilities` → uzunluk. Bu
bilinçli (§ 18.4, "Sıra önemlidir"): zayıf bir ilan zayıf olduğu için
reddedilsin, "şüpheli çıktı" diye değil. Yani bir enjeksiyon denemesi aynı
anda ikiden az beceri de üretirse size `too_few_skills` olarak gelir.
`suspicious_output`, "sayılar yerinde ama şekil bozuk" hâlinin adı.

Denetimi ararken § 18.4'ün kod parçacığında bir hata bulduk ve düzelttik:
parçacık yalnız `requiredSkills`'e bakıyordu, kod `allSkills()` kullanıyor —
tercih edilen beceriler de kapsanıyor. Kod doğruydu, spec eksikti. Şekil
değişmedi, `gen:api` gerekmez.

_(`B-037`…`B-042` `resolved/to-frontend-2026-08.md`'de)_

---

## Kalıcı kurallar

Eski maddelerin `spec/`'e işlendiği yerlerin tablosu
`resolved/to-frontend-2026-08.md`'ye taşındı (2026-08-24) — dosya sınırı.
