# İnşa Notları — Aşama 2, Adım 2.1-2.5 (kapandı)

> `notes/current.md`'den taşındı (2026-08-24), çünkü 200 satır sınırını üçüncü kez
> zorladı. Kalıcı kararlar zaten `spec/`'te; burası **nasıl bulunduklarının**
> kaydı. Canlı indeksler — promote tablosu ve kasıtlı-ihlal tablosu —
> `current.md`'de kaldı, çünkü 2.6 ve sonrası da onlara yazıyor.

---

## Aşama 2 kayıtları

**`F-001`…`F-006` kapandı** (§ 35.2 / § 35.6). **İkisi ileriye dönük:**

- **Toplu JPQL `update` `@Version`'ı atlar.** 2.7'nin kota sayaçları isteyecek
  ve bayat etag üretecek. `update versioned` — ama *hepsini* sürümlemek
  promote'u kırıyor (denendi, dört test düştü). 2.6'da ısırmadı: `jobs`'ta da
  `generations`'ta da `version` kolonu yok.
- **Okuma, yakalanmak istenen bayatlığı onarır** — etag'i **önceki yazmanın
  yanıtından** al, yoksa araya giren `GET` rakamı tazeler.

**Düzeltme — build guide üç kez "migration" dedi, üçü de yanlıştı:** 2.4'ün
pgvector kolonu, 2.6'nın `jobs` ve `generations` tabloları `V1`'de vardı.
Dördüncüsünü görürsen **önce `V1`'e bak**. · **`continue_anyway` sözlüğe girdi**
(§ 18.1, EK D.6.1) — `B-037` hâlâ açık.


## Adım 2.5 kapanışı

**Düzeltme — keyword bileşeni tag bileşeninin kopyasıydı** (§ 19.2). Ölü duran
`titleTokens`, `contentTokens` adıyla bağlandı.

**Ekleme — `atom_tags`'te `profile_id` yok**, kapsamlama join'den geçiyor;
`AtomTag`, `ProfileOwned`'ı uygulayamayan tek profil satırı. Etiketler
`ProfileAssembler`'a beşinci sorgu olarak **eklenmedi** (§ 52.2 dört diyor).

**Ekleme — kapıların sırası maliyete göre.** Sırayı bozmak ne derlemeyi kırar ne
çıktıyı değiştirir, yalnız para harcatır — bu yüzden testi var.

**Açık soru — § 19.4'ün ikincil kriterleri ilan modunda okunmuyor.** Bölüm
"yakın skorlu atomlar arasında ve genel CV modunda" diyor; bugün yalnız ikincisi
var. "Yakın"ın tanımı yok ve tanımlamak `RelevanceScorer`'ın sözleşmesini
değiştiriyor — uydurulmadı, sorulacak.

---

> Adım 2.6'nın **kuyruk dilimi** de buraya taşındı (2026-08-24, aynı sebeple).

## Adım 2.6 — kuyruk dilimi

**Düzeltme — sondam yanlış geçti.** `SKIP LOCKED` kaldırıldı, 14 test de geçti:
düz `FOR UPDATE` kilidi bekleyip yüklemi yeniden değerlendiriyor. Gerçek fark
**canlılık**; onu ölçen test bir kilidi açık tutup claim'in *hemen* boş
dönmesini bekliyor. Kural § 30.2'de, ders `CLAUDE.md`'de.

**Ekleme — kuyruğun iki okuyucusu ayrı tip** (§ 30.2); ArchUnit `..jobs..` ve
`..generation..` için kendi satırlarını kazandı.

**Ekleme — toplayıcının iki kuralı ve backoff'un taşması § 30.4-30.5'te:** hak
geri verilmiyor, hakkı bitmiş iş `failed`'e gidiyor, üs kaydırmadan sınırlanıyor.

**Düzeltme — CI'da düşen test, yerelde geçen kod.** `Set.copyOf`/`Map.copyOf`
**her JVM çalıştırmasında farklı** sırayla dolaşıyor (üç ölçüm, üç sıra). İki
yerde ısırdı: `TagRepository.labelsByAtom` sorgunun sırasını atıyordu, `Job`'un
üç JSONB kolonu da `JobWorker`'ın sıralı kurduğu hata haritasını bozuyordu.
İkisi de `Collections.unmodifiable*` + `Linked*`; kural `CLAUDE.md`'de.

