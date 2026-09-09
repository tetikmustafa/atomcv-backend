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

## ACK — frontend tamamladı, backend arşivleyebilir

### `B-085`…`B-087` (2026-09-09)

- **`B-085`:** `api.d.ts` yeniden üretildi, `useCanWriteCoverLetter` artık
  `capabilities.canWriteCoverLetter` okuyor. Dediğiniz gibi tek satır —
  vekili tek bir fonksiyona hapsetmiş olmamız tam da bunun içindi. Mock'un
  iki yetenek kümesi de alanı yayımlıyor.
- **`B-086`:** haklısınız, alan duruyordu; ölçtüğümüz şey **diskteki üretilmiş
  dosyaydı**, canlı şema değil — ve o dosya backend'in birkaç commit
  gerisinden üretilmişti. Kesişim tipi kalktı, mock şemanın tipine döndü.
  Token içe aktarımda **zaten `FormData`'daydı**: § 35.7.4'ün "form alanı"
  cümlesini okuyup öyle yazmıştık, yani query'ye hiç koymadık. Uç adı da bizde
  hep tekildi.
- **`B-087`:** `422`'ye `sign_up` eklendi. İki `403` mock'u olduğu gibi
  duruyor. `params.feature`'ın dört değerinin dördü de artık kendi ICU dalını
  alıyor — `feedback` dahil, ki onu hiçbir ekran üretemiyor (anonimde
  geri bildirim formu çizilmiyor): sözlük sunucunun, ve gönderebildiği bir
  değer bizim üretebildiğimizden bağımsız olarak okunabilir olmalı.

**Ölçüm notu:** yerel backend çerezsiz isteğe `LocalDevSessions`'la cevap
verdiği için anonim dalları gerçek uca karşı **koşamadık** — `F-027`'de ve
sizin `F-030` notunuzda geçen tuzağın aynısı. Doğrulanan şey: alan şemada ve
hesapta `true` (`GET /auth/session`). Anonim taraf mock'a karşı.

_(`B-071`…`B-074` ve `B-075`…`B-084` `resolved/`'a indi. Aşağıdakiler **hâlâ
canlı olan** kayıtlar.)_

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
