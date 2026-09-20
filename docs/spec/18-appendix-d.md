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
| Bölüm 51.6'nın anonim testi "hiçbir tabloda satır sayısı değişmez" diyor, ama kuyruk (`jobs.anon_session_id`) ve `llm_invocations` Postgres'te | Düzeltme | Test **kullanıcı verisi tablolarına** daralır. **Kuyruğu kullanıyor** (karar: Adım 3.6) ve `V3` idempotency indeksini anonim satırları kapsayacak şekilde yeniden yazdı; testin kendisi § 51.6.1'de tersine döndü — anonim profil artık `profiles`'ta sahipsiz bir satır ve test satırların yazıldığını doğruluyor. |
| ArchUnit kuralları, modül paketleri yalnız `package-info.java` taşırken "failed to check any classes" ile düşüyor | Ekleme | Geçici olarak `archunit.properties` içinde `archRule.failOnEmptyShould=false`. **Adım 1.1 sonunda kaldırıldı:** artık yalnız `renderersAreDeterministic` kuralı boş kümede çalışıyor ve izni tek başına taşıyor (`allowEmptyShould(true)`). Global ayar açıkken bir paket adı değişirse ilgili kural hiçbir şeyle eşleşmeyip sessizce geçerdi. |
| Bölüm 47.1'deki `--spring.flyway.migrate-only=true` | Düzeltme | **Böyle bir Spring Boot özelliği yok.** Karara bağlandı (2026-09-16): migration **açılışta, tek örnekle** koşuyor ve ayrı bir adım yok. Koşulu yazılı — aynı anda tek backend örneği — ve ölçeklenme günü yeniden açılır; rollback kodu geri alır şemayı değil. Kayıt § 47.1 ve `docs/vps-dagitim-plani.md` § 0. |
| OWASP dependency-check (Bölüm 47.1) | Sapma | Kullanılmıyor: NVD API anahtarı istiyor, anahtarsız taraması yavaş ve oran-sınırlı. Aynı kapsamı **Dependabot** derleme maliyeti olmadan veriyor. |
| Lombok (XI-A.2 Adım 0.1'in bağımlılık listesinde var, örnek `build.gradle.kts`'te yok) | Düzeltme | **Kullanılmıyor.** Değer nesneleri record, gerisi düz constructor. |
| Satır sonları ve dosya izinleri | Ekleme | `.gitattributes` (`* text=auto eol=lf`, `.bat`/`.cmd` için CRLF) ve `gradlew`'in 100755 kalması. Windows'ta geliştirilip Linux runner'da çalışan bir repo, bu ikisi olmadan sessizce kırılır: CRLF'li ya da 100644 modlu `gradlew` her CI koşusunu düşürür. |
| Entegrasyon testlerinin veritabanı | Ekleme | Tek bir Postgres container'ı, ortak bir taban sınıfta **statik başlatılıp hiç durdurulmuyor** (Testcontainers'ın singleton deseni). `@Testcontainers` + `@Container` ilk test sınıfından sonra container'ı durdurur, ama Spring'in önbelleklediği context hâlâ o portu gösterir ve sonraki her sınıf "connection refused" ile düşer. Ryuk, JVM kapanınca temizliyor. |
| Hibernate istatistikleri | Ekleme | Tüm entegrasyon suite'inde açık. Bölüm 52.2'nin altı sorgu bütçesi bununla ölçülüyor; özelliği tek bir sınıfın üstünde tutmak, o sınıf yeniden düzenlendiği anda sayacın sıfır okumasına ve **testin yine geçmesine** yol açtı. Alt sınır iddiası (`isBetween(4, 6)`) yakaladı. |
| Gradle dağıtımının doğrulanması | Ekleme | `gradle-wrapper.properties` içinde `distributionSha256Sum`, yayınlanan toplama karşı doğrulanmış. Wrapper, indirdiği arşivi aksi halde denetlemez. |
| Entegrasyon testlerinin yeri | Ekleme | Ayrı `integrationTest` source set'i, `check`'e **bağlanmadan**. `gradlew test` Docker'sız ve hızlı kalır; `gradlew build` Docker Desktop kapalıyken de çalışır. CI ikisini ayrı adım olarak çalıştırır. |
| Commit kancası (XI-A.1.4 elle `.git/hooks/pre-commit` yazıyor) | Sapma | **pre-commit framework** + `.pre-commit-config.yaml`. `.git/hooks/` versiyonlanmaz; elle yazılan kanca ikinci makinede yoktur ve kimse fark etmez. İlk üç commit kancasız geçtikten sonra fark edildi. |
| Formatlama kapısı (Bölüm 47.1 `spotlessCheck` çalıştırıyor) | Ekleme | **Spotless yapılandırıldı ve kapı CI'nın ilk adımı** (`ci.yml`, `Format`): burada bir hata tek satırlık bir düzeltmedir, ve testlerden önce koşması onu en ucuz yerde bulur. Bu satır bir süre "formatter yok, eklensin mi açık karar" diyordu; karar verilip uygulandı, kayıt geride kaldı (denetim, beşinci tur). **Yerelde yeni bir dosyadan sonra `gradlew spotlessApply`** — CRLF yüzünden yerelde düşüp CI'da geçen tek kontrol budur. |
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

