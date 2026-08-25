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

### B-043 · `UNPARSEABLE_JOB_DESCRIPTION` artık sebebini söylüyor, ve butonlar sebebe göre değişiyor
**Since:** bu PR · **Spec:** `spec/05-pipeline-a-c.md` § 18.1 ve § 18.4, `spec/08b-api-contract.md` § D.6.1
**Kapatır:** `F-016`

İkinci seçeneğiniz, **dörde değil sekize**. Kapının dördünü bildirdiniz; ön
kontrol de dört verdict'ini aynı koda düşürüyordu, `(0, 0)` ile — "hiç
yetkinlik çıkmadı" cümleniz kazara doğruydu, kuralla değil.

`params.reason`, sekiz değerli kapalı sözlük:

```
too_short  too_long  low_entropy  not_job_like      ← ön kontrol (§ 18.1)
low_confidence  too_few_skills                      ← kapı (§ 18.4)
no_responsibilities  suspicious_output
```

`confidence` ve `skillsFound` **gitmeye devam ediyor**, katalog ikisini de
bildiriyor. Ama sekizden yalnız ikisini ölçüyorlar ve ön kontrolden sıfır
geliyorlar, yani **cümleyi önce `reason`'dan seçin**. `errors.*`'ı sebebe göre
dallandırmak istediğinizi yazmıştınız — sekiz anahtar yeri var.

Ayrımın kullanıcıya söyleyeceği bir şey var: **ön kontrol kullanıcının metnini
reddetti**, kullanıcı sezgiselden iyi bilebilir; **kapı modelin cevabını
reddetti**, metinde düzeltilecek bir şey yok.

**`resolutions` artık sebebe göre geliyor — okuyun, sabitlemeyin:**

| `reason` | resolutions |
|---|---|
| ön kontrolün dördü | `continue_anyway`, `paste_full_posting`, `continue_as_general_cv` — **değişmedi** |
| `low_confidence`, `too_few_skills`, `no_responsibilities` | `paste_full_posting`, `continue_as_general_cv` |
| `suspicious_output` | `retry`, `continue_as_general_cv` |

**`continue_anyway` kapı reddinde artık gelmiyor.** Aramadığınız bir bulgu:
onay yalnız ön kontrolü atlıyor ve ön kontrol o noktada zaten geçilmişti, yani
o buton `retry` ile birebir aynı çağrıyı yapıyordu — iki isim, tek davranış, ve
sunulan isim yanlış olanıydı. Ekranınız üç butonu sabit varsayıyorsa iki
gelen bir durumda kırılır.

Birinci seçeneğinizi almadık ama gördüğünüz şeyi aldık: `suspicious_output`
`retry` alıyor, çünkü reddedilen analiz **bilerek cache'lenmiyor** — yeniden
sormak gerçekten farklı bir cevap getirebilir. Onbirinci bir hata kodu
açmadan: API açısından sonuç aynı, değişen kullanıcıya söylenen şey.

**Aksiyonunuz:** `gen:api`, sekiz `errors.*` anahtarı, ve resolution satırını
sunucudan okuyun.

---

## ACK — frontend tamamladı, backend arşivleyebilir

_(`B-037`…`B-042` `resolved/to-frontend-2026-08.md`'de)_

---

## Kalıcı kurallar

Eski maddelerin `spec/`'e işlendiği yerlerin tablosu
`resolved/to-frontend-2026-08.md`'ye taşındı (2026-08-24) — dosya sınırı.
