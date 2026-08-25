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

### B-042 · CV'nin dili artık koşullu, ve yanıt hangisi olduğunu söylüyor
**Since:** bu PR · **Spec:** `spec/06-pipeline-d-g.md` § 21.8, `spec/08-api.md` § 35.3
**Kapatır:** `F-013`

Üçüncü bir yol seçtik — 1. ve 2. seçeneklerinizin ortası, ve ikisinin de
istediğini veriyor.

**Kural: bir belge tek dilde yazılır.** `auto`, ilanın diline **yalnızca profil
o dilde gerçekten yazılabiliyorsa** çözülüyor — sayfaya çıkabilecek her atomun
(aktif, en az bir sözcüklemesi olan) hedef dilde varyantı varsa. Yoksa
`sourceLanguage`'de kalıyor. Gövde, tarih ve "Halen"/"Present" tek bir
`contentLanguage` okuduğu için ayrışamıyorlar.

2. seçeneğinizi düz uygulamadık, çünkü § 21.8'in **çalışan** yarısını kapatırdı:
profilinde tüm atomların İngilizce varyantı olan bir kullanıcı bugün gerçek bir
İngilizce CV alıyor ve maliyeti sıfır. Onu geri almadık.

1. seçeneğinizi de seçmedik: geri düşüş atom atom, yani tarihi ölçüme uydurmak
tarihi düzeltir, **karışık gövdeyi düzeltmezdi**.

**Yanıtta iki alan var** (`GET /generations/{id}`):

```
contentLanguage   belgenin gerçekten yazıldığı dil        "tr"
postingLanguage   Faz A'nın ilanı okuduğu dil             "en"
```

İkisi de BCP 47, ikisi de boşken **gönderilmiyor** (`F-010`'un kuralı).
Genel modda `postingLanguage` hiç gelmiyor.

**Aksiyonunuz:** `gen:api`, sonra ikisi ayrıldığında istediğiniz cümleyi yazın —
"bu CV profil dilinde yazıldı, ilanın dilinde değil". Karşılaştırmayı size
bıraktık **bilerek**: o cümle iki dilin de adını anıyor, tek bir boolean size
yine iki alanı sordururdu.

Bu geçici ve geçiciliği kasıtlı. § 21.8'in çeviren fazı indiğinde kontrol her
dil için doğru olur, alanlar aynı değeri taşımaya başlar ve cümle kendiliğinden
çizilmez olur — bir bayrak arkasına koymadık.

**`F-014` ve `F-015` için aksiyonunuz yok**, ikisi de sunucu tarafında kapandı:

- **`F-014`** — adaptörden çıkışın tek yolu var ve WARN'ı orada basıyor:
  `promptRef`, `kind`, `detail`. Gövde ve prompt asla (mutlak kural 4).
  Dördünüzün dördü de kapsandı; `detail`'iniz artık okunuyor. Sonda iki yönlü:
  satırı kaldırınca dört test düşüyor, `detail`'e gövde ekleyince **yalnız
  sızıntı testi** düşüyor.
- **`F-015`** — haklıydınız, o slug'ı hiçbir zincir çalıştırmıyordu ve her
  maliyet sıfırdı. Tablo kullanılan modeli kapsıyor (ücretsiz model **açıkça**
  sıfır — rakam aynı, iddia değil), ve `LlmPricingAudit` açılışta fiyatı olmayan
  her yapılandırılmış modeli adıyla WARN'lıyor. Sıfır doğru sayı değil,
  bilinmeyen sayı — cümleniz aynen alındı.

---

## ACK — frontend tamamladı, backend arşivleyebilir

_(boş — `B-037`…`B-041` `resolved/to-frontend-2026-08.md`'de)_

---

## Kalıcı kurallar

Eski maddelerin `spec/`'e işlendiği yerlerin tablosu
`resolved/to-frontend-2026-08.md`'ye taşındı (2026-08-24) — dosya sınırı.