**Ekleme — `Scope.EPHEMERAL` Aşama 1'de yoktu.** Bölüm 41.3 iki kapsam
tanımlıyor, ama denetimli bir üretim yolu olmadan eklenen ikinci sabit,
kontrolü atlamanın yolu olurdu.

> **Aşama 3'te indi ve bu kayıt geride kaldı** (denetim, 2026-09-16). Sabit
> `ProfileRef`'te duruyor; denetimli üretim yolu § 41.3.1'in anlattığı
> **`AnonymousSessionId`** — yalnız oturumu sorabilen modülün üretebildiği bir
> değer, yani elinde düz bir String olan kazara anonim kapsama ulaşamıyor.

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

**Taşındı → [`08b-api-contract.md`](08b-api-contract.md).**

Ve bir süre iki kopya vardı. EK D monolitten bu dosyaya taşınırken içindeki
D.6 de geldi; canlı olanı `08b`'ye çoktan çıkmıştı, yani **ikisi de "tek kaynak
burasıdır" diyen iki tablo** kaldı ve ayrıştılar (denetim, altıncı tur).
Buradaki kopya 328 satırdı, `08b`'ninki 600; eksik olanlar arasında
`FEATURE_REQUIRES_ACCOUNT`'ın kapalı kümesi, `params.reason`'ın yedi değeri,
`COVER_LETTER_REJECTED`'ın sözlüğü ve D.6.9'un tamamı vardı. Dahası burada
**kapanmış bir soru açık duruyordu** — kota gününün zaman dilimi, `F-007`'nin
UTC diye cevapladığı — ve ölü bir yol anılıyordu (`/ingestion/cv`).

Ders § 35.2.1'in dersiyle aynı, üçüncü kez: **bir tabloyu taşımak eski kopyayı
silmiyor.** Silindi; gövdedeki `EK D.6.x` atıflarının tamamı `08b`'yi bulur.

### D.7 — İlerleme kaydı

**Taşındı → [`../STATUS.md`](../STATUS.md).**

