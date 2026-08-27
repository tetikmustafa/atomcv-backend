# AtomCV — Spec Index

**Ürün:** AtomCV · **Domain:** `atomcv.mustafatetik.com` *(ikisi de geçici)*
**Repolar:** `atomcv-backend` (Java/Spring) · `atomcv-frontend` (Next.js)

---

## ⚠️ Okuma kuralı

**Hiçbir spec dosyasını baştan sona okuma.** Aşağıdaki tablodan göreve karşılık gelen dosyayı bul, önce `rg` ile ara, sonra yalnız eşleşen bölümü satır aralığıyla oku.

```bash
rg -n "ETag|If-Match" docs/spec/08-api.md      # önce ara
# sonra: sadece bulunan aralığı oku
```

Tüm spec ~8.500 satır. Doğru dosyayı okumak 200-600 satır. **Fark 15-40 kat.**

---

## Her oturumda okunacaklar

| Dosya | Ne | Boyut |
|---|---|---|
| `CLAUDE.md` | Kalıcı bağlam, mutlak kurallar | ~150 satır |
| `docs/INDEX.md` | Bu dosya | ~120 satır |
| `docs/STATUS.md` | İki repo nerede | ~40 satır |
| `docs/handoff/to-<bu-repo>.md` | Karşı repodan gelen açık maddeler | <100 satır |
| `docs/notes/current.md` | Bu repodaki aktif aşama notları | <200 satır |

Toplam ~600 satır sabit maliyet. Gerisi göreve göre.

---

## Görev → dosya haritası

### Temeller
| Görev | Dosya |
|---|---|
| İlk oturum, mimarinin mantığı | `spec/01-foundations.md` |
| Sekiz tasarım prensibi | `spec/01-foundations.md` § Bölüm 4 |
| Neden bu teknoloji / neden şu değil | `spec/02-tech-stack.md` |
| Design pattern, algoritma seçimi | `spec/02-tech-stack.md` § 6-7 |

### Mimari ve veri
| Görev | Dosya |
|---|---|
| Modül yerleşimi, paket kararı | `spec/03-architecture.md` § 10 |
| Docker Compose, container topolojisi | `spec/03-architecture.md` § 11 |
| Atom/varyant modeli, run yapısı | `spec/04-data-model.md` § 12, 14 |
| SQL şeması, tablo, kolon | `spec/04-data-model.md` § 13 |
| İndeks kararı | `spec/04-data-model.md` § 15 |
| Migration, şema evrimi | `spec/04-data-model.md` § 16 |

### Pipeline
| Görev | Dosya |
|---|---|
| Faz A — ilan analizi | `spec/05-pipeline-a-c.md` § 18 |
| Faz B — skorlama | `spec/05-pipeline-a-c.md` § 19 |
| Faz C — seçim, bin-packing | `spec/05-pipeline-a-c.md` § 20 |
| Faz D — yeniden yazım, doğrulama | `spec/06-pipeline-d-g.md` § 21 |
| Faz E — render | `spec/06-pipeline-d-g.md` § 22 |
| Faz F — doğrulama, uygunluk raporu | `spec/06-pipeline-d-g.md` § 23 |
| Faz G — düzenleme döngüsü | `spec/06-pipeline-d-g.md` § 24 |
| Result tipi, hata hiyerarşisi | `spec/06-pipeline-d-g.md` § 25 |

### Alt sistemler
| Görev | Dosya |
|---|---|
| Render maliyeti ölçümü | `spec/07-subsystems.md` § 26 |
| LLM gateway, sağlayıcı zinciri | `spec/07-subsystems.md` § 27 |
| Embedding | `spec/07-subsystems.md` § 28 |
| LaTeX container, güvenlik izolasyonu | `spec/07-subsystems.md` § 29 |
| Kuyruk, SSE, worker | `spec/07-subsystems.md` § 30 |
| CV yükleme, çıkarım | `spec/07-subsystems.md` § 31 |
| Dosya doğrulama merdiveni, metin çıkarımı kararları | `spec/07-subsystems.md` § 31.2, § 31.3.1 |
| LLM ile yapılandırma, çıkarım prompt'u | `spec/07-subsystems.md` § 31.4, § 31.4.1 |
| Beceri/tarih/run normalizasyonu, alias sözlüğü | `spec/07-subsystems.md` § 31.5, § 31.5.1 |
| Çok dillilik, varyant senkronu | `spec/07-subsystems.md` § 32 |
| Şablon, özelleştirme | `spec/07-subsystems.md` § 33 |
| Cover letter | `spec/07-subsystems.md` § 34 |

