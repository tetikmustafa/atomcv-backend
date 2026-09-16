# İnşa Notları — Aktif

> Kural: bu dosya **200 satırı geçmez**. Aşama bitince `archive/`'a taşınır, boş başlanır.
> Kayıt tipleri: **Sapma** (doküman başka diyor) · **Ekleme** (doküman sessiz) · **Düzeltme** (doküman yanlış).
> Bir sapma kalıcıysa `spec/`'e işlenir ve buradan silinir.

**Aktif aşama:** Aşama 4 — Olgunlaşma. **Plan:** `spec/14-build-guide.md`
§ XI-A.7. Aşama 1-3 ve Aşama 4'ün kapanmış dilimleri `archive/`'da; harita
aşağıda.

---

## Denetim — dördüncü tur, mekanik (2026-09-16)

Üçüncü turun kaydı `archive/denetim-2026-09-16-ucuncu.md`'de; ilk ikisi
`denetim-2026-09-15.md` ile `denetim-2026-09-16.md`'de. **Bu tur düzyazı
okumadı** — üç kez okunmuş bir metni dördüncü kez okumak aynı gözle bakmaktı.
Yerine spec'ten çıkarılabilen her somut ad çıkarıldı ve repoda arandı: 1659 kod
parçacığı (tip, sabit, property, uç, tablo), 61 ucun tamamı, 35 enum'un kapalı
sözlükleri, 14 migration'a karşı veri modeli, 91 sayısal sabit, spec'in kendi
`§` atıfları. **Üç gerçek boşluk, ikisi kümelerde — bir adın repoda geçmesi
kümenin tam olduğunu göstermiyor.**

**Sapma — `§ 13` "Tam Veritabanı Şeması" on üç migration geriden geliyordu.**
Bloğu `V1__initial_schema.sql` başlığını taşıyor ve V2-V14 hiçbir yere
işlenmemişti: `profiles.user_id` nullable (anonim profil), `expires_at` ve XOR
kısıtı, `header_costs`, `rewritten_content`, `users`'ın iki kolonu,
`sections.layout`'un beşinci değeri, V3'ün anonim idempotency indeksi, V4'ün
unique indeksi, ve **`template_capacities` — bütün `docs/` ağacında hiç
geçmiyordu.** `§ 16.1`'in ağacı da üç dosya sayıyordu, ikisinin adı uydurma.

> **Blok düzenlenmedi, delta yazıldı** (`§ 13.2`). Birebir kopyası olduğu dosya
> duruyorken bloğu güncellemek onu ne V1 ne bugün yapardı — iki sürümün
> ortasında, hangisini anlattığı belirsiz bir metin kalırdı.
>
> **Ve kural bir teste bağlandı**, çünkü bu turun bulduğu şey tam olarak
> "düzyazıdaki bir kuralı kontrol eden bir şey yoktu"ydu:
> `SchemaDocumentationTest` her migration adının bölümde geçmesini **ve**
> bölümün olmayan bir dosya adı uydurmamasını zorluyor. Alıntı blokları muaf —
> bir düzeltme, düzelttiği adı yazabilmeli, yoksa bulunabilir olmaktan çıkar.

**Düzeltme — `no_responsibilities` altı yerde yayımlanıyordu, tel üretemiyor.**
`PlausibilityGate` dördüncü verdict'i Aşama 3'te düşürmüştü (nitelik sayıp
görev saymayan ilan olağan şekildir; düşüren ilan 0.92 güvenle, yirmi beceri
okunmuşken reddedilmişti) ve gerekçe javadoc'ta duruyordu. Spec sözlüğü
**sekiz** saymaya devam etti: § 18.1'in sayım cümlesi, § 18.4'ün kod
parçacığı, iki tablo, § 08b ve EK D.

> **`B-nnn` açılmadı ve sebebi kayda değer:** frontend dalı `B-072`'nin
> cevabında zaten silmiş, neden listeleri orada yediye inmişti. **Geride kalan
> tek kopya spec'ti** — yani bu bir frontend aksiyonu değil, kendi
> gecikmemizdi. Açık `B-111`'in yanlış sayımı düzeltildi, çünkü çeviri
> dosyaları ona bakılarak yazılacak.

