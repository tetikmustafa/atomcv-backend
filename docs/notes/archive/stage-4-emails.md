# Arşiv — Aşama 4 · yaşam döngüsü e-postaları ve açık kaynak

> Kapandı 2026-09-11: `B-096` indi ve frontend aynı gün karşıladı, `.env`
> düzeltmesi `EnvExampleTest`'e sabitlendi. Kalıcı kararlar § 57.7'de; hâlâ
> geçerli dersler `current.md`'nin "hâlâ canlı" bölümünde.

---

## Aşama 4 · e-postalar ve açık kaynak (2026-09-11)

**Ekleme — § 57.7 yaşam döngüsü e-postalarını tanımladı.** İnşa kılavuzu maddeyi
adlandırıp bırakıyordu, yani "bu ürün hangi e-postaları gönderir" sorusunun
uygulanacak bir cevabı yoktu. Liste **kapalı**: hoş geldin ve silme onayı.

**Ve spec uygulanmadan önce iki kez düzeltildi** — bu dilimin asıl kazancı bu.
(1) Tetikleyici "`users` satırı ilk yazıldığında" diyordu; § 40.4 hesap sayımını
engellemek için satırı adres yazılır yazılmaz yaratıyor, yani **giriş kutusuna
adresi yazılan herkese** posta giderdi. Doğrusu ilk başarılı giriş
(`last_seen_at` null). (2) Tercih `PUT /profile/preferences`'a konacaktı; o uç
profili yerine koyuyor ve profilin ETag'iyle korunuyor, yani bir CV çakışması
e-posta ayarını reddederdi ve alanı göndermemek onu kapatmak olurdu.
`GET`/`PATCH /api/v1/account` oldu.

**Ekleme — silme onayı işlemsel, hoş geldin değil.** Adres satır silinmeden
okunuyor ve posta commit'ten sonra çıkıyor (servis adresi döndürüyor, çağıran
gönderiyor): geri alınan bir silmenin onayı yalan olurdu. Hoş geldin için aynı
özen gereksiz — geri alınan bir girişin hoş geldini bir fazla postadır.

**Ekleme — kapatma bağlantısı uca değil sayfaya iniyor** (§ 40.3). Ağ geçitleri
mesajdaki her adresi kimse okumadan çekiyor; çekilince kapatan bir uç, hiç
tıklamamış kişilerin postasını keserdi. Jeton opak ve satırda (`unsubscribe_token`),
imzalı değil — doğrulaması sır istemesin diye. Bilinmeyen jeton da 204.

**Düzeltme — `.env.example` hiçbir şeyin okumadığı bir harcama limiti sunuyordu.**
`DAILY_BUDGET_USD`'yi spec dört yerde anıyordu ve yayın kontrol listesinde
maddesi vardı; kod `ANOMALY_DAILY_BUDGET_USD` okuyor. Kurtaran tek şey
varsayılanın daha düşük olması: kill switch geç değil erken ateşliyordu.
`EnvExampleTest` iki kuralı tutuyor — örnekte okunmayan ad olamaz, varsayılanı
olmayan ad eksik olamaz.

**Ders — görev girdisi olmayan bir dosya kimseyi bağlamaz.** Bugün ikinci kez:
`performance-budgets.yaml` gibi `.env.example` de `inputs.file` ile bildirildi.
Aksi hâlde Gradle `:test UP-TO-DATE` deyip onu denetleyen testi hiç koşturmuyor,
ki `DAILY_BUDGET_USD`'nin kayma yolu tam buydu.

**Ders — ölçüm belgesiyle sayfa arasındaki fark her zaman bir hatadır**, ve
sapma testi tek başına yetmez: bir maddedeki bir satır yedi yüz puanın içinde
kaybolur. `WordingCostIT` bir ifadenin, `EntryFurnitureIT` bir girdinin marjinal
bedelini sayfaya karşı ölçüyor. **`EntryFurnitureIT`'in sabitlediği ~2.9pt'lik
artık bir kusur değil**, aracın kendisi: test listesi *olan* belgeyi listesi
*olmayanla* karşılaştırdığı için listenin arkasında duran tek şey prob oluyor.
Girdi eklemenin marjinal bedeli üç şablonda da modelin yazdığına **tam** eşit
(43.72 / 25.47 / 51.72), her boyutta ve ardından başlık gelse de gelmese de.
