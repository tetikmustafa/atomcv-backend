# Bölüm XII-XIII — Maliyet, Hukuki, Sürdürülebilirlik

> AtomCV spec · [INDEX](../INDEX.md) · bu dosya yalnız aşağıdaki bölümleri içerir.

---

# BÖLÜM XII — MALİYET

## 56. Maliyet Analizi

### 56.1 Aylık sabit maliyetler

| Kalem | Detay | Maliyet |
|---|---|---|
| **Sunucu** | Hetzner CPX31 (4 vCPU / 8 GB / 160 GB NVMe) | **~€14** |
| | *Başlangıç alternatifi:* CPX21 (3/4/80) | ~€8 |
| | *Büyüme:* CPX41 (8/16/240) | ~€26 |
| **Domain** | `atomcv.mustafatetik.com` — mevcut domainin alt alanı | **€0** |
| **Cloudflare** | CDN, DDoS, WAF, DNS, Turnstile | **€0** |
| **Axiom** | 500 GB/ay, 30 gün saklama | **€0** |
| **Resend** | 3.000 e-posta/ay | **€0** |
| **Sentry** | Ücretsiz katman | **€0** |
| **UptimeRobot** | Ücretsiz katman | **€0** |
| **GitHub Actions** | Public repo → sınırsız | **€0** |
| **GHCR** | Public imaj | **€0** |
| **Cloudflare R2** | ~5 GB, egress ücretsiz | **~$0.50** |
| **Backblaze B2** | Haftalık arşiv, ~2 GB | **~$0.15** |
| **Umami** | Self-host (aynı sunucu) | €0 |
| **SABİT TOPLAM** | | **~€15 / ay** |

### 56.2 LLM maliyeti — çağrı bazında

Ucuz model sınıfı (~$0.10-0.15/M input, $0.40-0.60/M output) varsayımıyla:

| İşlem | Input | Output | Maliyet |
|---|---|---|---|
| Profil çıkarımı (bir kez/kullanıcı) | ~4.000 | ~6.000 | **~$0.003** |
| Faz A: ilan analizi | ~2.000 | ~500 | ~$0.0004 |
| Faz D: 6 madde yeniden yazımı | ~3.000 | ~1.200 | ~$0.0008 |
| About sentezi | ~1.500 | ~150 | ~$0.0002 |
| Cover letter | ~2.000 | ~500 | ~$0.0004 |
| Faz G: düzenleme parse | ~1.000 | ~300 | ~$0.0002 |
| **Üretim başına** | | | **~$0.002** |

**Embedding:** Self-host → **$0**

### 56.3 Senaryo bazlı aylık LLM maliyeti

| Senaryo | Hesap | Maliyet |
|---|---|---|
| **Sessiz** (50 kullanıcı × 3 üretim) | 50×0.003 + 150×0.002 | **~$0.5** |
| **Orta** (300 kullanıcı × 5) | 300×0.003 + 1.500×0.002 | **~$4** |
| **Aktif** (1.000 kullanıcı × 5) | 1.000×0.003 + 5.000×0.002 | **~$13** |
| **Yoğun** (1.000 kullanıcı × 20) | 1.000×0.003 + 20.000×0.002 | **~$43** |

Prompt caching + Batch API ile **%30-50 daha düşük** olabilir.

### 56.4 Toplam aylık maliyet

| Senaryo | Sabit | LLM | **Toplam** |
|---|---|---|---|
| Sessiz | €15 | ~$0.5 | **~€16** |
| Orta | €15 | ~$4 | **~€19** |
| Aktif | €15 | ~$13 | **~€27** |
| Yoğun (CPX41) | €27 | ~$43 | **~€67** |

**Gerçekçi ilk yıl beklentisi: €16-25/ay**

**Kill-switch eşiği: $40/ay** (`DAILY_BUDGET_USD` ile günlük ~$1.33)

### 56.5 Geliştirme sırasındaki maliyetler

| Kalem | Maliyet |
|---|---|
| Aşama 0-1 (LLM yok) | €0 |
| Aşama 2-3 test çağrıları | ~$5-15 (tek seferlik) |
| LLM eval (prompt PR başına) | ~$0.30 |
| Staging ortamı (opsiyonel) | +€8/ay |

