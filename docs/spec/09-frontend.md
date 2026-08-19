# Bölüm VII/2 — Frontend, i18n, a11y (36-39)

> AtomCV spec · [INDEX](../INDEX.md) · bu dosya yalnız aşağıdaki bölümleri içerir.

---

## 36. Frontend Mimarisi

### 36.1 Sayfa yapısı

```
app/
├── [locale]/
│   ├── page.tsx                    landing (SSG)
│   ├── (auth)/
│   │   ├── login/
│   │   └── verify/                 magic link onay sayfası
│   └── (app)/
│       ├── onboarding/             profil kurulum sihirbazı
│       ├── profile/                profil editörü
│       ├── generate/               üretim akışı
│       ├── generations/[id]/       sonuç ekranı
│       ├── applications/           başvuru takibi
│       └── settings/
│       └── legal/
│           ├── privacy/
│           └── terms/
```

> **Not (Frontend Aşama 0).** İki düzeltme. `legal/` **`[locale]` altında**:
> segment dışında çevrilemiyor, ve Türk kullanıcının okuyamadığı bir gizlilik
> politikası gizlilik politikası değildir. **`app/api/` yok ve olmayacak** —
> lokalde aynı-origin görüntüsü `next.config.ts` rewrite'ıyla korunuyor;
> rewrite bizim kodumuzu çalıştırmadığı için "iş mantığı yok" kuralı zaten
> ihlal edilmiyor (EK D.10 · 19, 20).

### 36.2 Durum yönetimi ayrımı

| Durum tipi | Araç |
|---|---|
| Sunucu verisi (profil, üretimler) | **TanStack Query** — granüler cache anahtarları |
| Geçici UI (açık bölüm, seçili atom) | **Zustand** |
| Form | **React Hook Form + Zod** |

**Kural:** Sunucu verisi Zustand'a kopyalanmaz — iki yerde tutmak senkronizasyon derdi doğurur.

### 36.3 Kod bölme

```typescript
const PdfPreview  = dynamic(() => import('./PdfPreview'),  { ssr: false });
const DiffViewer  = dynamic(() => import('./DiffViewer'),  { ssr: false });
const RichEditor  = dynamic(() => import('./RichEditor'),  { ssr: false });
```

`react-pdf` tek başına ~300 KB — sonuç ekranına gelene kadar yüklenmemeli.

**Bundle bütçesi:** ilk JS paketi < 200 KB gzip, CI'da denetlenir.

### 36.4 SSE tüketimi

```typescript
useEffect(() => {
  const es = new EventSource(`/api/v1/jobs/${jobId}/stream`);
  es.addEventListener('phase',     e => setProgress(JSON.parse(e.data)));
  es.addEventListener('completed', e => { onDone(JSON.parse(e.data)); es.close(); });
  es.addEventListener('failed',    e => { onError(JSON.parse(e.data)); es.close(); });
  return () => es.close();
}, [jobId]);
```

---

## 37. Profil Editörü

### 37.1 Kaydetme stratejisi

Tek "Kaydet" butonu yok — alan bazlı otomatik kaydetme.

| İşlem | Debounce | Gerekçe |
|---|---|---|
| Metin yazma | 1200ms | Her tuşta istek atma |
| Slider (önem) | 500ms | Sürükleme bitince |
| Toggle (aktif/kilit) | 0ms | Tek tıklama |
| Sürükle-bırak sıralama | 0ms | Bırakıldığı anda |

### 37.2 Optimistic update

```typescript
const { mutate } = useMutation({
  mutationFn: (content) => api.patch(`/profile/atoms/${atomId}/variants/${vid}`, { content }),
  onMutate: async (next) => {
    await qc.cancelQueries(['atom', atomId]);
    const prev = qc.getQueryData(['atom', atomId]);
    qc.setQueryData(['atom', atomId], old => ({ ...old, content: next }));
    return { prev };
  },
  onError: (err, _v, ctx) => {
    qc.setQueryData(['atom', atomId], ctx.prev);
    if (err.status === 412) showConflictDialog();
    else toast.error('Kaydedilemedi, tekrar denenecek');
  },
});
```

### 37.3 Durum göstergesi

```
idle → dirty → saving → saved → (2sn) → idle
                  ↓
                error → [Tekrar dene]
```

Alanın yanında küçük nokta + `aria-live` metni (ekran okuyucu için).

**Sayfadan ayrılma koruması:**
```typescript
useEffect(() => {
  const h = (e) => { if (hasPendingSaves()) e.preventDefault(); };
  window.addEventListener('beforeunload', h);
  return () => window.removeEventListener('beforeunload', h);
}, []);
```

### 37.4 Çakışma çözümü

412 alınca otomatik birleştirme **yapılmaz** (OT/CRDT bu ölçekte aşırı mühendislik):

```
Bu maddeyi başka bir sekmede değiştirmişsin.
[ Benim halimi kullan ]  [ Diğer halini kullan ]
```

### 37.5 Değişikliğin tetiklediği arka plan işleri

```
TR varyantı düzenlendi
  ├── render_cost NULL'landı  → ölçüm işi
  ├── EN varyantı is_stale     → çeviri işi
  └── EN değişince             → embedding işi
```