**Ekleme — `FakeLatexCompiler` yazıldı; § 54.2 onu bir aşamadır listeliyordu.**
Tek yol `LatexCompilerClient`'tı ve `make dev` latex konteynerini kaldırmıyor,
yani sahte LLM'le çalışan bir klon Faz E'de dinleyen kimsenin olmadığı bir
adrese gidiyordu. Sayfa sayısı sabit 1 (hareket eden bir sayı, kimsenin
ölçmediği ikinci bir sayfa modeli olurdu); ölçüm probları kutudaki
karakterlerden aritmetikle cevaplanıyor; **kalibrasyon bilerek cevapsız**,
çünkü uydurulmuş bir kapasite `template_capacities`'e yazılır ve onu uyduran
oturumdan sonra da yaşardı.

> **İlk hâli `latexTest` hattını kırdı, ve bunu yalnız hattı koşturmak
> gösterdi.** `@Profile("!local-fake")` idi; dört IT o profil altında koşuyor
> çünkü **sahte modeli** istiyor ve **gerçek derleyiciyi** ölçüyor. "Gerçek bir
> tek sayfalık PDF iki bin bayttan büyüktür" diyen beş iddia 669 baytlık yer
> tutucuyla karşılaştı. Ayrım `atomcv.latex.fake` anahtarına taşındı;
> `AbstractLatexTest` tek yerden kapatıyor, ki beşinci bir sınıf onsuz
> yazılamasın.

---

**Tamir etmeye kalkma — bilinçli:**

- **Admin teşhis ucu yok** (§ 41.4) — çevrimdışı okuyucu var, ve 2026-09-15'te
  bir kez daha soruldu, bir kez daha hayır denildi. Lehindeki tek gerçek
  argüman kayda değer: okuyucu **sunucu erişimi** istiyor, bir uç yalnız bir
  oturum isterdi. Karşısındaki ağır bastı: kapsamsız okumayı bir path
  değişkenine bağlamak mutlak kural 3'ün yasakladığı şeklin ta kendisi, üç yeni
  kontrol sonsuza kadar doğru kalmalı, ve rol kontrolündeki bir hata
  **herkesin** CV'sini HTTP'ye açar. Sunucuya erişmemesi gereken bir destek
  ekibi olduğu gün yeniden açılır.
- **Faz B ve Faz C replay edilemiyor** (§ 48.5). Faz E ediyor, çünkü
  `content_snapshot` zaten onun girdisi. Ötekilerin girdileri — puanlanmış
  ağaç, ölçülmüş yükseklikli `SelectionRequest` — hiçbir yerde saklanmıyor;
  saklamak bir hata ayıklama kolaylığı değil, birinin profilinin kopyası
  hakkında bir saklama kararı olurdu. `GenerationExport`'un javadoc'u gereken
  alanları adıyla yazıyor.
- **`generation.pages.drift` kaba, ve hiçbir şey ona göre davranmıyor**
  (§ 26.6).
- **GitHub eşleştirmesi kısaltmayı ıskalıyor** (§ 31.8.1), eşik bilerek
  yüksek: yanlış birleştirme yanlış paragrafa bağlantı koyar.
- **R2 yok** (§ 57.4, karar: 2026-08-28) — silinecek PDF de yok. Depolama
  indiği gün silme yolunun oradan geçmesi gerekiyor, ve **bunu hatırlatan şey
  artık bir paragraf değil bir test**: `ObjectStorageDeletionTripwireTest` bir
  `pdfKey` yazıcısı ya da bir S3 istemcisi belirdiği gün düşüyor, ve mesajı
  talimatın kendisi.

**Test yazarken (denetimde öğrenilenler):**

- **Bir dosyayı çalışma anında okuyan test, o dosyayı Gradle girdisi olarak
  ilan etmezse koşmaz.** Listede artık `performance-budgets.yaml`,
  `.env.example`, `nginx.conf`, `docker-compose.prod.yml`, `openapi.json`,
  `docker/latex/*` ve **`docs/spec/04-data-model.md`** var.
