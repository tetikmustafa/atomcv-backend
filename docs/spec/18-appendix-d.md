# Bölüm XIV — EK D: İnşa Notları

> AtomCV spec · [INDEX](../INDEX.md) · bu dosya yalnız aşağıdaki bölümü içerir.

> **Nereden geldi.** EK D, spec monolitten dosyalara bölünürken ayrılmadı ve
> `docs/_archive-monolith.md` içinde kaldı. Spec'in geri kalanı ona **75 kez**
> atıf yapıyor (`EK D.1`-`EK D.10`), `INDEX.md` onu rotalamıyordu ve
> `scripts/sync-spec.sh` yalnız `docs/spec/**` kopyaladığı için frontend'in
> spec kopyasındaki atıfların hepsi boşa düşüyordu. Taşındı; içerik aynı.

---

## EK D — İnşa Notları: Sapmalar, Eklemeler, Düzeltmeler

Kod yazılırken alınan ve dokümanın gövdesinde karşılığı olmayan kararlar
burada tutulur. Üç tür kayıt var:

- **Sapma** — doküman bir şey söylüyor, uygulama gerekçesiyle başkasını yapıyor.
- **Ekleme** — doküman sessiz kaldığı için karara bağlanmış ayrıntı.
- **Düzeltme** — dokümandaki ifade yanlış ya da eksik; doğrusu burada.

**Frontend'i ilgilendiren maddeler D.9'da toplanır**, frontend'in kendi inşa
kararları **D.10**'da; ikisi de sonda durur ve yeni konu başlıkları araya
girer. Doküman iki repoya da kopyalandığı için (XI-B.1.3) frontend tarafının
okuması gereken yerler bunlar ve D.7'deki ilerleme kaydıdır.

### D.1 — Aşama 0: iskelet

| Konu | Tür | Karar |
|---|---|---|
| Repo düzeni: XI-A.1 tek repo, XI-B.2 iki repo gösteriyor | Düzeltme | **XI-B.2 geçerli.** Bu repo yalnız backend, `src/` kökte. XI-A.2'nin `backend/` alt klasörü varsayan adımları buna uyarlanır. |
| İlk migration'ın kapsamı (XI-A.2 "identity + profile core", Bölüm 13 tek dosya) | Düzeltme | `V1__initial_schema.sql` **Bölüm 13'ün tamamını** içerir. Boş tablo maliyetsizdir; bölmek, uygulanmış migration'ı değiştirme yasağı altında aynı tabloları V2/V3'te tekrar açmak demekti. |
| Denormalize `profile_id` ile ebeveynin profili arasında hiçbir garanti yok | Ekleme | **Bileşik yabancı anahtar** (`UNIQUE (id, profile_id)` + `FOREIGN KEY (parent_id, profile_id)`). Uyuşmazlık aksi halde sessiz bir çapraz-kiracı sızıntısı olurdu. `atoms.entry_id IS NULL` durumunda uygulanmaz — bölüm seviyesindeki atomlar için kasıtlı. |
| `llm_invocations.user_id` FK'siz; Bölüm 13.1'in "tek DELETE her şeyi siler" sözüyle çelişiyor | Ekleme | `user_id` ve `job_id` için **`ON DELETE SET NULL`**. Toplam maliyet geçmişi hesap silinince yaşar, kişisel bağ yaşamaz. |
| Bölüm 51.6'nın anonim testi "hiçbir tabloda satır sayısı değişmez" diyor, ama kuyruk (`jobs.anon_session_id`) ve `llm_invocations` Postgres'te | Düzeltme | Test **kullanıcı verisi tablolarına** daralır. Anonim akışın kuyruğu hiç kullanıp kullanmayacağı Aşama 3'te karara bağlanacak. |
| ArchUnit kuralları, modül paketleri yalnız `package-info.java` taşırken "failed to check any classes" ile düşüyor | Ekleme | Geçici olarak `archunit.properties` içinde `archRule.failOnEmptyShould=false`. **Adım 1.1 sonunda kaldırıldı:** artık yalnız `renderersAreDeterministic` kuralı boş kümede çalışıyor ve izni tek başına taşıyor (`allowEmptyShould(true)`). Global ayar açıkken bir paket adı değişirse ilgili kural hiçbir şeyle eşleşmeyip sessizce geçerdi. |
| Bölüm 47.1'deki `--spring.flyway.migrate-only=true` | Düzeltme | **Böyle bir Spring Boot özelliği yok.** Üretimde migration'ı deploy öncesi çalıştırmanın yolu ayrıca kararlaştırılacak; şu an Flyway üretimde de uygulama açılışında çalışıyor. |
| OWASP dependency-check (Bölüm 47.1) | Sapma | Kullanılmıyor: NVD API anahtarı istiyor, anahtarsız taraması yavaş ve oran-sınırlı. Aynı kapsamı **Dependabot** derleme maliyeti olmadan veriyor. |
| Lombok (XI-A.2 Adım 0.1'in bağımlılık listesinde var, örnek `build.gradle.kts`'te yok) | Düzeltme | **Kullanılmıyor.** Değer nesneleri record, gerisi düz constructor. |
| Satır sonları ve dosya izinleri | Ekleme | `.gitattributes` (`* text=auto eol=lf`, `.bat`/`.cmd` için CRLF) ve `gradlew`'in 100755 kalması. Windows'ta geliştirilip Linux runner'da çalışan bir repo, bu ikisi olmadan sessizce kırılır: CRLF'li ya da 100644 modlu `gradlew` her CI koşusunu düşürür. |
| Entegrasyon testlerinin veritabanı | Ekleme | Tek bir Postgres container'ı, ortak bir taban sınıfta **statik başlatılıp hiç durdurulmuyor** (Testcontainers'ın singleton deseni). `@Testcontainers` + `@Container` ilk test sınıfından sonra container'ı durdurur, ama Spring'in önbelleklediği context hâlâ o portu gösterir ve sonraki her sınıf "connection refused" ile düşer. Ryuk, JVM kapanınca temizliyor. |
| Hibernate istatistikleri | Ekleme | Tüm entegrasyon suite'inde açık. Bölüm 52.2'nin altı sorgu bütçesi bununla ölçülüyor; özelliği tek bir sınıfın üstünde tutmak, o sınıf yeniden düzenlendiği anda sayacın sıfır okumasına ve **testin yine geçmesine** yol açtı. Alt sınır iddiası (`isBetween(4, 6)`) yakaladı. |
| Gradle dağıtımının doğrulanması | Ekleme | `gradle-wrapper.properties` içinde `distributionSha256Sum`, yayınlanan toplama karşı doğrulanmış. Wrapper, indirdiği arşivi aksi halde denetlemez. |
| Entegrasyon testlerinin yeri | Ekleme | Ayrı `integrationTest` source set'i, `check`'e **bağlanmadan**. `gradlew test` Docker'sız ve hızlı kalır; `gradlew build` Docker Desktop kapalıyken de çalışır. CI ikisini ayrı adım olarak çalıştırır. |
| Commit kancası (XI-A.1.4 elle `.git/hooks/pre-commit` yazıyor) | Sapma | **pre-commit framework** + `.pre-commit-config.yaml`. `.git/hooks/` versiyonlanmaz; elle yazılan kanca ikinci makinede yoktur ve kimse fark etmez. İlk üç commit kancasız geçtikten sonra fark edildi. |
| Formatlama kapısı (Bölüm 47.1 `spotlessCheck` çalıştırıyor) | Düzeltme | **Yapılandırılmış formatter yok**, dolayısıyla CI'da formatlama kapısı da yok. Spotless eklensin mi, açık karar. |
| CI tetikleyicileri | Ekleme | Yalnız `main` push'u ve `pull_request`. Bir dalı push etmek hattı çalıştırmaz — kontrolleri görmek için PR açmak gerekir. Test raporları `if: always()` ile yüklenir: sıfır test çalıştıran suite de "başarılı" der. |
| Kaynak dosya kodlaması | Ekleme | `options.encoding = "UTF-8"`. `javac` varsayılan olarak platform charset'ini kullanır — geliştirme makinesinde `Cp1254`, CI runner'da UTF-8. Türkçe karakter içeren bir dosya aksi halde iki ortamda **iki farklı string sabitine** derlenirdi. |

### D.2 — Aşama 1: içerik modeli (Bölüm 12, 14.1, 16.2)

| Konu | Tür | Karar |
|---|---|---|
| `Mark` tipi | Ekleme | Java'da **enum değil**, `String` saran bir record + beş sabit. Bölüm 16.2 ileri uyumluluk istiyor: daha yeni bir sürümün yazdığı bilinmeyen mark parse edilmeli ve geri yazımda kaybolmamalı. Enum bunu yapamaz. `isKnown()` bilinen sözlüğü ayırır, renderer bilinmeyeni düz metne düşürür. |
| `href` ve `link` ilişkisi | Ekleme | Yapısal kural: `link` mark'ı olan run'da `href` **zorunlu**, olmayan run'da **yasak**. Hiç render edilmeyecek bir `href`'in sessizce saklanmasını engeller. İhlal `IllegalArgumentException`. |
| `content_hash` biçimi | Ekleme | `sha256(plainText)`, **küçük harf hex**, UTF-8 baytları üzerinden. Boş içerik için `e3b0c442...b855`. Sabit vektörlerle teste bağlandı: saklanmış bir hash, buradaki her yeniden düzenlemeden uzun yaşar. |
| Daha yeni sürüm damgası okununca ne olur | Ekleme | **Hata verilir**, best-effort okunmaz (`IllegalStateException`). Anlaşılmayan bir alanı düşürüp kaydetmek satırı bozardı — P4. Bu, kademeli deploy sırasında eski sürümün yeni satırı okumasını kasıtlı olarak yasaklar. |
| Bozuk satır hataları | Ekleme | Mesaj **içerik taşımaz**: `"Run 1 has no text"` — indeks var, metin yok. Ayrı bir testle bağlandı (mutlak kural 4). |
| `toString()` | Ekleme | `RichContent`, `Run` ve tüm profil entity'lerinde ezildi; yalnız şekil basar (`RichContent[runs=2, chars=22]`). Bölüm 48.1'deki ArchUnit kuralı yalnız logger'a **parametre olarak** geçen içeriği yakalar; string birleştirmeyle sızmanın yapısal savunması budur. |
| `m` alanının zorunluluğu iki yönde farklıdır | Düzeltme | D.9 · 4 "`m` her zaman dizidir" diyor, şema ise `m`'i opsiyonel gösteriyor. İkisi de doğru, ama farklı yönler için: **sunucu her zaman yazar** — işaretsiz run bile `"m": []` taşır — **istemci ise atlayabilir**, atlanmışsa boş dizi sayılır. `Content` tek şema ile hem okuma hem yazma taşıdığı için OpenAPI bunu ayrı ayrı söyleyemiyor; şemayı zorunlu yapmak her yazana `[]` göndertirdi, ikiye bölmek iki tip demekti. Çelişki dokümanda kapanır, şemada değil (EK D.6.4). `v` aynı şekle sahip ve düzeltme gerektirmiyor: sunucuya ait, yazmada atlanır, açıklaması bunu zaten söylüyor. |

### D.3 — Aşama 1: entity katmanı (Bölüm 13)

| Konu | Tür | Karar |
|---|---|---|
| JPA ilişkileri | Ekleme | **Yok.** Ebeveyn bağı düz `UUID` kolonu. Profil dört düz sorgu + bellekte birleştirmeyle yükleniyor (XI-A.3); lazy bir koleksiyon altı sorgu bütçesini gürültüsüzce delerdi. |
| Kapalı sözlükler | Ekleme | `sections.kind/layout`, `atoms.kind/source`, `atom_variants.created_by/tone` Java enum'u. Şema küçük harf saklıyor, `EnumType.STRING` sabit adını olduğu gibi yazardı — ortak bir converter **`Locale.ROOT` ile** küçültüyor (mutlak kural 7: Türkçe locale'de `INLINE_LIST` → `ınline_list`). Bilinmeyen değer yüksek sesle patlar: sözlüğün sahibi migration'dır. |
| Kimlik üretimi | Ekleme | `UUID` **constructor'da** atanır, veritabanı `DEFAULT gen_random_uuid()` yalnız SQL tarafı için yedektir. Nesne grafiği flush'tan önce kurulabiliyor ve `equals` sabit bir şeye dayanıyor. |
| `version` kolonunun Java tipi | Ekleme | Sarmalayıcı `Long`. Spring Data `null` version'ı "yeni" okuyup `persist` eder; ilkel `long` olsaydı her kayıt `merge` olur, gereksiz bir SELECT eklerdi. |
| `plain_text` ve `content_hash` | Ekleme | Dışarıdan yazılamaz; `AtomVariant.setContent()` türetir. Hash değiştiyse **ölçülmüş `render_costs` ve `cost_measured_at` temizlenir** — Adım 1.5'teki geçersizleşme kuralı, içeriğin değişebildiği tek yere gömülü. Aynı cümlenin yeniden işaretlenmesi hash'i değiştirmediği için maliyet korunur. |
| `atoms.embedding` | Ekleme | **Eşlenmedi.** Aşama 2'ye kadar embedding hesaplayan bir şey yok ve `vector(1024)` için Hibernate tipi yok. Eşlenmemiş kolon `ddl-auto: validate`'i rahatsız etmez. |
| Mapping doğrulaması | Ekleme | `validate` açık olduğu için context'in açılması zaten bir iddia. Testte bir kolon adı kasten bozulup altı entegrasyon testinin de `SchemaManagementException` ile düştüğü doğrulandı. |

### D.4 — Aşama 1: yetkilendirme (Bölüm 41)

**Düzeltme — Bölüm 41.2'nin tek temel sınıfı yetmiyor.** `UserScopedRepository`
her satırı `ownerId()` ile eliyor, ama `sections`, `entries`, `atoms` ve
`atom_variants` tablolarında `user_id` **yok**; yalnız `profile_id` var. Bu dört
entity, bir join olmadan "sahibi kim" sorusuna cevap veremez.

**Karar: iki temel sınıf.**

```
UserScopedRepository<T extends UserOwned>       → user_id taşıyan tablolar
ProfileScopedRepository<T extends ProfileOwned> → profile'a asılı dört tablo
```

Sahiplik kontrolü **bir kez**, `ProfileRef` çözülürken yapılır:

```java
ProfileRef.persistent(user, profileId, profileOwnerId)   // ikisini karşılaştırır
```

- Constructor **private**; tip bilerek **record değil** — record'un canonical
  constructor'ı record'un kendisinden daha kısıtlı olamaz, yani `public record`
  denetimsiz bir üretim yolu dağıtırdı.
- Bir controller, path parametresinden `ProfileRef` uyduramaz: profilin gerçek
  sahibini bilmesi gerekir, o da zaten kontrolün kendisidir.
- `ProfileRef` alan bir repository hiçbir şeyi yeniden kontrol etmez.
- Bir şekil testi, `ProfileRef` dönen her public static metodun `UserContext`
  aldığını doğrular; ileride eklenen bir "kolaylık factory'si" garantiyi sessizce
  kaldıramaz.

**Reddedilen iki alternatif:** alt tablolara `user_id` eklemek (tutarlı tutulması
gereken ikinci bir denormalizasyon), ve her okumaya
`profile_id IN (SELECT ... WHERE user_id = ?)` alt sorgusu koymak (ölçüm ve
seçim yollarında sıcak).

**Ekleme — yabancı satırın davranışı.** Okuma `Optional.empty()` döner, yazma
`CrossTenantAccessException` fırlatır. Yasak dönmek satırın varlığını doğrulardı;
yazma denemesi ise kodun yanlış sahiple nesne kurduğu anlamına gelir — kibarca
cevaplanacak bir istek değil, hatadır.

**Ekleme — admin'in ekstra erişimi yoktur.** Bölüm 41.4 destek erişimini role
değil `support_grant`'e bağlıyor; bu iki temel sınıfta rol hiç okunmaz.

**Ekleme — `Scope.EPHEMERAL` henüz yok.** Bölüm 41.3 iki kapsam tanımlıyor, ama
denetimli bir üretim yolu olmadan eklenen ikinci sabit, kontrolü atlamanın yolu
olurdu. Anonim akışla birlikte Aşama 3'te gelir.

**Ekleme — ArchUnit.** Bölüm 51.4'teki `..api..` kuralı `..service..`'i de
kapsayacak şekilde genişletildi (mutlak kural 3 ikisini de söylüyor). Ayrıca:
`..profile..` içinde `..profile.repository..` dışındaki hiçbir sınıf Spring Data
`Repository`'ye bağımlı olamaz. Kural modül başına yazılır — Bölüm 30'daki kuyruk
kendi paket düzenini taşıyor, şimdiden bağlanmadı.

### D.5 — Aşama 1: profil yükleme (Bölüm 52.2)

