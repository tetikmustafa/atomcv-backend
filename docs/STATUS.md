# AtomCV — Durum Panosu

> İki repo da bu dosyayı okur ve kendi satırlarını günceller. **Kural: 60 satırı geçmez.**
> Ayrıntılı inşa kayıtları repo-yerel `notes/current.md`'dedir, buraya taşınmaz.

**Son güncelleme:** 2026-08-25 · **Aşama 2 iki repoda da kapandı**; **`F-008` açık · `B-040` açık**

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
**`F-009`…`F-012` kapandı** ve şemayı değiştirdiler — **`B-040` açık,
`gen:api` çalıştırılmalı**: `generalMode` düştü, ilerleme alanları opsiyonel
oldu, `usage` `attempted` kazandı. `B-037`…`B-039` arşivlendi.
**`F-008` (uygunluk raporu) sıradaki dilim.** `sync-spec.sh` o dilim
kapanınca.

**Aşama 1:** 9/9 ✅, `F-001`…`F-007` kapandı · **Test:** 580 birim · 245 entegrasyon · 47 latex

## Frontend — `atomcv-frontend`

| Aşama / Adım | Durum |
|---|---|
| Aşama 0 — İskelet | ✅ |
| Aşama 1 — Profil editörü | ✅ |
| Aşama 2 — Üretim akışı + SSE | ✅ (uygunluk raporu hariç · `F-008`) |

**`B-040` açık** (`gen:api`) — `B-037`…`B-039` arşivlendi. **Test:** 352 birim · 23 e2e ·
**bundle** profil 250.6 / üretim 214.8 KB.
**Aşama 2 kapandı:** üretim akışı, SSE, sonuç ekranı ve kota bağlı; gerçek uca
karşı MSW kapalı **10 kontrol** geçti ve ikisi gerçek hata buldu (`Accept` →
406, dev proxy'nin SSE'yi gzip'lemesi). **Uygunluk raporu yazılamadı** —
veri yayımlanmadı (`F-008`). Notlar `notes/archive/stage-2.md`.

---

## Açık kararlar (ikisini de ilgilendirir)

| Soru | Bekleyen taraf |
|---|---|
| Üretimde migration nasıl çalışır | backend · Aşama 2 |
| Anonim akış kuyruğu kullanacak mı | backend · Aşama 3 |
| Atomsuz entry seçilemiyor | backend · Bölüm 20.2 modelini etkiler |

---

## Sonraki senkronizasyon noktası

**`B-040` → `gen:api`.** Dördü kapandı; kalan `F-008`, uygunluk raporu:
Faz F hesaplar, `GET /generations/{id}` yayımlar, `completed` olayı
`matchLevel` kazanır.