- **Bean'i override eden her test sınıfı kendi context'i ve kendi havuzu
  demek.** Havuz context başına dörde çekildi.
- **Bir metriği kaybetmek hiçbir şeyi kırmaz.** Yeniden adlandırmak build'i
  bozmuyor, silmek testi düşürmüyor, ve kayıp aylar sonra düz duran bir panel
  olarak ortaya çıkıyor. `MetricCatalogueTest` adları kaynaktan okuyup her
  birini § 48.3'ün cevapladığı satırla eşleştiriyor: eklemek de silmek de
  düşürüyor.
- **`@ServiceConnection` `spring.datasource`'u doldurmuyor.** `LISTEN`
  bağlantısı ayarlarını havuz yerine property'lerden okuyunca var olmayan bir
  veritabanını arayıp iki saniyede bir yeniden bağlandı — teslimat hatası gibi
  görünen bir adres hatası.

**Ölçümler:**

- **Faz D eşikleri yeniden ölçüldü** — gömme olmadan en güçlü atom **0.1259**,
  varsayılan ağırlıklarla **0.2756**, taban 0.40. Faz D hâlâ hiçbir şey
  planlamıyor: sonuç değişmedi, sebebi artık tam.
- **`cover_letter` aktif `v1`.** v2 turu 169 kelime verdi, bant 255-290.
- **XeTeX format dökümü imkânsız** (§ 29.2): motor `Can't \dump a format with
  native fonts or font-mappings` diyor. Asgari bir belgenin **tam** derlemesi
  620-925 ms, yani vaat edilen "1-2 saniye" belgenin tamamından uzun.
- **`MeasurementDriftIT.heightOnThePage`'in `\pagetotal`'ı yalnız bulunulan
  sayfayı sayıyor** — iki sayfalık belgede sapması anlamsız, bilerek bırakıldı.

**Aşama 1-3'ten taşınan, hâlâ canlı:**

- Geçmişin `total`'ı satır değil **CV** sayıyor (`F-020`); yeniden koşu Faz
  C'den başlıyor, skorlar snapshot'tan.
- `SectionFloor` bir tavandır talep değil; `PARAGRAPH` `INLINE_LIST`'e
  katlanmadı; `suspicious_output` telde hiç görülmedi.
- **"Kritik uyarı" diye bir şey yok.** `ImportWarning.code` `String`, şeması
  enum; `OpenApiSchemaIT`'in okuduğu altı değer elle yazılı.
- **Hata kataloğu tablosunun `params` sütunu düzyazı kabul etmiyor** —
  `ErrorCatalogueSpecTest` birebir ayrıştırıyor.
- **`local` profilinde LLM sağlayıcısı yok**; `AccountDeletionIT` tablo
  listesini `information_schema`'dan okuyor — **ve bir nesne deposunu göremez**
  (yukarıdaki tuzak tel tam bunun için var).
- **Dev stub ölçümü yiyor:** çerezsiz istekle yazılmış hiçbir test kimlik
  davranışını ölçmüyor. Doğru kurgu **çözülmeyen bir çerez**.
- **`UserScopedRepository`'de `findAll` yok.** **Faz D sekize kadar eşzamanlı
  çağrı yapıyor**, havuz 10, işçi eşzamanlılığı 2 → havuz büyütülmeden işçi
  eşzamanlılığı artırılmamalı. Çeviri adımı altmışa kadar çıkıyor ama
  **işlem dışında** (yukarıya bak).
- **`accessedAt` (`B-078`):** tek kapsanmamış adım `SupportGrantLookup`.
- **Geliştiricide kalan:** VPS ve restore testi (§ 49.4); OAuth, Turnstile ve
  `B-083`'ün challenge'ı gerçek uca karşı denenmedi.

