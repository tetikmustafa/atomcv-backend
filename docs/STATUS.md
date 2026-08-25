# AtomCV — Durum Panosu

> İki repo da bu dosyayı okur ve kendi satırlarını günceller. **Kural: 60 satırı geçmez.**
> Ayrıntılı inşa kayıtları repo-yerel `notes/current.md`'dedir, buraya taşınmaz.

**Son güncelleme:** 2026-08-25 · **`F-016` kapandı**; **`B-043` açık**

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
**`F-016` kapandı** — `UNPARSEABLE_JOB_DESCRIPTION` artık `params.reason`
taşıyor (sekiz değerli kapalı sözlük: ön kontrolün dördü + § 18.4'ün dördü) ve
`resolutions` sebebe göre değişiyor. **`B-043` açık, `gen:api` + sekiz
`errors.*` anahtarı gerekiyor**; `continue_anyway` kapı reddinden kalktı.
`B-042` arşivlendi. **`sync-spec.sh` çalıştırılmalı** — § 18.1, § 18.4 ve
EK D.6.1 değişti.

**Aşama 1:** 9/9 ✅, `F-001`…`F-007` kapandı · **Test:** 614 birim · 251 entegrasyon · 48 latex

## Frontend — `atomcv-frontend`

| Aşama / Adım | Durum |
|---|---|
| Aşama 0 — İskelet | ✅ |
| Aşama 1 — Profil editörü | ✅ |
| Aşama 2 — Üretim akışı + SSE | ✅ |

**`B-042` kapandı** — `gen:api` çalıştı, not yazıldı. **`F-016` backend'de
kapandı**, gerçek LLM'e karşı ilk turda çıkmıştı; aksiyon `B-043` olarak döndü.
**Test:** 366 birim · 25 e2e · **bundle** profil 250.7 / üretim 214.8 KB.
**Aşama 2 tam kapandı**; notlar `notes/archive/stage-2.md`.

---

## Açık kararlar (ikisini de ilgilendirir)

| Soru | Bekleyen taraf |
|---|---|
| Üretimde migration nasıl çalışır | backend · Aşama 2 |
| Anonim akış kuyruğu kullanacak mı | backend · Aşama 3 |
| Atomsuz entry seçilemiyor | backend · Bölüm 20.2 modelini etkiler |

---

## Sonraki senkronizasyon noktası

**`B-043`** — sebebe göre hata cümlesi ve resolution satırı. Sonrası Aşama 3'ün
ilk ucu (`/auth/session` ve `capabilities`).