Bu bölüm "her dilim bittiğinde güncellenir" ve "frontend için tek adres
burasıdır" diyordu; **son güncellenişi Aşama 1'in kapanışıydı** (denetim,
altıncı tur). Aşama 2, 3 ve 4 hiç işlenmedi, üç satırı çoktan kapanmış
kararları açık gösteriyordu (kota zaman dilimi, anonim kuyruk, CI imaj
taraması — sonuncusu `deploy.yml`'de indi), ve taşıdığı test sayıları
312/132/44 idi.

Sebebi yapısal, unutkanlık değil: **iki repo arasındaki durum kanalı
`STATUS.md`**, ve iki ilerleme kaydı tutmak ikisinin ayrışmasını beklemektir.
Bu dosya sapmaların kaydı olarak kalıyor; nerede olunduğunu `STATUS.md`
söylüyor.

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
| Ölçüm anahtarı | ~~Sapma~~ · **kapandı** | Bölüm 22.4 `{variantId}:{customizationId}:{templateVersion}` diyor ve Aşama 1'de yalnız `variantId` uygulanmıştı — özelleştirme entity'si yoktu. **Aşama 4'te geldi** (`SavedCustomization`, Katman A+B) ve anahtar da genişledi: `MeasurementRequest` üç parçayı da taşıyor, yani bölümle uygulama artık aynı şeyi söylüyor. Bu satır on bir ay boyunca kapanmış bir sapmayı açık gösteriyordu (denetim, beşinci tur). |
| Eksik ölçüm | Ekleme | Bir varyantın maliyeti log'da yoksa **diğerleri yine yazılır**. Tek bir eksik ölçüm için tüm profili ölçümsüz bırakmak, seçimin tahmine düşeceği tek atom yerine hepsini tahmine düşürürdü (Bölüm 26.5). |
| `FontMetricEstimator` ertelendi | ~~Açık~~ · **kapandı** | Bölüm 26.2'nin 1. katmanı font dosyalarını **backend tarafında** okumayı gerektiriyor ve fontlar container imajında; tüketicisi çıkana kadar bekletildi. **Adım 1.8'de indi**, ve FontBox'sız: `RenderCostEstimator`, bağımlılıksız ve kasten daha kötümser, tek sözü gerçek derleyiciden asla az yazmaması (EK D.8.7). Bu satır bir aşama boyunca inmiş bir işi açık gösterdi (denetim, altıncı tur). |

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
| ~~**`POST /api/v1/generations/general`**~~ · **silindi (Adım 2.6)** | Ekleme | Bölüm 35.3'ün `POST /generations`'ı 202 + iş döndürüyor, çünkü içinde LLM var. Genel modda LLM de kuyruk da yok: bu uç belgeyi **doğrudan** döndürüyor (`application/pdf`, `Content-Disposition: attachment`, `Cache-Control: no-store`). Aşama 1'e özgü ve öyle işaretli; kuyruklu sözleşme üretim kaydıyla birlikte Aşama 2'de gelecek. Gövde **isteğe bağlı**; `maxPages` ve `language` verilmezse profilin kendi varsayılanları geçerli. **Aşama 2'de kuyruklu sözleşme gelince uç kaldırıldı** (§ 08-api.md, Adım 2.6); burada duruyor çünkü neden var olduğunu anlatan tek kayıt bu. |
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
| Beraberlikler id ile çözülüyor, ve id kalıcı değil | ~~Bulgu~~ · **cevaplandı** | Aynı puanı **ve** aynı maliyeti taşıyan iki atom arasında Bölüm 19.6'nın tie-break'i id'ye bakıyor. Veritabanındaki bir profil için id sabit, dolayısıyla çıktı sabit; ama aynı içerik yeniden içe aktarılırsa ikisinden diğeri seçilebilir. Golden test bu yüzden "aynı atomlar" değil **"aynı sayıda atom ve aynı punto"** diyor. **İçerikten türeyen bir tie-break yazılmadı, ve gerek kalmadı:** Adım 2.7 sonrası kova genişliği (§ 19.6) ikincil skoru araya soktu, yani id son çare olarak kaldı ve çok daha seyrek ulaşılıyor. Anonim devralma da satırları **kopyalamıyor, sahipleniyor** (§ 41.3.3) — id'ler değişmiyor, yani bulgunun adlandırdığı senaryo da oluşmuyor. |
| Atomsuz entry hiç görünmüyor | ~~Bulgu~~ · **kapandı** | Seçim atom üzerinden çalışıyordu; hiç atomu olmayan bir entry (yalnız derece satırı olan bir eğitim kaydı) aday bile olmuyordu. **Gerçek çözüm indi** (2026-08-28): entry'nin kendisi **başlık-adayı** oluyor, maliyeti `ENTRY_HEADER`, skoru entry'den geliyor ve minimum kısıtı ona uygulanmıyor — Bölüm 20.2 bunu ve `headerOnlyEntries`'i kaydediyor. |
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
| 24 | ~~**Bölüm 37.6'nın iki düğmesi Aşama 2**~~ · **ikisi de çalışıyor** | Satır `Variant.stale`'in her zaman false olduğunu ve yeniden üreten bir uç bulunmadığını söylüyordu; ikisi de Aşama 3'te değişti ve **satır değişmedi** (denetim, altıncı tur). Bugün: bir sözcüklemeyi düzenlemek ondan türeyen her şeyi bayatlıyor (`VariantSynchronization`), ve "İngilizceyi yeniden üret" `PATCH .../variants/{id}` gövdesinde `{"userEdited": false}` — bayat olanı **hemen** çeviri kuyruğuna veriyor. "Benim halimi koru" hiçbir şey göndermemek. Aksiyon `B-115`'te. |
| 1 | `link` run'ı `href` **zorunlu**, diğer run'larda `href` **yasak** | Editör bu ikisini üretmemeli; backend içeriği reddeder. `richContent.ts` tarafında bir invariant olarak tutulmalı. |
| 2 | **Bilinmeyen mark'lar korunmalı** | İleri uyumluluk simetriktir: backend bilinmeyen bir mark'ı düşürmüyor, editör de düşürmemeli. Aksi halde daha yeni bir sürümün yazdığı işaretler, kullanıcı o cümleyi kaydettiği anda sessizce silinir. |
| 3 | `v` alanı **sunucuya ait** | Frontend `runs` gönderir; `v` göndermesi gerekmez. Gönderirse **mevcut sürümden büyük olamaz** — backend daha yeni damgayı okumayı reddeder. |
| 4 | `m` her zaman dizidir | Yanıtlarda mark'sız run bile `"m": []` taşır; `undefined` kontrolü gereksiz. |
| 5 | `content_hash` **düz metnin** hash'i | Yalnız işaretleme değişince hash değişmez. "Değişti, yeniden ölçülmeli" türü bir gösterge run yapısına değil hash'e bakmalı. |
| 6 | Sözlükler küçük harf | `kind`, `layout`, `source`, `created_by`, `tone` API'de küçük harf gider/gelir (`bullet_list`, `about_paragraph`). |
| 7 | **Sözleşme cevapları artık EK D.6'da** | `BACKEND-CONTRACT-GAPS.md` ve `backend-contract-response.md` silindi; on altı maddenin verdiktleri de, kabul edilen iki tablo da EK D.6'da. Frontend reposundaki kopyalar da silindi. Aynı desen artık iki yönlü: frontend tarafı bir doküman değişikliği gerektirdiğinde `DOC-SYNC-REQUEST.md` yazıyor, burada uygulanıyor ve dosya siliniyor (EK D.10). |
| 8 | `generations` **ETag taşımaz** | O tabloda `version` kolonu yok. Sonuç ekranı iyimser kilit isterse bu bir şema değişikliği talebidir — sessizce `If-Match` göndermek işe yaramaz. |
| 9 | Anonim süre metni | Kopya "iki saat sonra" değil **"son etkinliğinden iki saat sonra"** demeli; TTL kayıyor. Ürün dokümanındaki ifade düzeltildi, dizedeki karşılığı frontend'in. |
| 10 | **Hata kataloğu tamamlandı** | Her kodun `params` anahtarları ve tipleri yayımlanıyor; `en.json` ve `tr.json` buradan yazılır. Üç kod o gün yeniydi: `RESOURCE_NOT_FOUND`, `VERSION_CONFLICT`, `VALIDATION_FAILED`. **Tablo artık `error-catalogue.md`'de ve `ErrorCode`'dan üretiliyor** — bu satır elle yazılmış kopyayı gösteriyordu ve `B-110`/`B-111` o kopyanın ayrıştığını ölçtü. |
| 11 | Fazladan `params` gönderilmez | Sunucu, bildirilmemiş bir anahtarı gövdeye koymayı reddediyor. Frontend bir alan eksik diye şikâyet ederse çözüm katalogda; gövdeye elle eklenmiş bir alan hiç gelmeyecek. |
| 12 | **`type` göreli, `RESOURCE_NOT_FOUND`/`VERSION_CONFLICT` parametresiz** | `type` alanı `/errors/conflicting-preferences` biçiminde göreli gelir (alan adı koda gömülmüyor). Bilinmeyen bir yol 404 `RESOURCE_NOT_FOUND` döner, 500 değil. `INTERNAL_ERROR` (500) eklendi — beklenmeyen hatada bile gövdede `code` bulunur, yani istemcinin hata yolu her zaman çalışır. |
| 13 | **`GET /profile` yeni kullanıcıda 404 dönmez** | Profil ilk kullanımda sunucu tarafında yaratılır (EK D.8). İstemcinin "henüz profilin yok" diye ayrı bir durum taşımasına gerek yok: boş ama gerçek bir profil gelir, `completeness: 0` ile. |
| 23 | **Yeni resolution: `complete_profile`** | Sözlük dokuz eyleme çıktı. Davranışı: profil düzenleyiciyi aç. `INSUFFICIENT_PROFILE` ile birlikte geliyor ve `params.missing` hangi parçanın eksik olduğunu söylüyor (`atoms`, `sections`). Şema (`/v3/api-docs`) güncel; `npm run gen:api` yeniden çalıştırılmalı. |
| 22 | ~~**PDF veren ilk uç: `POST /api/v1/generations/general`**~~ · **uç silindi** | Aşama 1'e özgü senkron bir uçtu ve satırın kendisi "bu uca kalıcı bir ekran bağlamayın, Aşama 2'de `POST /generations` + 202 gelecek" diyordu. Geldi, ve uç **kaldırıldı** — `GenerationController` nedenini yazıyor: kuyruk da geçmiş de yokken vardı, ikisi de var. Satır bir aşama boyunca silinmiş bir ucu canlı gibi anlatmaya devam etti, ve D.9 frontend'in okuduğu tablo (denetim, beşinci tur). Bugünkü akış Bölüm 35.3'te. |
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
