# Aşama 3'ü elle test etme

> Repo-yerel, senkronlanmaz. Frontend'in Aşama 3 ekranları **yokken** backend'in
> kimlik, çıkarım, anonim mod ve kapanış uçlarını sürmek için yazıldı.
> `manual-test-stage-2.md` üretim akışını anlatıyor; bu dosya onun üstüne biner.

---

## 0. Kabuk — hangisi, ve neden ötekiler değil

**Buradaki her komut Git Bash içindir.** Üçünü de denemek zaman kaybı, çünkü
ikisi bu komutları hiç çalıştıramaz:

| Kabuk | Ne olur |
|---|---|
| **Git Bash** ✅ | Doğrusu bu. `curl.exe`, `awk`, `sed` hepsi var (`/mingw64/bin/curl`, 8.16). |
| **PowerShell** ❌ | `curl` orada **`Invoke-WebRequest`'in takma adı** — `-s`, `-c`, `-b`, `-H` diye bayrakları yok, hepsi hata verir. `$(...)` ve tek tırnak da başka anlama gelir. |
| **cmd.exe** ❌ | `$(...)` yok, `awk` yok, tek tırnaklı JSON gövdesi olduğu gibi geçmez. |

Git Bash'i **repo klasöründe** aç: Explorer'da klasöre sağ tık → *Open Git Bash
here*, ya da `Git Bash` açıp `cd /c/Users/tetik/Desktop/xd/projects/atomcv-backend`.

**`jq` bu makinede kurulu değil.** Aşağıdaki ve `manual-test-stage-2.md`'deki
`| jq` boruları onsuz "command not found" verir. Tek seferlik:

```powershell
winget install jqlang.jq        # PowerShell'de, sonra Git Bash'i yeniden aç
```

Kurmak istemiyorsan `| jq` yerine `| python -m json.tool` yaz — Python zaten
PATH'te ve script'ler de onu kullanıyor.

### Yazmak istemiyorsan: iki script

Bu dosyadaki en uzun iki tur script'e alındı. **Elle komut yazman gerekmiyor:**

```bash
./scripts/dev-signin.sh                    # 2. bölümün tamamı, uçtan uca
./scripts/dev-record.sh /c/.../cv.pdf      # 6. bölümün tamamı
```

`dev-signin.sh` çerez kavanozunu `.dev-jar.txt`'e bırakır; sonraki her istek
onu kullanır. İkisi de bu makinede çalışır durumda **denendi**.

---

## 1. CSRF — Aşama 2 notunun eskidiği yer

Adım 3.3 CSRF'i açtı ve **istisnası yok**: her `POST`/`PATCH`/`DELETE` bir
`X-XSRF-TOKEN` başlığı ister, değeri de aynı isteğin `XSRF-TOKEN` çerezinden
gelir (çift gönderim, EK D.6.6). `manual-test-stage-2.md`'deki çıplak `curl -X
POST` komutları bu yüzden bugün **403** alır — o dosya Aşama 2'de yazıldı.

İki yol var.

**Swagger UI** — hiçbir şey yapmana gerek yok. `application-local.yml`
`springdoc.swagger-ui.csrf.enabled: true` diyor, UI çerezi okuyup başlığı
kendisi ekliyor. `http://localhost:8080/swagger-ui/index.html`.

**curl** — bir çerez kavanozu tut, tokenı oradan oku:

```bash
# Her yanıt çerezi taşıyor (SecurityConfig eagerTokens), yani health yeter.
curl -s -c jar.txt localhost:8080/actuator/health > /dev/null
XSRF=$(awk '/XSRF-TOKEN/ {print $7}' jar.txt)

curl -s -b jar.txt -H "X-XSRF-TOKEN: $XSRF" -X POST ...
```

Kavanozu **bütün tur boyunca kullan** — oturum çerezi (`sid`) de oraya düşecek.

---

## 2. Magic link ile giriş — frontend olmadan (§ 40.2, § 40.3)

