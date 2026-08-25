# AtomCV — Durum Panosu

> İki repo da bu dosyayı okur ve kendi satırlarını günceller. **Kural: 60 satırı geçmez.**
> Ayrıntılı inşa kayıtları repo-yerel `notes/current.md`'dedir, buraya taşınmaz.

**Son güncelleme:** 2026-08-25 · **`F-013`…`F-015` kapandı**; **`B-042` açık**

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
**`F-013`…`F-015` kapandı**, biri şemayı değiştiriyor — **`B-042` açık,
`gen:api` çalıştırılmalı**: `GET /generations/{id}` `contentLanguage` ve
`postingLanguage` taşıyor. Diğer ikisi sunucuda kapandı, aksiyon yok:
sağlayıcının dört sessiz hata yolu artık WARN basıyor (`F-014`), ve fiyat
tablosu kullanılan modeli kapsıyor + açılışta fiyatsız modeli adıyla uyarıyor
(`F-015`). `B-040`, `B-041` arşivlendi. **`sync-spec.sh` çalıştırılmalı** —
§ 21.8, § 27.2, § 27.4 ve § 35.3 değişti.

**Aşama 1:** 9/9 ✅, `F-001`…`F-007` kapandı · **Test:** 606 birim · 251 entegrasyon · 48 latex

## Frontend — `atomcv-frontend`

| Aşama / Adım | Durum |
|---|---|
| Aşama 0 — İskelet | ✅ |
| Aşama 1 — Profil editörü | ✅ |
| Aşama 2 — Üretim akışı + SSE | ✅ |

**`B-042` açık** (`gen:api` + dil uyarısı). **Test:** 362 birim · 25 e2e ·
**bundle** profil 250.6 / üretim 214.8 KB.
**Aşama 2 tam kapandı:** üretim akışı, SSE, kota ve uygunluk raporu; gerçek uca
karşı MSW kapalı iki tur geçti. Notlar `notes/archive/stage-2.md`.

---

## Açık kararlar (ikisini de ilgilendirir)

| Soru | Bekleyen taraf |
|---|---|
| Üretimde migration nasıl çalışır | backend · Aşama 2 |
| Anonim akış kuyruğu kullanacak mı | backend · Aşama 3 |
| Atomsuz entry seçilemiyor | backend · Bölüm 20.2 modelini etkiler |

---

## Sonraki senkronizasyon noktası

**`B-042` → `gen:api`, sonra sonuç ekranında dil uyarısı.** Sonrası Aşama 3'ün
ilk ucu (`/auth/session` ve `capabilities`).
