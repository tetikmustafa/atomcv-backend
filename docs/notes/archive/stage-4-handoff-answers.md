# Arşiv — Aşama 4 · `F-031`-`F-033`

> Kapandı 2026-09-12, geldikleri gün (PR #194). Üçünün de kalıcı kararı
> `spec/`'e işlendi — § 24.2 ve § 24.2.1 (numaralanmış satırlar), § 35.3
> (`GET /generations/{id}/selection`, `supersededByGenerationId`, işin
> `result`'ı ile `JobStatusResponse` arasındaki kural), § 35.8.1-2
> (`operationId` ve `isX()`) — ve bu yüzden `current.md`'den silindi.
> Frontend'e `B-097`-`B-099`.

---

## Aşama 4 · `F-031`-`F-033` (2026-09-12)

**Ekleme — `GET /generations/{id}/selection`.** Elle aç/kapa ucu çizilecek
listesi olmadan inmişti; tartılmamış atom `400` döndüğü için profilden çizmek
basılamayacak düğme demekti. Metin çözümü `WeighedLines`'ta **tek yerde**:
kişinin gördüğü satır ile modele numaralanan satır aynı cevap; 30'luk sınır
yalnız prompt'un. Silinmiş atom listede yok ama **edit onu hâlâ kabul ediyor**.

**Sapma — `matchLevel` istenmemişti, `supersededGenerationId` ile eklendi.**
İkisi de worker'ın `result`'ına yazılıp SSE'de görünen, tipte görünmeyen
alanlardı. **Kural: `result`'a konan her anahtar `JobStatusResponse`'ta bir
alandır**, yoksa poll'a düşen istemci kaybeder (üçüncü kez: `F-008`, `F-018`,
`F-032`). `MatchLevel` bu yüzden `shared.wire`'a taşındı — `jobs`'un onu
adlandırması `generation` ile çevrim yapardı; şema adı değişmedi.

**Ekleme — numaralı `operationId` konumsaldır.** `DELETE /account` `delete_1`
iken başvuru controller'ı inince `delete_2` oldu ve `delete_1` başvuru silmeye
geçti; ikisi de 204 döndüğü için istemci sessizce yanlış operasyona bağlandı.
33 ucun hepsi adlandırıldı, ama **muhafız isimler değil test**: şemada `_<sayı>`
ile biten `operationId` olamaz. `isX()` de bir alandır — `Appearance` kolona
`"empty"` yazmış satırlar bıraktığı için `ignoreUnknown` de gerekti.

---

_`notes/current.md`'den 2026-10-24'te taşındı._

## Frontend'in dört maddesi — `F-037`…`F-040` (2026-09-20)

**Ekleme — beyan edilen bir dil, tespiti tamamen atlıyor.** Spec sessizdi
(§ 08b Adım 3.4 alanı adlandırmıyor). `F-037`'nin üç seçeneğinden birincisi:
`POST /profile/import` `language` alıyor. **Dokunmadan önce iki gerekçeyi oku,
ikisi de "daha ihtiyatlı" görünen şeye karşı:** (1) *eşik düşükse kullan*
işe yaramaz — redd worker'dan gelir, cevap ancak bir sonraki yüklemede
gönderilir, ve o yükleme aynı düşük skora düşüp yine reddedilebilirdi, ki
`F-037`'nin şikayeti o döngü; (2) beyan **güvenli bir tespiti de geçer** —
anlaşmazlıkta modele sessizce yenilen bir alan, birinin fark ettiği tek
durumda hiçbir şey yapmaz. Satın almadığı: atomsuz belge hâlâ
`NothingExtracted` (`adeclaredLanguageDoesNotRescueAnEmptyDocument`).

**Ekleme — `maxPages` yayımlanıyor** (`F-039`), çünkü "kısa CV pad edilmez, not
gösterilir" (konsept § 11.2) sinyalsizdi. Sinyal **doluluk olmak zorunda
değil**: sınırın altındaki `pageCount` sayılabilir bir olgu, § 23.3'ün
yasakladığı yüzde değil. Kolon gerekmedi — `options`'a hep yazılıyormuş.
Eski satırlarda varsayılan **değil** `null`: makul bir sayı uydurma olurdu.

**Düzeltme — `two_column`'un şekli ikinci kez çıktı** (`F-040`). Altıncı tur
onu **girdi** olarak ayırmıştı (seçiliyor, kabul ediliyor, belge başkasını
basıyor); `customizationId` aynısıydı ve görülmedi — bilinmeyen id `202`
alıyordu. **Ders: o tur bir örnek buldu, kusur sınıfını değil.** Artık kapıda,
kotanın önünde, `404`. `B-104`'ün *"`freeformNote` gelmedi"* cümlesi de bayat
çıktı (`F-038`); arşivde not düşüldü, `B-117`…`B-119` frontend'e gitti.
