# → Backend

> **Kanal kuralları**
> - Frontend yazar, backend okur ve `OPEN` → `ACK` taşır.
> - Her madde bir ID taşır (`F-nnn`), numaralar tekrar kullanılmaz.
> - **Dosya 100 satırı geçerse arşivleme gecikmiştir.**
> - Bir spec değişikliği gerekiyorsa burada iste — `spec/`'i frontend reposunda düzenleme,
>   bir sonraki senkronda kaybolur.

---

## OPEN

**Beş madde, hepsi bir doküman–kod denetiminden** (2026-09-21). Denetim üç
ürün yüzeyinin **telde ve dokümanda var, ekranda yok** olduğunu buldu; üçü de
frontend'de kapatıldı ve backend'den bir şey istemiyorlar. Aşağıdakiler
kapatılamayanlar: dördü sizin sahip olduğunuz metin, biri gerçek bir tel
sorusu.

### F-041 · `fontFamily`'nin sözlüğü yayımlanmıyor

**Neden:** Şema alanı düz `string` veriyor. § 38.5 **dört** yazı tipi sayıyor
(Latin Modern, Termes, Heros, **Pagella/`book`**), arayüz **üçünü** sunuyor.
Hangisinin doğru olduğu buradan bilinemez ve **hiçbir typecheck göremez** —
`two_column`'un `B-116`'da düzelttiği şeklin aynısı.

**İstenen:** Alan `enum` olarak yayımlansın (`layout` gibi), ya da § 38.5'in
dördüncü satırı düzeltilsin. Küme bilinene kadar arayüze dördüncü değer
**eklenmedi**: tahmin etmek `B-116`'yı ters yönden tekrarlamak olurdu.

### F-042 · `INDEX.md` bu repoda olmayan iki şeye yönlendiriyor

**Neden:** `docs/vps-dagitim-plani.md` altı kez anılıyor ve senkronize
edilmiyor (tablo bunu yazıyor, **kural doğru**) — ama buradan okuyan için altı
ölü bağlantı. § Dosya sahipliği de `scripts/sync-spec.sh`'i buradaymış gibi
anıyor; o betik backend'de.

**İstenen:** İkisi de "`atomcv-backend`'dedir" desin. `CLAUDE.md`'nin kendi
satırı bu tarafta düzeltildi.

### F-043 · § 31.6.5 "altı değer" diyor, şema yedi yayımlıyor

**Neden:** `ImportWarning.code` yedi değer taşıyor; yedincisi
`unsupported_by_source`, ve frontend yedisini de karşılıyor. Bölümün gerekçesi
("yedinci bir değer karşı reponun duyması gereken bir tel değişikliğidir")
sayı tutmadığı için bayat görünüyor.

**İstenen:** Sayı düzeltilsin ya da yedincinin ne zaman indiği yazılsın.

### F-044 · EK C.5 `app.name` mesaj anahtarı varsayıyor

**Neden:** Liste "frontend i18n: `messages/*.json` → `app.name` anahtarı"
diyor. İnen şey **tek bir sabit**: `src/lib/seo.ts`'te `APP_NAME`. Ad
**çevrilmiyor**, ve kök layout'un `metadata`'sı statik bir export — bir
çeviriciyi çağıramaz. Adı anan üç cümle `{appName}` ICU argümanı alıyor.

**İstenen:** Satır sabitin adını söylesin. Listenin niyeti — tek yerden
değişsin — karşılanıyor; yanlış olan mekanizmanın adı.

### F-045 · Cover letter dili istek başına seçilemiyor

**Neden:** `CoverLetterRequest` yalnız `style` ve `companyNote` taşıyor. Ürün
dokümanı § 10.1 "CV ve cover letter için **ayrı seçim**" diyor, § 38.1 çıktı
dilini "her üretimde ayrı" sayıyor. CV tarafı karşılandı
(`GenerationRequest.language` artık **gönderiliyor**); cover letter'ın dili
yalnız `defaults.coverLetterLanguage`'ten geliyor.

**İstenen:** Ya `POST …/cover-letter/regenerate` bir `language` alsın, ya da
"ayrı seçim"in varsayılan düzeyinde kaldığı yazılsın. **Alan gelmeden düğme
çizilmez** — `keep_top_pinned`'in durumu.

---

**Bir madde yazılıp ölçüldüğü için açılmadı** (2026-09-21): `language`'ın
`400`'ünün kapı sırasındaki yeri. Soru gerçekti — mock bir sıra seçmek
zorunda — ama cevabı sizde değil, ayakta duran dağıtımdaydı: **413 → `400`
(`language`) → 409 → 415.** Üç sonda, kayıt `notes/current.md`'de. Kapı
sırası zaten `B-051`'de bir kez yanlış varsayılmıştı; bu sefer sorulmadan
önce bakıldı.

<!-- Şablon:
### F-001 · Kısa başlık
**Since:** frontend commit <sha> · Adım <n>
**Neden:** <sorunun ne olduğu>
**İstenen:** <backend'den beklenen somut şey>
**Spec:** <ilgili dosya ve bölüm, varsa>
-->

---

## ACK — backend tamamladı, frontend arşivleyebilir

_(bekleyen yok.)_

**`F-031`…`F-040` arşivlendi** (2026-09-21) — hepsi geldikleri gün
karşılanmıştı, ve bu dosya 142 satıra çıkmıştı. Satır satır kayıt
`resolved/to-backend-2026-09.md`'de; `F-001`…`F-024`
`resolved/to-backend-2026-08.md`'de, `F-025`…`F-030` aynı dosyanın başında.
