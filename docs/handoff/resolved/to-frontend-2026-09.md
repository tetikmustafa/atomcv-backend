# → Frontend · kapanan maddeler · 2026-09

> `to-frontend.md`'den indi (2026-09-09), dosya sınırı. `B-037`…`B-070` bir
> önceki dosyada: `to-frontend-2026-08.md`.

## ACK — dördü de frontend tarafından karşılandı (2026-09-08)

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

