# İnşa Notları — Aktif

> Kural: bu dosya **200 satırı geçmez**. Aşama bitince `archive/`'a taşınır, boş başlanır.
> Kayıt tipleri: **Sapma** (doküman başka diyor) · **Ekleme** (doküman sessiz) · **Düzeltme** (doküman yanlış).
> Bir sapma kalıcıysa `spec/`'e işlenir ve buradan silinir.

**Aktif aşama:** Aşama 4 — Olgunlaşma. **Plan:** `spec/14-build-guide.md`
§ XI-A.7. Aşama 1-3 ve Aşama 4'ün kapanmış dilimleri `archive/`'da; harita
aşağıda.

---

## Denetim — spec'in tamamı koda karşı (2026-09-15)

Spec baştan sona okundu, kodla karşılaştırıldı, ve **açık kalan maddelerin
hepsi kapatıldı**. Kalıcı kararlar `spec/`'e işlendi ve buradan silindi
(§ 18.7.1, § 22.6.1, § 26.6, § 29.2, § 31.8.1, § 35.2.1, § 41.4, § 47.1,
§ 48.5, § 51.3, § 52.4, § 52.5, § 5.1). Aşağıdakiler kalıcı değil, **canlı**.

**İnenler — ilk tur:** arşivleme ucu, atom etiketleri, `/api/v1/warmup`,
commit'li `openapi.json` + iki CI işi, `emphasize`, HTML renderer,
`format=source`, GitHub içe aktarımı, CDS, Umami, `generation.pages.drift`,
golden `jobs/` + `content-formats/`.

**İnenler — ikinci tur:** § 21.8'in çeviri adımı, § 32.5'in pivotu, § 30.6'nın
`LISTEN/NOTIFY`'ı, § 48.3'ün beş metrik satırı, § 48.5'in replay'i ve formatı,
golden set'in etiketleri. Frontend'e `B-100`-`B-107`.

**En pahalı bulgu, ve kapandı:** `tags`/`atom_tags` tablolarına hiçbir şey
yazmıyordu — Faz B'nin etiket bileşeni ham skorun dörtte biri (§ 19.1) ve her
atom için yapısal olarak sıfırdı. Hiçbir test düşmez; bütün skorlar birlikte
düşer. Yazım ilk turda indi; golden fixture'lar ikinci turda etiket kazandı ve
**`PhaseDReachTest` yeniden ölçüldü: 0.0959 → 0.1259.**

> **Ve bir şey daha öğretti: etiket sözlüğü ilanınkiyle kesişmezse hiçbir şey
> ölçmez.** İlk deneme atomları yalnız kaba temalarla etiketledi (`backend`,
> `devops`) ve **hiçbir sayı binde bir oynamadı** — skorlayıcı etiketi ilanın
> kendi dağarcığına (alan + anahtar kelimeler + başlığın sözcükleri) karşı
> Jaccard'la ölçüyor, yani bir etiket ancak birebir o dizgelerden biriyse
> katkı yapıyor. Tire aynı meselenin öbür yüzü: profil `spring-boot` saklıyor,
> ilan "Spring Boot" yazıyor. Etiketler artık ilanın yazdığı gibi yazılıyor.
>
> **Terimin tavanı da yazıldı:** Jaccard'ın paydası birleşim, birleşim de
> ilanın tüm dağarcığı (bu ilanda 26 dizge). Dört etiketin dördü de tutsa
> 4/26. Terim canlı ama küçük, ve bu ölçünün seçiminin sonucu — fixture'ın
> değil.

**İkinci en pahalı, ve hiçbir lane'de görünmezdi:** nginx'in CSP'si Turnstile'ı
blokluyordu. `ContentSecurityPolicyTest` muhafız, ve `nginx.conf` artık bir
test girdisi — olmadan, ekilen kusur görevi UP-TO-DATE bırakıp testi başarı
raporlattı. **Aynı tuzağa üçüncü kez düşülmedi:** `docker/latex/Dockerfile` ve
`CompileServer.java` da girdi olarak ilan edildi (`LatexImageTest`).

**Kendi eklediğim kusur, ve kaydı burada duruyor:** § 21.8'in çeviri adımı
sanal iplikte altmışa kadar çağrı yapıyor ve `VariantTranslationService`
`@Transactional` idi — havuz on bağlantı, çağrı otuz saniye, yani elli iplik
henüz başlamamış bir çağrı için bağlantı bekler, hepsi düşer, ve hep-ya-hiç
kuralı **her çok dilli üretimi** geri düşürürdü. Yazma `TranslationWriter`'a
taşındı (**ayrı bean olmak zorunda**: bir sınıfın kendine yaptığı
`@Transactional` çağrısı proxy'lenmiyor, yani davranış aynı kalır görünüşü
değişirdi) ve bir ArchUnit kuralı tutuyor: `@Transactional` bir sınıf
`ProviderChain`'i çağıramaz.

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
  `.env.example`, `nginx.conf`, `docker-compose.prod.yml`, `openapi.json` ve
  `docker/latex/*` var.
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
**`git checkout --` commit'siz işi de götürür**.

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
| Aşama 4 · Faz G | `stage-4-faz-g.md` | § 24.2, § 24.2.1 |
| Aşama 4 · sayfa garantisi | `stage-4-page-guarantee.md` | § 26.4, § 33.1 (sabitler) |
| Aşama 4 · e-postalar, açık kaynak | `stage-4-emails.md` | § 57.7 |
| Aşama 4 · `F-031`-`F-033` | `stage-4-handoff-answers.md` | § 24.2, § 24.2.1, § 35.3, § 35.8.1-2 |

Frontend aksiyonları: `B-055`-`B-107`.
