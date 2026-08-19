# Bölüm I — Temeller (1-4)

> AtomCV spec · [INDEX](../INDEX.md) · bu dosya yalnız aşağıdaki bölümleri içerir.

---

# BÖLÜM I — TEMELLER

## 1. Ürün Özeti

### 1.1 Tanım

Kullanıcılar **bir kez** kapsamlı bir "Master Profil" oluşturur; sonrasında **her iş ilanı için saniyeler içinde** o ilana özel optimize edilmiş, ATS uyumlu, garantili sayfa sınırında CV ve cover letter üretir.

### 1.2 Temel iddia

Bu ürünü rakiplerinden ayıran beş özellik:

| # | İddia | Nasıl sağlanıyor |
|---|---|---|
| 1 | **Garantili sayfa sınırı** | Sayfa sınırı bir "rica" değil, matematiksel kısıt. Render maliyetleri gerçekten ölçülür, seçim bir optimizasyon problemi olarak çözülür. |
| 2 | **Deterministik seçim** | Aynı girdi her zaman aynı çıktıyı verir. Skorlama ve seçim LLM'e değil koda dayanır. |
| 3 | **Yapısal uydurma koruması** | LLM serbest içerik üretmez; var olandan seçer ve dar kapsamlı yeniden yazar. Her yeniden yazım otomatik doğrulanır. |
| 4 | **Format bağımsızlığı** | İçerik hiçbir çıktı formatına bağımlı değil. LaTeX/HTML/DOCX bağımsız eklentiler. |
| 5 | **Şeffaflık** | Her seçimin gerekçesi gösterilir. Kullanıcı her kararı geçersiz kılabilir. |

### 1.3 Ticari konum

- **Ücretsiz.** Gelir modeli yok.
- **MIT lisanslı açık kaynak.** Portfolyo ve marka değeri hedefli.
- **SLA yok.** Kişisel proje olarak konumlandırılır.

Bu karar iki teknik sonuç doğurur: maliyet koruması normalden kritik hale gelir (Bölüm 44), ve sürdürülebilirlik planı gerekir (Bölüm 58).

---

## 2. Problem Tanımı

### 2.1 İşe alım süreçlerinin gerçekliği

Modern işe alımda CV'ler önce **ATS (Applicant Tracking System)** yazılımlarından geçer:
- CV'den metin çıkarımı yapılır — karmaşık tablolar, çoklu kolonlar, grafikler bu aşamada bozulabilir
- İlan anahtar kelimeleriyle eşleştirme yapılır
- Düşük skorlu CV'ler insan görmeden elenebilir

İnsan aşamasına ulaşanlar ise İK uzmanı tarafından birkaç saniyede taranır.

### 2.2 Doğan ihtiyaçlar

1. **İlana özel keyword optimizasyonu** — her ilan farklı terimler arar
2. **Sıkı alan yönetimi** — genellikle 1 sayfa; en değerli bilgi en üstte
3. **Makine okunabilirliği** — görsel olarak güzel ama ATS'nin okuyamadığı CV işe yaramaz
4. **Tekrarlanabilirlik** — her başvuruda elle yapmak sürdürülemez

### 2.3 Mevcut çözümlerin eksikleri

| Çözüm | Eksik |
|---|---|
| Manuel düzenleme | Zaman alıcı, tutarsız, insan hatasına açık |
| Genel CV oluşturucular | İlana özel optimizasyon yok |
| Basit AI araçları | Tüm CV'yi LLM'e verip yeniden yazdırır → uydurma, format bozulması, sayfa taşması, tekrarlanamazlık |

---

## 3. Önceki Nesil Sistemin Analizi

Bu proje sıfırdan tasarlanmıyor; çalışan bir önceki nesil sistemin deneyimi üzerine kuruluyor. Yeni mimarinin gerekçesi burada.

### 3.1 Önceki sistem nasıl çalışıyordu

- CV, `% @id:...` yorum işaretleriyle bloklara ayrılmış **tek bir LaTeX dosyası** olarak saklanıyordu
- İş ilanı + tüm LaTeX dosyası, tek bir büyük LLM çağrısına gönderiliyordu
- LLM'den aynı anda isteniyordu: ilanı analiz et, hangi blokların kalacağına karar ver, metinleri yeniden yaz, LaTeX syntax'ını bozma, 1 sayfaya sığdır, geçerli JSON döndür
- Çıktı derlenip sayfa sayısı ölçülüyor, aşarsa "şu kadar satır kes" talimatıyla geri gönderiliyordu

### 3.2 Karşılaşılan yapısal sorunlar

