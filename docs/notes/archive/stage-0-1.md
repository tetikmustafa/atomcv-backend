# İnşa Notları Arşivi — Aşama 0 ve Aşama 1 (D.1-D.5)

> Kapanmış aşamaların sapma/ekleme/düzeltme kayıtları. **Rutin okunmaz.**
> Yalnız "bu karar neden böyle alınmış?" sorusunda `rg` ile aranır.

---

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
| `m` alanının zorunluluğu iki yönde farklıdır | Düzeltme | D.9 · 4 "`m` her zaman dizidir" diyor, şema ise `m`'i opsiyonel gösteriyor. İkisi de doğru, ama farklı yönler için: **sunucu her zaman yazar** — işaretsiz run bile `"m": []` taşır — **istemci ise atlayabilir**, atlanmışsa boş dizi sayılır. `Content` tek şema ile hem okuma hem yazma taşıdığı için OpenAPI bunu ayrı ayrı söyleyemiyor; şemayı zorunlu yapmak her yazana `[]` göndertirdi, ikiye bölmek iki tip demekti. Çelişki dokümanda kapanır, şemada değil (`spec/08b-api-contract.md` § D.6.4). `v` aynı şekle sahip ve düzeltme gerektirmiyor: sunucuya ait, yazmada atlanır, açıklaması bunu zaten söylüyor. |

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