### 56.6 Zaman maliyeti

```
MVP'ye kadar:  ~14 hafta part-time (~3.5 ay)
Aşama 4:       sürekli
```

---

# BÖLÜM XIII — HUKUKİ VE SÜRDÜRÜLEBİLİRLİK

## 57. Hukuki Çerçeve

### 57.1 Gizlilik Politikası — zorunlu içerik

```
1. HANGİ VERİYİ TOPLUYORUZ
   Profil içeriği (CV bilgileri), e-posta, kullanım metrikleri

2. NEDEN
   CV üretimi, hesap yönetimi, hizmet iyileştirme

3. KİMLERLE PAYLAŞIYORUZ  ← EN KRİTİK BÖLÜM
   • AI sağlayıcıları: [güncel liste], hangi veri gidiyor
   • ⚠️ Bazı ücretsiz AI katmanlarının veriyi model eğitiminde
     kullanabileceği AÇIKÇA belirtilmeli
   • E-posta: Resend
   • Analitik: Umami (anonim, çerezsiz)
   • Depolama: Cloudflare R2

4. NE KADAR SAKLIYORUZ
   Hesap aktif olduğu sürece; anonim mod son etkinlikten 2 saat sonra;
   PDF 14 gün (arşivlenirse süresiz); loglar 30 gün

5. HAKLAR
   Erişim, düzeltme, silme, taşınabilirlik (export)

6. NASIL SİLİNİR
   "Hesabımı sil" butonu — kalıcı, geri alınamaz

7. VERİ SORUMLUSU İLETİŞİM
```

### 57.2 Kullanım Şartları

```
• İş bulma garantisi verilmez
• Uygunluk analizi bir tahmindir, gerçek ATS davranışını
  birebir yansıtmayabilir
• Üretilen içeriğin doğruluğundan kullanıcı sorumludur
• Kötüye kullanım halinde hesap kapatılabilir
• Ücretsiz hizmet, SLA yok
• Hizmet sonlandırılırsa en az 30 gün önce bildirilir
```

### 57.3 Çerez bildirimi

Sadece oturum çerezi (zorunlu) + çerezsiz analitik → **çerez izni banner'ı gerekmez.** Umami/Plausible seçmenin ikinci faydası.

### 57.4 Unutulma hakkı — teknik gereklilik

"Hesabımı sil" **gerçekten her yerden** silmeli:

```
├── PostgreSQL          (ON DELETE CASCADE ile otomatik)
├── Redis oturumları    (session invalidation)
├── Redis anonim veri   (varsa)
├── R2'deki PDF'ler     (pdf_key ile) ⚠️ HENÜZ YOK — aşağıya bak
├── Embedding'ler       (atoms tablosunda, cascade)
├── OAuth bağlantıları  (revoke + cascade)
└── Silme kaydı loglanır (içerik olmadan, yasal kanıt)
```

**Kullanıcıya bildirilmeli:** LLM sağlayıcıları kendi taraflarında kısa süreli log tutabilir.

> **⚠️ R2 bu listede var, kodda yok (2026-08-28 denetimi).** Bu repoda hiçbir R2
> istemcisi yok: PDF saklanmıyor, her indirmede saklanan anlık görüntüden
> yeniden derleniyor, ve `generations.pdf_key` her satırda NULL. Yani bugün
> **silinecek bir PDF yok** — liste yanlış değil, henüz karşılıksız.
>
> **MVP'ye depolama girmiyor** (2026-08-28 kararı): yeniden derlemek çalışıyor
> ve bir nesne deposu, silme yolu ile gizlilik politikasına aynı anda eklenmesi
> gereken bir bileşen.
>
> **Depolama indiği gün silme yolunun oradan da geçmesi gerekir**, ve bunu
> hatırlatacak tek şey bu paragraftır: `AccountDeletionIT` tabloları
> `information_schema`'dan okuyor, yani veritabanına eklenen her tabloyu kendisi
> yakalar — **ama veritabanında olmayan bir nesne deposunu göremez.**

#### 57.4.1 Kararlar (Adım 3.9, dilim 1)

