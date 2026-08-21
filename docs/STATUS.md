# AtomCV — Durum Panosu

> İki repo da bu dosyayı okur ve kendi satırlarını günceller. **Kural: 60 satırı geçmez.**
> Ayrıntılı inşa kayıtları repo-yerel `notes/current.md`'dedir, buraya taşınmaz.

**Son güncelleme:** 2026-08-21 · Adım 2.2 LLM gateway — sözleşme, prompt registry, fake sağlayıcı

---

## Backend — `atomcv-backend`

| Aşama / Adım | Durum |
|---|---|
| Aşama 0 — İskelet | ✅ |
| Aşama 1 — Yürüyen iskelet (1.1-1.9) | ✅ |
| **Aşama 2 — İlana özel üretim** | 🔨 Sürüyor |
| 2.1 Sağlayıcı hesapları | ✅ |
| 2.2 LLM Gateway | 🔨 sözleşme + fake indi, adaptör kaldı |

**Aşama 2 planı:** `spec/14-build-guide.md` § XI-A.5, Adım 2.1-2.7.
**API şekli değişmedi; frontend'in yapacağı bir şey yok.** Değişenler iç:
`Result`/`PipelineError` `shared/error`'a taşındı, `LlmProvider` `LlmOutcome`
döndürüyor (§ 27.1 güncellendi), `local-fake/-record/-real` profilleri yazıldı.

**Aşama 1 kontrol listesi:** 9/9 ✅ · **`F-003`…`F-007` kapandı** → `spec/08-api.md` § 35.2, § 35.6
**Test:** 352 birim · 142 entegrasyon · 44 latex

## Frontend — `atomcv-frontend`

| Aşama / Adım | Durum |
|---|---|
| Aşama 0 — İskelet | ✅ |
| Aşama 1 — Profil editörü | ✅ |
| Aşama 2 — Üretim akışı + SSE | ⬜ |

**Açık `B-nnn` yok.** **Test:** 305 birim · 15 e2e · **bundle** 244.0 / 75.8 KB.
**Aşama 1 kapandı:** 1.2'nin frontend kutuları ✅, devredilenler 4/4, `gen:api` güncel,
gerçek uca karşı 34 kontrol geçti. Notlar `notes/archive/stage-1.md`.

---

## Açık kararlar (ikisini de ilgilendirir)

| Soru | Bekleyen taraf |
|---|---|
| Üretimde migration nasıl çalışır | backend · Aşama 2 |
| Anonim akış kuyruğu kullanacak mı | backend · Aşama 3 |
| Atomsuz entry seçilemiyor | backend · Bölüm 20.2 modelini etkiler |

---

## Sonraki senkronizasyon noktası

Backend Aşama 2'de `POST /generations` + 202 + SSE'yi bitirdiğinde:
`gen:api` yeniden çalıştırılacak, `POST /generations/general` **kaldırılacak**.
Frontend o uca kalıcı ekran bağlamamalı (handoff · B-022).
