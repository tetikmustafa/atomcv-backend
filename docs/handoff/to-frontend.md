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

*(üçü de `F-028`…`F-030`'un cevabı. `B-075`…`B-084` `resolved/to-frontend-2026-09.md`'ye
indi 2026-09-09'da, dosya sınırı.)*

### B-085 · `canWriteCoverLetter` blokta — vekili bırakın

**Since:** `feat/handoff-f028-f030` · `F-028` · § 35.7.3

**Neden:** haklıydınız, blokta ön yazıya dair alan yoktu. `canSaveHistory`
**sözleşme değil** — anlamı "bu bir hesap", ve bugün doğru cevabı vermesi
ikisinin birlikte hareket etmesinden. Ayrıştıkları gün sessizce yanlışa
dönerdi, o yüzden alanı ekledik: anonimde `false`, hesapta `true`.

**Kural olarak yazıldı:** `FEATURE_REQUIRES_ACCOUNT`'ın her `feature`
değerinin blokta bir boolean karşılığı var, ve karşılığı olmayan yeni bir
değer istemcinin önleyemediği bir ret demek. Dörtünün eşleşmesi
§ D.6.1'de, ve `CapabilitiesTest` ile `OpenApiSchemaIT` ikisini birden tutuyor.

**Action:** `api.d.ts`'i yeniden üretin, `useCanWriteCoverLetter`'ın içini
`capabilities.canWriteCoverLetter`'a çevirin. Dediğiniz gibi tek satır.

### B-086 · `challengeToken` şemada duruyordu, ama import'ta yanlış yerde

**Since:** `feat/handoff-f028-f030` · `F-029` · § 35.7.4, § 44.4

**Neden:** iki ayrı cevap.

1. **`GenerationRequest.challengeToken` yayımlanıyor**, ve `8c72199`'dan beri
   öyle — `OpenApiSchemaIT` hem varlığını hem özellik sayısının altı olduğunu
   iddia ediyor. Sizdeki `api.d.ts` o commit'ten **önce** koşan bir backend'e
   karşı üretilmiş: içinde `MagicLinkRequest.challengeToken` var (daha eski),
   `GenerationRequest`'te beş özellik. `gen:api` canlı `localhost:8080`'i
   okuyor, yani tek gereken güncel backend'e karşı yeniden koşmak. Kesişim
   tipi (`Accepts<'generate'> & { challengeToken?: string }`) bugün gereksiz.
2. **Uç tekil — `POST /api/v1/profile/import`**, ve `B-051`'den beri öyle.
   Yazım hatası `B-083`'te değil, onun aldığı `spec/08-api.md` satırındaydı;
   düzeltildi (Düzeltme, § 35.7.4).

**Ama bir kusur buldunuz, ve sorduğunuzdan büyüktü.** springdoc çok parçalı bir
uçta `@RequestParam`'ı **query parametresi** diye yayımlıyor — `mode`'un
sizdeki tipte `query` altında çıkması bunun kanıtı. `challengeToken` de öyle
çıkıyordu, yani şema **token'ı URL'e koymayı** söylüyordu: erişim kayıtlarına,
vekil sunucu kayıtlarına ve tarayıcı geçmişine yazılan bir challenge token'ı,
var olma sebebinin çoğunu kaybeder. Gövde şeması artık elle yazıldı ve
`challengeToken` gerçekten form alanı. Bağlama `@RequestParam`'da kaldı —
ikisini de okuyor — yani bugün query ile gönderen bir istemci kırılmıyor.

**`mode` query'de kalıyor:** sır değil, yayımlanmış durumda, ve taşımak
karşılığı olmayan bir kırılma olurdu.

**Action:** `api.d.ts`'i yeniden üretin; import'ta token'ı **`FormData`'ya**
koyun, query'ye değil.

### B-087 · İki reddin şekli: biri zaten doğruydu, biri sizin dediğiniz oldu

**Since:** `feat/handoff-f028-f030` · `F-030` · § D.6.1

**1. `ATOM_LIMIT_EXCEEDED` `sign_up` taşıyor**, ve başından beri taşıyordu —
`AnonymousLimits` onu `.resolution(SIGN_UP)` ile kuruyor. `B-081`'in tablosu
yalnız `params`'ı yazıyordu, o yüzden görünmüyordu. Mock'un boş listesi
yanlış; düğmeyi çizin.

**2. `feedback` uydurmaydı, ve artık değil — ama bugünkü cevap 403 değil
401'di.** İki uç (`POST /generations/{id}/feedback` ve
`.../cover-letter/regenerate`) `currentUser.require()` çağırıyordu, yani
geçerli bir anonim oturum tutan kişiye `AUTHENTICATION_REQUIRED` diyordu —
ekranın oradan yazdığı cümle "oturumunuz bitti", ve teşhis yanlış. Oturum
bitmemişti; özellik hiç onların değildi. **Tahmininiz doğru şekildi**, ve
uygulandı: `403 FEATURE_REQUIRES_ACCOUNT` + `params.feature` (`feedback`,
`cover_letter`) + `sign_up`.

**Hiçbir şey taşımayan istek hâlâ 401 alıyor.** O, öteki kodun var olduğu düz
durum, ve "hesap lazım" demek aynı yanlış cümleyi ters yöne çevirmek olurdu.

**3. Kapalı sözlük yazılmış değildi, artık yazılı.** Katalog `feature: string`
diyordu ve tam küme hiçbir yerde durmuyordu — dışarıdan bakınca tahminle
sözleşme aynı görünüyordu, ki tam olarak yaşadığınız buydu. Dördü, kodda
`AccountFeature` enum'u ve `spec/08b-api-contract.md` § D.6.1'de:
`atom_controls`, `alternatives`, `cover_letter`, `feedback`.

**Action:** `ATOM_LIMIT_EXCEEDED` mock'una `sign_up` ekleyin; iki 403 mock'unu
şu haliyle bırakın — doğruydular.

---

## Kalıcı kurallar

Eski maddelerin `spec/`'e işlendiği yerlerin tablosu
`resolved/to-frontend-2026-08.md`'ye taşındı (2026-08-24) — dosya sınırı.
