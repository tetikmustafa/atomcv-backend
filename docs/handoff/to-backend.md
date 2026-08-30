# → Backend

> **Kanal kuralları**
> - Frontend yazar, backend okur ve `OPEN` → `ACK` taşır.
> - Her madde bir ID taşır (`F-nnn`), numaralar tekrar kullanılmaz.
> - **Dosya 100 satırı geçerse arşivleme gecikmiştir.**
> - Bir spec değişikliği gerekiyorsa burada iste — `spec/`'i frontend reposunda düzenleme,
>   bir sonraki senkronda kaybolur.

---

## OPEN

*(şu an açık madde yok — `F-022`-`F-024`'ün üçü de `ACK`'te)*

<!-- Şablon:
### F-001 · Kısa başlık
**Since:** frontend commit <sha> · Adım <n>
**Neden:** <sorunun ne olduğu>
**İstenen:** <backend'den beklenen somut şey>
**Spec:** <ilgili dosya ve bölüm, varsa>
-->

---

## ACK — backend tamamladı, frontend arşivleyebilir

### F-022 · (b) uygulandı, ve sınır istediğinizden geniş yazıldı
`GenerationSummary` üstünde `roleTitle` ve `companyName`. **İkisi bağımsız**
ve **boş dize hiç dönmüyor** — alan ya doludur ya yoktur, yani `""` kontrolü
yazmanız gerekmiyor.

§ 57.6'yı istediğiniz gibi bir cümle olarak değil, **üç ölçüt ve bir liste**
olarak yazdık: amaç adlandırmaksa, model çıkarımıysa, bir satıra sığıyorsa —
ve listede olmayan alan istisna değil. Sizin gördüğünüz risk ("sınırı yazan
bir cümle yoksa bir sonraki alan da aynı gerekçeyle girer") tam olarak doğru
riskti; bir cümle onu ölçemezdi. **Aksiyonunuz var — `B-070`.**

### F-023 · Haklıydınız, ve gerekçeniz kendi gerekçemizdi
`ImportWarning.code` artık `enum`. Altı değer: `ambiguous_date`,
`missing_organization`, `unclear_section`, `scrambled_text`,
`overlapping_dates`, `untranslatable_atom`.

Yayımlamak enum'u `shared`'a taşımayı gerektirdi — kodları çıkarım üretiyor,
`GET /jobs/{id}` yayımlıyor, ve çıkarım işi kuyruğa vermek için `jobs`'a
zaten bağımlı; ters yöndeki import bir çevrim kapatıyordu.

**Alanın tipi telde `String` kaldı, bilerek**, ve bu tam sizin okuma
biçiminize göre: değer JSONB'den geri geliyor, adı sonradan değişmiş bir kod
taşıyan eski satır tipi enum olsa ya düşerdi ya isteği bozardı. Kapalı
olduğunu bilerek açık okuyun. **Aksiyonunuz var — `B-069`.**

### F-024 · Doğru teşhis, ve sizde iş yok
`MissingServletRequestPartException`'ın işleyicisi yoktu, istek son çareye
düşüyordu. Artık `400 VALIDATION_FAILED`, `fields: ["file"]` — mock'unuzun
ürettiği şey. `B-064` ile aynı sınıf ve bir istisna kadar yakın:
`handleBadParameter` query parametresinin eksiğini zaten yakalıyordu.

**Asıl değerli olan madde değil, ölçümü yapmış olmanız.** Kendi formunuzdan
bakan hiçbir test buraya ulaşamazdı; bir aşama boyunca durmasının sebebi o, ve
EK D.6.9'a ders olarak öyle yazıldı. **`B-068`.**

*(`F-001`…`F-021` `resolved/to-backend-2026-08.md`'de — beşinin de cevabı
oraya indi 2026-08-29'da, dosya sınırı.)*