| Sorun | Kök neden |
|---|---|
| LaTeX syntax bozulması | LLM'den format-özel kod üretmesi isteniyordu |
| Sayfa taşması / aşırı kısaltma | Sayfa sınırı LLM'e "rica" olarak iletiliyordu; LLM render sonucunu göremiyordu |
| Tutarsızlık | Aynı girdi farklı zamanlarda farklı çıktı veriyordu |
| Yüksek maliyet/gecikme | Her istekte tüm doküman token olarak gönderiliyordu, retry'larla katlanıyordu |
| Uydurma bilgi riski | "Uydurma yapma" yalnızca bir prompt kuralıydı, yapısal engel yoktu |
| Ölçeklenememe | Prompt tek kullanıcının CV yapısına sabitlenmişti |
| Kırılgan düzenleme | Her düzenleme tüm dokümanın yeniden üretilmesini gerektiriyordu |

### 3.3 Çıkarılan temel ders

> **LLM'e aynı anda birden fazla farklı doğada problem verilmemelidir.**

Önceki sistemde LLM'den istenen dört problem:

| Problem | Doğası | LLM uygun mu |
|---|---|---|
| İlan ne istiyor? | Doğal dil anlama | ✅ Evet |
| Sayfaya ne sığar? | Matematiksel optimizasyon | ❌ Hayır |
| Geçerli LaTeX üret | Deterministik kod üretimi | ❌ Hayır |
| Uydurma yapma | Doğrulama | ❌ Hayır |

**Yeni mimarinin tamamı bu dört problemi birbirinden ayırma prensibi üzerine kuruludur.**

---

## 4. Temel Tasarım Prensipleri

Bu sekiz prensip, dokümandaki her kararın arkasındadır. Bir tasarım sorusuyla karşılaşıldığında önce buraya bakılmalıdır.

### P1 — İçerik ile görünümü ayır

Master Profil'de hiçbir format-özel işaretleme bulunmaz.

```
❌ "Engineered \textbf{ETL} pipelines processing \textbf{300K+ rows}"
✅ { "runs": [
      { "t": "Engineered ", "m": [] },
      { "t": "ETL", "m": ["technology"] },
      { "t": " pipelines processing ", "m": [] },
      { "t": "300K+ rows", "m": ["metric"] }
   ]}
```

Vurgu bilgisi **semantiktir**; render aşamasında LaTeX'te `\textbf{}`, HTML'de `<strong>`, DOCX'te bold run olarak karşılık bulur.

### P2 — Deterministik olan yerde LLM kullanma

| İş | Kim yapar | Neden |
|---|---|---|
| İlan anlama | LLM | Doğal dil anlama gerektirir |
| Alaka skorlama | Kod | Tekrarlanabilir, hızlı, ücretsiz olmalı |
| İçerik seçimi | Kod | Matematiksel kısıt problemi |
| Metin yeniden yazımı | LLM | Doğal dil üretimi gerektirir |
| Doğrulama | Kod | Kesin kural kontrolü |
| Render | Kod | Format doğruluğu garantisi |

### P3 — Uydurmayı yapısal olarak engelle

Üç katmanlı garanti:
1. **Kapsam kısıtı** — LLM'e yalnızca yeniden yazacağı tek atom gönderilir
2. **Görev kısıtı** — Yeni bilgi değil, var olan bilginin yeniden ifadesi istenir
3. **Doğrulama** — Çıktıdaki sayılar ve özel isimler orijinalle karşılaştırılır; eksikse reddedilir

### P4 — Sessizce kötü sonuç üretme

Her problemli durumda:
1. Sorunu net açıkla
2. Nedenini belirt
3. Somut seçenekler sun
4. Kararı kullanıcıya bırak

### P5 — Kontrolleri maliyet oluşmadan önce yap

Tüm doğrulamalar (profil yeterliliği, ilan geçerliliği, tercih çelişkileri) **LLM çağrısı yapılmadan önce** gerçekleşir.

### P6 — Düzenlemeler state üzerinde yapılır

Kullanıcı düzenlemesi, render edilmiş çıktı metnine değil, **selection state**'e uygulanır; sonra hat yeniden çalıştırılır. Bu, sınırsız iterasyonu güvenli kılar.

### P7 — Şeffaflık

Her seçimin gerekçesi kullanıcıya gösterilir. Skor, eşleşen keyword'ler, red nedeni.

### P8 — Kullanıcının emeğini silme

Kullanıcı bir metni elle düzenlediyse (`is_user_edited`), sistem onu otomatik ezmez — sorar.

---
