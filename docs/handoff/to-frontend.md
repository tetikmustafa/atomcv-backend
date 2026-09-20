# → Frontend

> **Kanal kuralları**
>
> - Backend yazar, frontend okur ve `OPEN` → `ACK` taşır.
> - Her madde bir ID taşır (`B-nnn`); numaralar tekrar kullanılmaz.
> - **Dosya 100 satırı geçerse arşivleme gecikmiştir.** `ACK` maddeleri `resolved/`'a taşınır.
> - API _şekli_ için otorite OpenAPI şemasıdır. Burası **neden değişti + ne yapman lazım** taşır.
> - Kalıcı kural niteliğindeki maddeler `spec/`'e işlenir ve buradan silinir.

---

## OPEN

### B-117 · `F-038`'in dört şeyi, ve `B-104`'ün bayat olan yarısı

**Since:** `feat/frontend-f037-f040` · `08-api.md` § 35.3, § 35.8 · `F-038`

**Neden:** `F-038` haklı ve kapsam sorusu değil muhafız sorusu olduğunu da
doğru söylüyor. Dördü de **kodda var ve çalışıyor**; hiçbirinin maddesi
yoktu, yani `gen:api` koşmayan biri bunları göremezdi.

| Ne | Nerede | Ne yapmanız lazım |
|---|---|---|
| `POST /generations` **`note`** (500 karakter) | `JobSpecificGenerationService` → `RewriteContext`'in dördüncü alanı | Faz D'ye **gerçekten ulaşıyor.** Kullanın. |
| `POST /generations` **`customizationId`** | aynı uç; artık kapıda doğrulanıyor (`B-118`) | Kayıtlı set seçiciyi bağlayın |
| **`GET /templates`** (`TemplateSummary`) | `CustomizationController.listTemplates` | Şablon listesi buradan, sabit listeden değil |
| **`/customizations`** (liste/oluştur/yama/sil) | `CustomizationController`, profil başına 20 | 20 tavanı `400 VALIDATION_FAILED` + `fields: ["name"]` döner — ekranda söyleyin |

**`B-104` bayattı, `08-api.md` değil.** Maddenin *"`freeformNote` gelmedi ve
bilerek"* cümlesi yazıldığı gün doğruydu; sonra alan geldi ve madde
güncellenmedi. Gerekçesi de ("yeni prompt sürümü + EK C.3 eval koşusu") o iş
yapıldığı için artık bir engel değil. **İkisinden biri bayat sorusunun cevabı:
madde.**

**Action:** `npm run gen:api`; `note`, `customizationId`, `GET /templates` ve
dört `/customizations` ucu şemada. D6 ve D12'de telden okuyacağınız şey bu.
`B-101`'in `contract-check`'i hâlâ ikimizde de yapılmadı ve tam olarak bunu
yakalardı — **bu maddeyle kapanmıyor**, sadece bu turun dört boşluğu kapanıyor.

### B-118 · Bayat bir `customizationId` artık `404`, ve `maxPages` yayımlanıyor

**Since:** `feat/frontend-f037-f040` · `F-039`, `F-040`

**Neden:** ikisi de sizin ölçümünüz.

- **`F-040`** — set kontrolü worker'ın içindeydi, yani `202`'den sonra. Artık
  `POST /generations` kuyruğa girmeden bakıyor: bilinmeyen ya da başkasına ait
  bir `customizationId` **`404 RESOURCE_NOT_FOUND`**. Kotanın önünde, yani
  **bir üretim harcanmıyor** — sizin harcadığınız gibi.
- **`F-039`** — `GenerationResponse` **ve** `GenerationSummary` artık
  `maxPages` taşıyor. Sayıyı seçtiniz, sayı verdik: `pageCount < maxPages` o
  belgenin izin verilenden kısa olduğunu söyler, ve **o üretimin kullandığı**
  sınırdır — profilin bugünkü tercihi değil. İki yıl önceki bir CV'yi bugünkü
  ayara göre "kısa" ilan etme sorununuz yok.

