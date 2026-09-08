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

_(Açık madde yok.)_

## ACK — frontend tamamladı, backend arşivleyebilir

**B-074** — şablon seçicisi de, `classic` için elde hazırlanmış bir önizleme
de yok: `templateId` hiçbir bileşende geçmiyor, `src/components/preview/` boş.
Yenilenecek bir görsel yok, iş yok.

**B-073** — düzen adını gösteren ya da seçtiren bir arayüz yok, dolayısıyla
karşılanacak bir `select` de yok. `paragraph` `npm run gen:api` ile üretilen
tipe girdi (üç şemada: `Section`, `SectionCreateRequest`, `SectionPatchRequest`)
ve birliği genişletmekten başka bir şey yapmadı. Fixture'a `about` bölümü
eklenmedi: mock'ta düzeni okuyan hiçbir şey yok, eklenen bölüm yalnızca
`sectionCount`/`atomCount` bekleyen testleri oynatırdı. Mock'un bölüm oluşturma
varsayılanı sunucununkiyle aynı (`bullet_list`) kalıyor — şema hâlâ öyle
diyor, `about` satırlarını `V9` taşıdı, kolon varsayılanı değil.

**B-072** — canlı şemada da doğrulandı: `/v3/api-docs` içinde
`no_responsibilities` hiç geçmiyor (neden sözlüğü zaten şemada değil, hata
kataloğunda). `no_responsibilities` dalı `errors.UNPARSEABLE_JOB_DESCRIPTION`
içinden kaldırıldı (en + tr), `gateRefusal`'ın kabul ettiği nedenlerden çıktı,
katalog testindeki neden listesi yediye indi. Yakalanmış yük de silindi:
`wireErrors` gerçekten gönderilmiş gövdeleri taşıyor, uydurulmuş bir tanesi
oraya konmadı. `F-016`'nın nöbeti — "sayımı suçlama" — kayıptan kurtarıldı ve
katalog testinde artık tek bir yük yerine `too_few_skills` dışındaki her
nedene karşı koşuyor.

**B-071** — yedinci dal `Onboarding.warning`'e eklendi (en + tr), ve
`ImportWarning.code` üretilen tipte artık yedi değer listeliyor. Kod zaten
**açık** okunuyordu (`B-069`), o yüzden tip tarafında iş çıkmadı; eksik olan
yalnızca cümleydi ve o gelene kadar uyarı `other`'a düşüyordu. Yeri de
çalışıyor: `sectionOrder`/`entryOrder` taşıdığı için satıra bağlanıyor,
testi `ReviewGate` içinde. Ortak fixture'a **eklenmedi** — ekrandaki
davranışı diğer altısından farklı değil, ve üçüncü bir uyarı e2e'deki
sayıları oynatırdı.

**Yanlış pozitif konusunda söylenecek bir şey henüz yok:** mock'ta bu kodu
üreten bir yol yok, yani gürültü ancak gerçek uca karşı görülür.

_(`B-037`…`B-070`'in hepsi kapandı ve `resolved/to-frontend-2026-08.md`'de —
hangi dilimin hangisini kapattığı orada. Aşağıdakiler **hâlâ canlı olan**
kayıtlar; gerisi arşive indi.)_

**İki maddede bir doğrulama eksik ve söylenmesi gerekiyor:** ne OAuth
sıçraması (`B-048`) ne de Turnstile (`B-050`) gerçek uca karşı denendi —
ikisi de kendi anahtarları yapılandırılmış bir dağıtım istiyor. Bugün
doğrulanan şey mock'a karşı.

**`B-059` kapandı ama EK C.1'in maddesi kapanmadı.** Alt işleyen listesi doğru;
eksik olan **sağlayıcı listesinin kendisi** — hangi model, ona ne gidiyor,
ücretsiz katman eğitimde kullanıyor mu. Model seçimini bekliyor. **Yayın
öncesi kontrol listesi hâlâ açık.**

---

## Kalıcı kurallar

Eski maddelerin `spec/`'e işlendiği yerlerin tablosu
`resolved/to-frontend-2026-08.md`'ye taşındı (2026-08-24) — dosya sınırı.
