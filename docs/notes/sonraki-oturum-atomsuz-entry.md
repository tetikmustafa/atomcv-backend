# Sonraki oturum — atomsuz entry sayfaya çıkabilsin

> **Bu dosya yeni bir oturuma yapıştırılmak için yazıldı.** Kapanış denetiminin
> (2026-08-28) tek açık kalan kod maddesi bu. Gerekçesi ve kapsamı
> `kapanis-denetimi.md` § "Atomsuz entry"de; burada **ne yapılacağı** var.
>
> Bittiğinde bu dosya silinir.

---

## Yeni oturumda yazacağın mesaj

Aşağıyı olduğu gibi yapıştır:

```
docs/notes/sonraki-oturum-atomsuz-entry.md dosyasını oku ve oradaki işi yap.
```

Bu kadar. Oturum başlangıcında `CLAUDE.md`, `STATUS.md`, `handoff/to-backend.md`
ve `notes/current.md` zaten okunuyor; bu dosya gerisini veriyor.

---

## Karar

**Altında maddesi olmayan bir entry de sayfaya çıkabilmeli.** Bugün çıkamıyor:
seçim atom atom çalışıyor, madde yoksa aday da yok, ve "2019-2023, Yıldız
Teknik Üniversitesi, Bilgisayar Mühendisliği" satırı CV'ye hiç girmiyor.

Alternatifi ("her entry'ye zorunlu bir atom yaz") kullanıcıya yalan söyletir —
bir diploma satırının maddesi olmaz. Onaylandı, 2026-08-28.

Maliyeti `ENTRY_HEADER` (ya da liste sonrası geliyorsa
`ENTRY_HEADER_AFTER_LIST`). **`ITEMIZE_OVERHEAD` ödemez** — madde yok, liste
yok.

## Neden ayrı bir oturum

Dördü de **sayfa sınırı garantisinin** üzerinden geçiyor. Ürünün tek
matematiksel vaadi bu, ve `selection_state` saklanan bir JSONB anlık görüntüsü:
şeklini değiştirmek eski üretimlerin yeniden render'ını da ilgilendiriyor.

## Engel yok — ikisi zaten hazır

- **Renderer atomsuz entry'yi zaten basıyor.** `LatexDocumentRenderer.bullets(...)`
  boş listede erken dönüyor, yani `\atomcvEntry{...}` tek başına çıkıyor.
- **Maliyet zaten ölçülüyor.** Kalibrasyon dokümanı `afterSecondEntry` ve
  `afterEntryFollowingAList` işaretlerini basıyor; `CapacityModel.ENTRY_HEADER`
  ve `ENTRY_HEADER_AFTER_LIST` sabitleri dolu.

## Dokunulacak dört yer

**1. `SelectionRequestBuilder.candidates(...)`**
Bir entry'nin hiç atom adayı yoksa, o entry için **başlık-adayı** üret.
`AtomCandidate`'e bir tür ayrımı gerekiyor (`headerOnly` gibi) — `contentKey`
eklerken kullandığım kolaylık-kurucu kalıbı burada da işe yarar. Skoru entry'nin
kendi alaka skorundan gelir; `contentKey` entry başlığının özeti olsun ki
determinizm bozucusu burada da içerikten türesin.

**2. `SelectionPhase.effectiveCostOf(...)`**
Başlık-adayı için: `ENTRY_HEADER` (+ gerekiyorsa `SECTION_HEADER`) ödesin,
**`ITEMIZE_OVERHEAD` ödemesin**. `entryFurniturePt` muhasebesinin de buna göre
davranması lazım — bugün "ilk atom entry'yi açar" varsayımı üzerine kurulu.

**3. `SelectionState`**
Hangi entry'lerin atomsuz açıldığını taşımalı. Yeni bir alan; JSONB'de
varsayılanı boş liste olsun ki **eski kayıtlar okunmaya devam etsin** (migration
gerekmiyor, ama geriye dönük okuma test edilmeli).

**4. `RenderPhase`** — satır ~85, `if (!bullets.isEmpty())`
Bu filtre gevşemeli. **Oradaki mevcut yorum değişmesi gereken sözleşmeyi tam
olarak söylüyor:**

> "A heading with nothing under it is not printed. Selection only charged for
> the ones it opened, so printing an empty one would spend points the budget
> never accounted for."

Yani: seçim onu **açıkça açtıysa** basılır, açmadıysa basılmaz. Yorum da
güncellenmeli.

## Testler — bunlar olmadan bitmiş sayma

- **Golden sette sayfa sınırı hâlâ tutuyor** (`GoldenSelectionTest`,
  `theSelectionNeverExceedsThePage`). Bu değişikliğin ilk kırabileceği şey bu.
- **Atomsuz bir entry taşıyan golden profil.** Bugün var mı bak; yoksa fixture'a
  ekle — yoksa yeni yol hiç sınanmaz.
- **`moreRoomNeverMeansLessContent`** hâlâ geçmeli.
- **Muhafızı düşür:** başlık-adayı üretimini sökünce testin **düştüğünü gör**,
  sonra geri koy. CLAUDE.md'nin kuralı.
- `latexTest` — gerçek derleyicide atomsuz entry gerçekten tek satır basıyor mu.

## Bitince

- `kapanis-denetimi.md`'de Dilim 5 satırını ✅ yap ve kaydı yaz.
- Sayfa maliyeti modeli değiştiyse `spec/05-pipeline-a-c.md` § 20.2'nin (5)
  numaralı kısıtı **Düzeltme** olarak güncellensin: bugün "bir entry'nin atomu
  seçilirse entry başlığı maliyeti eklenir" diyor, artık atomsuz da açılabiliyor.
- **Bu dosyayı sil.**

## Bağlam — bu oturumda kapanan diğer her şey

Dilim 1-4 bitti, Dilim 5'in ikisi bitti (determinizm bozucusu, ATS geri
okuması). Kalan: **6** deploy altyapısı (`docker-compose.prod.yml`, `nginx.conf`,
`deploy.sh`, `deploy.yml`, yedek + restore, Trivy) · **7** Gemini adaptörü +
Resend webhook · **8** dokümanlar (§ 47 ve § 57.4 düzeltmeleri,
`to-frontend.md` arşivleme, `STATUS.md`).

Hepsi tek PR'da birikiyor — henüz commit yok.

**Test tabanı:** 1005 birim · 420 entegrasyon · spotless yeşil.