**Tek profil seviyesi gösterge:**
```
⟳ Profil hazırlanıyor (3 işlem)     [detay ▾]
```

Kullanıcı bitmeden üretim yaparsa **engellenmez**, bilgilendirilir:
```
Bazı değişikliklerin henüz işlenmedi. Yine de devam edebilirsin,
ancak Türkçe CV eski metinleri içerebilir.   [ Bekle ] [ Devam et ]
```

### 37.6 Bayat varyant uyarısı

```
Deneyim maddesi
├── 🇹🇷 Türkçe    "300 bin satırlık veriyi..."     ✓ güncel
└── 🇬🇧 İngilizce "Engineered ETL pipelines..."    ⚠ eski
                   [ Yeniden üret ] [ Benimkini koru ]
```

> **Frontend (EK D.9 · 24). Bu akış Aşama 2'dir.** Aşama 1'de `stale`
> bayrağı **her zaman false**: 37.5'in zinciri (TR düzenlendi → EN bayat
> işaretlendi → çeviri işi) çeviri işine bağlı ve o iş henüz yok. Bir varyantı
> **yeniden üreten uç de yok**. Bu yüzden yukarıdaki iki düğme Aşama 1'de
> çizilmemeli: çalışmayan bir kontrol, kullanıcıya zaten bir sorun olduğunu
> söyleyen bir ekranda hiç olmamasından kötüdür. Rozet ve açıklama gösterilir,
> elle düzenleme sunulur — işleyen tek şey odur. Şemadaki `Variant.stale`
> açıklaması bu satıra göre okunmalı.

### 37.7 Performans

```typescript
const AtomEditor = memo(({ atomId }: Props) => {
  const { data } = useQuery(['atom', atomId]);   // granüler cache anahtarı
  ...
}, (p, n) => p.atomId === n.atomId);
```

200 atom için `memo` yeterli; sanallaştırma 500+ atomda değerlendirilir.

---

## 38. Uluslararasılaştırma (i18n)

### 38.1 Üç bağımsız eksen

| Eksen | Ne | Kim belirler |
|---|---|---|
| **Arayüz dili** | Butonlar, menüler, hatalar | Kullanıcı tercihi / tarayıcı |
| **Profil kaynak dili** | Atomların yazıldığı dil | Profil oluştururken |
| **Çıktı dili** | CV / cover letter | Her üretimde ayrı |

Üçü farklı olabilir.

### 38.2 ICU MessageFormat zorunlu

```json
{
  "generation.result.pageCount": "{count, plural, =1 {# sayfa} other {# sayfa}}",
  "profile.completeness": "Profilin %{value} tamamlandı"
}
```

Türkçede çoğul yok, İngilizcede var — string birleştirmeyle çözülemez.

### 38.3 CV içi tarih formatı

CV'nin dili neyse tarih formatı da o olmalı — **arayüz dili değil**:

```java
var fmt = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.forLanguageTag(contentLanguage));
// EN CV → "September 2025"
// TR CV → "Eylül 2025"
```

### 38.4 ⚠️ Türkçe locale tuzağı

```java
// Sunucu locale'i TR ise:
"TITLE".toLowerCase()      → "tıtle"     ← I → ı
"instagram".toUpperCase()  → "İNSTAGRAM" ← i → İ
```

**Kural:**
```java
skill.toLowerCase(Locale.ROOT)      // kimlik/eşleştirme
displayName.toUpperCase(userLocale) // gösterim
```

JVM: `-Duser.language=en -Duser.country=US`

```java
@ArchTest
static final ArchRule noLocaleSensitiveCase = noClasses()
    .should().callMethod(String.class, "toLowerCase")     // parametresiz
    .orShould().callMethod(String.class, "toUpperCase");
```

### 38.5 Font kapsamı

| Font | Latin Ext (TR) |
|---|---|
| Latin Modern | ✅ |
| TeX Gyre Pagella/Termes/Heros | ✅ |
| Fira Sans | ✅ |
| Source Sans 3 | ✅ |

**Test fixture:** Türkçe karakterli atom kümesiyle her şablonu derle, PDF'ten metin çıkarımı yapıp `ş ğ ı İ ö ü ç` doğru çıktığını doğrula.

---

## 39. Erişilebilirlik (a11y)

### 39.1 Bedava gelenler

shadcn/ui Radix üzerine kurulu — dialog, dropdown, tab, tooltip'te focus trap, ARIA rolleri, klavye navigasyonu hazır.

### 39.2 Özel dikkat gerektirenler

| Alan | Sorun | Çözüm |
|---|---|---|
| Sürükle-bırak | Fare gerektirir | dnd-kit klavye sensörü + "yukarı/aşağı taşı" butonları |
| SSE ilerleme | Ekran okuyucu görmez | `aria-live="polite"` bölgesi |
| Kaydetme durumu | Sadece renk/ikon | `aria-live` + metin |
| Uzun listeler | Navigasyon zorluğu | Landmark rolleri, skip link |

### 39.3 Kapsam dışı

**Tagged PDF** — XeLaTeX'te zahmetli. ATS metin çıkarımı temizliği zaten ekran okuyucu uyumluluğunun büyük kısmını karşılıyor.

---
