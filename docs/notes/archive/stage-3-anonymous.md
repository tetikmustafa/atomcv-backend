# Aşama 3 · Adım 3.6 — anonim mod (kapandı)

> Altı dilim, 2026-08-26/28. Kalıcı kararlar `spec/`'te: § 35.7.1 (yetenekler),
> § 41.3.1 (kalıcı olmayan profil), § 41.3.2 (işin sahibi), § 41.3.3
> (yükseltme), § 44.1.1 (adres kotası), § 31.6.3 (anonim yükleme), § 44.2
> (iade öznesi). Burada kalan, kararların değil **inşanın** kaydı.

## Adım 3.6 — anonim mod · 6/6 · kapandı

Dilim 1-2 (oturum, kalıcı olmayan profil) § 35.7.1 ve § 41.3.1'de; dilim 3
(adres kotası) § 44.1.1'de; dilim 4 (işin anonim sahibi) § 41.3.2'de, ve
`jobs (user_id, idempotency_key)` carry-over'ı `V3` ile kapandı; dilim 5
(anonim yükleme) § 31.6.3 ve § 44.2'de; **dilim 6 (yükseltme) § 41.3.3'te**.

**Taşınan carry-over kendiliğinden kapandı.** "Atom id'leri her içe aktarımda
yeniden üretiliyor, yükseltme buna çarpacak" diyordu; çarpmadı, çünkü yükseltme
**yeniden içe aktarmıyor** — anonim profilin satırlarını olduğu gibi, kendi
id'leriyle yazıyor ve profil satırını anonim id ile açıyor. Kopyalayıcı yok,
dolayısıyla kopyalayıcının unutacağı alan da yok. İçe aktarımlar arası id
kararsızlığı hâlâ duruyor ama yükseltmenin sorunu değil.

**Ders — entegrasyon testi işleyiciyi çağırmıyorsa işleyiciyi korumuyor.**
İlk hali `EphemeralProfileWriter`'ı doğrudan çağırıyordu; anonim dalı kalıcı
yazmaya çevirdiğimde **geçti**. Yazıcı zaten bunu yanlış yapabilecek parça
değildi. Yalnız LLM aşaması taklit edilerek gerçek işleyici koşturulunca ihlal
üç testi birden düşürdü. *Ekilen ihlalin hangi testi düşürdüğüne bak; hiçbirini
düşürmüyorsa test yanlış yerde duruyor.*

**Bulgu — `AnomalyDetectorIT` § 44.3'ün frenini arkasında bırakıyordu.** Bayrak
ayarlanmamışken açık sayılıyor; fren çekili kalınca paylaşılan bağlamda ondan
sonra koşan her üretim 503 alıyordu. On altı hata, dört ilgisiz sınıfta, ve
kendi testleri geçiyordu. **Önemli olan sınıfın arkasında bıraktığı durum.**

**Canlı — hesabın *boş* profil satırı yükseltmeyi engelliyor.** Kontrol
"profil satırı var mı", "içinde bir şey var mı" değil. Bir kez giriş yapıp
uygulamayı açan herkeste boş satır oluşuyor (`ProfileResolver.own` tembel
yaratıyor); o kişi çıkış yapıp anonim çalışırsa `kept_existing` alıyor ve emeği
gidiyor. Dar bir durum, ama **sessiz**: satırın boş olduğunu kontrol etmek ya da
birleştirme akışı, ikisi de ürün kararı istiyor.

**Canlı — anonim işler yükseltmede taşınmıyor.** İş `anon_session_id` ile
kapsanıyor; giriş sonrası kişi kendi çıkarım işini artık göremiyor. Bugün zararı
yok (iş yükseltmeden önce bitmiş oluyor), ama anonim *üretim* inince değişir.

**Canlı — anonim oturumun kullanıcı indeksi yok**, yani "bu kişinin bütün
oturumlarını iptal et" diye bir işlem de yok; onu bitiren tek şey TTL'i.

**Canlı — `profiles` tablosu entegrasyon paketinde hiç boş değil** (`DevSeeder`
`local` altında bir profil ekiyor). Anonim gizlilik iddiası bu yüzden "hiç satır
yok" değil, **"satır sayısı değişmedi"** diye kuruluyor.

**Canlı — testler kimliksiz duruma çözülmeyen bir çerezle ulaşıyor**, ikinci bir
bağlamla değil. `local` altında çerezsiz her istek dev kullanıcısı; bayat çerez
**bilinçli olarak** local-dev isteği sayılmıyor.

**Dikkat — `git checkout --` ekilmiş ihlali geri alırken commit edilmemiş
gerçek değişikliği de alır.** İhlal denemesinden **yedek kopyadan** dön.

## Dersler (notlardan taşındı, 2026-08-28)

**Entegrasyon testi işleyiciyi çağırmıyorsa işleyiciyi korumuyor.** Anonim
yükleme testi önce yazıcıyı doğrudan çağırıyordu; dalı kalıcı yazmaya
çevirdiğimde **geçti**. Yazıcı zaten bunu yanlış yapabilecek parça değildi.
*Ekilen ihlalin hangi testi düşürdüğüne bak; hiçbirini düşürmüyorsa test yanlış
yerde duruyor.*

**Taşınan carry-over kendiliğinden kapandı** — yükseltme yeniden içe
aktarmıyor, satırları kendi id'leriyle yazıyor. İçe aktarımlar arası id
kararsızlığı duruyor, yükseltmenin sorunu değil.

---

## Akışın altı dilimi — yuvarlanan kayıt (indi `current.md`'den 2026-09-09)

> Altı dilim de indi ve son cümlesi akışın kapandığını söylüyor; kalıcı
> kararların hepsi `spec/`'te (§ 51.6.1, § 35.7.2-.5). Bir satırı kendi
> içinde bayat: `dailyGenerationQuota` 0 değil, iki cümle sonra beşe
> döndüğü yazıyor.

- **Anonim akış inşa ediliyor** (karar 2026-09-09: CV'den profil, düzenleme,
  ilana göre üretim; veri saklanmaz, kota düşük, ön yazı yok). Dilim (i) indi:
  **anonim profil artık Redis belgesi değil, sahibi olmayan + `expires_at`
  taşıyan satır.** Kalıcı sapma, altı yere işlendi — gerekçe, yedek bedeli ve
  zorlanan altı iddia **§ 51.6.1**'de. `dailyGenerationQuota` hâlâ 0
  (`B-079`); üretim dilimi inince 5'e döner. **(ii) de indi:** profil uçları
  anonim oturumu kabul ediyor (`CallerProfiles`) ve § 35.7'nin üç limiti artık
  *zorlanıyor* (§ 35.7.2, `B-081`). **(iii) ve (vi) indi:** anonim üretim
  çalışıyor, kota beşe döndü (§ 35.7.3, `B-082`). **Migration gerekmedi** —
  `generations.profile_id` cascade ediyor — ve **sayfa garantisi kendiliğinden
  sağlandı**: `measureMissing` boru hattının içinde. **(iv) de indi:** anonim
  içe aktarım ve üretim challenge istiyor (§ 35.7.4, `B-083`). **(v) ile akış
  kapandı:** hesap açmak üretimleri de taşıyor (§ 35.7.5), bir olayla — çağrı
  `profile` → `generation` döngüsü olurdu.
