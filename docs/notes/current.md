# İnşa Notları — Aktif

> Kural: bu dosya **200 satırı geçmez**. Aşama bitince `archive/`'a taşınır, boş başlanır.
> Kayıt tipleri: **Sapma** (doküman başka diyor) · **Ekleme** (doküman sessiz) · **Düzeltme** (doküman yanlış).
>
> Bir sapma kalıcıysa `spec/`'e işlenir ve buradan silinir.

**Aktif aşama:** Aşama 2 — ilana özel üretim.
**Plan:** `spec/14-build-guide.md` § XI-A.5, Adım 2.1-2.7 (LLM sağlayıcı hesapları →
gateway → Faz A → embedding → Faz B → kuyruk ve SSE → kota ve maliyet); aynı aşamanın
gerekçesi `spec/13-development.md` § 55, kapanış kontrol listesi § XI-A.5 sonunda.
Aşama 1'in kayıtları `archive/stage-1.md`'de, Aşama 0 ve öncesi `archive/stage-0-1.md`'de.

---

## Aşama 1'den taşınan kısıtlar

Bunlar sapma değil, **bilinçli eksikler**. Sorulmadan "düzeltilmez" — her biri
bir sonraki aşamanın işi ve erken doldurmak kararı yanlış yerden verdirir.

| Eksik | Ne zaman | Neden şimdi değil |
|---|---|---|
| `atoms.embedding` eşlenmemiş | Aşama 2 | `vector(1024)`'ün Hibernate tipi yok ve embedding'i hesaplayan bir şey de yok |
| `ProfileRef.Scope` yalnız `PERSISTENT` | Aşama 3 | `EPHEMERAL`'ı anonim akıştan önce eklemek, üretmenin denetimli yolu yokken sahiplik kontrolünün etrafından dolaşmanın yolu olurdu |
| `tags` / `atom_tags` entity'siz | Aşama 2 | Aşama 2 skorlamasından önce okuyan yok |
| `generations`'a yazan yok | Aşama 2 | Hat `GeneratedDocument` döndürüyor; kalıcı kayıt, kuyruk ve `GET /generations/{id}/download` birlikte gelir (`spec/08b-api-contract.md` § D.6.3) |
| `PipelineError` yalnız üç durum | Aşama 2 | Kalanları erken eklemek, frontend'in mesajlarının isteyeceği `params`'ı tahmin etmek olurdu |
| ATS raporu ve `FitReport` yok | Aşama 2 | Biri PDF metin çıkarımı, diğeri Faz A istiyor (`spec/06-pipeline-d-g.md` § 23) |
| `UserScopedRepository`'de `findAll` yok | — | Bölüm 41.2 parçacığı `findByUserId` çağırıyor, o da `JpaRepository`'de yok. Alt sınıflar kendi daraltılmış bulucularını ekler |

## Isırmadan önce ele alınacak iki bulgu

**Atomsuz entry sayfaya hiç çıkmıyor.** Seçim atom atom çalışıyor, dolayısıyla
altında madde olmayan bir diploma satırı aday bile değil. Golden fixture'lar her
eğitim entry'sine bir atom vererek etrafından dolaşıyor; gerçek çözüm
`spec/05-pipeline-a-c.md` § 20.2'nin modelini değiştiriyor ve Aşama 2'ye ait.

**Eşitlik atom id'siyle bozuluyor, id'ler her içe aktarımda yeniden üretiliyor.**
Aynı skora *ve* aynı maliyete sahip iki atom, aynı içerik yeniden içe
aktarıldığında yer değiştiriyor — Aşama 3'teki anonim profil devralma tam olarak
bunu yapacak. İçerikten türetilen bir eşitlik bozucu düzeltir; bilinçli karar
verilmeli.

## Aşama 1'den devredilen açık kararlar

- **Üretimde migration nasıl çalışacak.** `spec/11-operations.md` § 47 dağıtım
  öncesi `--spring.flyway.migrate-only=true` ile bir adım gösteriyor; bu gerçek
  bir Spring Boot property'si değil. Flyway şu an üretimde de açılışta çalışıyor.
- **CI'a imaj taraması.** Dockerfile artık var, yani Trivy'nin yanlış-yapılandırma
  taraması onu görüyor; ama derlenen imaj taranmıyor. Bu, CI'da bir build (birkaç
  GB) istiyor ve registry push'uyla birlikte gelir.
