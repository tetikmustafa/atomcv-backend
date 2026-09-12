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

### B-097 · Elle toggle'ın listesi indi — ve emekli üretim halefini adlandırıyor

**Since:** backend, Aşama 4 · `F-031` · § 24.4, § 35.3

**Neden:** `B-088`'in aç/kapa arayüzü çizilemiyordu: bu üretimin hangi
atomları tarttığını söyleyen bir uç yoktu ve profilden çizmek, tartılmamış
atom `400` döndüğü için basılamayacak düğme demekti.

**İstenen — iki şey:**

1. **`GET /api/v1/generations/{id}/selection`** · `{ generationId, lines:
   [{ atomId, text, onPage }] }`. Sayfaya girenler önce, girmeyenler
   yarıştıkları sıraya göre arkasında. **Buradaki her id'yi düzenleme ucu
   kabul eder** — maddenin tamamı bu. `text` **o CV'nin bastığı** metin,
   bugünkü profilin değil; kişi o maddeyi sonradan düzenlemiş olabilir.
   Skor yayımlanmıyor (§ 23.3'ün gerekçesi), sıra zaten onu söylüyor.
   Üst sınır yok. Profilden silinmiş atom listede yok: geri konamaz.

2. **`GenerationResponse.supersededByGenerationId`** — yalnız `status`
   `superseded` iken dolu. Ekran artık "bunun daha yenisi var" derken
   **bağlantı da verebiliyor**.

### B-098 · `JobStatusResponse` iki alan kazandı

**Since:** backend, Aşama 4 · `F-032` · § 35.3, EK D.6.4

**İstenen:** `supersededGenerationId` istediğiniz gibi indi. Yanında
**`matchLevel`** da var — istememiştiniz, ama birebir aynı kusurdu (terminal
olay ham `result` map'i, alan akışta var tipte yok) ve akış kopup poll
devreye girdiğinde ikisi de kayboluyordu. Şeması dört değerli enum.
İkisi de yalnız kendi işinde dolu: `matchLevel` genel modda yok, `superseded…`
yalnız bir Faz G düzenlemesinde.

### B-099 · **`gen:api` yeniden koşulmalı** — 33 operasyon adlandırıldı

**Since:** backend, Aşama 4 · `F-033` · § 35.8

**İstenen:** İstediğiniz adlar indi (`deleteAccount`, `accountSettings`,
`listApplications`, `updateApplication`, `deleteApplication`) ve **bütün
uçlar** adlandırıldı — numaralı hiçbir `operationId` kalmadı. Şemayı
yeniden üretin; **yoldan bağlanan istemciniz kırılmaz**, ama üretilen adlar
değişecek. Bir daha kaymaması artık teste bağlı: şemada `_<sayı>` ile biten
bir `operationId` görülürse CI düşer.

**Ayrıca `empty` gitti** — `Appearance` ve `SelectionEditRequest`. Okunan
`Appearance`'ı doğrudan `AppearanceUpdate` olarak geri yazmak artık
fazladan alan göndermiyor, eleme kodunuz kalkabilir.

## ACK — frontend karşıladı

**`B-088`…`B-094` ve `B-096` `resolved/to-frontend-2026-09.md`'de**
(2026-09-11). `B-088`'in eksik kalan yarısını açan üç `F-nnn`'in üçü de
2026-09-12'de karşılandı ve yukarıdaki üç madde onların cevabı.

---

## Dağıtım bekleyen doğrulamalar

*(`B-085`…`B-087` de `resolved/to-frontend-2026-09.md`'ye indi 2026-09-09'da.
Aşağıdaki ikisi bir maddenin kapanışı değil, **bir dağıtım bekleyen doğrulama** —
o yüzden arşive inmiyorlar.)*

**Üç yerde bir doğrulama eksik ve söylenmesi gerekiyor:** ne OAuth sıçraması
(`B-048`), ne sihirli bağlantının Turnstile'ı (`B-050`), ne de `B-083`'ün
üretim/içe aktarım challenge'ı gerçek uca karşı denendi — üçü de kendi
anahtarları yapılandırılmış bir dağıtım istiyor.

**EK C.1'in sağlayıcı listesi yazıldı** (`B-076`). Yayın öncesi kontrol
listesinde kalan tek şey, yayımlanan sayfayı `ProcessorAudit`'in açılış
satırına karşı okumak — dağıtım işi, kod işi değil.

---

## Kalıcı kurallar

Eski maddelerin `spec/`'e işlendiği yerlerin tablosu
`resolved/to-frontend-2026-08.md`'ye taşındı (2026-08-24) — dosya sınırı.
