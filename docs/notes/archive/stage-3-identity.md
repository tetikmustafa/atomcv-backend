# Arşiv — Aşama 3, kimlik dilimleri 1-2

> Kapalı dilimlerin inşa kayıtları. **Kalıcı kararlar `spec/`'te**, burası
> yalnız nasıl bulunduklarını taşır. `current.md` sınırını aştığı için taşındı
> (2026-08-26).
>
> Hâlâ canlı olan iki madde `current.md`'de bırakıldı: `citext` karşılaştırma
> tuzağı ve `LocalDevUser` sapması.

---

### Adım 3.3 · dilim 1 — oturum omurgası

**Sapma — `LocalDevCurrentUser` "kimlik gelince bütünüyle silinecek" diyordu;
silinmedi, ikiye bölündü.** Oturumlar, oturum *başlatmanın* herhangi bir
yolundan önce indi: OAuth bir sonraki dilim. Sınıfı silmek `make dev`'e
kullanıcısı olmayan bir veritabanı ve kimse olamayan bir tarayıcı bırakırdı.
Kalanı `LocalDevUser` (yalnız satırı basan tohum) ve
`identity.service.LocalDevSessions` (yalnız `sid` çerezi **hiç yokken**
cevaplayan yedek). Gerçek çerez her zaman kazanır, ikisi de `@Profile("local")`.
Gerçek giriş inince ikisi de gider.

**Ekleme — `AUTHENTICATION_REQUIRED` (401).** Katalogda oturumsuz isteğin
karşılığı yoktu. Kalıcı olduğu için `spec/08b-api-contract.md` § EK D.6'ya
işlendi; frontend'e `B-045`.

**Ekleme — hesabın yetenek kümesi.** § 35.7 yalnız anonim gövdeyi yazmıştı.
Karar `spec/08-api.md` § 35.7'ye işlendi; frontend'e `B-046`.

**Ekleme — oturum deposu Spring Session değil, elde yazıldı.** Kayan TTL
(§ D.6.6) ve Adım 3.6'nın anonim→hesap devri `SessionRepository`'nin etrafından
dolaşmayı gerektirirdi. Spring Security yalnız filtre zinciri ve CSRF için
duruyor; kimliği o taşımıyor. `SessionStore` kullanıcı başına ikinci bir anahtar
tutuyor (`sess:user:{id}`) — Bölüm 40.1'in JWT'ye karşı öne sürdüğü "iptal
anında" iddiasının tek dayanağı o, ve sonradan eklenirse o ana kadarki her
oturum iptal edilemez kalırdı.

**Ekleme — `sid` çerezinde `secure` yalnız `local`'de kapalı.** Safari
`http://localhost` üzerinde `Secure` çerezi reddediyor, Chrome ve Firefox
kabul ediyor. `application.yml` açık tutuyor, `application-local.yml`
kapatıyor, üretim hiçbir şeyi geçersiz kılmıyor. `SessionProperties` noktayla
başlayan bir `domain`'i **açılışta reddediyor** — Adım 3.3'ün uyarısı yazı
değil kural.

**Testteki iki tuzak `CLAUDE.md`'ye yazıldı** (iç içe `@TestConfiguration`
miras alınan taban sınıfta bulunmaz; `@WebMvcTest` kendi zincirini kurar).
Burada ikinci kopyası durmasın.

**Redis artık entegrasyon paketinde gerçek.** Taban sınıfa bir konteyner
eklendi. Yoksa `SessionStore` her aramada başarısız oluyor — ve tam olarak
tasarlandığı gibi, "kimse giriş yapmamış" diye — yani oturum testi bir kesintiye
karşı iddiada bulunurdu. Bölüm 18.6'nın analiz önbelleği de aynı yokluğu fark
edilmeden hoş görüyordu.

**Açık kalan:** giriş yolu yok (OAuth · dilim 2), magic link (dilim 3), üç
katmanlı rate limit ve Turnstile (dilim 4). `revokeAllFor` yazıldı ve sınandı
ama henüz çağıran yok — rol değişimi ve hesap silme onu isteyecek.


### Adım 3.3 · dilim 2 — OAuth

Kalıcı kararların tamamı `spec/10-security.md` § 40.6.1'e yazıldı; burada
yalnız izleri ve **koddan öğrenilenler** duruyor.

**Düzeltme — `citext` kolonu `varchar` parametreyle aranırsa büyük/küçük harf
duyarlı arar.** JPA'nın türettiği `findByEmail`, JDBC parametresini `varchar`
bağlıyor; Postgres `citext = varchar`'ı citext'i `text`'e indirerek çözüyor.
Sonuç iki yarının çelişmesi: **UNIQUE index duyarsız koruyor, sorgu duyarlı
arıyor** — var olan hesap bulunamıyor, giriş "yeni kişi" diyor, insert
`users_email_key`'de ölüyor ve kullanıcı 500 görüyor. Çözüm parametreyi
`CAST(:email AS citext)` ile yukarı çevirmek; aynı index'i de kullanıyor.
`OAuthApiIT` yalnız harf büyüklüğü farkeden bir adresle iki kez giriyor — bunu
o buldu. **Aynı tuzak `users.email`'e dokunan her yeni sorguda var.**

**Düzeltme — çok kuruculu bir record'u Spring Boot bağlamıyor.**
`Registration`'a kolaylık olsun diye eklenen ikinci kurucu, her sağlayıcıyı
sessizce yapılandırılmamış bıraktı; belirti, hiç görünmeyen bir giriş
düğmesiydi. Kolaylık artık statik `of(...)` fabrikası.

**Ekleme — `SignInAccounts` kullanıcı kapsamlı değil.** Mutlak kural 3'ün tek
istisnası: giriş, *kullanıcının kim olduğunu hesaplama* eylemi ve hesaplamakta
olduğu cevapla kapsanamaz. Yerine geçen şey cephenin darlığı — id alan bulucu
yok, listeleyen yok. ArchUnit'e modülün satırı eklendi ve **kasıtlı ihlalle
düştüğü görüldü**. Metot eklenecekse ölçüt: saldırganın cevabını istediği bir
soruyu yanıtlıyorsa kapsamlı bir repository'nin arkasına ait.

**Ekleme — `UserAccount.email` `@Column(columnDefinition = "citext")` şart;**
onsuz Hibernate `validate` açılışta düşüyor (`citext (Types#OTHER)` vs
`varchar`).

**Ekleme — yerel giriş kısayolu kapatılabilir** (`LOCAL_DEV_SESSION=false`).
Açıkken çıkış yapmak dev kullanıcısına düşürüyor ve tarayıcı tekrar girişli
görünüyor — logout'un çalıştığını izlemeye çalışırken kafa karıştırıcı.

**Açık kalan:** magic link (dilim 3), üç katmanlı rate limit + Turnstile
(dilim 4). `LocalDevUser` ve `LocalDevSessions` hâlâ duruyor — entegrasyon
paketinin 27 sınıfı çerezsiz istek atıyor; onları gerçek oturuma taşımak ayrı
bir test-altyapısı dilimi.