| Konu | Tür | Karar |
|---|---|---|
| `assemble()` imzası | Sapma | Bölüm 52.2'deki `assemble(sections, entries, atoms, variants)` yerine **`assemble(profileId, ...)`**. Dört ayrı sorgu, yanlış kapsamı geçirmek için dört fırsat demek; fonksiyon her satırın `profile_id`'sini verilen profile karşı doğruluyor. Karışmış bir sonuç, render hatası gibi görünen bir çapraz-kiracı sızıntısı olurdu. İhlal `CrossTenantAccessException`. |
| Yükleme çıktısının tipi | Ekleme | `ProfileTree` (`SectionNode` / `EntryNode` / `AtomNode`). Entity'lerde ilişki olmadığı için ağaç yalnız burada var; "profil nasıl yüklenir" tek yerde tek karar kalıyor. |
| Repository katmanı | Ekleme | Her tablo için **paket-özel** bir Spring Data arayüzü + `ProfileScopedRepository` türeten **public** bir cephe. Arayüz paketin dışına çıkamadığı için kapsamsız çağrı derlenmiyor; ArchUnit kuralı da aynı şeyi bağımsızca bekliyor. |
| Sıralama | Ekleme | Sorgular `display_order` **ve `id`** ile sıralıyor. Aynı sıra numarasını taşıyan iki satır aksi halde her çalıştırmada farklı gelebilir; determinizm testi girdisi belirsizse tutmaz. Varyantlar: önce birincil, sonra dil, sonra ton, sonra id. |
| Kopuk referans | Ekleme | Bir atom bulunmayan bir entry'yi ya da entry'siyle çelişen bir bölümü gösteriyorsa **hata verilir**, satır sessizce düşürülmez (P4). |
| ≤6 sorgu testi | Ekleme | Hibernate `Statistics.getPrepareStatementCount()` ile ölçülüyor ve **alt sınır da iddia ediliyor** (`isBetween(4, 6)`): istatistik kapalı kalıp sıfır dönseydi test ölçmeden geçerdi. Ayrıca profil büyütülüp sorgu sayısının değişmediği ayrıca doğrulanıyor. |

### D.6 — API sözleşmesi (Bölüm 35)

Frontend, Aşama 0'ın sonunda on altı sözleşme boşluğu çıkardı: dokümanın
adlandırmadığı enum'lar, tanımlamadığı başlıklar, örneklemediği yanıt şekilleri.
Sorular ve cevaplar iki ayrı dosyada duruyordu (`BACKEND-CONTRACT-GAPS.md`,
`docs/backend-contract-response.md`); ikisi de buraya taşınıp silindi. **Tek
kaynak burasıdır.**

Aşağıdaki kararların altısı ilk endpoint'ten önce, springdoc şeması yazılırken
uygulanır; gerisi ait olduğu aşamada. **Otorite yayınlanan OpenAPI şemasıdır**,
buradaki düzyazı değil — bu yüzden enum'lar ve başlıklar şemaya girer, yalnız
mutlu yol gövdelerine değil.

#### D.6.1 — Kapalı sözlükler

**`resolutions[].action`** — Bölüm 35.4 üçünü, 35.5 bir tanesini adlandırıyor,
Bölüm 11.5 ve 11.8 ikisini düzyazıyla anlatıp adlandırmıyor. Tam küme:

