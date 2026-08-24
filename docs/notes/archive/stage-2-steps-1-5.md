# İnşa Notları — Aşama 2, Adım 2.1-2.5 (kapandı)

> `notes/current.md`'den taşındı (2026-08-24), çünkü 200 satır sınırını üçüncü kez
> zorladı. Kalıcı kararlar zaten `spec/`'te; burası **nasıl bulunduklarının**
> kaydı. Canlı indeksler — promote tablosu ve kasıtlı-ihlal tablosu —
> `current.md`'de kaldı, çünkü 2.6 ve sonrası da onlara yazıyor.

---

## Aşama 2 kayıtları

**`F-001`…`F-006` kapandı** (§ 35.2 / § 35.6). **İkisi ileriye dönük:**

- **Toplu JPQL `update` `@Version`'ı atlar.** 2.7'nin kota sayaçları isteyecek
  ve bayat etag üretecek. `update versioned` — ama *hepsini* sürümlemek
  promote'u kırıyor (denendi, dört test düştü). 2.6'da ısırmadı: `jobs`'ta da
  `generations`'ta da `version` kolonu yok.
- **Okuma, yakalanmak istenen bayatlığı onarır** — etag'i **önceki yazmanın
  yanıtından** al, yoksa araya giren `GET` rakamı tazeler.

**Düzeltme — build guide üç kez "migration" dedi, üçü de yanlıştı:** 2.4'ün
pgvector kolonu, 2.6'nın `jobs` ve `generations` tabloları `V1`'de vardı.
Dördüncüsünü görürsen **önce `V1`'e bak**. · **`continue_anyway` sözlüğe girdi**
(§ 18.1, EK D.6.1) — `B-037` hâlâ açık.


## Adım 2.5 kapanışı

**Düzeltme — keyword bileşeni tag bileşeninin kopyasıydı** (§ 19.2). Ölü duran
`titleTokens`, `contentTokens` adıyla bağlandı.

**Ekleme — `atom_tags`'te `profile_id` yok**, kapsamlama join'den geçiyor;
`AtomTag`, `ProfileOwned`'ı uygulayamayan tek profil satırı. Etiketler
`ProfileAssembler`'a beşinci sorgu olarak **eklenmedi** (§ 52.2 dört diyor).

**Ekleme — kapıların sırası maliyete göre.** Sırayı bozmak ne derlemeyi kırar ne
çıktıyı değiştirir, yalnız para harcatır — bu yüzden testi var.

**Açık soru — § 19.4'ün ikincil kriterleri ilan modunda okunmuyor.** Bölüm
"yakın skorlu atomlar arasında ve genel CV modunda" diyor; bugün yalnız ikincisi
var. "Yakın"ın tanımı yok ve tanımlamak `RelevanceScorer`'ın sözleşmesini
değiştiriyor — uydurulmadı, sorulacak.

---

> Adım 2.6'nın **kuyruk dilimi** de buraya taşındı (2026-08-24, aynı sebeple).

## Adım 2.6 — kuyruk dilimi

**Düzeltme — sondam yanlış geçti.** `SKIP LOCKED` kaldırıldı, 14 test de geçti:
düz `FOR UPDATE` kilidi bekleyip yüklemi yeniden değerlendiriyor. Gerçek fark
**canlılık**; onu ölçen test bir kilidi açık tutup claim'in *hemen* boş
dönmesini bekliyor. Kural § 30.2'de, ders `CLAUDE.md`'de.

**Ekleme — kuyruğun iki okuyucusu ayrı tip** (§ 30.2); ArchUnit `..jobs..` ve
`..generation..` için kendi satırlarını kazandı.

**Ekleme — toplayıcının iki kuralı ve backoff'un taşması § 30.4-30.5'te:** hak
geri verilmiyor, hakkı bitmiş iş `failed`'e gidiyor, üs kaydırmadan sınırlanıyor.

**Düzeltme — CI'da düşen test, yerelde geçen kod.** `Set.copyOf`/`Map.copyOf`
**her JVM çalıştırmasında farklı** sırayla dolaşıyor (üç ölçüm, üç sıra). İki
yerde ısırdı: `TagRepository.labelsByAtom` sorgunun sırasını atıyordu, `Job`'un
üç JSONB kolonu da `JobWorker`'ın sıralı kurduğu hata haritasını bozuyordu.
İkisi de `Collections.unmodifiable*` + `Linked*`; kural `CLAUDE.md`'de.

---

> Adım 2.6 kapandı (2026-08-24); kalan kayıtları da buraya taşındı.

## Adım 2.6 — kayıt, API, SSE ve indirme

