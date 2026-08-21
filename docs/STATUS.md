# AtomCV — Durum Panosu

> İki repo da bu dosyayı okur ve kendi satırlarını günceller. **Kural: 60 satırı geçmez.**
> Ayrıntılı inşa kayıtları repo-yerel `notes/current.md`'dedir, buraya taşınmaz.

**Son güncelleme:** 2026-08-21 · `F-003`…`F-007` kapandı, kota gün sınırı UTC olarak karara bağlandı

---

## Backend — `atomcv-backend`

| Aşama / Adım | Durum |
|---|---|
| Aşama 0 — İskelet | ✅ |
| 1.1 Domain modeli | ✅ |
| 1.2 Profil CRUD + şema | ✅ |
| 1.3 LaTeX container | ✅ |
| 1.4 Renderer | ✅ |
| 1.5 Ölçüm sistemi | ✅ |
| 1.6 Faz C (seçim) | ✅ |
| 1.7 Faz E/F | ✅ |
| 1.8 Genel mod + PDF ucu | ✅ |
| 1.9 Golden set + kritik testler | ✅ |
| **Aşama 2 — İlana özel üretim** | ⬜ Sırada |

**Aşama 2 planı:** `spec/14-build-guide.md` § XI-A.5, Adım 2.1-2.7.
**Aşama 1'in devrettikleri:** `notes/current.md` — kapanmadan Aşama 2'ye girilmez.

**Aşama 1 kontrol listesi:** 9/9 ✅ · **`F-003`…`F-007` kapandı** → `spec/08-api.md` § 35.2, § 35.6
**Test:** 312 birim · 142 entegrasyon · 44 latex

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
