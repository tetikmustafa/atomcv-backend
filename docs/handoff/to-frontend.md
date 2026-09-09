# → Frontend

> **Kanal kuralları**
>
> - Backend yazar, frontend okur ve `OPEN` → `ACK` taşır.
> - Her madde bir ID taşır (`B-nnn`), numaralar tekrar kullanılmaz.
> - **Dosya 100 satırı geçerse arşivleme gecikmiştir.** `ACK` maddeleri `resolved/`'a taşınır.
> - API _şekli_ için otorite OpenAPI şemasıdır. Burası **neden değişti + ne yapman lazım** taşır.
> - Kalıcı kural niteliğindeki maddeler `spec/`'e işlenir ve buradan silinir.

---

## OPEN

*(açık madde yok — `B-075`…`B-084`'ün dokuzu da karşılandı ve `ACK`'e indi
2026-09-09'da. Dokuzunun kaydı aşağıda; kod tarafındaki gerekçeler
`docs/notes/current.md`'de.)*

---

## ACK — frontend tamamladı, backend arşivleyebilir

### `B-075`…`B-084` — dokuzu birden (2026-09-09)

- **`B-075` + `B-078` birlikte, ve net etki sıfır kod.** `grant.accessedAt`
  dalı hiç kaldırılmamıştı — `B-075` geldiğinde `Feedback.tsx` zaten "okundu /
  henüz okunmadı" cümlesini kuruyordu ve `B-078` onu geri istedi. Alan
  `api.d.ts`'te duruyor (o dosya `B-075` sonrası yeniden üretilmedi), yani
  cümle bugün de doğru: null artık "yazacak bir şey yok" değil, "kimse
  bakmadı".
- **`B-076` yazıldı.** Gizlilik metninin alt işleyen bölümü artık
  OpenRouter'ı broker olarak, OpenAI · Microsoft Azure (küresel/ABD/AB) ·
  Amazon Bedrock (us-east-1) · Google'ı yukarı akış olarak adlandırıyor; ne
  gittiğini sayıyor ("kişisel veri" diyerek), "eğitebilecek sağlayıcıya
  yönlendirilmiyor" ifadesini kullanıyor ve modeli **şu an kullanılan model**
  olarak yazıyor (`openai/gpt-5.6-sol`). Model değişince sayfa gözden
  geçirilecek. `ProcessorAudit` satırına karşı kontrol **yapılmadı** — backend
  ayakta değildi; dağıtım gelince ilk iş.
- **`B-077` çalışıyordu, bir yanlış anlaşılma kaldı.** `usePatchAtom` yanıtı
  zaten iki önbelleğe de yazıyor ve `TagInput` tamamen `values`'tan çiziliyor,
  yani kanonik biçim ekrana kendiliğinden geliyor. Eklenen: mock artık
  gerçekten kanonikleştiriyor (küçük harf + tire, `postgresql`→`postgres`,
  tekrar edenleri düşürüyor — yani liste **kısalabiliyor**), ve alanın ipucu
  bunu önden söylüyor, hata gibi göstermeden.
- **`B-079` + `B-082` birlikte.** Kota hiç 0 görmedi: mock'ta 5'ti, 5 kaldı,
  o yüzden "0 gören sürüm" hiç dağıtılmadı. Anonim üretim yolu açık;
  `ANONYMOUS_SESSION_EXPIRED` **iş sonucu olarak** da ele alınıyor — akıştan
  gelen refüz aynı panele düşüyor ve `sign_up` gerçekten `/login`'e götürüyor
  (birim testi var). `feedback` anonimde null: sonuç ekranı o bölümü hiç
  çizmiyor.
- **`B-080` yazıldı**, parantez içi dahil: *"anonim mod son etkinlikten 2 saat
  sonra (veritabanından beş dakika içinde silinir; şifreli yedeklerde en fazla
  altı aya kadar kalabilir)"*. Ekranda ayrıca uyarı yok, dediğiniz gibi.
- **`B-081` — üçünden ikisi kod işi çıktı.** (1) Atom kontrolleri zaten
  `canEditAtomControls` ile gizliydi. (2) **"Alternatif ekle" düğmesi henüz
  hiç yok** — `POST /atoms/{id}/variants` istemcide tanımlı, hiçbir ekran onu
  çağırmıyor; düğme çizildiği gün `canAddAlternatives` ile kapanacak, ve
  mevcut yazımı düzenlemek zaten açık. (3) `params.feature` artık cümleyi
  seçiyor: üç jetonun üçü de kendi cümlesini alıyor, jeton ekrana hiç
  çıkmıyor. Ayrıca **sessiz bir kusur düzeldi**: profil editöründeki paneller
  sunucunun `sign_up` düğmesini çiziyor ama basılınca hiçbir şey yapmıyordu.
- **`B-083` bağlandı.** Turnstile widget'ı anonim çağıranda hem CV yükleme
  hem üret ekranında; token gövdede (`challengeToken`) ve multipart'ta form
  alanı olarak. **Token yoksa alan hiç gönderilmiyor** — boş göndermek
  başarısızlık sayıldığı için. Her denemeden sonra widget sıfırlanıyor
  (yalnız refüzde değil: kabul edilen istek de token'ı harcıyor), ve iş
  ekranında da duruyor, çünkü başarısız bir işten çıkan her yol yeni bir
  istek. Hesaplı çağıranda hiç çizilmiyor. **Gerçek uca karşı denenmedi** —
  sırrı olan bir dağıtım gerekiyor.
- **`B-084` fırsatı alındı.** `upgraded` artık geçmişe düşürüyor: sihirli
  bağlantıda doğrudan, OAuth dönüşünde yalnız `next` yoksa (bir `next`
  kullanıcının gönderildiği ekran, ve o kazanıyor). `kept_existing` ve
  `unavailable` eskisi gibi. Cümle de düzeldi: profil **ve ondan üretilen
  CV'ler** deniyor.

**Üç soru `to-backend.md`'ye gitti:** `F-028` (blokta ön yazı alanı yok),
`F-029` (`challengeToken` şemada yok + uç adı tekil mi), `F-030` (`422`'nin
`resolutions`'ı ve anonim `feedback` yazısının reddi — ikisini de tahmin ettik).

_(`B-071`…`B-074`'ün dördü de karşılandı ve `resolved/to-frontend-2026-09.md`'ye
indi 2026-09-09'da, dosya sınırı; `B-037`…`B-070` bir öncekinde. Aşağıdakiler
**hâlâ canlı olan** kayıtlar.)_

**Üç yerde bir doğrulama eksik ve söylenmesi gerekiyor:** ne OAuth sıçraması
(`B-048`), ne sihirli bağlantının Turnstile'ı (`B-050`), ne de `B-083`'ün
üretim/içe aktarım challenge'ı gerçek uca karşı denendi — üçü de kendi
anahtarları yapılandırılmış bir dağıtım istiyor. Bugün doğrulanan şey
mock'a karşı.

**EK C.1'in sağlayıcı listesi yazıldı** (`B-076`, yukarıda). Yayın öncesi
kontrol listesinde kalan tek şey, yayımlanan sayfayı `ProcessorAudit`'in
açılış satırına karşı okumak — dağıtım işi, kod işi değil.

---

## Kalıcı kurallar

Eski maddelerin `spec/`'e işlendiği yerlerin tablosu
`resolved/to-frontend-2026-08.md`'ye taşındı (2026-08-24) — dosya sınırı.
