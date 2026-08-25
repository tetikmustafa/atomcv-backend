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

### F-016 · Tek kodun arkasındaki sekiz sebep — kapandı
İkinci seçeneğiniz, ama **dörde değil sekize**. Şikâyetiniz § 18.4'ün kapısı
üzerineydi; ön kontrol de dört verdict'ini aynı koda düşürüyor ve `(0, 0)`
gönderiyordu, yani "hiç yetkinlik çıkmadı" cümlesi kazara doğruydu. Yalnız
bildirdiğiniz yarıyı düzeltmek aynı maddeyi ikinci kez açtırırdı.

`params.reason` sekiz değerli kapalı bir sözlük ve hangi kapının reddettiğini
söylüyor. `confidence` ile `skillsFound` gitmeye devam ediyor — katalog onları
bildiriyor — ama cümle artık önce `reason`'dan seçilir.

Birinci seçeneğinizi almadık, ama **asıl gördüğünüz şeyi** aldık:
`suspicious_output` `retry` alıyor. Onbirinci bir hata kodu açmadan, çünkü API
açısından sonuç aynı — değişen, kullanıcıya söylenen şey.

Aramadığınız bir şey de çıktı: **`continue_anyway` kapı reddinde `retry` ile
birebir aynı işi yapıyordu.** Onay yalnız ön kontrolü atlıyor, ön kontrol zaten
geçilmişti. Kaldırdık. **Aksiyonunuz var — `B-043`.**

### F-013 · Tek CV iki dil taşıyor — kapandı, üçüncü bir yolla
İkisinden birini değil, ortasını seçtik: **bir belge tek dilde yazılır ve o dil
profilin taşıdığından seçilir.** `auto`, ilanın diline yalnızca profil o dilde
gerçekten yazılabiliyorsa çözülüyor — sayfaya çıkabilecek her atomun hedef
dilde varyantı varsa. Yoksa `sourceLanguage`'de kalıyor, ve tarih ile "Halen"
tek bir `contentLanguage` okuduğu için ayrışamıyorlar.

2. seçeneğiniz § 21.8'in **çalışan** yarısını kapatırdı (tüm atomları çevrilmiş
bir profil bugün gerçek bir İngilizce CV alıyor, maliyeti sıfır); 1. seçeneğiniz
tarihi düzeltir, atom atom geri düşen gövdeyi düzeltmezdi.

İstediğiniz sinyal telde: `contentLanguage` ve `postingLanguage`.
**Aksiyonunuz var — `B-042`.**

### F-014 · Sessiz sağlayıcı hataları — kapandı
Adaptörden çıkışın **tek** yolu var ve WARN'ı orada basıyor: `promptRef`,
`kind`, `detail`. Dört yolun dördü de kapsandı, ve iki mükerrer satır düştü —
bir başarısızlık artık tam olarak bir satır. Gövde ve prompt asla
(mutlak kural 4); teşhisi zincirin yan etkilerinden çıkarmanız gerekmeyecek.
§ 27.2'ye yazıldı.

### F-015 · Fiyat tablosundaki ölü model — kapandı
Haklıydınız, ve alıntıladığınız cümle sonucu tam olarak söylüyordu. Tablo artık
kullanılan modeli kapsıyor; **ücretsiz model açıkça sıfır** yazılıyor, çünkü
rakam aynı olsa da iddia değil — biri "sağlayıcı ücret almıyor" der, öteki
"bilmiyoruz". Asıl eklenen `LlmPricingAudit`: `ApplicationReadyEvent`'te
tabloyu `atomcv.llm.models` ile karşılaştırıyor ve fiyatı olmayan her modeli
adıyla WARN'lıyor. § 27.4'e yazıldı.

*(`F-001`…`F-012` `resolved/to-backend-2026-08.md`'de)*
