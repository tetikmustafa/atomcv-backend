# AtomCV — Durum Panosu

> İki repo da bu dosyayı okur ve kendi satırlarını günceller. **Kural: 60 satırı geçmez.**
> Ayrıntılı inşa kayıtları repo-yerel `notes/current.md`'dedir, buraya taşınmaz.

**Son güncelleme:** 2026-08-25 · **`B-043` kapandı**, açık madde yok · Aşama 0-2 yeniden doğrulandı

---

## Backend — `atomcv-backend`

| Aşama / Adım | Durum |
|---|---|
| Aşama 0 — İskelet | ✅ |
| Aşama 1 — Yürüyen iskelet (1.1-1.9) | ✅ |
| **Aşama 2 — İlana özel üretim** | ✅ kapanış listesi 8/8 |
| 2.1 hesaplar · 2.2 gateway · 2.3 Faz A · 2.4 embedding · 2.5 Faz B · 2.6 kuyruk+SSE | ✅ |
| 2.7 kota ve maliyet | ✅ (Axiom dataset'i 3.1'e taşındı) |

**Aşama 3 planı:** § XI-A.6. Aşama 2'nin kaydı `notes/archive/stage-2.md`'de.
**`B-043` frontend'de kapandı**; spec zaten senkrondu, `sync-spec.sh` fark
üretmedi. **Aşama 0-2 bu makinede baştan doğrulandı (2026-08-25):** üç koşu da
`--rerun-tasks` ile sıfırdan koştu, `make dev` ayağa kalktı (health UP, Flyway
V1 `success`, 22 tablo, pgvector 0.8.6), Mailpit 200, gitleaks kancası kurulu,
CI main'de yeşil. `suspicious_output` sorunuzun cevabı `to-frontend.md`'de.

**Aşama 1:** 9/9 ✅, `F-001`…`F-007` kapandı
**Test:** 614 birim · 251 entegrasyon · 48 latex — 0 hata, 0 atlanan

## Frontend — `atomcv-frontend`

| Aşama / Adım | Durum |
|---|---|
| Aşama 0 — İskelet | ✅ |
| Aşama 1 — Profil editörü | ✅ |
| Aşama 2 — Üretim akışı + SSE | ✅ |

**`B-042` ve `B-043` kapandı**, açık `B-nnn` yok. `gen:api` fark üretmedi.
**Test:** 401 birim · 25 e2e · **bundle** profil 250.7 / üretim 214.8 KB.
**Aşama 2 gerçek uca karşı yeniden denetlendi (2026-08-25): 26/26 kontrol.**
Kota, idempotency, SSE ilerleme (0→10→30→50→70), uygunluk raporu, iki dil
etiketi, PDF + 406, terminal olayın tekrar oynatılması. Üç gerçek hata reddi
`wireErrors.test.ts`'e alındı. **Yalnız `suspicious_output` tetiklenemedi.**

---

## Açık kararlar (ikisini de ilgilendirir)

| Soru | Bekleyen taraf |
|---|---|
| Üretimde migration nasıl çalışır | backend · Aşama 2 |
| Anonim akış kuyruğu kullanacak mı | backend · Aşama 3 |
| Atomsuz entry seçilemiyor | backend · Bölüm 20.2 modelini etkiler |

---

## Sonraki senkronizasyon noktası

Aşama 3'ün ilk ucu: **`/auth/session` ve `capabilities`** (Adım 3.3 ve 3.6).
Şu an iki tarafta da açık `B-nnn`/`F-nnn` yok.