Frontend'in `/verify` sayfası henüz yok. Gerek de yok: e-postadaki bağlantı bir
*sayfaya* gidiyor, giriş ise o sayfanın yaptığı `POST`. O POST'u elle yapacağız.

**Önce dev kullanıcısını kapat.** Açıkken çerezsiz her istek sabit bir
geliştirici kullanıcısı döndürür ve gerçekten giriş yapıp yapmadığını göremezsin:

```bash
LOCAL_DEV_SESSION=false make dev
```

Açılışta iki uyarı görmen **normal ve doğru**: "No challenge configured" ve
Resend anahtarı yoksa e-posta uyarısı. İkisi de `application-local.yml`'in
kasıtlı sonucu — `.env`'deki üretim anahtarları yerelde okunmuyor.

**Link iste:**

```bash
curl -s -c jar.txt localhost:8080/actuator/health > /dev/null
XSRF=$(awk '/XSRF-TOKEN/ {print $7}' jar.txt)

curl -s -o /dev/null -w '%{http_code}\n' -b jar.txt -H "X-XSRF-TOKEN: $XSRF" \
  -X POST localhost:8080/api/v1/auth/magic-link \
  -H 'Content-Type: application/json' \
  -d '{"email":"ben@example.com"}'
```

**202** bekleniyor — ve adresin hesabı olsun olmasın **hep 202** (§ 40.4, hesap
sayımına karşı). `403` alıyorsan `.env`'de `TURNSTILE_SECRET_KEY` dolu ve
`application-local.yml` devrede değil demektir; `400` alıyorsan CSRF başlığı
gitmemiştir.

**E-postayı Mailpit'ten al:** `http://localhost:8025` — bağlantı şu şekilde:

```
http://localhost:3000/verify?s=<selector>&v=<verifier>
```

`s` ve `v`'yi tarayıcıdan kopyala. Kabuktan çekmek istersen — gövde JSON içinde
kaçışlı geldiği için `grep` işe yaramaz, bağlantıyı ayrıştırmak gerekir:

```bash
curl -s localhost:8025/api/v1/message/latest \
  | python -c "import sys,json,re;print(re.search(r'/verify\?s=\S+',json.load(sys.stdin)['Text']).group(0))"
```

(`dev-signin.sh` tam olarak bunu yapıyor — elle uğraşmana gerek yok.)

**Girişi yap** — bağlantının kendisi değil, sayfanın yaptığı POST:

```bash
curl -s -b jar.txt -c jar.txt -H "X-XSRF-TOKEN: $XSRF" \
  -X POST localhost:8080/api/v1/auth/verify \
  -H 'Content-Type: application/json' \
  -d '{"selector":"<s>","verifier":"<v>"}' -D - | head -20
```

Görmen gerekenler: **200**, gövdede `profileUpgrade` (anonim çalışman yoksa
`none`), ve `Set-Cookie: sid=...`. Kavanoza düştü, artık girişlisin:

```bash
curl -s -b jar.txt localhost:8080/api/v1/auth/session | jq
```

**Üç şeyi ayrıca dene** — üçü de § 40.3'ün sözü:

| Deneme | Beklenen |
|---|---|
| Aynı `s`/`v` ile ikinci `POST /verify` | **400** `MAGIC_LINK_INVALID` — tek kullanımlık |
| Uydurma bir `v`, gerçek bir `s` ile | **400**, *aynı* kod — hangi yarının yanlış olduğu söylenmez |
| Aynı adrese arka arkaya 4 link isteği | 4.'sü **429** (adres başına 3 / 15dk) |

⚠️ Rate limit denedikten sonra Redis'i temizle, yoksa sonraki denemen ilgisiz
bir 429'a düşer:

```bash
docker compose exec redis redis-cli --scan --pattern 'ratelimit:*' | xargs -r docker compose exec -T redis redis-cli del
```

