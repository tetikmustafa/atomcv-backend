# İnşa Notları — Aktif

> Kural: bu dosya **200 satırı geçmez**. Aşama bitince `archive/`'a taşınır, boş başlanır.
> Kayıt tipleri: **Sapma** (doküman başka diyor) · **Ekleme** (doküman sessiz) · **Düzeltme** (doküman yanlış).
> Bir sapma kalıcıysa `spec/`'e işlenir ve buradan silinir.

**Aktif aşama:** Aşama 4 — Olgunlaşma. **Plan:** `spec/14-build-guide.md`
§ XI-A.7. Aşama 1-3 ve Aşama 4'ün kapanmış dilimleri `archive/`'da; harita
aşağıda.

---

## Denetim — spec'in tamamı koda karşı (2026-09-15)

Spec baştan sona okundu ve kodla karşılaştırıldı. **Kalıcı kararların hepsi
`spec/`'e işlendi ve buradan silindi** (§ 18.7.1, § 22.6.1, § 26.6'nın sapması,
§ 31.8.1, § 35.2.1, § 41.4'ün sapması, § 47.1, § 48.5'in sapması, § 51.3,
§ 52.5, § 5.1). Aşağıdakiler kalıcı değil, **canlı**.

**İnenler:** arşivleme ucu, atom etiketleri (uç **ve** içe aktarım),
`/api/v1/warmup`, commit'li `openapi.json` + iki CI işi, `emphasize`,
HTML renderer, `format=source`, GitHub içe aktarımı, CDS, Umami,
`generation.pages.drift`, golden `jobs/` + `content-formats/`.
Frontend'e `B-100`-`B-106`.

**En pahalı bulgu, ve testi olmayan türden:** `tags`/`atom_tags` tablolarına
**hiçbir şey yazmıyordu** — içe aktarım modelin bulduğu etiketleri normalize
edip düşürüyordu. Faz B'nin etiket bileşeni ham skorun dörtte biri (§ 19.1) ve
her atom için her ilana karşı yapısal olarak sıfırdı. Hiçbir test düşmez; bütün
skorlar birlikte düşer. **§ 28.4'ün "en yüksek atom skoru 0.0959, sebep
aritmetik" ölçümü bu dosyada duruyor ve aritmetiğin bir parçası buydu** —
etiketler artık yazılıyor, yani **o ölçüm bayat**. Yeniden ölçülmeden Faz D
eşikleri hakkında bir şey söylenmemeli (`PhaseDReachTest` hâlâ eski sayıları
tutuyor ve golden profillerde etiket yok, yani test düşmedi).

**İkinci en pahalı, ve hiçbir lane'de görünmezdi:** nginx'in CSP'si Turnstile'ı
blokluyordu. Üretimde giriş, anonim içe aktarım ve anonim üretimin üçü de
`CHALLENGE_FAILED` olurdu. `ContentSecurityPolicyTest` muhafız, ve
`nginx.conf` artık bir test girdisi — olmadan ekilen kusur görevi UP-TO-DATE
bırakıp testi başarı raporlattı.

**Tamir etmeye kalkma — bilinçli:**
- **`freeformNote` yok** (§ 18.7.1). Alanı adlandıran bölüm onu kimin
  okuduğunu söylemiyor; tek makul okuyucu Faz D'nin prompt'u, o da yeni bir
  sürüm ve EK C.3'ün eval koşusu.
