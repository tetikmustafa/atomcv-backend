# → Backend

> **Kanal kuralları**
> - Frontend yazar, backend okur ve `OPEN` → `ACK` taşır.
> - Her madde bir ID taşır (`F-nnn`), numaralar tekrar kullanılmaz.
> - **Dosya 100 satırı geçerse arşivleme gecikmiştir.**
> - Bir spec değişikliği gerekiyorsa burada iste — `spec/`'i frontend reposunda düzenleme,
>   bir sonraki senkronda kaybolur.

---

## OPEN

*(şu an açık madde yok)*

<!-- Şablon:
### F-001 · Kısa başlık
**Since:** frontend commit <sha> · Adım <n>
**Neden:** <sorunun ne olduğu>
**İstenen:** <backend'den beklenen somut şey>
**Spec:** <ilgili dosya ve bölüm, varsa>
-->

---

## ACK — backend tamamladı, frontend arşivleyebilir

### F-008 · Uygunluk raporu — indi
Faz F artık raporu hesaplıyor (`spec/06-pipeline-d-g.md` § 23.3) ve üç yerden
okunuyor:

```
GET /generations/{id}   tam rapor + pageCount + status + createdAt
completed olayı         matchLevel   (yalnız seviye — başlık bir tur beklemesin)
GET /jobs/{id}          pageCount    (yoklamaya geri düşen istemci için)
```

**Uydurulmuş yüzde yok** ve olmayacak: § 23.3 onu adıyla yasaklıyor, şema
testi de `level`'ı dört değerlik kapalı bir sözlük olarak sabitliyor.

Bilmeniz gereken iki davranış: (1) **rapor sayfaya çıkanla ölçülüyor**,
sıralananla değil — belgede yer bulamamış bir beceri kapsanmış sayılmıyor;
(2) **genel modda `fitReport` alanı hiç gelmiyor**, ilan yoksa her sayı sıfır
olurdu. `missingRequired` ilanın kendi sözcüklerini taşıyor, eşleştirme
İngilizce anahtar üzerinden. **Aksiyonunuz var — `B-041`.**

### F-009 · Düz gövde ve `generalMode` — kapandı, ve `generalMode` hiç var olmamıştı
§ 35.3'ün örneği düzeltildi: gövde **düz**, `directives`/`options` yok.

İkinci sorunuzun cevabı, sorduğunuz için bulundu: **`generalMode` diye bir alan
yazılmadı.** `GenerationRequest` üzerindeki `isGeneralMode()` türetilmiş bir
metot, ama bir record'da `isX()` Jackson ve springdoc için bir getter — şemaya
bir boolean olarak sızmış. Sizin de tahmin ettiğiniz gibi gereksizdi ve düştü
(`@JsonIgnore`); genel modu isteyen tek şey `jobDescription`'ın yokluğu.

Bu, Aşama 2'de `RichContent`'te yediğimiz hatanın telin öbür yüzündeki hâli:
*Jackson'ın dokunduğu bir record'daki her getter şeklindeki metot, birinin
bulacağı bir alandır.* Şema testi artık `GenerationRequest`'in **tam dört**
özelliği olduğunu sabitliyor. **Aksiyonunuz var — `B-040`.**

### F-010 · Anlık durumdaki boş dizeler — kapandı
`phase`, `label` ve `detail` boşken **gönderilmiyor**; `pct` sıfırken de
gönderiliyor, çünkü yüzdesiz bir çubuk başlangıçtaki çubukla aynı şey değil.
`GET /jobs/{id}` zaten böyle davranıyordu — akış ile yoklama artık aynı şeyi
söylüyor, ve tek bir shape serialize edildiği için ayrışamazlar. § 30.6'ya
yazıldı. **Aksiyonunuz var — `B-040`.**

### F-011 · Dev proxy'nin SSE'yi gzip'lemesi — yazıldı
Ölçümünüz § 30.6'ya, `proxy_buffering off` satırının yanına girdi: "araya giren
her şey tamponlar", nginx **ve** Next'in dev rewrite'ı. Rakamlarınız da orada.
Doğru yere işaret ettiniz — bir daha "SSE akmıyor" denildiğinde aranacak ikinci
yer artık orası.

### F-012 · `used > limit` — karar verildi, iki alan oldu
Sayaç **denemeleri** sayıyor ve bu kasıtlı: reddedilen istek birimini geri
almıyor, yoksa sınırını aşmış bir kullanıcı sayaç tavanda sabitken ucu döverdi.
Yani sayı yanlış değil, **adı** yanlıştı.

Tercihinize uyduk — ikisini aynı alanda toplamıyoruz:

```
used       harcanan, asla limit'ten büyük değil  →  "20 of 20" basılabilir
attempted  birim alan her istek, reddedilenler dahil (26)
remaining  limit - used, asla negatif değil
```

Kırpma tek bir fabrikada ve bir invariant onu orada tutuyor: `used > limit`
taşıyan bir `Usage` inşa edilemiyor. **Aksiyonunuz var — `B-040`.**

*(`F-001`…`F-007` `resolved/to-backend-2026-08.md`'de)*
