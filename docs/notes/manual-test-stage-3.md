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