**Action:** `npm run gen:api`. `maxPages` **eski satırlarda yok** (`null`) —
seçenek kaydedilmeden önce yazılmış üretimler sınırlarını bilmiyor ve makul bir
varsayılan, sunucunun olguyu uydurması olurdu. Notu `maxPages` yokken
çizmeyin.

### B-119 · `POST /profile/import` artık `language` alıyor — `choose_language` çizilebilir

**Since:** `feat/frontend-f037-f040` · `F-037` · `08b-api-contract.md` Adım 3.4

**Neden:** `F-037`'nin üç seçeneğinden **birincisi**. `choose_language`'ın
gideceği yer yoktu, siz de `ErrorPanel` politikası gereği düğmeyi çizmiyordunuz
— yani sunucunun gönderdiği bir çözüm düşüyordu.

Multipart gövdeye **`language`** alanı eklendi (ISO 639-1, opsiyonel).
Gönderildiğinde **tespit atlanır** ve profilin dili o olur; gönderilmezse
bugünkü davranış. Atlaması önemli: yalnız "eşik düşükse kullan" olsaydı ikinci
yükleme aynı reddi üretebilirdi, ki `F-037`'nin şikayet ettiği döngü bu.

Beyan modelin güvenli tahminini de geçer. Kişi kendi CV'si hakkında bir soruya
cevap veriyor; anlaşmazlıkta modele sessizce yenilen bir alan, birinin fark
ettiği durumda hiçbir şey yapmayan bir alan olurdu.

**Action:** `LANGUAGE_UNDETECTED` ekranında `choose_language` düğmesini
**çizin**; cevabı bir sonraki `POST /profile/import`'un `language` alanında
gönderin. `detectedCandidates` en fazla tek elemanlı, yani "şu mu, yoksa
başka bir dil mi" sorusu doğru soru.

**Bir uyarı:** tanımadığımız bir kod **`400 VALIDATION_FAILED`** +
`fields: ["language"]`. Sessizce yoksaymıyoruz — yoksayılsa profile yazılır ve
sonraki her üretim var olmayan bir dilde yapılırdı, kişiye söylenmeden.

---

`B-100`…`B-116` **ACK'lendi ve indi** (2026-09-20). On yedisi de altı denetim
turundan çıkmıştı ve hepsi tek bir kapanış sırasında karşılandı —
`resolved/to-frontend-2026-09.md` satır satır ne yapıldığını söylüyor, niçin
öyle yapıldığı `notes/archive/stage-4.md`'de.

**Dosya 347 satıra çıkmıştı ve bu bir arşivleme değil koordinasyon meselesiydi:**
taşınabilecek madde yoktu, çünkü hiçbiri karşılanmamıştı. Şimdi karşılandılar.

---

## Frontend'in beklediği dört cevap — **dördü de verildi**

`F-037`, `F-038`, `F-039`, `F-040` geldikleri gün karşılandı (2026-09-20) ve
`to-backend.md`'de `ACK`'e taşındı. Karşılıkları yukarıda: `B-117` (`F-038`),
`B-118` (`F-039` + `F-040`), `B-119` (`F-037`).

## Dağıtım bekleyen doğrulamalar

Üçü bir dağıtım bekliyor ve hiçbiri kod işi değil: OAuth sıçraması (`B-048`),
sihirli bağlantının Turnstile'ı (`B-050`), `B-083`'ün challenge'ı. `B-100`
üçünün önündeki kapıyı açtı. `B-076`'dan kalan tek şey yayımlanan sağlayıcı
sayfasını `ProcessorAudit`'in açılış satırına karşı okumak. Sıra
`notes/current.md` § *Dağıtım günü*'nde.

Kalıcı kuralların `spec/`'e işlendiği yerler: `resolved/to-frontend-2026-08.md`.
