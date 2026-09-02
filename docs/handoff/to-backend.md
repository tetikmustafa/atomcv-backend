# → Backend

> **Kanal kuralları**
> - Frontend yazar, backend okur ve `OPEN` → `ACK` taşır.
> - Her madde bir ID taşır (`F-nnn`), numaralar tekrar kullanılmaz.
> - **Dosya 100 satırı geçerse arşivleme gecikmiştir.**
> - Bir spec değişikliği gerekiyorsa burada iste — `spec/`'i frontend reposunda düzenleme,
>   bir sonraki senkronda kaybolur.

---

## OPEN

> Üçü de **gerçek uca karşı ölçümden** çıktı (2026-08-30, `make record`),
> mock'a karşı değil — ikisi bir aşama boyunca durmuş, biri hesap silmenin
> ardındaki bir yolda duruyordu. Hiçbiri bir ekranı bloke etmiyor.
>
> **Dosya sınırın üstünde**, ve arşivlenebilen her şey arşivlendi: fazlalık bu
> üç açık madde, ve `ACK` gelmeden taşınacak bir yerleri yok.

### F-025 · `companyName` telde `"not specified"` olabiliyor — boş dize kuralı bunu tutmuyor
**Since:** frontend commit `1c63e27` · gerçek uca karşı ölçüm, 2026-08-30
**Neden:** `B-070` "boş dize hiç dönmüyor, alan ya doludur ya yoktur" diyor ve
`""` için doğru. Ama telde duran 45 satırın birinde `companyName` **`"not
specified"`** — modelin, şirketin adı geçmediğini söylemek için yazdığı bir
cümle. `""` değil, o yüzden çeviren kural onu yakalamıyor, ve satır ekranda
*"Business Intelligence Specialist (SQL Developer) · not specified"* diye
çıkıyor: § 57.6'nın "bir şey söylüyormuş gibi duran etiket" diye tarif ettiği
şeyin ta kendisi.

**Bizde çözülemez, ve denemeyeceğiz.** İstemci tarafında bu, bir yer tutucu
ifade kara listesi demek — `"not specified"`, `"belirtilmemiş"`, `"N/A"`,
`"unknown"`, ve modelin yarın yazacağı yedincisi. Dilden ve modelden bağımlı
bir tahmin, ve yanlış tarafta.

**İstenen:** ikisinden biri. (a) `JobAnalysis` "yok"u tek bir biçimde
söylesin — prompt'ta şirket yoksa alanı boş bırakma talimatı, ve mevcut
`""` → yok çevirisi işini görsün; ya da (b) çevirici, boş dizeye ek olarak
modelin "yok" demek için kullandığı kalıpları da yok sayar — hangisi
sizin tarafınızda daha az kırılgansa. Kararı sizinki, çünkü hangi kalıpların
çıktığını **prompt'u yazan** taraf görebiliyor.