| action | Anlamı | İstemci davranışı |
|---|---|---|
| `increase_page_limit` | `maxPages`'i `params.maxPages`'e yükselt | Yeni seçenekle yeniden gönder |
| `review_pins` | Sabitlenmiş içerik incelemesini aç | Profile git, sabitlere filtrele |
| `keep_top_pinned` | En iyi `params.keep` sabiti tut | Daraltılmış kümeyle yeniden gönder |
| `sign_up` | Özellik hesap gerektiriyor | Kayda git, durumu koru |
| `paste_full_posting` | İlan metni yetersizdi | İlan alanına odaklan |
| `continue_as_general_cv` | İlansız devam | Boş `jobDescription` ile yeniden gönder |
| `switch_to_manual_form` | Çıkarım başarısız | Manuel profil formuna git |
| `complete_profile` | Üretecek kadar profil yok | Profil düzenleyiciyi aç (Adım 1.8'de eklendi; Bölüm 25.3 bu adı kullanıyordu, sözlükte yoktu) |
| `retry` | Geçici hata | Değiştirmeden yeniden gönder |

**Frontend kendi resolution'ını uydurmaz.** Listeyi sunucu sahiplenir; istemci
yalnız render eder ve isterse resolution satırının dışına düz bir "kapat"
kontrolü koyar.

**Hata kodları — tam katalog.** Bölüm 35.5 on pipeline hatasını sayıyor,
Bölüm 31.10'daki ingestion durumları düzyazıyla anlatılıp kodsuz bırakılmış.
Her kodun `params` anahtarları **ve tipleri** burada: ICU mesajı bunlarsız
yazılamaz, çünkü `{pinnedPages, number}` biçimlendirir, `{pinnedPages}`
yalnızca yerine koyar.

| Kod | HTTP | `params` |
|---|---|---|
| `INSUFFICIENT_PROFILE` | 422 | `completeness: integer`, `missing: string[]` |
| `UNPARSEABLE_JOB_DESCRIPTION` | 422 | `confidence: number`, `skillsFound: integer` |
| `CONFLICTING_PREFERENCES` | 409 | `pinnedPages: number`, `maxPages: integer` |
| `FEATURE_REQUIRES_ACCOUNT` | 403 | `feature: string` |
| `QUOTA_EXCEEDED` | 429 | `metric: string`, `resetsAt: timestamp` |
| `ALL_PROVIDERS_UNAVAILABLE` | 503 | `tried: string[]` |
| `COMPILATION_FAILED` | 502 | `detail: string`, `rawSourceAvailable: boolean` |
| `PAGE_LIMIT_EXCEEDED` | 422 | `actual: integer`, `limit: integer` |
| `REWRITE_VALIDATION_FAILED` | 500 | `atomId: uuid`, `issues: string[]` |
| `EMBEDDING_UNAVAILABLE` | 503 | — |
| `PDF_NOT_TEXT_BASED` | 422 | — |
| `PDF_ENCRYPTED` | 422 | — |
| `EXTRACTION_EMPTY` | 422 | — |
| `EXTRACTION_TIMEOUT` | 504 | — |
| `LANGUAGE_UNDETECTED` | 422 | `detectedCandidates: string[]` |
| `PROFILE_QUOTA_EXCEEDED` | 429 | `limit: integer`, `resetsAt: timestamp` |
| `ANONYMOUS_SESSION_EXPIRED` | 401 | — |
| `ATOM_LIMIT_EXCEEDED` | 422 | `limit: integer`, `current: integer` |
| `NO_ANONYMOUS_PROFILE` | 404 | — |
| `PROFILE_ALREADY_EXISTS` | 409 | — |
| `GENERATION_ARTIFACT_EXPIRED` | 410 | — |
| `CSRF_TOKEN_INVALID` | 403 | — |
| `RESOURCE_NOT_FOUND` | 404 | — |
| `VERSION_CONFLICT` | 412 | — |
| `PRECONDITION_REQUIRED` | 428 | — |
| `VALIDATION_FAILED` | 400 | `fields: string[]` |
| `INTERNAL_ERROR` | 500 | — |

**Adım 1.2'de eklenen dört kod.** CRUD'un ihtiyacı olan ve dokümanın hiç
adlandırmadığı durumlar: bulunamayan kaynak, `If-Match` uyuşmazlığı (Bölüm 35.6
durumu veriyor, kodu vermiyor), girdi doğrulama, ve beklenmeyen hata için bir
son çare. `RESOURCE_NOT_FOUND` ile `VERSION_CONFLICT` **parametresizdir**: hangi
kaynağın kastedildiğini istemci zaten bilir (isteği o attı), ve advice
katmanının elinde o bilgi olmadığı için tek alternatif uydurmaktı.

**`EXTRACTION_TIMEOUT` için 504 seçildi**; doküman bir durum vermiyordu.

**Katalog kodda zorlanıyor, yalnız belgelenmiyor.** `params`, hata nesnesi
kurulurken bildirime karşı doğrulanır: eksik anahtar, fazladan anahtar ve yanlış
tip kurulumda patlar. Eksik bir parametre küçük bir kusur değildir — frontend'in
ICU mesajı onu yerine koyar ve kullanıcı "Sabitlediğin içerik {pinnedPages}
sayfa tutuyor" okur. P4'ün önlemek için var olduğu şey tam olarak budur ve
burada patlaması, ekran görüntüsünde keşfedilmesinden ucuzdur.

**`params` asla kullanıcı içeriği taşımaz** (mutlak kural 4): sayı, sınır,
tanımlayıcı ve alan adı taşır — sorunun şeklini, ona sebep olan metni değil.

#### D.6.2 — Hata gövdesi, ETag, sayfalama (Aşama 1)

| Konu | Tür | Karar |
|---|---|---|
| Hata gövdesindeki `title` (Bölüm 35.4'ün Türkçe örneği yanıltıyor) | Düzeltme | **Geliştiriciye yöneliktir, sabit İngilizcedir, kullanıcıya hiç gösterilmez.** RFC 7807 `title`'ın oluşumlar arası sabit olmasını ister; Bölüm 35.4'ün kendi kuralı da sunucunun metin değil çeviri anahtarı gönderdiğini söylüyor. Frontend'in `title`'ı yalnız log'a yazması doğru davranıştır. |
| ETag kapsamı | Ekleme | Yalnız V1'in `version` kolonu verdiği altı tablo: `profiles`, `sections`, `entries`, `atoms`, `atom_variants`, `applications`. **`generations`'ın `version`'ı yok**, dolayısıyla üretim kaynakları ETag ve `If-Match` taşımaz. Sonuç ekranı iyimser kilit isterse bu bir şema değişikliğidir, geç fark edilen bir eksik değil. |
| ETag biçimi | Ekleme | Tekil kaynak GET'inde `ETag: "7"`; koleksiyon yanıtlarında **her öğede `version` alanı**. Editör N sürümü öğrenmek için N istek atmak zorunda kalmaz. |
| Atom bazlı GET | Ekleme | **Yok.** Editör zaten tüm profili yüklüyor ve koleksiyon her öğenin `version`'ını taşıyor; alan bazlı PATCH için gereken her şey elde. `GET /profile/atoms/{id}` somut bir çağıran çıkınca eklenir — ilk aday Bölüm 37.5'teki bayatlama akışı. |
| Sayfalama | Ekleme | `GET /profile/atoms` **sayfalanmaz**. `/generations` ve `/applications` Aşama 2'de gelirken cursor tabanlı: `{ items, nextCursor }`. Offset sayfalama, üstten büyüyen listelerde satır atlar. |

**Gövdeyi üreten katman (Adım 1.2).** `ProblemDetailAdvice`, her hatayı aynı
şekle çeviriyor:

| Konu | Tür | Karar |
|---|---|---|
| `type` alanı | Sapma | **Göreli**: `/errors/conflicting-preferences`. Bölüm 35.4'ün örneği üretim alan adını kullanıyor, ama ürün dokümanı ne ismin ne alan adının koda gömülmesine izin veriyor (EK C.5) — RFC 7807 göreli referansa izin verir. |
| `title` alanı | Ekleme | Koddan **türetilir** (`CONFLICTING_PREFERENCES` → "Conflicting preferences"), ayrı bir listede tutulmaz. RFC 7807 başlığın oluşumlar arası sabit olmasını ister; bakımı ayrı bir liste, kayan bir listedir. |
| Yanıt durumu | Ekleme | Handler'lar `ResponseEntity` döner. Çıplak bir `ProblemDetail` dönmek yanıtın durumunu belirlemiyor — gövde 409 derken yanıt 500 gidiyordu. |
| Bilinmeyen yol | Ekleme | `NoResourceFoundException` → **404 `RESOURCE_NOT_FOUND`**, son çareye düşmez. Eski bir yer imi ya da bir tarayıcı botu, 500 üretip log'u yığınla dolduracak kadar sıradan. |
| Çapraz kiracı yazma denemesi | Ekleme | **500 `INTERNAL_ERROR`** + kimliksiz bir log satırı. 403 dönmek satırın varlığını doğrulardı; 404 dönmek de yanlış olurdu, çünkü okumalar zaten boş dönüyor — buraya ulaşan bir istek meşru bir istemciden gelemez, koddaki bir kusurdur. |
| Doğrulama hatası | Ekleme | Yalnız **alan adları** yayınlanır, reddedilen değer değil: değer kullanıcı içeriğidir ve log'lanan, ekran görüntüsü alınan bir gövdede yeri yoktur (mutlak kural 4). |
| `params` sıralaması | Düzeltme | `Map.copyOf` **kullanılmaz**. JDK'nın değişmez map'leri her JVM çalışmasında farklı tuzlanan bir sırayla dolaşılır; aynı hata iki koşuda farklı serileşiyordu. `LinkedHashMap` ile ekleme sırası korunuyor. |

**Yazma işlemleri (Adım 1.2).**

| Konu | Tür | Karar |
|---|---|---|
| `If-Match` zorunlu | Ekleme | Bölüm 35.6 başlığı gösteriyor ama zorunlu olduğunu söylemiyor. **Zorunlu.** Önkoşulsuz bir yazma, P8'in yasakladığı şeyin ta kendisi: iki sekme açık, ikinci kayıt kazanıyor, ilk düzenleme kimseye söylenmeden gidiyor. İstemcide sürüm zaten var (tekilde ETag, koleksiyonda öğe başına `version`), yani istemek bedava. Başlık yoksa **428 `PRECONDITION_REQUIRED`**. |
| Önkoşulun kontrol yeri | Ekleme | Yazan transaction'ın **içinde**. Kontrolle kayıt arasına bir şey giremiyor; girerse de `version` kolonu yakalıyor. |
| Zayıf etiket | Ekleme | `W/"7"` kabul edilir — bir vekil sunucu etiketi yolda zayıflatabilir, satırı tanımlayan içindeki sürümdür. Tırnaksız `7` kabul edilmez. |
| `PUT /profile` semantiği | Ekleme | **Değiştirir, yamalamaz**: gönderilmeyen alan temizlenir. `preferences` bu gövdenin parçası değil — başlığını düzenleyen biri, unutarak yazım tercihlerini sıfırlamasın diye. |
| `PATCH /profile/preferences` yerine `PUT` | Sapma | Bölüm 35.2 `PATCH` diyor. Uygulanan **`PUT /api/v1/profile/preferences`**: tercihler bir ayar formudur, istemci her zaman tüm nesneyi taşır, ve nested bir merge-patch'in belirsizliğini (bir alanı silmekle göndermemek arasındaki fark) taşımaya değmez. |
| Ayrıştırılamayan gövde | Ekleme | `HttpMessageNotReadableException` → **400 `VALIDATION_FAILED`**, alan adıyla. Bu olmadan bozuk bir JSON ya da record constructor'ının reddettiği bir değer son çareye düşüp 500 dönerdi. |
| Doğrulama sınırları | Ekleme | Uzunluklar API katmanında (`headline` 200, `selfDescription` 4000, `customInstructions` 1000, `maxPages` 1-10). Kolonlar `TEXT` kalıyor — Türkçe bir başlık İngilizcesinden uzun ve kimse sınırı cümlenin ortasında keşfetmemeli — ama sınırsız alan, sınırsız satır, sınırsız render ve sınırsız prompt demek. |

**Koleksiyon kaynakları — bölümler (Adım 1.2).**

| Konu | Tür | Karar |
|---|---|---|
| `PATCH` yalnız adlandırılanı değiştirir | Ekleme | Bölüm 35.6'nın kuralı. Bölümlerin **her kolonu `NOT NULL`**, yani "gönderilmedi" ile "null yapıldı" ayrımına burada hiç gerek yok. Entry'lerde tarih ya da kurum meşru biçimde temizlenebildiği için orada bir null kontrolünden fazlası gerekecek. |
| `displayOrder` yamalanamaz | Ekleme | Bir bölümü taşımak komşularını da numaralandırır; bu tek satırdaki bir alan değil, listenin tamamı üzerinde bir işlemdir. `POST /sections/reorder` yapar. |
| Sıralama isteği **tam liste** ister | Ekleme | Eksik liste, geri kalanın yerini sunucunun tahmin etmesi demek; iki istemci farklı tahmin ederse iki satır aynı pozisyonu iddia eder. Tam liste ayrıca çağrıyı idempotent yapıyor. |
| Sıralamada `If-Match` yok | Ekleme | İstek zaten çağıranın sıraya dair **tüm görüşünü** taşıyor; "bunları şu sıraya koy" demenin anlamı budur. Bayat bir sıralama pozisyon kaybettirir, içerik değil. |
| Oluşturma sona ekler | Ekleme | Yeni bölümün nereye ait olduğu listenin tamamına dair bir karar; istemci bunu reorder ile verir, başka bir sekmenin çoktan aldığı bir indeksi tahmin ederek değil. `201` + `ETag`. |
| Silme `If-Match` ister ve **cascade eder** | Ekleme | Bölümle birlikte entry'leri, atomları ve varyantları gider (veritabanı cascade'i). Yumuşatılmadı: açık bir silme kullanıcının kararıdır, sonucu gizlemek asıl sürpriz olurdu. |
| Bölüm yanıtında `version` **alanı var** | Sapma | Profil başında yoktu (D.6.2). Bölümler hem tek başına hem koleksiyon içinde dönüyor; alanın hangi endpoint'in döndürdüğüne göre kaybolması, `ETag`'in yanında küçük bir tekrardan daha kötü olurdu. |
| Sözlükler artık JSON'da da küçük harf | Düzeltme | `SectionKind`, `SectionLayout`, `AtomKind`, `AtomSource`, `VariantAuthor` yalnız JPA converter'ı taşıyordu; API gövdesinde `EXPERIENCE` gidiyordu. Hepsine `@JsonValue`/`@JsonCreator` eklendi (D.9 · 6'nın sözü). |

**Koleksiyon kaynakları — entry'ler (Adım 1.2).**

| Konu | Tür | Karar |
|---|---|---|
| `GET /profile/entries` | Ekleme | Bölüm 35.2 entry'ler için **hiç `GET` listelemiyor**. Onsuz editör bir deneyim listesini render edemez. Eklendi, isteğe bağlı `?sectionId=` süzgeciyle. |
| `POST /profile/entries/reorder` | Ekleme | Aynı boşluk sıralamada da vardı. İstek **bir bölüme** kapsanır (`sectionId` + o bölümün tam id listesi); iki bölüme yayılan bir liste, sıralama kılığında bir taşıma olurdu. |
| "Dokunma" ile "temizle" ayrımı | Ekleme | Entry'nin kolonları nullable: iş sürerken bitiş tarihi yoktur, yanlış yazılmış bir kurum boşaltılabilmelidir. Java'nın üç durumlu bir `Optional`'ı yok — Jackson **eksik** bir `Optional` alanını da `Optional.empty()` okur, yani açık `null`'dan ayırt edilemez. `JsonNullable` (`jackson-databind-nullable`) bu ayrımı taşıyor: tanımsız → dokunma, tanımlı-null → temizle, tanımlı-değer → ata. |
| Şemada sarmalayıcı görünmüyor | Ekleme | Üç durum **Java'nın meselesi**, sözleşmenin değil: telde alan yalnızca null olabilen bir değerdir. `@Schema(implementation = …, nullable = true)` ile öyle yayınlanıyor; aksi hâlde üretilen istemci doldurulacak bir `{ present, value }` nesnesiyle kalırdı. Bir test bunu sabitliyor. |
| Entry `PATCH`'inde `sectionId` yok | Ekleme | Bir entry'yi başka bölüme taşımak iki listeyi birden numaralandırır; bu bir alan düzenlemesi değil, sıralama işlemidir. Taşıma ucu gerektiğinde ayrıca eklenecek. |
| Başka profilin bölümüne entry | Ekleme | `sectionId` kapsamlı repository üzerinden çözülüyor, yani başkasının bölüm id'sini göndermek 400 `VALIDATION_FAILED` verir — satırın varlığını doğrulamayan bir cevap. |

**Koleksiyon kaynakları — atomlar ve varyantlar (Adım 1.2).**

| Konu | Tür | Karar |
|---|---|---|
| Atom **içeriğiyle birlikte** yaratılır | Ekleme | `POST /atoms` içerik ister ve birincil varyantı aynı transaction'da yazar. Varyantsız bir atom, kimsenin okuyamadığı bir olgudur: renderer basacak, ölçüm ölçecek bir şey bulamaz. O durumun hiç var olmaması, sonradan temizlenmesinden ucuz. |
| Kontroller ve metin ayrı uçlarda | Ekleme | Bölüm 35.2 `PATCH /atoms/{id}` için zaten "kontroller" diyor. Metin varyantın; ikisi ayrı satır, ayrı sürüm. Cümleyi atom üzerinden düzenlemek, iki satırın tek bir önkoşulu paylaşması olurdu. |
| Varyant `PATCH`'i içeriğin **tamamını** alır | Ekleme | Cümle run run değil, cümle olarak düzenlenir. Sunucu düz metni ve hash'i tek yetkili değerden türetiyor; hash değişince ölçülmüş maliyetler de düşüyor (EK D.3). |
| Dil+ton çakışması | Ekleme | `(atom, language, tone)` tekil indeksi var. İkinci bir aynı çift, kısıt ihlalinin 500 olarak yüzeye çıkması yerine **400 `VALIDATION_FAILED`** ile reddediliyor. |
| Birincil varyant terfisi | Ekleme | Atom başına tek birincil (kısmi tekil indeks). Terfi, eskisini **ayrı bir toplu güncellemeyle** düşürüyor: iki yazımı persistence context'e bırakmak, Hibernate'in sırayı ters kurup indekse takılmasına açık kapı bırakırdı. |
| Son varyant ve birincil silinemez | Ekleme | Bir atom bir varyantını korumak zorunda, ve aralarında bir varsayılan. İkisi de 400 döner; istemcinin yapacağı bir şey kalır (başkasını terfi ettir, ya da atomu sil), okunamaz bir atom kalmaz. |
| İçerik kuralları istemcinin hatasıdır | Düzeltme | `href`siz `link` run'ı ya da gelecekten bir `v` damgası, model constructor'ında `IllegalArgumentException` üretiyordu ve son çareye düşüp **500** dönüyordu. İstek gövdesinden gelen bir ihlal istemcinin hatasıdır: artık 400 `VALIDATION_FAILED` (`fields: ["content"]`). |
| Varyantların yüklenmesi | Ekleme | Liste ucu tüm varyantları **tek sorguda** çekip atoma göre grupluyor; atom başına sorgu, Bölüm 52.2'nin yasakladığı desenin ta kendisi olurdu. |

**Tamamlanma ve profil silme (Adım 1.2).**

| Konu | Tür | Karar |
|---|---|---|
| Bölüm 31.9'un tanımsız yüklemleri | Ekleme | Formül ağırlıkları veriyor, yüklemleri metot adından okumaya bırakıyor. Karara bağlananlar: **iletişim** = ad **ve** e-posta (CV başlığı bu ikisi olmadan render edilemez; telefon iyidir ama üretimi engellemez), **beceri sayısı** = `kind = skill` atomları (nerede asılı oldukları değil, ne oldukları), **metrikli atom** = `metrics` dizisi boş olmayan atom. |
| Ne zaman hesaplanır | Ekleme | **Okumada**, her yazımda değil. Formül profilin tamamını sayıyor; yazımda güncellemek her bölüm, entry ve atom ucuna profilin tamamını yükleme maliyeti bindirirdi. Sayı okunduğu yerde hesaplanıyor, ve `profiles.completeness` kolonu **yalnız değiştiğinde** yazılıyor — o kolon Aşama 2'deki ön kontrol kapısı için var (Bölüm 25.5). |
| Doküman eşiği | Düzeltme | Bölüm 31.9 "iletişim + (1 eğitim VEYA 1 deneyim) + 3 beceri ≈ %45" diyor. Hesap: **eğitimle 38**, **deneyimle 48** — dokümanın tahmini ikisinin arasında. Test ikisini de sabitliyor. |
| `DELETE /profile` | Ekleme | Profil ve altındaki her şey gider, **hesap kalır**: profili olmayan bir kullanıcı, henüz başlamamış bir kullanıcıdır ve sonraki okuma ona boş bir profil verir. `If-Match` zorunlu — geri alınamayan tek çağrı. |

#### D.6.3 — İndirme ve dışa aktarma (Aşama 1)

- Baytlar doğrudan API'den, `Content-Disposition: attachment` ile. Dosya adı,
  biliniyorsa şirket ve pozisyonu taşır.
- 14 günlük saklama dolduğunda `410 Gone` + `GENERATION_ARTIFACT_EXPIRED` +
  `retry` resolution'ı. Bunu vermek ucuz: `generations.selection_state`
  `pdf_expires_at`'ten bağımsız kalıcı bir anlık görüntüdür, yani PDF her zaman
  yeniden üretilebilir — süre dolması kullanıcıya emeğine mal olmaz.
- `GET /profile/export` biçimi `?format=json|markdown` ile seçer; indirme
  endpoint'iyle aynı desen. Bilinmeyen biçim 400 `VALIDATION_FAILED`
  (`fields: ["format"]`).

| Konu | Tür | Karar |
|---|---|---|
| JSON dışa aktarımın şekli | Ekleme | **İç içe** (bölüm → entry → atom → varyant), düzenleme uçlarının aksine. Bir export ya bir insan tarafından okunur ya bütün olarak geri beslenir; ikisi de yapıyı görmek ister. Öğe şekilleri API'nin **zaten yayınladığı** şekillerdir, yani export'tan çıkan şey şemada tarif edilmiş olan şeydir. |
| Markdown, CV render'ı değildir | Ekleme | Sayfa bütçesi, şablon ve ölçüm yok — bu veri kopyasıdır, ve profil hiçbir CV'ye sığmayacak kadar uzun olsa da okunabilir kalır. Bu yüzden `rendering` modülünde değil, `profile` içinde. |
| Mark'lar Markdown'a çevrilmez | Ekleme | Atom metni **düz metin** olarak yazılır. Mark'lar semantiktir; onları yıldıza çevirmek, verinin bilerek taşımadığı bir sunum uydurmak olurdu (P1). |
| Markdown kaçışı | Ekleme | Yalnız **satır içinde** anlam değiştiren karakterler kaçırılır (`` \ ` * _ [ ] < > | ``). `.` `-` `#` `+` yalnız satır başında anlamlıdır ve her satırın başını bu kod yazıyor; hepsini kaçırmak `name@example\.com` gibi, insanların okuduğu bir dosyayı ters bölü çöplüğüne çevirirdi. |
| Yanıt karakter kümesi | Düzeltme | `text/markdown;charset=UTF-8`. Charset belirtilmezse istemci ISO-8859-1'e düşüyor ve "İstanbul" bozuk geliyor — test bunu yakaladı. |
| Dosya adı | Ekleme | `atomcv-profile-<tarih>.md`. İsim konmuyor: indirme klasörlerine, vekil sunucu loglarına ve ekran görüntülerine kişisel veri taşımanın karşılığı yok (mutlak kural 4). |

#### D.6.4 — İş durumu ve SSE (Aşama 2)

Her SSE olayı bir `id` taşır ve yeniden bağlanmada `Last-Event-ID` onurlandırılır
(o noktadan itibaren tekrar oynatma, en azından güncel durumu yeniden gönderme).
Bunsuz ilerleme ekranının tek bir hata modu olur: iş çoktan bitmişken spinner
sonsuza kadar döner — P4'ün yasakladığı sessiz kötü sonuç.

```json
// GET /api/v1/jobs/{id}
{
  "jobId": "...",
  "status": "queued | running | completed | failed",
  "phase": "C",
  "pct": 60,
  "generationId": "...",
  "error": { "code": "...", "params": {}, "resolutions": [] }
}
```

`generationId` yalnız `completed`'da, `error` yalnız `failed`'da bulunur; bu
ikisi terminal durumlardır. Akış terminal olay olmadan kapanırsa bu endpoint'i
yoklamak (polling) kabul edilebilir bir geri düşüştür.

#### D.6.5 — Idempotency ve kota (Aşama 2)

`Idempotency-Key`, para harcayan veya iş başlatan her POST'ta onurlandırılır:
`/generations`, `/generations/{id}/edits`,
`/generations/{id}/cover-letter/regenerate`, `/ingestion/cv`. Anahtarlar 24 saat
saklanır.

> **Kayda geçirilmiş kusur.** V1'deki
> `CREATE UNIQUE INDEX ON jobs (user_id, idempotency_key) WHERE idempotency_key IS NOT NULL`
> anonim istekleri tekilleştirmez: orada `user_id` NULL'dır ve Postgres NULL'ları
> birbirinden farklı sayar, yani aynı anahtar ikinci bir iş açar.
> `COALESCE(user_id::text, anon_session_id)` üzerinden bir migration gerekir.
> Hemen düzeltilmedi, çünkü anonim akışın kuyruğu kullanıp kullanmayacağı hâlâ
> açık.

Kota: `429` ile birlikte `Retry-After` başlığı ve `params` içinde `resetsAt`.
Sayaçlar (`generationsUsedToday`, `dailyGenerationQuota`, `quotaResetsAt`)
`capabilities` içinde de yayınlanır — sınır, çarpılmadan önce görünür olur.

> **Açık:** `usage_counters.period` bir `DATE`; günlük sayaç hiçbir yerde
> tanımlanmayan bir gün sınırında dönüyor. UTC mi, Europe/Istanbul mu?
> `resetsAt` gönderilmeden önce cevaplanmalı ve kullanıcıya görünür: UTC dönüşü
> Türkiye'de saat 03:00'e denk gelir.

#### D.6.6 — Anonim oturum, CSRF, profil devralma (Aşama 3)

| Konu | Karar |
|---|---|
| Anonim oturum çerezi | Hesaplı oturumla **aynı `sid` çerezi**. Kimlik doğrulama, istemci tarafında bir `capabilities` sorusu olarak kalır. |
| Süre bilgisi | `capabilities` içinde `anonymousExpiresAt` (ISO 8601). |
| Süre dolduğunda | `401` + `ANONYMOUS_SESSION_EXPIRED` + `sign_up` resolution'ı. |
| TTL davranışı (Bölüm 9 "2 saat sonra silinir" diyor) | **TTL kayar: etkinlikte tazelenir.** Mutlak iki saat, inceleme ekranında çalışmakta olan kullanıcıyı keserdi — P8'in önlemek için var olduğu emek kaybı. Kullanıcıya gösterilen metin "son etkinliğinden iki saat sonra" demeli. |
| CSRF (Bölüm 40.1 adını koyup tanımlamıyor) | Spring Security'nin double-submit varsayılanı: sunucu okunabilir (HttpOnly olmayan) `XSRF-TOKEN` çerezi verir, istemci güvensiz metotlarda (POST/PUT/PATCH/DELETE) `X-XSRF-TOKEN` başlığında yankılar, uyuşmazlıkta `403` + `CSRF_TOKEN_INVALID`. Oturum çerezi zaten `SameSite=Strict` olduğu için asıl vektör kapalı; bu derinlemesine savunmadır, o yüzden kimlikle birlikte gelir, öne çekilmez. |
| Profil devralma | `POST /api/v1/profile/claim` → `200`, `404 NO_ANONYMOUS_PROFILE`, `409 PROFILE_ALREADY_EXISTS`. 409 yalnız **değiştir veya koru** sunar, **birleştir sunmaz**: birleştirme atom düzeyinde tekilleştirme demek (Bölüm 7, Jaro-Winkler + embedding) ve o Aşama 4 işi. Erken sunmak ya endpoint'i alakasız bir işe bağlar ya da içeriği sessizce çoğaltan bir birleştirme gönderir — P8 ikincisini yasaklar. API, yerine getiremeyeceği bir resolution'ı adlandırmamalı. |

#### D.6.7 — Kapsam dışı bırakılanlar

- **Sunucu tarafı render API çağırmaz.** Kimlik doğrulamalı her fetch tarayıcıda
  kalır; server component'ler yalnız kabuk ve statik içerik render eder.
  Frontend'in `client.ts` dosyasındaki açıklayıcı `throw` doğru davranıştır,
  yer tutucu değil. Bu değişirse iç ağ adresi ve çerez taşıma kararı gerekir.
- **`/api/v1/warmup` public API değildir** (Bölüm 52.5). OpenAPI şemasının
  dışında tutulur, nginx üzerinden yönlendirilmez, üretilen tiplerde
  görünmemelidir.

**Etkinleştirici.** springdoc-openapi ilk endpoint'le birlikte gelir. On altı
maddenin altısı, `npm run gen:api` çalışabilir olduğu anda kendiliğinden kapanır
— ama yalnız şema enum'ları ve başlıkları taşıyorsa.

#### D.6.8 — Frontend'in ikinci senkronizasyon isteği (Aşama 1 kapanışı)

Frontend, profil editörünü yayımlanan şemaya bağlarken her iddiayı **çalışan
sunucuya karşı** denetledi ve ikinci bir `DOC-SYNC-REQUEST.md` yazdı. Sonuç
üç kutuya ayrıldı: dokümanın yanlış olduğu yerler, şemanın eksik olduğu
yerler, ve sunucunun bozuk olduğu yerler. **Üçüncüsü en pahalısıydı ve
frontend onun yalnız bir yüzünü görmüştü.**

**Protokol düzeyindeki reddetmeler 500 dönüyordu.** `ProblemDetailAdvice`'ın
`Exception` yakalayıcısı Spring MVC'nin istek reddi istisnalarını da yutuyordu:

| İstek | Önce | Sonra |
|---|---|---|
| `Content-Type: application/merge-patch+json` | 500 `INTERNAL_ERROR` | **415** `UNSUPPORTED_MEDIA_TYPE` |
| `PUT` (yalnız `PATCH` kabul eden yolda) | 500 | **405** `METHOD_NOT_ALLOWED` + `Allow` |
| `Accept: text/plain` | 500 | **406** `NOT_ACCEPTABLE` |
| `?sectionId=not-a-uuid` | 500 | **400** `VALIDATION_FAILED`, `fields: ["sectionId"]` |

Üçü de tam stack trace ile `ERROR` seviyesinde loglanıyordu, yani herhangi bir
istemcinin bozuk isteği üretimin 500 oranını yükseltip logu dolduruyordu.
İlkinin sebebi bizzat bu doküman: Bölüm 35.6 merge-patch yazıyordu, hiçbir
controller onu kabul etmiyordu, dolayısıyla **spesifikasyonu izleyen istemciye
sunucunun bozulduğu söyleniyordu.** Bölüm 35.6 düzeltildi (media type ve
`If-Match` örneği), üç yeni kod katalogda: `METHOD_NOT_ALLOWED`,
`NOT_ACCEPTABLE`, `UNSUPPORTED_MEDIA_TYPE`.

**Varyant yaması taşıdığından fazlasını değiştiriyordu.** `PATCH
…/variants/{id}` için `content` zorunluydu, yani bir sözcüklemeyi varsayılan
yapmak metnin tamamını geri göndermeyi gerektiriyordu — metin düzenlemesi
olmayan bir yazmada. Frontend'in bu yüzden benimsediği "metni aynen geri
gönder" çözümü diğer iki hatayı ortaya çıkardı:

- `tone` istekten koşulsuz yazılıyordu, yani o çözüm **kullanıcının seçtiği
  tonu sessizce siliyordu** (P8). Alanı yalnız opsiyonel yapmak yetmez:
  göndermemek ile temizlemek farklı anlamlara gelmeli, bu yüzden `tone` artık
  bir `JsonNullable` — entry'nin null'lanabilir kolonlarındaki desenin aynısı.
- `userEdited` her yamada set ediliyordu. Anlamı "bu cümleyi bir insan yazdı"
  ve Aşama 2'nin çeviri işi neyi yeniden üretebileceğine ona bakarak karar
  verecek; yani bir promote, kimsenin dokunmadığı metni kullanıcının kendi işi
  gibi işaretliyordu. Artık yalnız sözcük taşıyan bir yazmayı izliyor.

İstek tipi ikiye ayrıldı: `VariantWrite` (POST) `content` istemeye devam
ediyor — sözcüklemesi olmayan atom kimsenin okuyamayacağı bir olgudur —
`VariantPatch` (PATCH) hiçbir şey istemiyor.

**Şema, API'nin zaten verdiği sözleri söylemiyordu.** Hepsi anotasyon, davranış
değişikliği değil; ama ilan edilmemiş bir garanti kimsenin güvenemeyeceği ve
iki tarafta da tek bir test kızarmadan kaldırılabilecek bir garantidir.

| # | Eksik | Karar |
|---|---|---|
| B.1 | `ApiError.code` ve `.status` opsiyonel görünüyordu | **Zorunlu.** D.9 · 12 her hatanın bir kod taşıdığını söylüyor; opsiyonel yayımlanınca her tüketici sözleşmenin "olamaz" dediği bir dala bakmak zorunda kalıyordu. |
| B.2 | Yazma yanıtlarında `ETag` ilan edilmemişti | **Her tekil kaynak yazmasında ilan edildi** (`POST` 201 dahil). Koleksiyon okumaları bilerek taşımıyor. |
| B.3 | `Run.m` şemada opsiyonel, D.9 · 4 "her zaman dizi" diyor | **İkisi de doğru, yön farkı.** Şema değişmedi; D.2'ye satır eklendi. |
| B.4 | On operasyon hiç `200` ilan etmiyordu | **Hepsine eklendi.** Aralarında her koleksiyon okuması ve her kısmi yazma var. |
| B.5 | `EntryPatch`'in temizlenebilir alanları düz `string` | `nullable = true` bir OpenAPI **3.0** bayrağı; bu doküman 3.1 ve orada null bir *tip*. springdoc bayrağı sessizce düşürüyordu. `types = {"string", "null"}` ile düzeltildi — ama `implementation` bırakılırsa springdoc `{ present, value }` sarmalayıcısını bileşen olarak yayımlıyor, yani aynı kusurun öteki yüzü. İki yarım da teste bağlandı. |
| B.6 | `/profile/export` yalnız JSON ilan ediyordu | **`text/markdown` de ilan edildi.** Şemaya güvenen istemci markdown'ı JSON diye ayrıştırıp ilk karakterde patlıyordu. |
| B.7 | Operasyon id'leri konumsal (`list_2`) | **Adlandırıldı** (`listAtoms`, `patchSection`, …). Üreticiler isimleri bunlardan türetiyor. |

**Golden set'e ikinci bir sözcükleme eklendi.** Sunucudaki hiçbir atomun
birden fazla varyantı yoktu ve her sözcükleme Türkçeyken `enabledLanguages`
`["en"]` idi — yani sekmeler, promote ve bayatlık yolu iki tarafta da yalnız
mock'larla vardı. `senior_backend_tr` artık iki dili de açıyor ve ilk
maddesinde İngilizce bir alternatif taşıyor. Fixture formatı iki alan
büyüdü (`enabledLanguages`, `alternatives`); okuyucu aynı dil+ton çiftini iki
kez talep eden fixture'ı reddediyor, yoksa seeder açılışta bir kısıt ihlaliyle
ölüyor ve hangi dosyanın yanlış olduğunu söylemiyor. Maliyetler yeniden
kaydedildi: bir yeni kayıt, mevcut her sayı aynı.

**Frontend'in doğruladığı, değişiklik istemeyen davranışlar** (D.9 · 25-28'de
tekrar edilir, çünkü bir sonraki oturum bunları deneyerek keşfetmesin): atom ve
varyant sürümleri **bağımsız ilerler**; hiçbir şeyi değiştirmeyen bir yazma
sürümü **artırmaz**; bir atom **son birincil sözcüklemesini bırakmaz** (400,
`fields: ["primary"]`); promote **öncekini indirir ve listeyi yeniden sıralar**,
ama yanıt yalnız yazılan sözcüklemeyi taşır.

**Kanıtlanan koruyucular.** 415/405/400/406'nın hepsi düzeltmeden **önce**
çalışan sunucuda 500 olarak gösterildi; promote-only yaması 400 olarak
gösterildi; null'lanabilirlik testi `nullable = true` geri konularak
kızartıldı. `OpenApiSchemaIT` 8 testten 16'ya, `AtomApiIT` 17'den 19'a,
`ProblemDetailAdviceTest` 7'den 12'ye çıktı.

### D.7 — İlerleme kaydı

Her dilim bittiğinde güncellenir: ne üretildi, sırada ne var, frontend'i ne
ilgilendiriyor. Backend deposundaki `CLAUDE.md` aynı bilgiyi oturum bağlamı
olarak taşır, ama o dosya senkronize edilmez — **frontend için tek adres
burasıdır.**

| Adım | Durum | Üretilen | Frontend'e etkisi |
|---|---|---|---|
| Aşama 0 — İskelet | ✅ Bitti | Paket ağacı, Gradle, Compose (core), Flyway V1 (Bölüm 13'ün tamamı), health endpoint, ArchUnit, Testcontainers, CI (CodeQL/Trivy/gitleaks), Makefile | — |
| Adım 1.1 — Domain | ✅ Bitti | `RichContent`/`Run`/`Mark` + `ContentMigrator`; dört entity + altı kapalı sözlük; `UserScopedRepository` + `ProfileScopedRepository` + `ProfileRef`; dört repository; `ProfileAssembler` (dört sorguda profil) | D.9 · 1-6 |
| Adım 1.2 — Profil CRUD | ✅ Bitti | **Bitti:** hata kataloğu (27 kod, tipli `params`, `ResolutionAction`); `ProblemDetailAdvice`; `CurrentUser` + yerel stand-in; `Profile` entity (tipli `contact`/`preferences`) + `ProfileRepository` + `ProfileResolver`; **`GET /api/v1/profile` + springdoc şeması** (ETag başlığı, iki sözlük enum olarak). **`PUT /profile`**, **`PUT /profile/preferences`**, **bölüm, entry, atom ve varyant CRUD + sıralama**. **tamamlanma yüzdesi**, **`DELETE /profile`**. **`GET /profile/export`** (JSON + Markdown). | D.9 · 7-20 |
| Adım 1.3 — LaTeX container | ✅ Bitti | `docker/latex` imajı (xelatex + TeX Gyre + tek dosyalık HTTP sarmalayıcı), `/compile` ve `/measure`, derleme başına rlimit, salt-okunur kök, uid 1000. `-no-shell-escape`'in gerçekten reddettiği çalışan container'a sorularak doğrulandı. `make dev-full` artık gerçekten bir şey başlatıyor. Ayrıntılar ve iki doküman düzeltmesi: **EK D.8.1**. | — |
| Adım 1.4 — Renderer | ✅ Bitti | `LatexEscaper`, `LatexInlineRenderer`, `PreambleBuilder`, `LatexDocumentRenderer`; klasik şablon, `TemplateCustomization` (enum + aralık + regex ile sınırlı). Final ve ölçüm belgeleri **aynı preamble'ı** kullanıyor (kritik test), ve üretilen belgenin gerçekten derlendiği container'a gönderilerek doğrulandı. Ayrıntılar: **EK D.8.2**. | — |
| Adım 1.5 — Ölçüm | ✅ Bitti | **Bitti:** `TexLogParser` (ATOMCOST + CALIB), `RenderCost`, `CapacityModel`, klasik şablonun **ölçülmüş** sabit maliyetleri ve onları her koşuda derleyiciden yeniden türeten kalibrasyon testi (EK D.8.3). **`LatexCompilerClient`** ve **`RenderCostService`**: profil içeriği tek bir derlemede ölçülüp `render_costs`a punto olarak yazılıyor (EK D.8.4). Tahmin katmanı Adım 1.8'de geldi (`RenderCostEstimator`, EK D.8.7); atom maliyeti formülü Adım 1.9'da düzeltildi (EK D.8.10). | — |
| Adım 1.6 — Seçim (Faz C) | ✅ Bitti | `SelectionRequest`/`SelectionState`, üç aşamalı algoritma (zorunlu yerleşim → etkin maliyetle greedy → swap), `Result`/`PipelineError`. Ölçülmüş kapasiteyle çalışan testler: sayfa hiç aşılmıyor, aynı girdi elli koşuda aynı çıktı, kilitler ve entry minimumları korunuyor (EK D.8.5). | — |
| Adım 1.7 — Faz E/F | ✅ Bitti | **Bitti:** `RenderPhase` (seçim + profil → `RenderRequest`), `GenerationPipeline` (seç → render → derle → say), bütçe geri beslemesi (%5 kıs, en çok iki tekrar), `X-Page-Count`, `GeneratedDocument`, iki yeni `PipelineError`. Gerçek container'a karşı: profil → tek sayfa PDF, ve ölçüm yanılınca sessiz taşma yerine hata (EK D.8.6). **Sırada:** indirme ucu — bir profilden `SelectionRequest` üretmek skorlama ister, o da Adım 1.8; uç oraya taşındı. | D.9 · 21 |
| Adım 1.8 — Genel mod | ✅ Bitti | `GeneralModeScorer` (Bölüm 19.4, yarılanma 5 yıl), `SelectionRequestBuilder` (pasif satırlar, kilitler, `min_atoms`, ölçülmüş maliyet ya da tahmin), `RenderCostEstimator` (Bölüm 26.5'in tahmin katmanı; gerçek derleyiciye karşı **asla az yazmadığı** doğrulanmış), `CapacityModel.textWidthPt` (EK D.8.7). `CvGenerationService` + **`POST /api/v1/generations/general`** + `ErrorPresenter` (Bölüm 25.3, dört durumun tamamı) (EK D.8.8). Veritabanındaki profil gerçek derleyiciden **tek sayfalık PDF** olarak çıkıyor. | D.9 · 22, 23 |
| Adım 1.9 — Golden set | ✅ Bitti | Beş golden profil (Bölüm 51.3) + ölçülmüş maliyetleri, `GoldenProfileReader`, `DevSeeder`, ve **dört kritik testin tamamı** (EK D.8.9). İzolasyon testi kasıtlı bir IDOR'a karşı doğrulandı. Ayrıca **ölçüm/sayfa sapması testi** — kontrol listesinin son maddesi — yazıldı ve üç ölçüm hatası buldurdu (EK D.8.10). | — |
| Aşama 1 kapanışı — ikinci senkronizasyon | ✅ Bitti | Frontend'in profil editörünü şemaya bağlarken bulduğu on beş madde (EK D.6.8). **Üç sunucu hatası:** protokol düzeyindeki her reddetme 500 dönüyordu (415/405/406/400 oldu); varyant yaması `content` istiyordu ve `tone`'u siliyordu; `userEdited` her yamada set ediliyordu. **Altı şema eksiği** kapatıldı (zorunlu `code`/`status`, on `200`, yazmalarda `ETag`, 3.1 null'lanabilirliği, `text/markdown`, adlandırılmış operasyon id'leri). Golden set'e ikinci bir sözcükleme eklendi. Bölüm 35.6, 37.6 ve XI-B.9.2 düzeltildi. | D.9 · 24-32 |

**Frontend.** Bu kayıt iki yönlü: aşağıdaki tablo `atomcv-frontend`'in
durumunu taşır ve son sütunu backend'den ne beklendiğidir. Frontend'in inşa
kararları **EK D.10**'da.

| Adım | Durum | Üretilen | Backend'den beklenen |
|---|---|---|---|
| Frontend Aşama 0 — İskelet | ✅ Bitti | Next 16 + Tailwind v4 + shadcn (Radix); XI-B.3 klasör yapısı; i18n iskeleti (next-intl, ICU, en+tr, locale yönlendirmesi); MSW mock altyapısı (dev, Vitest, Playwright); RFC 7807 hata zarfı ve fetch istemcisi; app shell ve erişilebilirlik tabanı; landing ve legal sayfaları; 17 birim + 9 uçtan uca test; rota başına bundle bütçesi; Docker imajı; CI (build + test + gitleaks) yeşil. CD Aşama 1 sonrasına ertelendi (Bölüm 55). | `/v3/api-docs` yayında (✅), `npm run gen:api` çalışabilir durumda (✅). `src/mocks/contracts.ts` üretilen tiplerle değiştirilecek. |
| Frontend Aşama 1 — Profil editörü | 🔨 Sürüyor | Şemaya bağlanma, profil editörü, varyant sekmeleri, etiket alanı; her iddia çalışan sunucuya karşı denetlendi ve ikinci `DOC-SYNC-REQUEST` yazıldı (EK D.6.8). | Bölüm/entry/atom/varyant uçları (✅ hazır, D.9 · 16-19), ETag disiplini (✅ D.9 · 15), `POST /generations/general` (✅ D.9 · 22), `complete_profile` (✅ D.9 · 23), şema tamamlığı ve varyant yaması (✅ D.9 · 24-32 — `gen:api` yeniden çalıştırılmalı) |

**Aşama 1 tamamlanma kontrolü (XI-A.3), madde madde:**

| Madde | Durum | Nerede kanıtlanıyor |
|---|---|---|
| Manuel form ile profil oluşturulabiliyor | ✅ (backend) | Bölüm/entry/atom/varyant CRUD + sıralama; form `atomcv-frontend`'de |
| PDF indiriliyor ve gerçekten 1 sayfa | ✅ | `GeneralCvIT` — veritabanındaki profil, gerçek container, tek sayfa |
| 5 golden profilde sayfa sınırı aşılmıyor | ✅ | `GoldenSelectionTest` (5 profil × 2 dil × {1,2} sayfa) |
| Determinizm (50 tekrar) | ✅ | `GoldenSelectionTest` |
| Kilitler ve yapısal kısıtlar | ✅ | `GoldenSelectionTest` |
| Multi-tenant izolasyon | ✅ | `MultiTenantIsolationIT`, kasıtlı IDOR'a karşı doğrulanmış |
| Türkçe karakterli doküman derleniyor | ✅ | `LatexContainerIT`; ayrıca golden set'in Türkçe profili uçtan uca |
| Profil okuma ≤6 sorgu | ✅ | `ProfileAssemblerIT` (Hibernate sorgu sayacı, alt sınırı da var) |
| Ölçüm ile gerçek sayfa arasında sapma <%3 | ✅ | `MeasurementDriftIT` — beş profilde %0.65-2.4, hepsi güvenli yönde (EK D.8.10) |

**Test sayıları:** 312 birim, 132 entegrasyon, 44 latex-etiketli.

**Aşama 1'de hâlâ açık olan kararlar:**

| Soru | Neden bekliyor |
|---|---|
| ~~İlk `UserContext` nereden gelir?~~ | **Karara bağlandı (EK D.8):** yalnız `local` profilinde var olan sabit bir kullanıcı; üretimde yedek bean yok, endpoint kullanıcı istediği anda uygulama açılışta düşer. |
| Üretimde migration nasıl çalışır? | Bölüm 47'nin önerdiği özellik yok (EK D.1). Şu an Flyway üretimde de açılışta çalışıyor. |
| Kota gününün zaman dilimi | `usage_counters.period` bir `DATE`; `resetsAt` gönderilmeden önce cevaplanmalı (EK D.6.5). |
| Anonim akış kuyruğu kullanacak mı? | `jobs` tekil indeksindeki NULL kusuru ve Bölüm 51.6'nın gizlilik testi buna bağlı. |
| CI imaj taraması | Trivy şu an yalnız yapılandırmayı tarıyor; üretilen imajı taramak CI'da bir build (birkaç GB) gerektiriyor ve registry push'uyla birlikte gelmeli (Bölüm 47). |
| Spotless | Bölüm 47.1 `spotlessCheck` çalıştırıyor ama yapılandırılmış bir biçimlendirici yok — bugün CI'da biçim kapısı hiç yok. |
| Atomsuz entry seçilemiyor | Seçim atom üzerinden çalışıyor; yalnız derece satırı olan bir eğitim kaydı aday bile olmuyor (EK D.8.9). Çözümü Bölüm 20.2'nin modelini değiştiriyor. |
| Beraberlik id ile çözülüyor | Aynı puan **ve** aynı maliyetteki iki atom, içerik yeniden içe aktarıldığında yer değiştirebiliyor — Aşama 3'ün profil devralması bunu yapacak (EK D.8.9). |

### D.8 — Adım 1.2: profil başı ve acting user

**Karar — Aşama 1'in acting user'ı.** Kimlik Aşama 3'te (XI-A.6), ama Adım
1.2'nin endpoint'leri bir `UserContext` istiyor ve `ProfileRef` onsuz
üretilemiyor. Üç seçenek vardı:

| Seçenek | Neden seçilmedi |
|---|---|
| İstek başlığından kullanıcı seçmek | Üretime sızdığı anda kimlik doğrulamayı komple atlayan bir arka kapı; test kolaylığı bu riski taşımıyor |
| Kimlik gelene kadar endpoint yazmamak | Aşama 1'in geri kalanı (ölçüm, seçim, render) profil verisine bağlı; tıkanırdı |
| **Yalnız `local` profilinde sabit bir kullanıcı** | ✅ Seçildi |

`CurrentUser` arayüzü + `LocalDevCurrentUser` (`@Profile("local")`). **Yedek bean
yok:** üretimde bir endpoint kullanıcı istediği anda uygulama açılışta gürültüyle
düşer — herkese aynı kullanıcının verisini sessizce servis etmektense. Bu
davranış kasıtlı ve gerçek implementasyon geldiğinde sınıf tek parça silinir.
`@Profile("local")` anotasyonunun varlığı testle sabitlendi.

`users` satırını JDBC ile ekliyor (`ON CONFLICT DO NOTHING`), çünkü identity
modülünün henüz entity'si yok; Flyway ile yarışmasın diye `ApplicationRunner`
olarak çalışıyor. Sabit kimlik `00000000-…-0001` — yeniden başlatmada yerel veri
ve seed'ler anlamını korusun diye.

**Profil başı.** `contact` ve `preferences` **map değil, tipli record**
(Bölüm 14.2, 14.3): her alan CV başlığına render ediliyor ve map, "hangi
anahtarlar var" sorusunu hem renderer'a hem frontend'e taşırdı. `Tone` artık
JSON'da da küçük harf — `preferences` içinde Jackson serileştiriyor, JPA
converter'ı değil.

`Contact.toString()` ve `WritingStyle.toString()` içerik basmıyor: ilki tamamen
kişisel veri, ikincisi kullanıcının yazdığı serbest metni taşıyor.

**İlk endpoint ve yayınlanan şema.** `GET /api/v1/profile`, springdoc ile
birlikte geldi.

| Konu | Tür | Karar |
|---|---|---|
| Şema üretimi | Ekleme | springdoc-openapi; `/v3/api-docs`. **Üretimde kapalı** (`springdoc.api-docs.enabled: false`): şema, frontend'in tip üretimi için bir derleme zamanı çıktısıdır (XI-B.9.1), üretimin servis etmesi gereken bir şey değil — servis etmek her endpoint'i ve gövde şeklini isteyene açar. |
| Hata gövdesinin şemaya girmesi | Ekleme | Yanıtları `ProblemDetailAdvice` üretiyor, ama bir advice şemaya görünmez. Bu yüzden `ApiErrorResponse` adında **yalnız dokümantasyon için** bir record var; iki kapalı sözlük şemaya onun üzerinden giriyor. Advice'in ürettiği gerçek gövdeyle alan alan karşılaştıran bir test, ikisinin sessizce ayrışmasını engelliyor. |
| Profil yanıtında `id` yok | Ekleme | Hiçbir endpoint profil id'si kabul etmiyor; sahiplik oturumdan geliyor (Bölüm 35.1). Şemada bir `id` alanı, geri gönderilebilecek bir şey varmış izlenimi verirdi. |
| Profil yanıtında `version` yok | Ekleme | Tekil kaynakta sürüm **ETag'de**. Gövdede de olsaydı ikisi çelişebilirdi. Koleksiyonlarda öğe başına `version` alanı kalıyor (D.6.2). |

**Karar — profil ilk kullanımda oluşur.** `ProfileResolver.resolve` profili
bulamazsa yaratır. `profiles.user_id` tekil, yani bir kullanıcının tam olarak
bir profili var ve yokluğu bir hata değil, hesabın yeni olması demek. 404
dönmek, her istemciyi "henüz profilin yok" durumunu aynı boş satırı yaratmaya
giden yolda bir hata hâli olarak ele almaya zorlardı.

### D.8.1 — Adım 1.3: LaTeX container

| Konu | Tür | Karar |
|---|---|---|
| `ulimit`'lerin yeri | Düzeltme | Bölüm 29.4 onları container entrypoint'ine koyuyor. Orada **JVM'e de** uygulanıyorlar: `ulimit -v 512m` ile sunucu heap'ini ayıramadan ölüyor, ve `ulimit -t 20` ilk yirmi saniyeden sonra sunucunun kendisini öldürürdü. Limitler her derlemeye ait; `run-xelatex.sh` onları kurup `exec xelatex` yapıyor. |
| Bölüm 22.4'ün `\newsavebox{\mbox}`'u | Düzeltme | `\mbox` LaTeX'te zaten tanımlı: doküman `! LaTeX Error: Command \mbox already defined.` ile durur. Ölçüm dokümanı başka bir ad kullanmalı (`\measurebox`). Adım 1.5 bunu ilk denemede yaşayacaktı. |
| HTTP sarmalayıcı | Ekleme | Tek dosyalık, bağımlılıksız bir Java sunucusu (JDK'nın kendi `HttpServer`'ı). İmaj, yamalanması gerekebilecek hiçbir kütüphane taşımıyor ve sarmalayıcının tamamı bir oturumda okunuyor. |
| İki uç | Ekleme | `/compile` → PDF; `/measure` → **TeX log'u**. Ölçüm (Bölüm 26) PDF'i değil, `\typeout{ATOMCOST\|…}` satırlarını istiyor; ikisini ayırmak, ölçümün PDF üretmeye zorlanmamasını sağlıyor. Başarısız derleme **422** döner (belge hatalı, servis değil) ve gövdesi log'dur. |
| `javac -encoding UTF-8` | Ekleme | `build.gradle.kts` ile aynı sebep: container'ın platform charset'i US-ASCII, ve bir yorumdaki tire derlemeyi düşürüyor. |
| Healthcheck kabuğu | Düzeltme | `/dev/tcp` bir **bash** özelliği; `sh` (dash) "Directory nonexistent" diyor ve container bozuk gibi görünüyor. `curl`/`wget` bilerek kurulmuyor — buradan ağa uzanabilen ne kadar az araç varsa o kadar iyi. |
| Geliştirmede ağ izolasyonu | Sapma | Üretimde container `internal: true` ağında, dışarı yolu ve yayınlanmış portu yok. **Geliştirmede olamıyor:** `make dev` backend'i host'ta çalıştırıyor ve Docker, yalnız internal ağa bağlı bir container için port yayınlamıyor — host'un içeri girecek yolu kalmıyor. Yerel içerik geliştiricinin kendi içeriği; fark `docker-compose.yml`'de yazılı, keşfedilmiyor. |
| Test maliyeti | Ekleme | `LatexContainerIT` `@Tag("latex")` taşıyor ve `integrationTest`'ten **dışlanıyor**: imaj birkaç GB ve dakikalar sürüyor. `gradlew latexTest` ile, `docker/latex` değiştiğinde çalıştırılır. |
| Ne doğrulanıyor | Ekleme | xelatex'in çalışması değil, **etrafındaki çitin durması**: `\write18` denemesi log'da `runsystem(touch /tmp/pwned)...disabled` ile reddediliyor ve dosya oluşmuyor; root dosya sistemi salt-okunur; süreç uid 1000. Bunlar bayrağın kurulu olduğuna inanmakla değil, çalışan container'a sorularak doğrulanıyor. |
| Henüz yapılmayanlar | Açık | (a) Bölüm 29.2'nin **preamble format dump**'ı: gerçek preamble Adım 1.4'te doğuyor, ondan önce uydurmak olurdu. (b) CI'da **imaj taraması**: Trivy'nin misconfig taraması Dockerfile'ı artık buluyor, ama imajın kendisini taramak her koşuda birkaç GB'lık bir derleme demek — kayıt defterine push eklendiğinde oraya bağlanacak. |

**`make dev-full` imajı yeniden inşa eder (`--build`).** Aksi hâlde Compose en
son inşa ettiği imajı kullanıyor; bayat bir imaj `X-Page-Count` başlığı
göndermiyor ve istemci bunu haklı olarak `UNAVAILABLE` sayıyor — container
ayakta ve sağlıklıyken başarısız olan bir üretim. Yerel kurulumda bir kez
yaşandı; hedef artık her seferinde inşa ediyor.

### D.8.2 — Adım 1.4: klasik şablon ve renderer

| Konu | Tür | Karar |
|---|---|---|
| Ortak preamble | Ekleme | `renderFinal` ve `renderMeasurement` **aynı metodu** çağırıyor (`PreambleBuilder.build`). Adım 1.4'ün kritik testi ikisinin preamble'ını karşılaştırıyor; farklı bir geometriyle alınan ölçüm, kimsenin basmayacağı bir belgeyi ölçer ve sayfa garantisi tam buna dayanıyor. |
| `FontRegistry` sınıfı yok | Sapma | Bölüm 22.5 `FontRegistry.resolve(enum)` çağırıyor. **Enum'un kendisi whitelist**: `FontFamily` LaTeX adını taşıyor, yani araya bir eşleme tablosu koymadan da hiçbir kullanıcı dizesi `\setmainfont`'a ulaşamıyor. |
| Fontlar imajda var olmalı | Ekleme | Kurulu olmayan bir font derleme sırasında **sessizce** başkasına düşer ve ölçülmüş bütün maliyetler yanlış olur — hatasız. `FontFamily`'nin üç değeri de container imajında (TeX Gyre). |
| `HexColor` büyük harfe çevriliyor | Ekleme | `Locale.ROOT` ile. Yalnızca harf büyüklüğüyle ayrışan iki özelleştirme aksi hâlde iki ayrı ölçüm işine yol açardı. |
| `String.format` ve locale | Ekleme | `Locale.ROOT`. Türkçe locale altında `%.2f` "0,60" yazıyor ve belge derlenmiyor — mutlak kural 7'nin sayı tarafı. Test locale'i değiştirip doğruluyor. |
| URL kaçışı | Ekleme | `\href` argümanında ters bölü ve süslü parantez argümanı erkenden kapatıyor; bunlar **kaçırılmıyor, atılıyor**. Bozuk bir link, derlenmeyen bir belgeden iyidir. |
| Kaçış önce, işaretleme sonra | Ekleme | Kullanıcı metni önce escape ediliyor, sonra mark komutuna sarılıyor: aksi hâlde metnin içindeki `\textbf{...}` gerçek bir komut olurdu. Ayrı bir test bunu sabitliyor. |
| Klasik şablon | Ekleme | Tek kolon, grafiksiz (Bölüm 33.5 "ATS-güvenli"). ATS metin çıkarır; insana hoş görünüp çıkarımda dağılan bir düzen, insana hiç ulaşmayan bir CV demektir. |
| Ölçüm anahtarı karakter kümesi | Ekleme | `MeasurableItem.key` içinde `|`, `%`, `{`, `}`, boşluk ve TeX'in özel karakterleri yasak: anahtar log satırından `|` ile bölünerek geri okunuyor. Anahtarlar kod tarafından id'lerden üretiliyor, yani bu bir saldırıyı değil bir hatayı yakalıyor. |
| Doğrulama | Ekleme | Renderer'ın çıktısı **gerçekten derleniyor**: `latexTest` içindeki iki test, üretilen CV'yi container'a gönderip PDF alıyor ve ölçüm belgesinden `ATOMCOST|var-1|<pt>|<pt>` satırlarını okuyor. Birim testler bunu gösteremez. |

### D.8.3 — Adım 1.5: ölçüm sistemi (ilk yarı)

| Konu | Tür | Karar |
|---|---|---|
| Sabit maliyetler **ölçüldü** | Ekleme | Bölüm 26.4'ün sayıları örnek; klasik şablonun kendi değerleri derleyiciden alındı. Varsayılan özelleştirmede: `pageTextHeight` **708.245pt**, `baselineSkip` **12.0pt**, bölüm başlığı **24.0pt**, entry başlığı **22.76pt**, madde listesi ek yükü **7.0pt**, madde satırı **13.0pt**. |
| Nasıl ölçüldü | Ekleme | Renderer bir **kalibrasyon belgesi** üretiyor: sonda `\the\pagetotal` yazan probe'lar; iki konum arasındaki fark, o mobilyanın maliyeti. Aynı preamble, aynı sebep. |
| Kalibrasyon bir test | Ekleme | `LatexCalibrationIT` her çalıştığında sayıları yeniden türetip saklananlarla karşılaştırıyor (0.01pt tolerans). Preamble değiştiğinde bu test düşer — şablon sürümünü yükseltme anı budur (Bölüm 16.3), saklanmış maliyetlerin sessizce yalan söylemeye başladığı an değil. |
| İlk çalıştırmada bir yanlış sabit yakalandı | Düzeltme | Entry başlığını elle ölçtüğüm belgede satır sonu (`\\`) kaybolmuştu; **10.87pt** okundu ve tamamen makul göründü. Gerçek değer **22.76pt** — iki satır. Bir sayfada altı entry'de bu 71 punto, yani neredeyse altı satırlık sessiz taşma demekti. |
| `capacity()` `Optional` döner | Sapma | Bölüm 22.2 koşulsuz bir model döndürüyor. Döndüremez: **ölçülmemiş bir özelleştirmenin kapasitesi yoktur**, ve uydurmak sayfa garantisini sessizce bozar — sistemin var olma sebebi olan tek hata. Boş optional "önce ölç" demek. Bölüm 33.1'in B katmanı (font, boyut, margin, aralık) bu yüzden ölçüm gerektiriyor. |
| Log ayrıştırma | Ekleme | `ATOMCOST\|key\|<pt>\|<pt>` deseni; yarım yazılmış bir satır (TeX log'u 79 karakterde sarar) **yok sayılır**, yarım okunmaz. Maliyet = yükseklik + derinlik + `baselineSkip`: aradaki boşluğu saymamak, on altı atomun kâğıtta teoride sığıp pratikte taşması demek. |

### D.8.4 — Adım 1.5: ölçümün veriye yazılması

| Konu | Tür | Karar |
|---|---|---|
| Bölüm 22.4'ün ölçüm belgesi derlenmiyor (ikinci kusur) | Düzeltme | `\begin{itemize}` açılıp **hiç `\item` konmadan** kapanıyor: LaTeX "Something's wrong--perhaps a missing `\item`" ile duruyor ve `-halt-on-error` altında koşu bitiyor. İlk atomun `ATOMCOST` satırı hatadan **önce** basıldığı için tek atomlu bir test geçiyor, iki atomlu düşüyor. Düzeltilmiş hâl: `\item\savebox{…}\usebox{…}`. |
| Ölçüm genişliği | Düzeltme | `\parbox{\measurewidth}` (yani `\textwidth`) hiçbir maddenin sahip olmadığı bir genişlikte ölçüyor. Madde `itemize` içinde ve orada gerçek genişlik **`\linewidth`** — girintiyle azaltılmış hâli. |
| Kendi testim de zayıftı | Düzeltme | "ATOMCOST satırı var mı" diye bakıyordu, "belge geçerli mi" diye değil; geçersiz bir belgeyle **geçiyordu**. Artık log'da LaTeX hatası olmadığını ve **on iki maddenin on ikisinin de** raporlandığını doğruluyor. |
| Derleyici istemcisi | Ekleme | `LatexCompilerClient` uygulamanın container'a uzanan tek yolu; hata dört türe ayrılıyor: **belge hatalı** (422, log'uyla), **meşgul** (503, tekrar denemeye değer), **zaman aşımı**, **ulaşılamıyor**. Kuyruk ve yeniden deneme kararları bunlara bakıyor. TeX log'u yanıta ve geliştiriciye gider, **log satırına asla** — kullanıcının kendi içeriğinden türetilmiştir (mutlak kural 4). |
| `RenderCostService` neden `rendering` içinde | Ekleme | Maliyet varyanta ait, ama servisi `profile`'a koymak **modül döngüsü** yaratırdı: `rendering` zaten içeriği tanıyor (`RichContent`), `profile` da rendering'e uzanırsa ikisi birbirine bağlanır ve ArchUnit'in döngü kuralı düşer. Yazma yine profilin kendi kapsamlı repository'sinden geçiyor. |
| Ölçüm anahtarı | Sapma | Bölüm 22.4 `{variantId}:{customizationId}:{templateVersion}` diyor. Uygulanan: **yalnız `variantId`**. Özelleştirme entity'si henüz yok, ve anahtarın tek işi log satırından geri okunmak; sürüm bilgisi zaten `render_costs` anahtarında (`classic:v1`). Özelleştirmeler geldiğinde genişler. |
| Eksik ölçüm | Ekleme | Bir varyantın maliyeti log'da yoksa **diğerleri yine yazılır**. Tek bir eksik ölçüm için tüm profili ölçümsüz bırakmak, seçimin tahmine düşeceği tek atom yerine hepsini tahmine düşürürdü (Bölüm 26.5). |
| `FontMetricEstimator` ertelendi | Açık | Bölüm 26.2'nin 1. katmanı font dosyalarını **backend tarafında** okumayı gerektiriyor; fontlar container imajında. Tahminin tek tüketicisi henüz olmayan bir arayüz önizlemesi (Bölüm 33.3) ve ölçümsüz üretim yolu. Fontları ikinci bir yere kopyalamadan önce tüketicisi olsun. |

### D.8.5 — Adım 1.6: Faz C, seçim

| Konu | Tür | Karar |
|---|---|---|
| Başlık bloğu ölçüldü | Ekleme | Bölüm 20.1 bütçeye `capacity.fixedCost("heading")` koyuyor ama kalibrasyonda yoktu. Ölçüldü: ad + iki ortalanmış satır = **52.0pt**. Onsuz her CV yarım satır fazla sığıyor sanılırdı. |
| `minAtoms` her entry için zorlanmıyor | Sapma | Bölüm 20.3'ün 1. aşaması **her görünür entry** için minimumu zorluyor. Bu, uzun bir profili "sığmıyor" hatasına düşürürdü — oysa doğru davranış zayıf entry'leri bırakmak. Uygulanan: minimum yalnız **kilitli bir atomun zaten açtığı** entry'lerde zorlanır; diğerlerinde greedy'den sonra **ya hepsi ya hiçbiri** olarak uygulanır (yeni red sebebi: `ENTRY_BELOW_MINIMUM`). |
| Öncelik kuyruğu yerine her turda yeniden hesap | Sapma | Bölüm 20.3 bir `PriorityQueue` kuruyor. Bir atomu almak kardeşlerinin **hem maliyetini** (entry başlığı artık ödendi) **hem değerini** (aynı entry'den beşinci madde daha az değerli) değiştiriyor; önceden sıralanmış bir kuyruk bayat sayıları sıralar. 200 atom için her turda yeniden taramak birkaç milisaniye, ve tamamen deterministik. |
| Swap tek-için-tek | Sapma | Bölüm 20.3 bir **küme** çıkarıp bir aday koymayı öneriyor. Bu boyutta kazanç küçük, alt küme araması pahalı, ve her ek serbestlik derecesi iki koşunun ayrışması için bir yol daha. |
| Etkin maliyet | Ekleme | Bir atom, açtığı mobilyayı da ödüyor: bölüm başlığı, entry başlığı ve madde listesi ek yükü. Kısıt (5) bu; problemin saf knapsack olmamasının sebebi de. |
| Model tutarlılığı kodda | Düzeltme | `EntryPlan` altındaki bir atomun `entryId`'si o entry'yi göstermek **zorunda**. Test yazarken tam bu hatayı yaptım: entry içindeki atom `entryId = null` taşıyınca seçim entry başlığını hiç ödemedi ve bütçe entry başına **22.76 punto** kazandı — görünür sebebi olmayan bir taşma. Artık kurulumda patlıyor. |
| `Result` ve `PipelineError` | Ekleme | Bölüm 25.1/25.2'nin biçimiyle, ama **yalnız bugün üretilebilen hata** ile: `ConflictingPreferences`. Sealed arayüz, hata sunumunu exhaustive switch yapıyor — yeni bir hata türü, kullanıcıya ne söyleneceği kararlaştırılmadan derlenmiyor (P4, dille zorlanmış). Diğer durumlar kendi fazlarıyla gelecek; erken eklemek parametrelerini tahmin etmek olurdu, ve frontend'in mesajlarının ihtiyacı tam olarak o parametreler. |

### D.8.6 — Adım 1.7: Faz E ve Faz F

| Konu | Tür | Karar |
|---|---|---|
| Sayfa sayısını **derleyici bildiriyor** | Ekleme | Bölüm 23.1 `pdfAnalyzer.pageCount(pdf)` diyor ama böyle bir bileşen tanımlı değil. PDF baytlarında `/Type /Page` saymak modern xelatex çıktısında güvenilir değil (sayfa ağacı object stream içinde sıkıştırılıyor) ve bunun için bir PDF kütüphanesi eklemek, container'ın "bağımlılıksız" olma gerekçesiyle çelişirdi. Container `/compile` yanıtına **`X-Page-Count`** başlığı koyuyor; değeri TeX'in kendi `Output written on ... (N pages)` satırından. |
| Sayfa sayısı **gelmezse belge reddedilir** | Ekleme | Başlıksız bir 200, "uzunluğu bilinmeyen bir CV" demek. Faz F ölçemediği bir sınırı garanti edemez, o yüzden `LatexCompilerClient` bunu belge hatası değil **`UNAVAILABLE`** (yanlış derleyici) sayar. P4'ün doğrudan uygulaması; testi var. |
| `SelectionRequest.withBudgetFactor` | Ekleme | Bölüm 23.1'in `input.withBudgetFactor(0.95)` çağrısının karşılığı. Faktör bileşen olarak eklendi (0 < f ≤ 1; **büyütülemez**), üç argümanlı kurucu 1.0 ile delege ediyor. |
| Geri besleme döngüsü | Uygulama | Seç → render et → derle → say. Sığmıyorsa bütçe %5 kısılır ve **Faz C tekrar koşar**; en çok iki tekrar, sonra `PageLimitExceeded`. LLM'e dönülmüyor — Faz F asla yeni metin istemez. `generation.budget.overshoot` sayacı Bölüm 23.1'in istediği oranı besliyor. |
| `PipelineError` iki yeni durum | Ekleme | `PageLimitExceeded(actualPages, maxPages)` — kataloğun `PAGE_LIMIT_EXCEEDED` (422) koduyla birebir. `CompilationFailed(kind, texLog)` — derleyici istisnası hattın dışına **fırlamıyor**, `Result.err` olarak taşınıyor; sunum yine exhaustive switch. |
| Sıra profilden gelir, seçimden değil | Uygulama | Seçim skora göre sıralar. Madde işaretleri profil sırasında basılıyor; aksi halde CV karıştırılmış gibi okunurdu. |
| Boş başlık basılmaz | Uygulama | Altında seçilmiş içeriği kalmayan bölüm ve entry render edilmez. Seçim yalnız **açtığı** mobilyayı ödediği için, boş bir başlık bütçede karşılığı olmayan punto harcardı. |
| "Halen" / "Present" | Sapma (geçici) | Bitiş tarihi olmayan entry için dilde bir kelime gerekiyor. Bölüm 32 çok dilli render'a kendi sözlüğünü getirene kadar iki dil `RenderPhase` içinde sabit; bilinmeyen dil İngilizce'ye düşer. Tarih biçimi `MMM yyyy`, içerik diliyle. |
| İndirme ucu Adım 1.8'e taşındı | Kapsam | XI-A.3 Adım 1.7'nin beşinci maddesi. Hattın girdisi **skorlanmış ve maliyeti bilinen** bir `SelectionRequest`; bir profili ona çeviren şey genel mod skorlaması, o da Adım 1.8. Uç orada tek parça yazılacak (`generations` tablosuna yazan kalıcı üretim kaydı ve `GET /generations/{id}/download` ise Aşama 2, D.6.3). Aşama 1 kontrol listesindeki "PDF indiriliyor ve gerçekten 1 sayfa" maddesi 1.8'de kapanır. |
| `ATS` raporu ve `FitReport` yok | Kapsam | Bölüm 23.2/23.3 metin çıkarma (PDF → text) ve ilan analizi istiyor; ikincisi Faz A'ya, birincisi bir PDF kütüphanesine bağlı. Aşama 1'in dört kritik testinde ikisi de yok, Aşama 2'ye bırakıldı. |
| `RenderableSection.toString` | Düzeltme | Kardeş record'lar (`ProfileHeader`, `RenderableEntry`) içerik basmıyordu, bu basıyordu — bölüm başlığı kullanıcının kendi metni (mutlak kural 4). |

**Doğrulama.** Birim testler döngünün aritmetiğini sahte derleyiciyle kanıtlıyor
(bir denemede sığar, iki denemede sığar, üç denemede sığmazsa reddedilir, kilitli
içerik derleyiciye **hiç ulaşmaz** — P5). Gerçek container'a karşı iki test:
üç bölümlük bir kariyer gerçekten tek sayfalık bir PDF oluyor, ve **her atomun
maliyeti bilerek beşte bir bildirildiğinde** seçim sığdığını sanıyor, derleyici
aksini söylüyor, sonuç sessiz bir üç sayfalık CV değil bir hata oluyor.

### D.8.7 — Adım 1.8: genel mod skorlaması ve seçim isteği

| Konu | Tür | Karar |
|---|---|---|
| Yarılanma süresi **5 yıl** | Ekleme | Bölüm 19.4 "üstel azalma" diyor, hızını vermiyor. On yıl önceki bir iş güncelin dörtte biri ediyor; **sıfır etmiyor**, çünkü içinde metrik olan on yıllık bir madde hâlâ sayfanın en iyi şeyi olabilir. |
| Tarihsiz atom cezalandırılmıyor | Ekleme | Entry'si olmayan atom (beceri, sertifika) için recency **1.0**. Bölüm 19.4 bu durumdan söz etmiyor; 0 vermek her beceriyi CV'nin dışına iterdi. |
| Skor sonda kırpılıyor | Düzeltme | Ağırlıklar bire tamamlanıyor ama dört double `1.0000000000000002` edebiliyor ve `AtomCandidate` birden büyük skoru reddediyor — yalnız kusursuz bir atomda ortaya çıkacak bir kusur. |
| Bugünün tarihi **parametre** | Ekleme | Saati okuyan bir skorlayıcı Bölüm 51.2'nin "aynı girdi → aynı çıktı" testini geçemez. |
| Ölçümsüz atom: `RenderCostEstimator` | Sapma | Bölüm 26.5 ölçüm yoksa **font-metrik tahmini + %8 pay** istiyor; Bölüm 26.2 bunu FontBox ile gerçek font metriklerinden kuruyor. Burada PDF kütüphanesi eklemek yerine bağımlılıksız ve **daha kötümser** bir tahmin var: ortalama karakter genişliği (0.46em, Termes'in gerçek ortalamasının altında — kasten), satır doluluğu %92, ve ölçümün biçimini taklit eden `(satır + 1) × baselineSkip`, üstüne %8. Tek sözü: **asla TeX'ten az yazmaz**, ve bu gerçek derleyiciye karşı altı farklı uzunlukta test ediliyor. |
| `CapacityModel.textWidthPt` | Ekleme | Kalibrasyon `\textwidth`'i zaten ölçüyordu ve atıyordu. Tahmin bu genişliğe bölüyor; yanlışsa her ölçümsüz atom yanlış sayıda satır ödüyor. Ölçülen değer **527.571pt** ve kalibrasyon testi artık onu da doğruluyor. |
| Entry kilidi atom kilidine çevriliyor | Ekleme | `entries.always_include` "bu iş CV'de kalsın" demek; `SelectionPhase` yalnız atom kilidi biliyor. Bütçedeki karşılıkları aynı: entry başlığı + `min_atoms` kadar madde. Kurucu, kilitli entry'nin **en yüksek skorlu** `min_atoms` atomunu kilitliyor (eşitlikte id ile, Bölüm 19.6). Kilitli bölüm için aynısı bir atomla. Bunu yapmamak, kullanıcının koyduğu kilidi sessizce yok saymak olurdu. |
| Pasif bölüm/entry hiç aday olmuyor | Karar | Pasif **atom** aday listesinde kalıyor ve `INACTIVE` sebebiyle reddediliyor (Bölüm 19.5), ama pasif bir bölüm ya da entry CV'nin parçası değil: altındaki atomlar için "neden yok" sorusu da doğmuyor. |
| Sözü olmayan atom sayılıyor | Ekleme | Hiçbir dilde varyantı olmayan atom render edilemez; sessizce düşürmek yerine `withoutWording` sayacına yazılıyor — yukarıda bir kusur olduğunun işareti. |

### D.8.8 — Adım 1.8: üretim servisi ve PDF ucu

| Konu | Tür | Karar |
|---|---|---|
| **`POST /api/v1/generations/general`** | Ekleme | Bölüm 35.3'ün `POST /generations`'ı 202 + iş döndürüyor, çünkü içinde LLM var. Genel modda LLM de kuyruk da yok: bu uç belgeyi **doğrudan** döndürüyor (`application/pdf`, `Content-Disposition: attachment`, `Cache-Control: no-store`). Aşama 1'e özgü ve öyle işaretli; kuyruklu sözleşme üretim kaydıyla birlikte Aşama 2'de gelecek. Gövde **isteğe bağlı**; `maxPages` ve `language` verilmezse profilin kendi varsayılanları geçerli. |
| Hiçbir şey saklanmıyor | Kapsam | `generations` tablosuna satır yazılmıyor, `selection_state` saklanmıyor, indirme bağlantısı yok. Saklama, saklama süresi (`EK D.6.3`'teki 14 gün ve 410) ve düzenleme döngüsü (Faz G) hep aynı kaydı gerektiriyor; biri olmadan diğerini yazmak yarım bir sözleşme olurdu. Bir test `generations`'ın boş kaldığını doğruluyor. |
| Ön kontrol **yapısal**, yüzde değil | Karar | Bölüm 25.2 `INSUFFICIENT_PROFILE(completeness, missing)` diyor, eşik vermiyor. Yüzde eşiği gayet iyi render edilecek profilleri reddederdi; üretimi durduran şey **basılacak bir şeyin olmaması**. Tamamlanma yüzdesi mesajda taşınıyor, kararı vermiyor. |
| `complete_profile` sözlüğe eklendi | Ekleme | Bölüm 25.3'ün örneği bu adı kullanıyor ama D.6.1'in sekiz eylemlik kümesinde yoktu. Dokuzuncu eylem; frontend'in buton davranışı yazması gerekiyor (D.9 · 23). |
| `ErrorPresenter` | Uygulama | Bölüm 25.3'ün biçimiyle, dört durumun **tamamı** için. `UserFacingError` parametreleri katalogla doğruladığı için her sunum aynı zamanda "ICU mesajının beklediği alanları yayınlıyor mu" testi. |
| `PAGE_LIMIT_EXCEEDED`'in çözümü | Karar | `increase_page_limit`, `maxPages` = **derleyicinin gerçekten ürettiği sayfa sayısı**. Yeterli olduğu bilinen tek sayı o. |
| `COMPILATION_FAILED.detail` **log değil** | Karar | Katalog `detail: string` istiyor ve bu dize ICU mesajına giriyor. TeX logu kullanıcının kendi içeriğinden türüyor, oraya konamaz: `detail` yalnız hatanın türü (`invalid_document`, `busy`, `timeout`, `unavailable`). `retry` çözümü TeX'in reddettiği belge dışında sunuluyor — o belge tekrar denenince yine reddedilir. |
| `PipelineError.Resolution` silindi | Düzeltme | Aynı kavramın iki tipi vardı; `generation` artık `shared.error.Resolution`'ı kullanıyor. Eylem adı artık `String` değil enum: yazım hatası derlenmiyor. |
| `Clock` bean'i | Ekleme | Skorlama bugünün tarihini parametre olarak alıyor (Bölüm 19.6); onu üreten yer bir bean, UTC. Kotanın gün sınırı ayrı bir karar olarak duruyor. |
| `ProfileResolver.owned()` | Ekleme | Üretim hem profilin kendi alanlarını (başlık, tercihler) hem de altındaki kapsamı istiyor. `ProfileRef`'in tek üretim yeri kuralını bozmamak için ikisini birlikte döndüren bir metot eklendi — satırı iki kez okumak yerine. |

**Doğrulama.** `GenerationApiIT` yedi test: PDF eki, isteğe bağlı gövde ve
`maxPages` geçersiz kılma, boş profilin **derleyiciye hiç gitmeden** reddi,
sayfa aşımının çözümüyle birlikte sunumu, derleyici çöküşünün 502'si,
`maxPages: 99`'un 400'ü, ve `generations` tablosunun boş kalması.
`GeneralCvIT` (latex etiketli) aynı ucu gerçek container'a karşı çalıştırıyor:
veritabanındaki bir profil ölçülüyor, seçiliyor, derleniyor ve **gerçekten tek
sayfalık** bir PDF olarak dönüyor — XI-A.3'ün Aşama 1 kontrol listesindeki
madde budur.

### D.8.9 — Adım 1.9: golden set, seeder ve dört kritik test

| Konu | Tür | Karar |
|---|---|---|
| Fixture formatı **export formatı değil** | Sapma | Bölüm 51.3 dosyaları adlandırıyor, biçimini vermiyor. Export biçimi her satır için `id` ve `version` taşıyor; elle yazılan bir fixture'da bunlar altmış kez uydurulurdu ve ikinci bir veritabanına yüklendiğinde yanlış olurdu. `GoldenProfileDocument`'te kimlik **ağaçtaki yer**; id'leri okuyucu üretiyor. Metinden başka her alan isteğe bağlı, yani bir fixture yalnız ilginç olan şeyi söylüyor. |
| Fixture'lar `src/main/resources` altında | Sapma | Bölüm 51.3 `src/test/resources` diyor. `DevSeeder` üretim kodu ve aynı dosyaları okuyor; test kaynakları onun sınıf yolunda değil. Kopyalamak iki formatın zamanla ayrışması demekti. Dosyalar jar'a giriyor (birkaç KB) ama onları okuyan tek şey `local` profiline bağlı seeder. |
| Maliyetler **içerik hash'iyle** anahtarlanıyor | Ekleme | `*.costs.json` Bölüm 51.3'ün istediği dosya. Varyant id'si her okumada değişiyor, içerik hash'i içeriğin kendisi — metin değişince anahtar da değişiyor, yani bayat bir maliyet sessizce eşleşemiyor. |
| `-Dgolden.record=true` | Ekleme | `GoldenCostsIT` normalde **doğruluyor**: saklanan her sayıyı gerçek derleyiciden yeniden ölçüyor ve 0.01 punto sapmada düşüyor. Kayıt modunda aynı test dosyaları yazıyor. Bir fixture'ın metni ya da şablonun geometrisi değişince yeniden kaydedilir. |
| **`max_print_line`** | Düzeltme | TeX logunu 79 sütunda katlıyor. Ölçüm anahtarı 64 karakterlik bir hash olunca `ATOMCOST` satırı ikiye bölündü ve parser hiçbir şey bulamadı — beş profilin **hepsi sıfır ölçümle** döndü. Container artık xelatex'e `max_print_line=10000` veriyor. Varyant id'siyle (36 karakter) hiç görünmeyecek, ama sınıra iki karakter kalmıştı. |
| Beraberlikler id ile çözülüyor, ve id kalıcı değil | Bulgu | Aynı puanı **ve** aynı maliyeti taşıyan iki atom arasında Bölüm 19.6'nın tie-break'i id'ye bakıyor. Veritabanındaki bir profil için id sabit, dolayısıyla çıktı sabit; ama aynı içerik yeniden içe aktarılırsa (Aşama 3'ün anonim profil devralması) ikisinden diğeri seçilebilir. Golden test bu yüzden "aynı atomlar" değil **"aynı sayıda atom ve aynı punto"** diyor. İçerikten türeyen bir tie-break Aşama 2'de bilinçli olarak kararlaştırılmalı. |
| Atomsuz entry hiç görünmüyor | Bulgu | Seçim atom üzerinden çalışıyor; hiç atomu olmayan bir entry (yalnız derece satırı olan bir eğitim kaydı) aday bile olmuyor. Fixture'larda her eğitim kaydına bir atom verildi. Gerçek çözüm — entry'nin kendisinin aday olması — Bölüm 20.2'nin modelini değiştirir ve Aşama 2'ye ait. |
| `DevSeeder` var olan profile dokunmuyor | Karar | `local` profiline bağlı, `@Order(100)` ile kullanıcı satırından sonra çalışıyor, ve profil zaten varsa **hiçbir şey yapmıyor**: yerel olarak denemek için girilen bir CV'nin üzerine yazmak tam olarak P8'in engellediği şey. Hangi fixture'ın ekileceği `atomcv.dev.seed-profile` ile seçiliyor. |

**Dört kritik test (Bölüm 51.2), nerede.**

| # | Test | Nerede | Kapsam |
|---|---|---|---|
| 1 | Sayfa sınırı aşılmıyor | `GoldenSelectionTest` | 5 profil × 2 dil × {1,2} sayfa |
| 2 | Determinizm | `GoldenSelectionTest` | Her profil için 50 koşu |
| 3 | Çok-kiracılı izolasyon | `MultiTenantIsolationIT` | Kimlik taşıyan **sekiz** uç + reorder + listeler + üretim ucu |
| 4 | Kilitler ve yapısal kısıtlar | `GoldenSelectionTest` | Kilitli atom seçiliyor, pasif olan seçilmiyor, entry ya minimumuna ulaşıyor ya da bütün olarak düşüyor |

Üçü Docker'sız koşuyor — maliyetler dosyada olduğu için. İzolasyon testi
kasıtlı bir ihlale karşı doğrulandı: `ProfileScopedRepository.findById`'nin
profil filtresi kaldırıldığında sekiz ucun hepsi düştü, geri konunca geçti.

### D.8.10 — Aşama 1'in son maddesi: ölçüm ile gerçek sayfa arasındaki sapma

XI-A.3'ün tamamlanma kontrolü "**ölçüm ile gerçek sayfa arasında sapma
<%3**" diyor. Bu maddeyi kapatan test (`MeasurementDriftIT`) yazıldığında sapma
**%15-32** çıktı — beş golden profilin hepsinde, hep aynı yönde: model sayfayı
gerçekte olduğundan **dolu** sanıyordu. Yönü güvenliydi (sayfa taşmıyor) ama
sonucu değildi: kullanıcının içeriğinin üçte biri sebepsiz yere dışarıda
kalıyordu.

Test, render edilen **gerçek belgeye** `\typeout{...\the\pagetotal}` ekleyip
TeX'e "bu sayfada ne kadar yer kapladın" diye soruyor ve seçimin harcadığını
sandığı puntoyla karşılaştırıyor. Üç ayrı hata buldu:

| Hata | Neydi | Ne oldu |
|---|---|---|
| **Atom maliyeti** | `height + depth + baselineSkip` (Bölüm 26.2'nin formülü) | Bir madde listesindeki kutu, sayfayı kendi yüksekliği kadar değil **satır sayısı kadar baseline** ilerletiyor. Doğrusu `satır × baselineSkip + itemsep`. Madde başına ~8 punto, yirmi maddelik bir sayfada üçte bir sayfa. |
| **Başlık bloğu** | 52.0pt | Kalibrasyon belgesi ölçümden önce `\null` koyuyordu; o boş kutu, gerçek belgede olmayan bir baseline boşluğu satın alıyordu. `\null` kaldırıldı: **45.68pt**. |
| **Entry başlığı tek sayı değil** | Her entry 22.76pt | Bölüm başlığından sonra gelen entry 22.76pt, **üstündeki işin madde listesinden sonra gelen** entry 32.0pt — arada paragraf boşluğu var. Dört işlik bir CV bunu üç kez ödüyor. Yeni sabit: `ENTRY_HEADER_AFTER_LIST`. Seçim, entry'yi açarken hangisinin geçerli olduğunu biliyor ve **ne ödediğini kaydediyor**, çünkü swap turunda geri alırken aynı sayıyı düşmesi gerekiyor. |

**Bölüm 26.3'e düzeltme.** O bölüm "satıra yuvarlama, puntoyla topla" diyor ve
gerekçesi doğru — ama satıra yuvarlamak burada bir yaklaşım değil, TeX'in
kendi aritmetiği: ardışık baseline'lar tam olarak `\baselineskip` uzaklıkta,
dolayısıyla n satırın yüksekliği tam olarak n baseline. Uyarı, *ölçümü satıra
çevirip artığı kaybetmek* için geçerli; toplama hâlâ puntoyla yapılıyor.

**Sonuç.** Sapma beş profilde de **%3'ün altında** ve hepsinde **fazla tahmin**
yönünde (senior %0.65, minimal_edge %2.4) — yani sayfa hâlâ taşmıyor, ama artık
neredeyse dolu. Kalan pay çoğunlukla başlık bloğunun sabit sayılmasından
geliyor: iletişim satırı kısa olan bir profil kalibre edilenden az yer kaplıyor.

**Kalıcı guard'lar:** `MeasurementDriftIT` (beş profil, %3), ve
`LatexCalibrationIT` artık ikinci bir bölümü, ikinci bir entry'yi, ikinci bir
listeyi **ve** listeden sonra gelen bir entry'yi de ölçüyor — tekrarlanan
mobilyanın maliyeti değişirse test düşer.

### D.9 — Frontend'i ilgilendirenler

Aşağıdakiler `atomcv-frontend` tarafında karşılığı olan maddelerdir — burası
toplu liste, **her madde ait olduğu bölümde de not olarak duruyor**, çünkü
dokümanı baştan sona okumayan biri de o bölüme baktığında görmeli:

| # | Madde | Bölümdeki notu |
|---|---|---|
| 1-4 | Run/mark kuralları | Bölüm 14.1 |
| 5 | `content_hash` düz metnin hash'i | Bölüm 16.2 |
| 6 | Sözlükler küçük harf, hata kodu büyük harf | Bölüm 35.1 |
| 7, 10, 11 | Hata kataloğu ve `params` disiplini | Bölüm 35.4 |
| 8 | ETag kapsamı | Bölüm 35.6 |
| 9 | Anonim oturum ve kayan TTL | Bölüm 35.7 |
| 24 | Bayat varyant akışı Aşama 2 | Bölüm 37.6 |
| 25-32 | Media type, şema, varyant yaması, seed | Bölüm 35.6, XI-B.9.2, EK D.6.8 |

| # | Konu | Frontend'in yapması gereken |
|---|---|---|
| 32 | **Seed profilinde artık iki sözcüklemeli bir atom var** | `senior_backend_tr` iki dili birden açıyor (`enabledLanguages: ["tr","en"]`) ve Deneyim bölümünün ilk maddesi Türkçe birincilin yanında İngilizce bir alternatif taşıyor. Sekmeleri, promote'u ve birincil-önce sıralamayı mock'suz görebilirsiniz. `make db-reset && make dev` gerekiyor: seeder mevcut bir profile dokunmuyor (P8). |
| 31 | **`?format=markdown` artık şemada** | `/profile/export` 200'ü iki media type ilan ediyor. Ayrı iki fonksiyona bölmüş olmanız doğru; `gen:api` yeniden çalıştırıldığında tipler bunu gösterecek. |
| 30 | **Operasyon id'leri adlandırıldı** | `list_2` → `listAtoms`, `create_1` → `createEntry`, `patch` → `patchSection` vb. Üretilen yüzeye isimle bağlanan bir şey varsa **kırılır**; `gen:api` sonrası bir arama gerekiyor. |
| 29 | **Şema artık `200`'leri ve `ETag`'i söylüyor** | On operasyon (`GET /sections|entries|atoms`, dört `PATCH`, üç `reorder`) başarı yanıtını ilan ediyor; her tekil kaynak yazması `ETag` başlığını da. `endpoints/profile.ts`'te elle beyan ettiğiniz yanıt tipleri ve `EntryPatch`'te genişlettiğiniz null'lanabilirlik **geri alınabilir** — `organization`, `location`, `startDate`, `endDate`, `url` şemada `["string","null"]`. `ApiError.code` ve `.status` artık zorunlu, yani `ProblemDetail`'daki yeniden-zorunlu-kılma da gereksiz. |
| 28 | **Bir sözcüklemeyi promote etmek için metni geri göndermeyin** | `PATCH …/variants/{id}` artık `content` istemiyor: `{"primary": true}` yeterli. **Bu bir hata düzeltmesidir, kolaylık değil** — metni geri gönderen istek `tone`'u da gönderiyordu, göndermezse siliyordu, yani `AtomEditor`'ün mevcut çözümü kullanıcının tonunu siliyor. `tone` artık üç durumlu: **atlanırsa korunur, `null` gönderilirse nötr registera döner.** |
| 27 | **Atom ve varyant sürümleri bağımsız** | `PATCH /atoms/{id}` atomun `version`'ını artırır, varyantlarınkine dokunmaz. Editör atom başına **iki** sürüm tutar; yanlışından kurulan bir `If-Match` eşzamanlılık hatası gibi görünen bir 412 verir. |
| 26 | **Hiçbir şeyi değiştirmeyen yazma sürümü artırmaz** | Depodakiyle aynı değerlerle `PATCH` 200 ve **aynı** sürümü döner. Otomatik kaydetme için taşıyıcı: kullanıcı yazıp geri aldıktan sonra tetiklenen debounce, açık diğer editörlerin tuttuğu sürümü geçersizleştirmez. |
| 25 | **Media type `application/json`, `If-Match: "7"`** | Bölüm 35.6'nın `application/merge-patch+json` yazması **hataydı**; öyle gönderilen istek artık **415** alıyor (önce 500 alıyordu). Şemayı izleyip `application/json` göndermeniz doğruydu, testle sabitlemeniz de. ETag'de `v` öneki yok. Ayrıca 405 (`Allow` başlığıyla), 406 ve bozuk parametrede 400 artık doğru kodla geliyor — **üç yeni ICU anahtarı**: `METHOD_NOT_ALLOWED`, `NOT_ACCEPTABLE`, `UNSUPPORTED_MEDIA_TYPE`. Hiçbiri doğru bir istemcinin göreceği hata değil; katalogda olmaları gövdenin `code`'suz kalmaması içindir. |
| 24 | **Bölüm 37.6'nın iki düğmesi Aşama 2** | `Variant.stale` Aşama 1'de **her zaman false** ve bir varyantı yeniden üreten uç yok. `VariantTabs`'ın rozeti gösterip kontrolü çizmemesi doğru karar; Bölüm 37.6 artık bunu söylüyor. Elle düzenleme işleyen tek yol. |
| 1 | `link` run'ı `href` **zorunlu**, diğer run'larda `href` **yasak** | Editör bu ikisini üretmemeli; backend içeriği reddeder. `richContent.ts` tarafında bir invariant olarak tutulmalı. |
| 2 | **Bilinmeyen mark'lar korunmalı** | İleri uyumluluk simetriktir: backend bilinmeyen bir mark'ı düşürmüyor, editör de düşürmemeli. Aksi halde daha yeni bir sürümün yazdığı işaretler, kullanıcı o cümleyi kaydettiği anda sessizce silinir. |
| 3 | `v` alanı **sunucuya ait** | Frontend `runs` gönderir; `v` göndermesi gerekmez. Gönderirse **mevcut sürümden büyük olamaz** — backend daha yeni damgayı okumayı reddeder. |
| 4 | `m` her zaman dizidir | Yanıtlarda mark'sız run bile `"m": []` taşır; `undefined` kontrolü gereksiz. |
| 5 | `content_hash` **düz metnin** hash'i | Yalnız işaretleme değişince hash değişmez. "Değişti, yeniden ölçülmeli" türü bir gösterge run yapısına değil hash'e bakmalı. |
| 6 | Sözlükler küçük harf | `kind`, `layout`, `source`, `created_by`, `tone` API'de küçük harf gider/gelir (`bullet_list`, `about_paragraph`). |
| 7 | **Sözleşme cevapları artık EK D.6'da** | `BACKEND-CONTRACT-GAPS.md` ve `backend-contract-response.md` silindi; on altı maddenin verdiktleri de, kabul edilen iki tablo da EK D.6'da. Frontend reposundaki kopyalar da silindi. Aynı desen artık iki yönlü: frontend tarafı bir doküman değişikliği gerektirdiğinde `DOC-SYNC-REQUEST.md` yazıyor, burada uygulanıyor ve dosya siliniyor (EK D.10). |
| 8 | `generations` **ETag taşımaz** | O tabloda `version` kolonu yok. Sonuç ekranı iyimser kilit isterse bu bir şema değişikliği talebidir — sessizce `If-Match` göndermek işe yaramaz. |
| 9 | Anonim süre metni | Kopya "iki saat sonra" değil **"son etkinliğinden iki saat sonra"** demeli; TTL kayıyor. Ürün dokümanındaki ifade düzeltildi, dizedeki karşılığı frontend'in. |
| 10 | **Hata kataloğu tamamlandı** | D.6.1'deki tablo her kodun `params` anahtarlarını ve tiplerini veriyor; `en.json` ve `tr.json` artık yazılabilir. Üç kod yeni: `RESOURCE_NOT_FOUND`, `VERSION_CONFLICT`, `VALIDATION_FAILED` — ICU karşılıkları gerekiyor. |
| 11 | Fazladan `params` gönderilmez | Sunucu, bildirilmemiş bir anahtarı gövdeye koymayı reddediyor. Frontend bir alan eksik diye şikâyet ederse çözüm katalogda; gövdeye elle eklenmiş bir alan hiç gelmeyecek. |
| 12 | **`type` göreli, `RESOURCE_NOT_FOUND`/`VERSION_CONFLICT` parametresiz** | `type` alanı `/errors/conflicting-preferences` biçiminde göreli gelir (alan adı koda gömülmüyor). Bilinmeyen bir yol 404 `RESOURCE_NOT_FOUND` döner, 500 değil. `INTERNAL_ERROR` (500) eklendi — beklenmeyen hatada bile gövdede `code` bulunur, yani istemcinin hata yolu her zaman çalışır. |
| 13 | **`GET /profile` yeni kullanıcıda 404 dönmez** | Profil ilk kullanımda sunucu tarafında yaratılır (EK D.8). İstemcinin "henüz profilin yok" diye ayrı bir durum taşımasına gerek yok: boş ama gerçek bir profil gelir, `completeness: 0` ile. |
| 23 | **Yeni resolution: `complete_profile`** | Sözlük dokuz eyleme çıktı. Davranışı: profil düzenleyiciyi aç. `INSUFFICIENT_PROFILE` ile birlikte geliyor ve `params.missing` hangi parçanın eksik olduğunu söylüyor (`atoms`, `sections`). Şema (`/v3/api-docs`) güncel; `npm run gen:api` yeniden çalıştırılmalı. |
| 22 | **PDF veren ilk uç: `POST /api/v1/generations/general`** | **Senkron** ve **Aşama 1'e özgü**. Gövde isteğe bağlı: `{ "maxPages": 1..10, "language": "en" }`; verilmeyen alan profilin varsayılanını kullanır. Yanıt `application/pdf` + `Content-Disposition: attachment`; **hiçbir yere kaydedilmiyor**, yani indirme bağlantısı, geçmiş listesi ya da düzenleme döngüsü henüz yok. Aşama 2'de Bölüm 35.3'ün `POST /generations` + 202 + iş akışı gelecek; **bu uca kalıcı bir ekran bağlamayın**. Hata durumları: 422 `INSUFFICIENT_PROFILE`, 422 `PAGE_LIMIT_EXCEEDED`, 409 `CONFLICTING_PREFERENCES`, 502 `COMPILATION_FAILED`, 400 `VALIDATION_FAILED`. |
| 21 | **`PAGE_LIMIT_EXCEEDED` artık gerçekten dönebilir** | Üretim isteği bir belge yerine bu hatayı döndürebilir: `actual` (çıkan sayfa) ve `limit` (istenen) parametreleriyle, 422. Sunucu içeriği kendi kısaltmayı iki kez dener; bu hataya ulaşıldıysa denemeler bitmiştir, yani "tekrar dene" düğmesi **yanlış** çözümdür — kullanıcıya sayfa sınırını artırmak veya içerik çıkarmak önerilmeli. `COMPILATION_FAILED` (502) de aynı akışta görünebilir. |
| 20 | **`GET /profile/export` hazır** | `?format=json` iç içe bir kopya verir (öğe şekilleri API ile aynı), `?format=markdown` okunacak hâlini. İkisi de `Content-Disposition: attachment` ile iner; dosya adında isim yok. Bilinmeyen biçim 400. Markdown `charset=UTF-8` bildirir. |
| 19 | **`completeness` gerçek bir sayı, ve `DELETE /profile` var** | `GET /profile` her okumada tamamlanmayı yeniden hesaplıyor (Bölüm 31.9); göstergeyi ayrıca hesaplamaya gerek yok. `DELETE /profile` profili ve altındaki her şeyi siler, **hesabı silmez** — sonraki okuma boş bir profil döndürür. `If-Match` zorunlu. |
| 18 | **Atom ve varyant uçları hazır** | Atom **içeriğiyle** yaratılır (`content` zorunlu). `PATCH /atoms/{id}` yalnız kontrolleri değiştirir; **metin `PATCH /atoms/{id}/variants/{vid}`'de** ve gönderilirse içeriğin tamamı gönderilir — ama artık **gönderilmesi zorunlu değil** (madde 28). Yanıt her atomun tüm varyantlarını **birincil önce** verir. Aynı dil+ton ikinci kez eklenemez, son varyant ve birincil silinemez (400). `href`siz bir `link` run'ı da 400 — 500 değil. |
| 17 | **Entry uçları hazır, ve `PATCH`'te "temizle" mümkün** | `GET /profile/entries` (`?sectionId=` ile süzülür) ve `POST /entries/reorder` **dokümanda yoktu**, eklendi. `PATCH`'te bir alanı **göndermemek** onu korur, **`null` göndermek** temizler — bitiş tarihini silip işi "devam ediyor" yapmanın yolu budur. Şemada bu alanlar `nullable` bir değer olarak görünür, sarmalayıcı nesne olarak değil. Entry'yi başka bölüme taşımak `PATCH` ile yapılamaz. |
| 16 | **Bölüm uçları hazır** | `GET/POST /profile/sections`, `PATCH/DELETE /{id}`, `POST /reorder`. `PATCH` yalnız gönderilen alanı değiştirir; `displayOrder` yamalanamaz, sıra `reorder` ile ve **tam liste** göndererek değişir (eksik liste 400). Silme `If-Match` ister ve **içeriğiyle birlikte** siler. Koleksiyonun her öğesi `version` taşır, yani düzenlemeden önce ikinci bir okuma gerekmez. |
| 15 | **Yazmalarda `If-Match` zorunlu, ve `preferences` `PUT` ile** | Başlıksız istek `428 PRECONDITION_REQUIRED` (yeni kod, ICU karşılığı gerekiyor), bayat etiket `412 VERSION_CONFLICT` + `retry`. `PUT /profile` **değiştirir**: gönderilmeyen alan temizlenir, yani formun tüm alanları gönderilmeli. Tercihler ayrı endpoint'te ve **`PATCH` değil `PUT`** — Bölüm 35.2'nin listesi bu satırda güncellendi. |
| 14 | **`npm run gen:api` artık çalışabilir** | Şema `/v3/api-docs` üzerinde yayınlanıyor (üretimde kapalı, lokalde ve CI'da açık). İçinde: `ResolutionAction` ve `ErrorCode` enum olarak, `ApiError` gövdesi, ve `GET /api/v1/profile` yanıtında **`ETag` başlığı**. `Profile` şemasında **`id` ve `version` alanı yok** — sahiplik oturumdan gelir, sürüm ETag'dedir. |

### D.10 — Frontend inşa notları

`atomcv-frontend`'in inşa kararları. Backend'inkiler D.1-D.8'de; bu bölüm
onun karşılığıdır ve **frontend reposunda yazılıp buraya taşınır** — `docs/`
orada salt-okunur bir kopyadır (Bölüm XI-B.1.3), dolayısıyla kaynak burasıdır.

| # | Konu | Tür | Karar |
|---|---|---|---|
| 1 | Next.js sürümü (Bölüm 5.2 "15" diyordu) | Sapma | **16.** 15.x backport dalına geçti; oradan başlamak ilk gün migration borcu demekti. `next-intl` 16'yı destekliyor. Turbopack varsayılan bundler, öyle bırakıldı. Bölüm 5.2 güncellendi. |
| 2 | `tailwind.config.ts` (XI-B.3 bekliyordu) | Düzeltme | **Yok.** Tailwind v4 CSS-first; tema token'ları `src/styles/globals.css` içinde `@theme` ile. |
| 3 | `next.config.mjs` (XI-B.3) | Düzeltme | **`next.config.ts`** — `create-next-app` tipli config üretiyor. |
| 4 | Locale yönlendirmesi | Ekleme | `src/proxy.ts`. Next 16'da `middleware.ts` **`proxy.ts` olarak yeniden adlandırıldı**. Matcher `/api`'yi dışlar: dışlamazsa her API çağrısı `/en/api/v1/...`'e yönlendirilip kırılır. |
| 5 | Client provider'ların yeri | Ekleme | Root layout'ta değil, **`[locale]/(app)/layout.tsx`** içinde. Landing ve legal hiçbir şey fetch etmiyor; app shell'in çektiği her kilobayt aksi hâlde ürünle ilk temasta ödeniyor (Bölüm 12). Ölçüldü: yalnız TanStack Query'yi taşımak **7 KB gzip** kazandırdı. |
| 6 | `NextIntlClientProvider` | Ekleme | O da `(app)` içinde: **tüm mesaj kataloğunu HTML'e serialize ediyor**, root'ta her landing ziyaretçisine legal metnin tamamını gönderiyordu. Bedeli: next-intl'in `Link`'i ve `useTranslations` çağıran client bileşenler yalnız `(app)` altında çalışıyor; dışarıda düz `<a>` + açık `/${locale}` öneki. |
| 7 | `setRequestLocale` | Ekleme | **Her sayfa ve layout'ta ayrı ayrı** çağrılmalı, yalnız parent layout'ta değil. Next layout ve page'i paralel render ediyor, parent'ın çağrısının önce koştuğu garanti değil; eksikse next-intl rotayı dinamik işaretliyor. Legal sayfalar bu yakalanana kadar on-demand render ediliyordu. |
| 8 | Typecheck | Ekleme | `tsc` tek başına yetmiyor. `PageProps`/`LayoutProps` `.next/types`'a **üretiliyor**; `npm run typecheck` önce `next typegen` çalıştırıyor. Temiz checkout'ta düz `tsc` olmayan hatalar uyduruyor, olanları kaçırıyor. |
| 9 | MSW'nin üretime sızması | Düzeltme | Bayrak `NODE_ENV`'e de bakmalı. `next build` `.env.local`'ı da okuyor, yani mock'u lokalde açık bırakan biri MSW runtime'ını kullanıcılara gönderiyor. **İki kez oldu:** ikincisinde dinamik `import()` guard'ın dışına, modül seviyesine taşınmıştı — bundler onu modül grafiğinde erişilebilir görüp chunk'ı korudu, hiçbir şey çağırmasa bile. |
| 10 | MSW worker başlatma | Ekleme | **Idempotent olmalı.** React Strict Mode effect'leri iki kez çalıştırıyor: ilk `start()` başarılı oluyor ama `setReady`'si cleanup'ta iptal ediliyor, ikincisi "cannot configure an already enabled network" ile patlıyor. Kapı hiç açılmıyor ve **tüm uygulama boş render ediliyordu.** |
| 11 | `[locale]/(app)/dev/mocks` | Ekleme | Dev-only doğrulama sayfası; üretim build'inde `notFound()` (Bölüm 51.5'in backend dev uçlarına uyguladığı kuralın aynısı). `(app)` altında başka rota olmadığı için shell, provider'lar ve worker başka türlü hiç mount olmuyordu. |
| 12 | SSE tüketimi (Bölüm 36.4) | Doğrulama | **`EventSource` MSW'nin service worker'ı üzerinden gerçek tarayıcıda doğrulandı**, frame'ler tamponlanmadan tek tek geliyor. Bölüm 36.4 olduğu gibi geçerli; `fetch` + `ReadableStream` yedeğine gerek yok. |
| 13 | Bundle bütçesi aracı (XI-B.3 `bundlesize` diyordu) | Sapma | Ne `bundlesize` ne `size-limit`: ikisi de **isim verebildiğiniz dosyaları** ölçüyor, Next ise içerik-hash'li chunk'lar üretiyor ve hangi rotanın hangisini çektiğini söylemiyor. `scripts/check-bundle-size.mjs` prerender edilmiş her rotanın script etiketlerini okuyor. |
| 14 | Bütçe eşiği (Bölüm 52.3 tek sayı veriyordu) | Karar | `bundle-budget.json`'da **üç sayı**: `sharedKb` (her rotanın ödediği taban, yalnız bağımlılık değişiminde oynar), `perRouteOwnKb` (özellik işinin kontrol ettiği pay), `totalKb` (52.3'ün tavanı). Ölçüm: taban tek başına 168.1 KB, pazarlama rotalarının kendi payı 0 KB. **Bölüm 52.3 rota sınıfına göre iki tavana ayrıldı** — gerekçe orada. |
| 15 | npm sürümü | Ekleme | **npm 11 zorunlu**, CI'da ve Dockerfile'da sabitli. Lock dosyası opsiyonel native paketleri npm 11'in çözdüğü şekilde kaydediyor; `node:22`'nin getirdiği npm 10 aynı dosyayı eksik okuyup `npm ci`'ı düşürüyor. Windows'ta `npm ci` her iki durumda da geçtiği için yalnız Linux'ta görünüyor. |
| 16 | `exactOptionalPropertyTypes` | Sapma | **Kapalı.** Her opsiyonel React prop'una sürtünme ekliyor, karşılığında tek gerçek tehlikeyi kapatıyor — o da merge-patch katmanında. Onun yerine `buildPatch()`: `undefined` anahtarları atıyor, geriye bir şey kalmazsa `null` dönüp çağıranı isteği atlamaya zorluyor. Boş bir merge-patch başarılı oluyor, hiçbir şeyi değiştirmiyor ve kaydetme göstergesini yine "saved"a çeviriyor (Bölüm 37.3) — editörün kullanıcıya yalan söylemesi. |
| 17 | e2e ortamı | Ekleme | Playwright **`next dev`'e** karşı koşuyor, **3100** portunda. MSW üretim build'inde tasarım gereği kapalı, yani backend var olana kadar üretim build'inin hiç API'si yok. Ayrı port, 3000'de asılı kalmış bir sunucunun test edilenle karışmasını engelliyor — bir kez yanlış ölçüme yol açtı. |
| 18 | shadcn primitive tabanı | Ekleme | **Radix açıkça sabitlendi** (`--base radix`). shadcn CLI'ın varsayılanı artık Base UI; Bölüm 5.2 ve 39.1 Radix diyor ve erişilebilirlik gerekçesi ona dayanıyor. |
| 19 | Legal sayfaların yeri (XI-B.3 ve 36.1 `[locale]` dışında gösteriyordu) | Düzeltme | **`[locale]` altında.** Segment dışında çevrilemiyorlar; Türk kullanıcının okuyamadığı bir gizlilik politikası gizlilik politikası değildir. İki bölüm de güncellendi. |
| 20 | `src/app/api/` (XI-B.3 ve Bölüm 36.1 gösteriyordu) | Düzeltme | **Oluşturulmadı ve oluşturulmayacak.** Lokalde aynı-origin görüntüsü `next.config.ts` rewrite'ıyla korunuyor; rewrite bizim kodumuzu çalıştırmadığı için "proxy'de iş mantığı yok" kuralı zaten ihlal edilemiyor. İki bölüm de güncellendi. |

**Bu bölüm nasıl güncellenir.** Frontend deposundaki `docs/` salt-okunur bir
kopyadır; oradaki oturum bir değişiklik gerektiğinde `DOC-SYNC-REQUEST.md`
yazar, backend deposunda uygulanır ve dosya silinir. Aynı desen sözleşme
boşluklarında da kullanıldı (D.6, D.9 · 7).