**Düzeltme — `usage_counters` cascade'e takılmıyor.** § 57.4 "PostgreSQL
(ON DELETE CASCADE ile otomatik)" diyor; o tablo `(subject_type, subject_id)`
ile anahtarlı ve `users`'a foreign key'i **yok**, çünkü özne bir adres ya da
anonim oturum da olabiliyor. Hesap silinince satırları kalıyordu: yanında bir
sayı olan bir kimlik, tam da silmenin kaldırması gereken şey. `UsageCounters.forget`
onları siliyor, ve `AccountDeletionIT` bunu şemadan okuyarak denetliyor —
tabloları elle sayan bir test, kimsenin haberi olmadan eklenen tabloda sonsuza
kadar geçerdi.

**Ekleme — silme uç noktası ek bir onay alanı istemiyor.** `DELETE` zaten
oturum çerezi ve CSRF tokenının arkasında, yani başka bir sitenin
tetikleyebileceği bir şey değil. "Emin misin" ekranı, neyin kaybolacağını
söyleyecek yeri olan tarafta — frontend'te. Gövdeye konacak ikinci bir kutu,
API'nin anlamını zorlayamadığı bir kutu olurdu.

**Ekleme — ikinci basış da `204`.** Silinmiş bir hesabı tekrar silmek hata
değil; kullanıcının istediği şey zaten olmuş durumda.

**Ekleme — `email_suppressions` hayatta kalıyor.** Adrese göre anahtarlı bir
teslimat kaydı, hesaba değil; silmek, hard bounce etmiş ya da şikâyet etmiş bir
adrese yeniden posta atmamıza izin verirdi.

**Açık — R2'deki PDF'ler.** § 57.4 listesinde var ama bu repoda R2 istemcisi
henüz hiç yok (`generations.pdf_key` her zaman NULL; indirme, saklanan
anlık görüntüden yeniden render ediliyor). Depolama indiğinde silme yolunun da
oradan geçmesi gerekecek.

### 57.5 Veri export

```
GET /api/v1/profile/export

├── JSON (makine okunabilir, tam)
└── Markdown (insan okunabilir, başka araca yapıştırılabilir)
```

GDPR/KVKK'daki taşınabilirlik hakkının karşılığı.

---

## 58. Proje Sürdürülebilirliği

### 58.1 Risk

Ücretsiz ürün, tek geliştirici. Üç senaryo: ilgi azalır, maliyet artar, sorun çıkar. Hepsi meşru — sorun kapatmak değil, **kullanıcıyı hazırlıksız yakalamak**.

### 58.2 Veri export (en önemli güvence)

Bölüm 57.5. Kullanıcı verisini alıp gidebilmeli. Hem etik hem yasal.

### 58.3 Maliyet tavanı ve otomatik fren

```java
if (monthlyCost > BUDGET_LIMIT) {
    featureFlags.disable("generation.new_requests");
}
```

**Kritik:** Fren veri erişimini kesmez. Üretim durur, profil görüntüleme ve export devam eder.

### 58.4 Kapatma prosedürü (şimdi yazılsın)

```
1. Duyuru (uygulama içi banner + e-posta) — 30 gün önce
2. Yeni kayıtlar kapatılır
3. Export özelliği öne çıkarılır
4. Son 7 gün: sadece okuma + export
5. Kapanış: tüm veri kalıcı silinir, silme onayı e-postası
```

### 58.5 Açık kaynak

**Lisans: MIT** (en permisif, portfolyo için ideal)

```
✓ Açık: uygulama kodu, şema, renderer'lar, algoritmalar, promptlar
✗ Kapalı: API anahtarları (env'de), sunucu yapılandırması
```

**Gerekli dosyalar:**
```
├── LICENSE (MIT)
├── README.md (kurulum, mimari özet)
├── CONTRIBUTING.md
├── SECURITY.md          ← güvenlik açığı nereye bildirilecek
├── CHANGELOG.md
└── .env.example         ← gerçek değerler ASLA
```

**Sır sızıntısı koruması:** `gitleaks` / `truffleHog` pre-commit hook + CI taraması.

---

# EKLER
