# EK A-B-C — Sözlük, Kapsam Dışı, Kontrol Listeleri

> AtomCV spec · [INDEX](../INDEX.md) · bu dosya yalnız aşağıdaki bölümleri içerir.

---

## EK A — Terimler Sözlüğü

| Terim | Anlamı |
|---|---|
| **Atom** | Profildeki en küçük bağımsız seçilebilir bilgi birimi (bir madde, bir beceri) |
| **Varyant (AtomVariant)** | Aynı atomun farklı dil/ton/uzunluk versiyonu; metin burada saklanır |
| **Run** | Aynı vurgu özelliklerine sahip ardışık metin parçası |
| **Mark** | Bir run'a uygulanan semantik etiket (`technology`, `metric`, `emphasis`) |
| **Master Profil** | Kullanıcının tüm profesyonel verisinin format-bağımsız, yapılandırılmış hali |
| **Selection State** | Belirli bir üretimde hangi atomların seçildiğini tutan durum nesnesi |
| **Render Cost** | Bir atomun belirli bir şablonda kapladığı dikey alan (punto) |
| **Alaka Skoru** | Bir atomun ilanla örtüşmesini gösteren 0-1 arası değer |
| **Uygunluk Raporu** | CV'nin ilan gereksinimlerini karşılama durumu (sayılabilir kapsama) |
| **alwaysInclude** | Skoru ne olursa olsun dahil edilme kilidi |
| **verbatim** | Yeniden yazıma gönderilmeme kilidi |
| **Genel CV Modu** | İlan girilmediğinde ikincil kriterlerle üretim |
| **Faz A-G** | Üretim hattının yedi aşaması |
| **ContentShape** | İçerik yerine loglanan istatistiksel özet (PII'siz teşhis) |
| **ATS** | Applicant Tracking System — CV'leri otomatik tarayan işe alım yazılımı |
| **IDOR** | Insecure Direct Object Reference — yetkisiz veri erişimi açığı |
| **SSRF** | Server-Side Request Forgery — sunucuyu iç kaynaklara istek atmaya zorlama |
| **Expand-Contract** | Geriye uyumlu şema migration deseni |
| **Bin-packing** | Sınırlı kapasiteye maksimum değerli öğe yerleştirme problemi |

## EK B — Kapsam Dışı Bırakılanlar

```
❌ Geri bildirim döngüsüyle öğrenen kalibrasyon
   → İstatistiksel anlamlılık için çok veri/zaman; orantısız karmaşıklık

❌ LinkedIn veri export entegrasyonu
   → Yüksek kullanıcı sürtünmesi; GitHub daha iyi sinyal veriyor

❌ Ücretli katmanlar / gelir modeli
   → Ürün konumu gereği

❌ Kubernetes / mikroservis
   → Tek geliştirici, öngörülebilir yük

❌ Self-host log altyapısı (ELK, Loki)
   → RAM maliyeti; Axiom seçildi

❌ Ham LaTeX düzenleme izni
   → Doğrudan RCE yüzeyi

❌ OCR (taranmış PDF)
   → Ek bağımlılık, kalite riski

❌ Tagged PDF (a11y)
   → XeLaTeX'te zahmetli; ATS temizliği çoğunu karşılıyor

❌ Session replay / davranış kaydı
   → Gizlilik konumlandırmasıyla çelişir

❌ İlan URL'den otomatik çekme (Aşama 4+)
   → Site bazlı kural gerektirir, sürekli bakım
```

## EK C — Kontrol Listeleri

### C.1 Yayına almadan önce

```
GÜVENLİK
□ Tüm endpoint'ler için multi-tenant izolasyon testi geçiyor
□ CSRF koruması aktif
□ Güvenlik header'ları (HSTS, CSP, X-Frame-Options) doğrulandı
□ Dev endpoint'leri prod profilinde yok (test ile doğrulandı)
□ LaTeX container izolasyonu doğrulandı (network=none, no-shell-escape)
□ Sırlar env'de, git'te değil (gitleaks taraması temiz)
□ Rate limiting 3 katmanda aktif
□ Turnstile signup + generation'da aktif

VERİ
□ Flyway migration'ları temiz uygulanıyor
□ Yedek script'i çalışıyor
□ ⚠️ Gerçek restore testi yapıldı
□ Hesap silme tüm veriyi siliyor (test edildi)
□ Export çalışıyor (JSON + Markdown)

MALİYET
□ Kill switch test edildi
□ Kota sayaçları doğru çalışıyor
□ DAILY_BUDGET_USD ayarlandı
□ Anomali alarmları e-postaya geliyor

HUKUKİ
□ Gizlilik Politikası yayında
□ Kullanım Şartları yayında
□ AI sağlayıcı listesi güncel ve açık

OPERASYON
□ Axiom log akışı çalışıyor
□ Sentry hata yakalıyor
□ UptimeRobot izliyor
□ Health check + rollback test edildi
□ E-posta teslimatı doğrulandı (SPF/DKIM/DMARC)
□ Log rotasyonu ayarlı (disk dolmasın)
```

### C.2 Yeni şablon eklerken

```
□ Renderer sınıfı yazıldı (final + ölçüm modu, AYNI preamble)
□ Kapasite ölçüldü (pageTextHeightPt, baselineSkipPt)
□ Sabit maliyetler ölçüldü (heading, sectionHeader, entryHeader...)
□ templates.yaml'a eklendi (version: 1)
□ Türkçe karakter testi geçti (ş ğ ı İ ö ü ç)
□ ATS metin çıkarım testi geçti
□ Golden test set'te sayfa sınırı testi geçti
```

### C.3 Prompt değiştirirken

```
□ Yeni sürüm dosyası oluşturuldu (vN.md), eskisi silinmedi
□ Şema değiştiyse schema.json güncellendi
□ Parse mantığı ve doğrulayıcılar uyumlu
□ LLM eval çalıştırıldı
□ Bloker metrikler geçiyor (yeni teknoloji uydurma = %0)
□ config'de active sürüm güncellendi
□ Rollback planı: config değişikliğiyle eski sürüme dönülebilir
```

### C.4 Yeni dil eklerken

```
□ Font whitelist'i o dilin karakterlerini kapsıyor mu
□ ICU çoğul kuralları test edildi
□ Tarih formatı doğru (CV içi = içerik dili)
□ Locale.ROOT normalizasyonu etkilenmiyor
□ Pivot çeviri (EN üzerinden) kuruldu
□ Render cost ölçümü o dil için yapıldı
□ Kullanıcıya "otomatik üretildi, gözden geçir" uyarısı
```

### C.5 İsim veya domain değişikliğinde

"AtomCV" adı ve `atomcv.mustafatetik.com` domaini geçicidir. Değişirse dokunulacak yerler:

```
KOD
□ Java paket adı: com.mustafatetik.atomcv → yeni ad (IDE refactor)
□ frontend i18n: messages/*.json → app.name anahtarı
□ package.json → name alanı
□ README.md, CHANGELOG.md başlıkları

KONFİGÜRASYON
□ .env / .env.example → APP_NAME, APP_BASE_URL
□ docker-compose*.yml → servis adları, volume adları, POSTGRES_DB
□ nginx.conf → server_name, ssl_certificate yolları
□ GitHub imaj adları → ghcr.io/.../yeni-ad-backend

ALTYAPI
□ Cloudflare DNS → yeni A/AAAA kaydı (eskisini hemen silme)
□ TLS sertifikası → yeni domain için certbot
□ OAuth redirect URI'ları → Google, GitHub (2 yerde, GitHub'da iki uygulama)
□ Turnstile site ayarı → yeni domain ekle
□ Resend domain doğrulama → yeni alt alan + DNS kayıtları
□ R2 bucket adı (opsiyonel, veri taşıma gerektirir)
□ Axiom dataset adı (opsiyonel)

VERİTABANI
□ Veritabanı adı değişecekse: dump → yeni DB'ye restore
  (veya olduğu gibi bırak — kullanıcı görmüyor)

GEÇİŞ
□ Eski domainden yeniye 301 yönlendirme (en az 30 gün)
□ Kullanıcılara duyuru (e-posta + uygulama içi banner)
□ Gizlilik Politikası ve Kullanım Şartları'ndaki domain referansları
```

**Sırayı bozma:** Önce yeni domaini çalışır hale getir, sonra eskisini yönlendirmeye çevir, en son kaldır.

---