- **`gradlew replay` yok** (§ 48.5'in sapması) — okuyacağı dışa aktarma
  formatı hiç tanımlanmadı.
- **`/customizations` ve `GET /templates` yok** (§ 35.2.1) — Katman B
  `preferences.appearance`'ta.
- **Admin teşhis ucu yok** (§ 41.4'ün sapması) — çevrimdışı okuyucu var.
- **LaTeX preamble format dump'ı yok** (§ 29.2, § 52.4): dump edilen bir
  format `\documentclass[..pt]`'i donduruyor ve § 33.2'nin Katman B slider'ı
  yazı boyutunu 9-12 arasında oynatıyor. Boyut başına bir format mümkün, ve
  bu üründe sessizce yanlış olmaması gereken tek şeye dokunuyor — ölçülmeden
  yapılmamalı.
- **`generation.pages.drift` kaba, ve hiçbir şey ona göre davranmıyor**
  (§ 26.6'nın sapması).
- **GitHub eşleştirmesi kısaltmayı ıskalıyor** (§ 31.8.1) ve eşik bilerek
  yüksek: yanlış birleştirme yanlış paragrafa bağlantı koyar.

**Test yazarken (denetimde öğrenilenler):**
- **Bir dosyayı çalışma anında okuyan test, o dosyayı Gradle girdisi olarak
  ilan etmezse koşmaz.** `performance-budgets.yaml` ve `.env.example` zaten
  öyleydi; `nginx.conf`, `docker-compose.prod.yml` ve `openapi.json` eklendi.
- **Bean'i override eden her test sınıfı kendi context'i ve kendi havuzu
  demek.** GitHub stub'ının context'i container'ın 100 bağlantısını aştı ve
  **sonraki her sınıf** "sorry, too many clients already" ile açılamadı — okuyan
  kişiye veritabanı bozulmuş gibi görünen bir aritmetik. Havuz context başına
  dörde çekildi.
- **`SelectionScalingTest` tam lane koşusunda düşüyor, tek başına geçiyor.**
  Oran 3.63'e karşı tavan 3.0. **Bu denetimden önce de böyleydi**: `c717413`
  worktree'sinde de düşüyor. Ölçüm en hızlı on beş örneği alıyor, yani
  makinenin meşguliyeti tek başına açıklamıyor — bakılması gereken bir şey.

**Ölçümler (değişmedi, ama biri bayatladı):**
- **Faz D eşikleri: `PhaseDReachTest` eski sayıları tutuyor** ve yukarıdaki
  etiket bulgusundan sonra **yeniden ölçülmesi gerekiyor**. Golden profillerde
  etiket olmadığı için test düşmedi — düşmeyen bir test doğru olduğunu
  söylemiyor.
- **`cover_letter` aktif `v1`.** v2 turu 169 kelime verdi, bant 255-290.
- **`MeasurementDriftIT.heightOnThePage`'in `\pagetotal`'ı yalnız bulunulan
  sayfayı sayıyor** — iki sayfalık belgede sapması anlamsız, bilerek bırakıldı.

**Aşama 1-3'ten taşınan, hâlâ canlı:**
- Geçmişin `total`'ı satır değil **CV** sayıyor (`F-020`); yeniden koşu Faz
  C'den başlıyor, skorlar snapshot'tan.
- `SectionFloor` bir tavandır talep değil; `reservedByFloor` `forcedByLock`'tan
  ayrı; `PARAGRAPH` `INLINE_LIST`'e katlanmadı; `suspicious_output` telde hiç
  görülmedi.
- **"Kritik uyarı" diye bir şey yok.** `ImportWarning.code` `String`, şeması
  enum; `OpenApiSchemaIT`'in okuduğu altı değer elle yazılı.
- **Hata kataloğu tablosunun `params` sütunu düzyazı kabul etmiyor** —
  `ErrorCatalogueSpecTest` birebir ayrıştırıyor.
- **`MagicLinkApiIT` her testten önce `ratelimit:*`'ı siliyor.**
- **`local` profilinde LLM sağlayıcısı yok**; `AccountDeletionIT` tablo
  listesini `information_schema`'dan okuyor.
- **Dev stub ölçümü yiyor:** çerezsiz istekle yazılmış hiçbir test kimlik
  davranışını ölçmüyor. Doğru kurgu **çözülmeyen bir çerez**.
- **Kalibrasyon belgesi ~0.6 inç'ten ferah bir geometriye sığmıyor**;
  `CalibrationService` geriye giden okumayı reddediyor.
- **`UserScopedRepository`'de `findAll` yok.** **Faz D sekize kadar eşzamanlı
  çağrı yapıyor**, havuz 10, işçi eşzamanlılığı 2 → havuz büyütülmeden işçi
  eşzamanlılığı artırılmamalı.
- **R2 yok** (`pdf_key` her satırda NULL) — § 57.4'ün açık paragrafı, ve
  arşivleme işaretinin bir gün okuyacağı kural.
- **`accessedAt` (`B-078`):** tek kapsanmamış adım `SupportGrantLookup`.
- **springdoc çok parçalı bir uçta `@RequestParam`'ı query parametresi diye
  yayımlıyor.**
- **Geliştiricide kalan:** VPS ve restore testi (§ 49.4), OAuth/Turnstile/
  challenge'ın gerçek uca karşı denenmesi (`B-100` önlerindeki kapıyı açtı).

**Dersler:** *bir javadoc ne zaman çalıştığını söylüyorsa çağıranı da ara* ·
*doğru davranan kod, korunan değildir* · *golden fixture yazılmaz, okunur* ·
*flake demeden önce dalına bak* · **§ 51.7: bir muhafızın düştüğünü görmeden
yazıldı sayma** · **yazılı bir kolon, yazan bir kod demek değil** (etiketler) ·
**bir testin iddiası üründen önce doğrulanmalı** (vurgu testi ilk hâlinde
kod hakkında değil ürün hakkında yanılıyordu) · **`git checkout --` commit'siz
işi de götürür** (bu oturumda bir kez oldu).

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

Frontend aksiyonları: `B-055`-`B-106`.