> Kuyruk diliminin kayıtları `archive/stage-2-steps-1-5.md`'de. Oradan
> hatırlanmaya değer tek şey **`SKIP LOCKED` sondasının yanlış geçmesi**:
> mükerrerliği ölçen test o cümleyi hiç ölçmüyordu, gerçek fark canlılık
> (§ 30.2, ders `CLAUDE.md`'de).

**Karar — Aşama 2'de PDF baytı saklanmıyor** (2026-08-24, EK D.6.3). R2 Adım
3.1'de; indirme `selection_state`'ten yeniden render ediyor. Devredilen
"`generations`'a yazan yok" kısıtı kapandı. **Sapma:** anlık görüntü
`customizationId` değil özelleştirmenin kendisini taşıyor (§ 14.5) — işaret
edilecek satır yok, hiçbir şeye çözülen id anlık görüntüyü işe yaramaz kılardı.

**Düzeltme — `"\s+"` tek ters bölüyle yazılmıştı.** Java 15'ten beri `\s`
geçerli bir string kaçışı ve **tek boşluk** demek: desen sessizce "boşluk
dizileri"ne daralmış, satır sonları normalleştirmeden sağ çıkmış, aynı ilanın
PDF'ten ve tarayıcıdan gelen hâli farklı hash'lenmişti. Derleniyor ve doğru
görünüyor. Çıkarma sırasında yakalandı, çünkü artık iki çağıran aynı cevabı
istiyor: cache ve `generations.jd_hash`.

**Ekleme — kayıt yalnız belge çıkınca yazılıyor.** `selection_state` satırın
sebebi; seçimden önce düşen koşunun arızası işin üstünde yaşıyor.
`GenerationStatus`'ta bu yüzden `queued`/`running` yok — tek iş üzerinde iki
durum makinesi haber vermeden ayrışır.

**Ekleme — prompt sürümü *çalışan* sürüm.** `promptVersionFor` saf bir fonksiyon,
iki kez sorulunca aynı cevabı veriyor. Varsayılanı yazmak, alanın işe yaradığı
tek durumda — A/B deneyinde — yanlış olurdu.

**Bilinçli boşluk:** handler uçtan uca koşturulmadı — kuyruğa koyan bir şey yok
ve gerçek koşu LaTeX container'ı + `local-fake` istiyor. O test uca ait.

**Düzeltme — § 30.6'nın `label`'ı düz metin taşıyordu, § 35.4 ile çelişiyordu.**
Anahtar tarafı seçildi (`generation.phase.<FAZ>`); § 30.6 ve EK D.6.4
düzeltildi, frontend maddesi `B-038`. Tek dilde gönderilen bir cümle her yeni
dilde yeniden gönderilirdi ve ilerleme satırı üründe en çok görülen metin.

**Ekleme — ArchUnit'e üçüncü bir IDOR satırı.** `JobQueue` Spring Data değil,
yani mevcut iki kural onu görmüyor ve bir controller'da **derlenirdi**: sahiplik
kontrolü olmadan id ile iş okumak. Kural `..api..`'nin `JobQueue`'ya bağlanmasını
yasaklıyor; servisten kuyruğa koymak serbest kalıyor. Kasıtlı ihlalde hem kural
hem çapraz kullanıcı testi düştü.

**Ekleme — ilerleme satıra da yazılıyor, yalnız olaya değil** (EK D.6.4):
kimseye gönderilmemiş olay yok olur. Bedeli faz başına bir update.

**Ekleme — SSE'nin iki kararı § 30.6'ya, `Last-Event-ID` yorumu EK D.6.4'e
yazıldı.** Bağlanışta güncel durum gönderiliyor (yeniden bağlanmayı *ve* 202 ile
abonelik arasında biten işi birden çözüyor), terminal olay akışı kapatıyor.
Replay tampon isterdi; § D.6.4 zaten daha ucuz olanı kabul ediyor. Kayıt süreç
içi — javadoc bunun ne zaman yetmeyeceğini de söylüyor.

**Ekleme — `JobEvents` no-op varsayılanlı arayüz**, kuyruk HTTP'ye bağlı değil.
Satır önce yazılıyor, sonra duyuruluyor.

**Düzeltme — bir testim yanlış şeyi taklit ediyordu.** MockMvc'nin koparacak
istemcisi yok; "istemci koptu" testi kendi stub'ına assert etmiş olurdu.
`remove` doğrudan çağrılıyor — gerçek callback'lerin yaptığı da bu.

**Doküman kararı (onaylandı 2026-08-24):** aşama kapanmadan da kapanmış
adımların kayıtları `archive/`'a taşınıyor; canlı indeksler burada kalıyor.
`to-frontend.md`'nin kalıcı-kurallar tablosu da `resolved/`'a taşındı.

**Karar — `content_snapshot` üretim anında yazılıyor** (onaylandı 2026-08-24,
EK D.6.3'e işlendi). `selection_state` atomları id'yle adlandırıyor ve metin
`atom_variants`'ta düzenlenmeye devam ediyor; profili yeniden okuyan bir indirme
işverene gönderilenden başka bir belge verirdi. § 22.2'nin `RenderRequest`'i
zaten id taşımıyor, yani onu saklamak render'ın kendisini saklamak.

**Ekleme — genel mod kuyruğa taşındı, sonra `/general` kaldırıldı** — bu sırayla,
tersi özellik kaybıydı. `jobDescription` opsiyonel; yokluğu genel mod (§ 19.4).
`engine_version` `general-mode` yazıyor, `default` değil: hiçbir şey
karşılaştırmamış bir koşu, karşılaştırmış gibi görünmemeli.

**`GenerationApiIT` silindi, taşınmadı.** Kapsadığı her vaka ya
`QueuedGenerationApiIT`'ye geçti ya da bir iş arızası olup
`GenerationJobHandlerTest`'e düştü. `GeneralCvIT` artık akışı uçtan uca gerçek
TeX'e karşı koşuyor: kuyruk → worker → `generations` satırı → download.

**Kalan boşluk:** ilana özel yol uçtan uca hiç koşmadı — `local-fake` profili
gerekiyor ve entegrasyon süiti `local` ile çalışıyor. Genel mod yolu koşuyor.

**Adım 2.6 kapandı.** Sırada 2.7: kota, kill switch, anomali, Axiom.
