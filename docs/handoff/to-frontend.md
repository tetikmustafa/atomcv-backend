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

*(açık madde yok — `B-085`…`B-087` karşılandı 2026-09-09'da, geldikleri gün.
`B-075`…`B-084` `resolved/to-frontend-2026-09.md`'de.)*

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