## 3. OAuth — yerelde nereye kadar

`GET /api/v1/auth/providers` yapılandırılmış sağlayıcıları listeler. `.env`'de
Google/GitHub çiftleri dolu, ama **callback URL'leri üretim domainine kayıtlı**;
yerelde `/oauth/google/start` seni Google'a götürür, Google `redirect_uri
mismatch` der. Yerel OAuth istiyorsan Google Console'a
`http://localhost:8080/api/v1/auth/oauth/google/callback`'i **ikinci bir redirect
URI olarak** ekle (GitHub tek URL kabul ettiği için orada üçüncü bir uygulama
gerekir — MVP için gereksiz).

**Yerelde OAuth'u atlaman bir kayıp değil:** oturum, CSRF, yükseltme ve kota
yollarının hepsi magic link turundan geçiyor.

## 4. CV yükleme ve çıkarım (§ 31.6)

`local-fake` altında **çıkarım fixture'ı yoksa** `job_analysis` gibi bu da
sentetik cevap alır ve anlamsız bir profil yazar. Anlamlı bir tur için önce
6. bölümdeki kayıt turunu yap.

```bash
curl -s -b jar.txt -H "X-XSRF-TOKEN: $XSRF" \
  -X POST localhost:8080/api/v1/profile/import \
  -F 'file=@/c/Users/tetik/Desktop/cv.pdf' | jq
```

**202** ve bir `jobId`. İlerlemeyi Aşama 2'deki gibi izle
(`/api/v1/jobs/$JOB` ve `/stream`). Beş senkron ret (§ 31.6.2) kuyruğa hiç
girmez — boş dosya, çok büyük dosya, tanınmayan tür, şifreli PDF, metni çok az
olan tarama. Hepsini denemeye değer; en kolayı:

```bash
echo "merhaba" > /tmp/bos.txt
curl -s -b jar.txt -H "X-XSRF-TOKEN: $XSRF" \
  -X POST localhost:8080/api/v1/profile/import -F 'file=@/tmp/bos.txt' | jq
```

⚠️ **İkinci bir CV yüklersen bugün profile *eklenir*, üzerine yazılmaz** —
bölümler ikiye katlanır. Bilinen açık, karar verildi, henüz inmedi.

## 5. Anonim mod (§ 41.3) — çerezsiz

Anonim akış hesabı olmayan bir tarayıcıdır: **temiz bir kavanoz** kullan.

```bash
rm -f anon.txt
curl -s -c anon.txt localhost:8080/actuator/health > /dev/null
AXSRF=$(awk '/XSRF-TOKEN/ {print $7}' anon.txt)

curl -s -b anon.txt -c anon.txt -H "X-XSRF-TOKEN: $AXSRF" \
  -X POST localhost:8080/api/v1/profile/import -F 'file=@cv.pdf' | jq
```

**Asıl test veritabanında** (§ 31.6.3 — anonim profil hiçbir tabloda satır
değil):

```bash
docker compose exec postgres psql -U atomcv -d atomcv -c \
  "SELECT count(*) FROM profiles;"     # yükleme öncesi ve sonrası AYNI olmalı
docker compose exec redis redis-cli --scan --pattern 'anon:*' | head
```

Sonra **yükseltme**: aynı `anon.txt` kavanozuyla 2. bölümün magic link turunu
yap. `POST /auth/verify` yanıtındaki `profileUpgrade` `upgraded` demeli ve
profil artık `profiles` tablosunda olmalı.

⚠️ **Bilinen tuzak:** o hesap daha önce bir kez giriş yapıp uygulamayı açtıysa
boş bir profil satırı oluşmuştur ve yanıt `kept_existing` gelir — anonim emek
gider. Karar verildi (boş satır sessizce ezilecek), henüz inmedi.

## 6. Fixture kaydı — `make record` (§ 54.2)