**Kayıt için doğru çıkanlar:** iki alan gerçekten bağımsız (45 satırın 28'inde
rol var, 19'unda şirket), ve genel modda ikisi de yok. Bir de şunu ölçtük:
`roleTitle` **kendi içinde tire taşıyabiliyor** (`"Integration Engineer —
Legacy Systems"`), o yüzden satırda rolü ve şirketi bir tire ile birleştirmiyor,
iki ayrı öğe olarak çiziyoruz.

**Spec:** `spec/16-cost-legal.md` § 57.6, `spec/08-api.md` EK D.8.7

### F-026 · Dört mektup taslağının dördü de reddedildi — `retry` tek çıkış yolu
**Since:** frontend commit `5df4d42` · gerçek uca karşı ölçüm, 2026-08-30
(`make record`, yani gerçek LLM çağrıları)
**Neden:** Altın profile (`senior_backend_tr`) karşı arka arkaya **dört**
taslak istedik ve dördü de `422 COVER_LETTER_REJECTED` döndü:

| # | `style` | `issues` |
|---|---|---|
| 1 | `default` | `number_invented`, `length_out_of_range` |
| 2 | `shorter` | `number_invented`, `length_out_of_range` |
| 3 | `shorter` | `number_invented`, `length_out_of_range` |
| 4 | `more_formal` + `companyNote` | `number_invented`, `length_out_of_range`, `cliche` |

Dördüncüsünün farklı çıkması, bunların **tekrar oynatılan tek bir fixture
olmadığını** gösteriyor: gerçekten dört ayrı taslak yazıldı ve dördü de
geçemedi.

**Ekranın söylediği cümle bu ölçüme göre yanlış.** `COVER_LETTER_REJECTED`'ın
tek çözümü `retry`, ve bizim metnimiz "tekrar istemek genelde geçen bir taslak
üretir" diyor. Dört denemede geçmedi; yani kullanıcının önündeki tek düğme onu
hiçbir yere götürmüyor olabilir. Metni yumuşatmadık — bir profil ve bir model
yapılandırmasına bakarak ürün metnini değiştirmek, ölçtüğümüzden fazlasını
iddia etmek olurdu.

**İstenen:** bu iki ihlalin sistematik olup olmadığına bakın. `number_invented`
altın profilin metriklerinden (`300 bin satır`) geliyor olabilir — mektuba
girmesi yasaksa prompt bunu söylemeli; `length_out_of_range` ise `shorter`
istendiğinde bile çıkıyor, ki bu bir aralık/prompt uyuşmazlığına benziyor.
**Sistematikse çıkışsız bir ekran var demektir**, ve o zaman ya guard'ın ya
prompt'un değişmesi gerekiyor — istemcinin ekleyebileceği bir şey yok.

**Doğru çalışan yarısı:** gövde tam beklediğimiz şekilde geldi ve ekran onu
doğru çiziyor — iki değer `errorValues` üzerinden adlandırılıp
`Intl.ListFormat` ile birleşiyor, panel kırmızı değil, ve `retry` düğmesi
zaten ekranda duran düğme. Mock'un tek elemanlı `issues`'u da ölçülen çiftle
değiştirildi: tek elemanlı bir liste `ListFormat`'ı hiç sınamıyordu.

**Spec:** `spec/07-subsystems.md` § 34, `spec/08b-api-contract.md` EK D.6.5

### F-027 · Silinmiş hesabın çerezi her profil okumasında `500` üretiyor
**Since:** frontend commit `5df4d42` · gerçek uca karşı ölçüm, 2026-08-30
**Neden:** `DELETE /api/v1/account` **doğru çalışıyor** — `204`, `sid` çerezi
`Max-Age=0` ile siliniyor, `GET /generations` artık `{"items":[],"total":0}`.
Ama aynı kullanıcı kimliğiyle yapılan **sonraki her profil okuması `500`**:

```
GET /api/v1/profile           500 INTERNAL_ERROR
GET /api/v1/profile/sections  500 INTERNAL_ERROR
GET /api/v1/profile/atoms     500 INTERNAL_ERROR
GET /api/v1/profile/entries   500 INTERNAL_ERROR
GET /api/v1/account/usage     200
GET /api/v1/generations       200
```

Yerelde bu durumu **dev auth stub'ı** üretiyor (istek çerezsiz de aynı
kullanıcı olarak kimliklendiriliyor), ama **üretimde de ulaşılabilir**: hesabı
bir cihazda silen kişinin öteki sekmesinde **eski oturum çerezi duruyor.** O
sekmenin bir sonraki isteği bugün `500` alıyor; doğrusu
`401 AUTHENTICATION_REQUIRED` olurdu — istemci onu zaten girişe yönlendiren
bir şey olarak tanıyor, `INTERNAL_ERROR`'ı ise "bir şeyler ters gitti"
panelinden başka bir şey olarak tanıyamaz.

**İstenen:** silinmiş bir kullanıcıyı gösteren oturum `401` alsın. Ayrıca:
§ 35.6 "hesabı olan ama profili olmayan çağırana boş profil dönülür, 404 yok"
diyor — bu yol o iddiayı da `500` ile bozuyor, yani düzeltme muhtemelen iki
yerde: oturum doğrulaması ve profil çözümleyicisinin eksik satır hâli.

**Spec:** `spec/16-cost-legal.md` § 57.4, `spec/08-api.md` § 35.6

<!-- Şablon:
### F-001 · Kısa başlık
**Since:** frontend commit <sha> · Adım <n>
**Neden:** <sorunun ne olduğu>
**İstenen:** <backend'den beklenen somut şey>
**Spec:** <ilgili dosya ve bölüm, varsa>
-->

---

## ACK — backend tamamladı, frontend arşivleyebilir

*(`F-001`…`F-024` `resolved/to-backend-2026-08.md`'de — üçünün de cevabı
oraya indi 2026-08-30'da, dosya sınırı.)*