**Dersler:** *bir javadoc ne zaman çalıştığını söylüyorsa çağıranı da ara* ·
*doğru davranan kod, korunan değildir* · *flake demeden önce dalına bak* ·
**§ 51.7: bir muhafızın düştüğünü görmeden yazıldı sayma** · **yazılı bir
kolon, yazan bir kod demek değil** · **bir fixture eklemek bir terimi ölçülür
kılmaz — kesiştiğini ölç** · **bir şartname parçacığının çalıştığını varsayma,
motora sor** · **kendi eklediğin fan-out'un neyi tuttuğuna bak** ·
**`git checkout --` commit'siz işi de götürür** · **bir bloğun kendi çıkış
koşulunu yazması onu silmiyor — koşulu kontrol eden bir şey yoksa** ·
**hesaplanıp atılan bir değer, yayımlanmış sayılmaz** · **yarım bir ayar
(`wal_level` ama `archive_mode` yok) hiç ayar olmamasından daha görünmez** ·
**bir denetim kapanmış bir denetimi tekrarlamaya değer: ikincisi sekiz,
üçüncüsü üç boşluk daha buldu** · **tanımlanmış bir tip, var olan bir tip
değil — muhafızın gösterdiğini import etmeyi dene** · **çağıranı olmayan bir
arayüz metodu, olmayan bir soyutlamanın ilanıdır** · **bir aracın hook'u ile
`core.hooksPath` birbirini sessizce iptal eder: koşunun neye benzediğini
yazmamış olsaydık görülmezdi** · **bir adın repoda geçmesi kümenin tam olduğu
anlamına gelmiyor — kümeleri karşılaştır** · **kendine "tam" diyen bir bölüm
takip eden bir şey yoksa tam kalmıyor** · **bir profil bir niyetin adıdır, iki
niyetin değil** · **kendi eklediğin sahteyi, onu istemeyen hattı koşturmadan
yazdım sayma**.

---

## Kapanan adımların arşiv haritası

| Adım | İnşa kaydı | Kalıcı kararlar |
|---|---|---|
| 3.3 kimlik | `stage-3-identity.md` | § 40.4.1, § 40.5.1, § 40.6.1, § 46.5 |
| 3.4 çıkarım | `stage-3-ingestion.md` | § 31.3.1, § 31.4.1, § 31.5.1, § 31.6.1-2, § 43.1, § 53.1 |
| 3.5 çok dillilik | `stage-3-multilingual.md` | § 32.2.1, § 32.3.1 |
| 3.6 anonim | `stage-3-anonymous.md` | § 35.7.1, § 41.3.1-3, § 44.1.1, § 44.2, § 31.6.3, § 51.6.1 |
| 3.8 Faz D | `stage-3-faz-d.md` | § 21.1 notu, § 21.3.1, § 21.5.1-7.1, § 34.4.1 |
| 3.9 hukuki | — | § 57.4.1, § 48.4.1 |
| dilim 9-14 · `F-017`-`F-027` | `stage-3-handoff-answers.md`, `stage-3-slice-14.md` | — |
| kapanış sonrası A-M | `stage-3-post-closure-e2e.md`, `stage-3-post-closure-shape.md` | § 18.4, § 20, § 31.3.1, § 33.4.1, § 21.2, § 22.4.1 |
| kapanış denetimi + yuvarlanan özetler | `kapanis-denetimi.md`, `stage-3-closeout.md` | § 47, § 57.4, § 3.2, § 51.7, § 20.2 |
| denetim · üçüncü tur | `denetim-2026-09-16-ucuncu.md` | § 17.1, § 6, § 43.1, § 48.2, § 9.2, § 10.2 |
| denetim · birinci tur | `denetim-2026-09-15.md` | § 18.7.1, § 22.6.1, § 26.6, § 29.2, § 31.8.1, § 35.2.1, § 41.4, § 47.1, § 48.5, § 51.3, § 52.4, § 52.5, § 5.1 |
| Aşama 4 · Faz G | `stage-4-faz-g.md` | § 24.2, § 24.2.1 |
| Aşama 4 · sayfa garantisi | `stage-4-page-guarantee.md` | § 26.4, § 33.1 (sabitler) |
| Aşama 4 · e-postalar, açık kaynak | `stage-4-emails.md` | § 57.7 |
| Aşama 4 · `F-031`-`F-033` | `stage-4-handoff-answers.md` | § 24.2, § 24.2.1, § 35.3, § 35.8.1-2 |

Frontend aksiyonları: `B-055`-`B-108`.