**Bu, `local-fake`'i anlamlı yapan adım.** Kayıt yolu Adım 3.x'e kadar bağlı
değildi: `FixtureStore.save` yazılmıştı, çağıranı yoktu, yani `make record`
gerçek çağrı yapıp hiçbir şey saklamıyordu. Artık `ProviderChain` başarılı her
cevabı `AnswerRecorder`'a veriyor.

**Para harcar.** Sırayla:

```bash
make dev-full          # container'lar (ayrı kabuk)
make record            # backend, local+local-record — gerçek OpenRouter
```

Sonra **kaydetmek istediğin her fazı bir kez sür**:

| Faz | Nasıl sürülür |
|---|---|
| `job_analysis` | `POST /generations` gerçek bir ilanla (Aşama 2 notu, 2. adım) |
| `profile_extraction` | `POST /profile/import` gerçek bir CV ile |
| `bullet_rewrite`, `about_synthesis` | aynı üretim; Faz D üretimin içinde koşar |
| `cover_letter` | `POST /generations` gövdesine `"coverLetter": true` ekle (`B-056`) |
| `translation` | profilde ikinci dil iste (§ 32) |

Log'da her kayıt için bir satır göreceksin:
`Recorded job_analysis:v1 to src/test/resources/fixtures/llm/...`

Bittiğinde:

```bash
ls -R src/test/resources/fixtures/llm
sh ./gradlew latexTest        # 48/48 olmalı — bugün 44/48
```

⚠️ **Fixture anahtarı istek metninin özetinden türüyor.** Kaydettiğin *tam o*
ilan/CV metni tekrar gönderilmezse fixture ıskalanır ve sentetik cevaba düşer.
Kaydederken kullandığın metinleri bir yere kaydet.

## 7. Hesap silme (§ 57.4) — en son yap

Adı üstünde: bu tur girişli kullanıcıyı **siler**.

```bash
curl -s -o /dev/null -w '%{http_code}\n' -b jar.txt -H "X-XSRF-TOKEN: $XSRF" \
  -X DELETE localhost:8080/api/v1/account
```

**204**, ve `sid` çerezi geçersiz. Kanıt şemadan okunur — `AccountDeletionIT`'in
yaptığı işi elle yapmak istersen:

```bash
docker compose exec postgres psql -U atomcv -d atomcv -c \
  "SELECT count(*) FROM profiles WHERE user_id = '<id>';"    # 0
```

---

## Bu turda bilerek görülemeyecekler

- **Turnstile'ın gerçekten reddetmesi** — widget frontend'de, secret yerelde
  kapalı. Üretimde `TURNSTILE_SECRET_KEY` yoksa uygulama zaten açılmıyor.
- **Gerçek e-posta teslimatı** — Mailpit teslim etmez, gösterir. SPF/DKIM/DMARC
  Adım 3.2'nin işi ve yalnız üretimde doğrulanır.
- **`suspicious_output`** — bir enjeksiyon tripwire'ı; uslu bir modelle
  açılmaması **beklenen** davranış, eksik değil.

---

## `cover_letter` v2'yi ölçmek — ve neden aktif değil

v2 prompt'u 2026-09-09'da indi ve **aynı gün aktiflikten alındı**: gerekçesi
diskteki kayıtlara karşı çürüdü. v2, "prompt 250-400 istiyor ama model şekle
uyup ~130 yazıyor" iddiasıyla sayıyı bırakmıştı. Kayıtlı on iki `cover_letter`
cevabının dağılımı şu:

| tarih | kelime | not |
|---|---|---|
| 08-28 ×3 | 106, 119, 127 | **hepsi sentetik girdiyle koşulmuş** |
| 08-30 ×2 | 130, 153 | `F-026`'nın ölçtüğü ikisi |
| 09-02 → 09-07 ×7 | 255, 260, 268, 272, 286, 287, 290 | hepsi gerçek |

