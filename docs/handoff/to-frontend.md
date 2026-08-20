# → Frontend

> **Kanal kuralları**
> - Backend yazar, frontend okur ve `OPEN` → `ACK` taşır.
> - Her madde bir ID taşır (`B-nnn`), numaralar tekrar kullanılmaz.
> - **Dosya 100 satırı geçerse arşivleme gecikmiştir.** `ACK` maddeleri `resolved/`'a taşınır.
> - API *şekli* için otorite OpenAPI şemasıdır. Burası **neden değişti + ne yapman lazım** taşır.
> - Kalıcı kural niteliğindeki maddeler `spec/`'e işlenir ve buradan silinir.

---

## OPEN

### B-033 · Doküman yapısı bölündü — aynısını sizde de kurun
**Since:** commit `221a7c1`, `02441b3`, `4f890fe` · **Spec:** `docs/INDEX.md`
Tek dosyalık `teknik-mimari-dokumani.md` erişim desenine göre bölündü: `spec/` (18 dosya,
salt-okunur kopya sizde), `notes/` (repo-yerel), `handoff/` (bu kanal), `INDEX.md`, `STATUS.md`.
Eski dosya backend'de `_archive-monolith.md` olarak duruyor — **okunmaz**, yalnız yedek.
**Aksiyon:** Kurulum `_PLACEMENT.md`'de. Sizde `sync-spec.sh` **olmayacak** (spec'i alırsınız,
senkronlamazsınız); `sync-handoff.sh` ve `check-doc-sizes.sh` olacak.

CLAUDE.md'de yaptığımız ve sizde de gereken beş şey — backend'de 445 → 276 satır:
1. **"Architecture Documents" bölümü** `_CLAUDE-md-replacement.md`'nin frontend bloğuyla
   değişir. Görev→bölüm tablosu silinir; artık `INDEX.md`'de.
2. **Spec'i tekrar eden bölümler işaretçiye iner** (tasarım prensipleri, tech stack, modül
   haritası, test stratejisi). İki kopya kaçınılmaz olarak ayrışıyor.
3. **"Current Stage" bir devralma reçetesine dönüşür**: `STATUS.md` → `handoff/to-frontend.md`
   → `notes/current.md` → adımın planı. Yeni bir oturum "Aşama 2'ye devam" dendiğinde tek yol
   izler. Bizde en çok işe yarayan değişiklik buydu.
4. **Bayat atıfları tarayın:** `teknik-mimari-dokumani.md`, `EK D`, `sync-docs.sh` geçen her
   yer. Bölünme `D.x` numaralandırmasını korudu, yani `EK D.6.3` → `spec/08b-api-contract.md`
   § D.6.3 gibi çözülüyor; sadece dosya adı eklenir.
5. **`check-doc-sizes.sh`'teki CLAUDE.md sınırı 160 gerçekçi değil.** Makineye özgü bilgiler
   ve mutlak kurallar spec'e gidemiyor (spec makineden bağımsız ve senkronlanıyor). Backend
   280'e çekti; sizinki daha ince olabilir, ölçüp karar verin.

İki tuzak: **`rsync` Git Bash'te yok** — betiklerinizde varsa `rm -rf` + `cp -r` yapın.
**`core.filemode=false`** olduğu için `chmod +x` commit'e yansımıyor;
`git update-index --chmod=+x scripts/*.sh` gerekiyor, yoksa Linux runner'da "Permission denied".

### B-022 · `POST /generations/general` geçicidir
**Since:** Adım 1.8 · **Spec:** `spec/08-api.md` § 35.3
Senkron, Aşama 1'e özgü. Gövde opsiyonel (`maxPages` 1-10, `language`). Yanıt `application/pdf`, **hiçbir yere kaydedilmiyor** — indirme bağlantısı, geçmiş, düzenleme döngüsü yok.
Aşama 2'de `POST /generations` + 202 + iş akışı gelecek.
**Aksiyon:** Bu uca **kalıcı ekran bağlama.** Geçici bir "önizle ve indir" akışı yeterli.
Hatalar: 422 `INSUFFICIENT_PROFILE` · 422 `PAGE_LIMIT_EXCEEDED` · 409 `CONFLICTING_PREFERENCES` · 502 `COMPILATION_FAILED` · 400 `VALIDATION_FAILED`

### B-021 · `PAGE_LIMIT_EXCEEDED` için "tekrar dene" yanlış çözüm
**Spec:** `spec/06-pipeline-d-g.md` § 23
Sunucu içeriği kendi iki kez kısaltmayı deniyor; bu hata geldiyse denemeler bitmiştir.
**Aksiyon:** Kullanıcıya sayfa sınırını artırmayı veya içerik çıkarmayı öner. Retry düğmesi koyma. `params`: `actual`, `limit`.

### B-024 · Bayat varyant düğmeleri Aşama 2'ye ait
**Spec:** `spec/09-frontend.md` § 37.6
`Variant.stale` Aşama 1'de **her zaman false**; yeniden üretim ucu yok.
**Aksiyon:** Rozeti göster, kontrolü çizme. (Mevcut kararınız doğru — teyit.)

### B-030 · Operasyon id'leri adlandırıldı
`list_2` → `listAtoms`, `create_1` → `createEntry`, `patch` → `patchSection` …
**Aksiyon:** `gen:api` sonrası üretilen yüzeye **isimle** bağlanan yerleri ara; kırılacaklar.

### B-032 · Seed profilinde iki sözcüklemeli atom var
`senior_backend_tr` artık `enabledLanguages: ["tr","en"]`; Deneyim'in ilk maddesi Türkçe birincilin yanında İngilizce alternatif taşıyor.
**Aksiyon:** Sekmeler, promote ve birincil-önce sıralama mock'suz test edilebilir. `make db-reset && make dev` gerekiyor — seeder mevcut profile dokunmuyor (P8).

---

## Kalıcı kurallar — `spec/`'e işlendi, burada tutulmuyor

| Eski # | Konu | Nerede |
|---|---|---|
| 1-4 | Run/mark kuralları (`href` zorunluluğu, bilinmeyen mark koruması, `v` sunucuya ait, `m` daima dizi) | `spec/04-data-model.md` § 14.1 |
| 5 | `content_hash` düz metnin hash'i | `spec/04-data-model.md` § 16.2 |
| 6 | Sözlükler küçük harf, hata kodu büyük harf | `spec/08b-api-contract.md` |
| 7, 10-12 | Hata kataloğu, `params` disiplini, göreli `type` | `spec/08b-api-contract.md` |
| 8 | ETag kapsamı (`generations` ETag taşımaz) | `spec/08-api.md` § 35.6 |
| 9 | Anonim TTL kayar — "son etkinliğinden iki saat sonra" | `spec/08-api.md` § 35.7 |
| 13-20, 23 | Profil/bölüm/entry/atom/varyant uçları, export, `completeness`, `complete_profile` | `spec/08-api.md` |