### API ve frontend
| Görev | Dosya |
|---|---|
| Endpoint, HTTP durum, hata zarfı | `spec/08-api.md` |
| **Hata kataloğu, kapalı sözlükler, `params`** | `spec/08b-api-contract.md` |
| ETag, PATCH semantiği, eşzamanlılık | `spec/08-api.md` § 35.6 |
| Frontend mimarisi, durum yönetimi | `spec/09-frontend.md` § 36 |
| Profil editörü davranışları | `spec/09-frontend.md` § 37 |
| i18n (üç dil ekseni) | `spec/09-frontend.md` § 38 |
| Erişilebilirlik | `spec/09-frontend.md` § 39 |

### Güvenlik
| Görev | Dosya |
|---|---|
| Auth, oturum, magic link | `spec/10-security.md` § 40 |
| Rate limit, Turnstile, bot koruması | `spec/10-security.md` § 40.5, § 40.5.1, § 44.4 |
| Multi-tenant izolasyon, IDOR | `spec/10-security.md` § 41 |
| Dosya yükleme, SSRF, format injection | `spec/10-security.md` § 42 |
| Prompt injection | `spec/10-security.md` § 43 |
| Kota, kill switch | `spec/10-security.md` § 44 |

### Operasyon ve kalite
| Görev | Dosya |
|---|---|
| Sunucu kurulumu, env değişkenleri | `spec/11-operations.md` § 46 |
| CI/CD (iki ayrı workflow) | `spec/11-operations.md` § 47 |
| Loglama, PII'siz teşhis | `spec/11-operations.md` § 48 |
| Yedekleme | `spec/11-operations.md` § 49 |
| **Test stratejisi, kritik testler** | `spec/12-quality.md` § 51 |
| Performans bütçeleri | `spec/12-quality.md` § 52 |
| Prompt yönetimi, eval | `spec/12-quality.md` § 53 |

### Süreç
| Görev | Dosya |
|---|---|
| Geliştirme ortamı, fake sağlayıcılar | `spec/13-development.md` § 54 |
| Yol haritası, aşama içerikleri | `spec/13-development.md` § 55 |
| **Adım adım inşa (Aşama 0-4)** | `spec/14-build-guide.md` |
| Aşama 0 — İskelet (adımlar) | `spec/14-build-guide.md` § XI-A.2 |
| Aşama 1 — Yürüyen iskelet (adımlar) | `spec/14-build-guide.md` § XI-A.3 |
| **Aşama 2 — İlana özel üretim (adımlar)** | `spec/14-build-guide.md` § XI-A.5 |
| Aşama 3 — Hesap ve MVP (adımlar) | `spec/14-build-guide.md` § XI-A.6 |
| Aşama içerikleri, süre tahmini, gerekçe | `spec/13-development.md` § 55 |
| Günlük geliştirme akışı | `spec/14-build-guide.md` § XI-A.8 |
| VPS kurulumu | `spec/14-build-guide.md` § XI-A.4 |
| Sık karşılaşılan sorunlar | `spec/14-build-guide.md` § XI-A.9 |
| Repo yapısı, klasör ağacı | `spec/15-repos-and-claude.md` § XI-B.2, XI-B.3 |
| Prompt şablonları | `spec/15-repos-and-claude.md` § XI-B.8 |
| Repolar arası koordinasyon | `spec/15-repos-and-claude.md` § XI-B.9 |
| Maliyet, hukuki | `spec/16-cost-legal.md` |
| Terimler sözlüğü | `spec/17-appendix-abc.md` § EK A |
| Yayın kontrol listeleri | `spec/17-appendix-abc.md` § EK C |

---

## Dosya sahipliği

| Yol | Sahibi | Senkronize mi |
|---|---|---|
| `docs/spec/**` | backend repo | ✅ backend → frontend (salt-okunur kopya) |
| `docs/INDEX.md` | backend repo | ✅ |
| `docs/STATUS.md` | ortak | ✅ iki yönlü |
| `docs/handoff/**` | ortak | ✅ **gerçek iletişim kanalı** |
| `docs/notes/**` | her repo kendi | ❌ repo-yerel |

**Spec'i yalnız backend reposunda düzenle.** Frontend'deki kopya `scripts/sync-spec.sh` ile güncellenir; orada yapılan düzenleme bir sonraki senkronda kaybolur.

Frontend bir spec değişikliği gerekiyorsa `handoff/to-backend.md`'ye madde yazar.