**Model isteği tutuyor.** Kısa olanların üçü sentetik, ikisi 30 Ağustos'tan; 2
Eylül'den sonraki her gerçek taslak bandın içinde. Yani v2 işleyen bir isteği
kaldırıyordu. Aktif sürüm `v1`; v2 diskte duruyor ve **tek bir değer** uzakta.

### Ölçüldü (2026-09-09): v2 mektupları kısaltıyor, `v1` kalıyor

Tur koşuldu, tek v2 taslağı kaydedildi: **169 kelime.** Bant 255-290'dı, yani
**86 kelime altında** ve yedi gerçek v1 taslağının hepsinden kısa. Alan alan
bakınca eksiğin **tamamı gövdede**:

| | v2 (169) | gerçek v1 (255) |
|---|---|---|
| opening | 30 | 41 |
| **body** | **102** | **181** |
| closing | 32 | 29 |

Yani v2 farklı *şekilde* bir mektup yazmadı, **kanıt paragraflarını inceltti** —
ve kaldırdığı tek şey 250-400 isteğiydi. Muhafız onu yine de geçiriyor (169 >
120), dolayısıyla bu bir ret değil, bir kalite gerilemesi.

**Tek örnek, ve neden yetiyor.** Bir taslak bir popülasyon değil; v2'yi *kabul
etmek* için yetmezdi. Ama karar statükoyu korumak, ve ölçüt ölçümden **önce**
yazılmıştı — 255-290'ı tutmazsa v1 kalır. 169 marjinal bir kaçış değil. İkinci
bir örnek isteyen aynı tarifi tekrar koşabilir; maliyeti birkaç kuruş.

Aktif sürüm `v1`. v2 diskte duruyor ve hâlâ **tek bir değer** uzakta.

**Kanıt bu satırların kendisi, fixture değil.** `cover_letter/` dizini
`.gitignore`'da: cevabı CV'den türeyen her prompt öyle (`profile_extraction`,
`about_synthesis`, `bullet_rewrite` de). Yani on üç fixture yalnız kaydeden
makinede duruyor ve yalnız orada replay olur — sayıları buraya yazmak, onları
saklamanın tek yolu.

**Tarif, ikinci bir örnek isteyene.** `application.yml`'da `cover_letter: v2`
(ya da `prompts.experiment` ile trafiğin bir kısmı) ve bir kayıt turu — para
harcar, `cover_letter` başına birkaç kuruş:

    # Kabuk 1
    make dev-full
    # Kabuk 2 — GERÇEK çağrılar
    make record
    # Kabuk 3
    ./scripts/dev-record.sh /c/Users/tetik/Desktop/cv.pdf

Sonra karşılaştırma; fixture adı sürümü taşıdığı için ikisi yan yana durur.
Git Bash'te, `python -` ile şu betiği besle:

    import glob, json, os
    for path in sorted(glob.glob('src/test/resources/fixtures/llm/cover_letter/*.json')):
        with open(path, encoding='utf-8') as handle:
            letter = json.load(handle)
        text = ' '.join(str(v) for v in letter.values() if isinstance(v, str))
        print(f"{os.path.basename(path):28} {len(text.split()):4d} kelime")

**Ne aranıyor:** `v2-*` satırları **255-290 bandını tutuyor mu**. Tutmuyorsa v2
mektupları kısaltmış olur ve v1 kalır. Tutuyorsa v2'nin eklediği şey bedavaya
gelir: modele muhafızın gerçek eşiklerini (120/400) söylemek.

**Ders:** bir prompt iddiasını fixture'ların *tamamına* karşı ölç, ilk beşine
karşı değil — ve sentetik girdiyle koşulmuş olanı sayma. **İkinci ders, aynı
madenin öteki yüzü:** ölçütü ölçümden önce yaz. Bir taslak v2'yi kabul etmeye
yetmezdi ama reddetmeye yetti, çünkü nereye bakılacağı önceden yazılıydı —
sonradan yazılsaydı 169'a bakıp bir gerekçe uydurmak serbest olurdu.
