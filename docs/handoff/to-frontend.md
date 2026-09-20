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

_(açık madde yok.)_

**`B-117`, `B-118`, `B-119` ACK'lendi ve indi** (2026-09-21), geldikleri
günün ertesi. Üçü de `resolved/to-frontend-2026-09.md`'de, ne yapıldığıyla
birlikte; gerekçeleri `notes/current.md`'de.

**Backend'e madde açılmadı, çünkü ölçülecek şey ölçüldü:** `language`'ın
`400`'ü kapı sırasında **413 ile 409 arasında** duruyor (2026-09-21, üç
sonda). Şema da gerçek uca karşı doğrulandı — `gen:api`'nin ürettiği dosya
backend'in commit'li `openapi.json`'ıyla birebir.

---

`B-100`…`B-116` **ACK'lendi ve indi** (2026-09-20). On yedisi de altı denetim
turundan çıkmıştı ve hepsi tek bir kapanış sırasında karşılandı —
**maddelerin tamamı** `resolved/to-frontend-2026-09.md`'de, indikleri haliyle.

**Dosya 347 satıra çıkmıştı ve bu bir arşivleme değil koordinasyon meselesiydi:**
taşınabilecek madde yoktu, çünkü hiçbiri karşılanmamıştı. Şimdi karşılandılar.

> **On yedisi bir gün arşivsiz kaldı.** `to-frontend.md`'den silindiler ama
> `resolved/`'a yazılmadılar; bu satır okuyucuyu `B-099`'da biten bir dosyaya
> yolluyordu. Aynı gün `d5172d8`'den kurtarılıp eklendiler. `stage-4.md`
> işaretçisi de kaldırıldı — **Aşama 4 açık, o dosya henüz yok**; on yedinin
> gerekçesi `notes/archive/denetim-*.md`'de.

---

## Frontend'in beklediği dört cevap — **dördü de verildi**

`F-037`, `F-038`, `F-039`, `F-040` geldikleri gün karşılandı (2026-09-20) ve
`to-backend.md`'de `ACK`'e taşındı. Karşılıkları `B-117` (`F-038`), `B-118`
(`F-039` + `F-040`) ve `B-119` (`F-037`) idi; üçü de ertesi gün karşılandı ve
`resolved/to-frontend-2026-09.md`'ye indi.

## Dağıtım bekleyen doğrulamalar

Üçü bir dağıtım bekliyor ve hiçbiri kod işi değil: OAuth sıçraması (`B-048`),
sihirli bağlantının Turnstile'ı (`B-050`), `B-083`'ün challenge'ı. `B-100`
üçünün önündeki kapıyı açtı. `B-076`'dan kalan tek şey yayımlanan sağlayıcı
sayfasını `ProcessorAudit`'in açılış satırına karşı okumak. Sıra
`notes/current.md` § *Dağıtım günü*'nde.

Kalıcı kuralların `spec/`'e işlendiği yerler: `resolved/to-frontend-2026-08.md`.
