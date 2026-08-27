# Arşiv — Adım 3.4, CV yükleme ve çıkarım

> Kapanan adımın inşa kaydı. **Kalıcı kararlar `spec/`'te** — § 31.3.1,
> § 31.4.1, § 31.5.1, § 31.6.1, § 31.6.2, ve § 43.1 ile § 53.1'de birer not.
> Burası yalnız nasıl bulunduklarını taşır. `current.md` sınırını aştığı için
> taşındı (2026-08-27).
>
> **Hâlâ canlı olanlar `current.md`'de bırakıldı** ve burada tekrarlanmıyor.

---

## Adım 3.4 — CV yükleme ve çıkarım · 4/4

Dört dilim de indi. Otuz bir kararın hepsi kalıcı çıktı ve `spec/`'e işlendi:
§ 31.3.1 (doğrulama, çıkarım), § 31.4.1 (LLM yapılandırma), § 31.5.1
(normalizasyon), § 31.6.1 (uç, iş, kalıcılaştırma); üçüncü enjeksiyon katmanı
§ 43.1'de, prompt dosyası kontrolü § 53.1'de.

**İki ders.** *Bir metodun javadoc'u ne zaman çalıştığını söylüyorsa, çağıranı
da ara* — `validateConfiguredPrompts()` "açılışta hata" diyordu ve kimse onu
çağırmıyordu. *Düşmeyen bir ihlal denemesi, bir eksik testtir* — 4b'de sıra
iddiasını hiçbir test tutmuyordu.

### Hâlâ canlı olanlar

**`jobs.payload` kullanıcı içeriği taşıyor ve tamamlanmış işleri budayan bir
şey yok.** Çıkarılan CV metni iş bittikten sonra da orada kalıyor. Dosyayı hiç
saklamama kararının yanındaki tek gedik. **Saklama süresi her iş tipinin
sorusu** — `generation` da ilan metnini taşıyor — o yüzden burada çözülmedi.

**`ProfileWriter` mevcut profile *ekliyor*, değiştirmiyor.** İkinci bir içe
aktarma bölümleri ikinci kez yazar. `PROFILE_ALREADY_EXISTS` (409) katalogda
duruyor ve **hiçbir şey onu üretmiyor**; ürünün "ikinci CV yüklenirse ne olur"
sorusuna cevabı yok. § 31.1 yalnız ilk kullanımı anlatıyor.

**`ExtractedContact`, `Contact` ve şema aynı şekli üç yerde taşıyor**
(§ 31.4.1). Alan eklendiğinde **üçü birden** güncellenmeli.

**`MIN_LANGUAGE_CONFIDENCE = 0.5` prompt'un cümlesiyle eşleşiyor**; birini
değiştiren ötekini de değiştirmeli.

**`SkillNames.canonical` dört çağıranın ortak kuralı** — ingestion, Faz B
skorlayıcı, Faz F raporu, run işaretleme. Alias dosyası
`resources/skills/aliases.txt`; üç özelliği testle tutuluyor ve **soldaki taraf
insanların gerçekten yazdığı gibi** olmalı, yoksa anahtar hiçbir şeyle
eşleşmez.

**`ZipSecureFile.setMinInflateRatio` global durum**; `DocxTextExtractor`'ın
kurucusu her açılışta yeniden yazıyor (§ 42.1).

**`ProfileImportService` ret için fırlatıyor** (§ 31.6.1); yeni bir ret
servise eklenir, denetleyiciye değil.

**Canlı — `TexTextExtractor`'ın kalıpları kullanıcının tamamen yazdığı bir
belge üzerinde koşuyor.** CodeQL dilim 4'te dört yüksek uyarı verdi, ve
haklıydı: uç inince o metin ilk kez dışarıdan geldi. **İki ayrı kusur vardı** —
sahiplenici olmayan niceleyiciler (bir eşleme denemesi içinde geri izleme) ve
"satır sonundaki boşluklar" kalıbının **her karakterde yeniden başlaması**
(geri izlemesiz ama yine kuadratik). İkincisi negatif geriye-bakışla
çözüldü. **Yeni bir kalıp eklerken
ikisini de düşün**; `atexFileBuiltToBacktrackIsStillReadInNoTime` ikisini de
tutuyor.

### Dilim 4b — arka plan tetiklemesi

Altı kararın hepsi § 31.6.2'ye işlendi. Bir ihlal denemesi hiçbir testi
düşürmedi — işleri yazmadan *önce* kuyruğa almak bir şey bozmuyordu, çünkü
sırayı tutan test yoktu. Yazıldı.

**Canlı — `AtomEmbeddingService` `atoms.embedding`'e yazan ilk şey.** Faz B
bugüne kadar atom vektörü olmadan skorluyordu. `AtomEmbeddingIT` kendi
bağlamını taşıyor; paylaşılandaki sağlayıcı çalışmayan bir servise bakıyor.

**İzlenecek — CI bir kez `PGVectorTypeContributor`'da `NoClassDefFoundError`
verdi; aynı ağaç tekrar koşuşta geçti** (2026-08-27, run 33091345512).
Paylaşılan dâhil dört bağlam birden düştü, yerelde hiç düşmedi. Tek makul
açıklama **bağlam sayısı**: entegrasyon paketi artık dört Spring bağlamı
kuruyor, her biri kendi `EntityManagerFactory`'siyle, ve runner'ın belleği
buradakinden az — başarısız bir statik başlatıcı sonraki her denemede
`NoClassDefFoundError` olur. **Tekrarlarsa ilk bakılacak yer beşinci bir
bağlam, kod değil.**

**Açık — `local-fake` fixture'ı yok ve uydurulamaz.** Fixture anahtarı istek
metninin özetinden türüyor; elle yazılan bir fixture yalnız tek bir CV'de
ateşlenir. **`make record` geliştiricinin anahtarını istiyor.**