- **Spotless eklenecek mi.** `spec/11-operations.md` § 47.1 `spotlessCheck`
  çalıştırıyor, ama yapılandırılmış bir formatlayıcı yok — bugün CI'da hiç
  biçim kapısı yok.

## Aşama 2'ye taşınanlar

- **Kota sıfırlaması bir zaman dilimi istiyor.** `usage_counters.period` bir
  `DATE`; günlük sayaç henüz hiçbir yerde tanımlı olmayan bir gün sınırında
  dönüyor. `resetsAt` frontend'e gönderilmeden önce kapanmalı.
- **`application-local-fake.yml`, `-local-record`, `-local-real` yok.** LLM
  gateway'le birlikte yazılacaklar. Spring bilinmeyen profili sessizce yok
  sayıyor, yani `make dev` bugün çalışıyor gibi görünürken `local-fake` hiçbir
  şey katmıyor.

- **`archunit.properties` içindeki `failOnEmptyShould=false` kaldırılmalı.**
  Modüller artık gerçek sınıf taşıyor, yani gerekçesi doldu; ayar açık kaldığı
  sürece bir paketi yeniden adlandırmak ilgili kuralı hiçbir şeyle eşleştirmeyip
  sessizce geçirir. Kaldırıldığında kuralların hâlâ sınıf gördüğü, kasıtlı bir
  ihlalle doğrulanmalı (`spec/12-quality.md` § 51.4).

## Aşama 3'e taşınanlar

- **`CREATE UNIQUE INDEX ON jobs (user_id, idempotency_key)` anonim istekleri
  tekilleştirmiyor.** Orada `user_id` NULL ve Postgres NULL'ları birbirinden
  farklı sayıyor, yani aynı anahtar ikinci bir iş yaratıyor.
  `COALESCE(user_id::text, anon_session_id)` üzerinden anahtarlayan bir migration
  gerekiyor — ertelendi, çünkü anonim akışın kuyruğu kullanıp kullanmayacağı hâlâ
  açık (`STATUS.md` · açık kararlar).
- **Anonim TTL etkinlikle kayıyor**, dolayısıyla kullanıcıya görünen metin "iki
  saat" değil "son etkinliğinden iki saat sonra" demeli. `spec/01-foundations.md`
  § 9 ve ürün dokümanı hâlâ mutlak ifadeyi taşıyor; ikisi de güncellenmeli ve
  metnin sahibi frontend.

## Aşamasız açık

**V1 bazı enum benzeri kolonlara `CHECK` koyuyor** (`sections.layout`,
`applications.status`, `jobs.status`), bazılarını yorum olarak bırakıyor
(`sections.kind`, `atoms.kind`, `generations.status`, `jobs.type`,
`llm_invocations.outcome`). Bu, `spec/04-data-model.md` § 13'ü bilinçli olarak
yansıtıyor; kısıt uydurmuyor. Eksikleri sonradan eklemek ucuz bir migration,
çünkü `CHECK` tabloyu yeniden yazmıyor.

---

## Aşama 2 kayıtları

**Düzeltme — toplu JPQL `update` `@Version`'ı atlıyor.** Frontend'in F-001'i
`atom_variants`'ta yakaladı: promote'ta demote edilen satır değişiyor ama `version`'ı
sabit kalıyordu, çünkü demote tek satırlık bir `@Modifying` sorgu. `update versioned`
oldu. Kuralın kendisi `spec/08-api.md` § 35.6'ya yazıldı; buraya not düşülmesinin
nedeni **tekrar edecek olması** — Aşama 2'de kota sayaçları ve `generations` durum
geçişleri de toplu update isteyecek ve aynı sessizlikle etag üretecek.

Yanına iki şey: sorgu artık `and variant.isPrimary = true` taşıyor, çünkü hepsini
sürümlemek promote'u **tamamen kırıyor** (promote edilen satırın merge'ü kendi
bilmediği bir sürüme çarpıyor). Bu kasıtlı olarak denendi ve dört test düştü —
`spec/12-quality.md` § 51.4'ün istediği doğrulama.

**Ekleme — entry tarih aralığı sıralı olmak zorunda.** F-002; `endDate >= startDate`,
`PATCH`'te yamanın sonucuna karşı ölçülüyor. `spec/08-api.md` § 35.2'ye yazıldı.
Doküman sessizdi ve altındaki hiçbir katman ters aralığı reddetmiyordu.
