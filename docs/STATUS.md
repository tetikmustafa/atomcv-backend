# AtomCV — Durum Panosu

> İki repo da bu dosyayı okur ve kendi satırlarını günceller. **Kural: 60 satırı geçmez.**
> Ayrıntılı inşa kayıtları repo-yerel `notes/current.md`'dedir, buraya taşınmaz.

**Son güncelleme:** 2026-08-24 · **Adım 2.7 kapandı**; **`B-037`…`B-039` açık**

---

## Backend — `atomcv-backend`

| Aşama / Adım | Durum |
|---|---|
| Aşama 0 — İskelet | ✅ |
| Aşama 1 — Yürüyen iskelet (1.1-1.9) | ✅ |
| **Aşama 2 — İlana özel üretim** | ✅ (bir açık kutu: ilan modu uçtan uca) |
| 2.1 hesaplar · 2.2 gateway · 2.3 Faz A · 2.4 embedding · 2.5 Faz B · 2.6 kuyruk+SSE | ✅ |
| 2.7 kota ve maliyet | ✅ (Axiom dataset'i 3.1'e taşındı) |

**Aşama 3 planı:** `spec/14-build-guide.md` § XI-A.6. Aşama 2'nin kaydı
`notes/archive/stage-2.md`'de; açık kutular `notes/current.md`'de.
**`B-039` açık:** `GET /account/usage`, `QUOTA_EXCEEDED` (429) ve yeni
`GENERATION_PAUSED` (503) — ICU mesajı gerekiyor. **`B-038` açık:** `POST /generations` (202), `GET /jobs/{id}`, SSE ve
`GET /generations/{id}/download` yayımlandı; `POST /generations/general`
**kaldırıldı** (`B-022` kapandı). İlerleme `label`'ı **çeviri anahtarı**
(`generation.phase.*`). **`gen:api` çalıştırılmalı.** **`B-037` açık:** `resolutions[].action` `continue_anyway`
kazandı, ICU mesajı gerekiyor. Spec § 14.3, § 14.5, § 18.1-18.6, § 19.2, § 27.1-27.3,
§ 28.4, § 30.2-30.6, § 35.3, § 53.3, § 54.2, § 10.1, EK D.6.1, D.6.3-4, XI-A.5-6
güncellendi — **`sync-spec.sh` Aşama 2 kapanışında** çalıştırılacak (karar: 2026-08-21).

**Aşama 1:** 9/9 ✅, `F-001`…`F-007` kapandı · **Test:** 558 birim · 239 entegrasyon · 44 latex

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

**Şimdi.** `POST /generations` + 202 + SSE + download indi, `/generations/general`
kaldırıldı. Frontend `gen:api`'yi çalıştırıp üretim akışını bu uçlara bağlayabilir
(handoff · `B-038`).
