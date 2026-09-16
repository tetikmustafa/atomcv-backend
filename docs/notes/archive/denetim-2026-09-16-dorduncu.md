# Denetim — dördüncü tur, mekanik (2026-09-16)

> Kapanmış kayıt. Üç bulgusu da indi ve PR #201'de merge edildi; kalıcı
> kararlar `spec/`'te (§ 13.2, § 16.1, § 18.4, § 08b, § 33.5, § 54.2).
> `current.md` 200 satır sınırına dayandığında buraya taşındı (Faz D ölçümünün
> kaydı için). İlk üç tur `denetim-2026-09-15.md`, `denetim-2026-09-16.md` ve
> `denetim-2026-09-16-ucuncu.md`'de.

Üçüncü turun kaydı `archive/denetim-2026-09-16-ucuncu.md`'de; ilk ikisi
`denetim-2026-09-15.md` ile `denetim-2026-09-16.md`'de. **Bu tur düzyazı
okumadı** — üç kez okunmuş bir metni dördüncü kez okumak aynı gözle bakmaktı.
Yerine spec'ten çıkarılabilen her somut ad çıkarıldı ve repoda arandı: 1659 kod
parçacığı (tip, sabit, property, uç, tablo), 61 ucun tamamı, 35 enum'un kapalı
sözlükleri, 14 migration'a karşı veri modeli, 91 sayısal sabit, spec'in kendi
`§` atıfları. **Üç gerçek boşluk, ikisi kümelerde — bir adın repoda geçmesi
kümenin tam olduğunu göstermiyor.**

**Sapma — `§ 13` "Tam Veritabanı Şeması" on üç migration geriden geliyordu.**
Bloğu `V1__initial_schema.sql` başlığını taşıyor ve V2-V14 hiçbir yere
işlenmemişti: `profiles.user_id` nullable (anonim profil), `expires_at` ve XOR
kısıtı, `header_costs`, `rewritten_content`, `users`'ın iki kolonu,
`sections.layout`'un beşinci değeri, V3'ün anonim idempotency indeksi, V4'ün
unique indeksi, ve **`template_capacities` — bütün `docs/` ağacında hiç
geçmiyordu.** `§ 16.1`'in ağacı da üç dosya sayıyordu, ikisinin adı uydurma.

> **Blok düzenlenmedi, delta yazıldı** (`§ 13.2`). Birebir kopyası olduğu dosya
> duruyorken bloğu güncellemek onu ne V1 ne bugün yapardı — iki sürümün
> ortasında, hangisini anlattığı belirsiz bir metin kalırdı.
>
> **Ve kural bir teste bağlandı**, çünkü bu turun bulduğu şey tam olarak
> "düzyazıdaki bir kuralı kontrol eden bir şey yoktu"ydu:
> `SchemaDocumentationTest` her migration adının bölümde geçmesini **ve**
> bölümün olmayan bir dosya adı uydurmamasını zorluyor. Alıntı blokları muaf —
> bir düzeltme, düzelttiği adı yazabilmeli, yoksa bulunabilir olmaktan çıkar.

**Düzeltme — `no_responsibilities` altı yerde yayımlanıyordu, tel üretemiyor.**
`PlausibilityGate` dördüncü verdict'i Aşama 3'te düşürmüştü (nitelik sayıp
görev saymayan ilan olağan şekildir; düşüren ilan 0.92 güvenle, yirmi beceri
okunmuşken reddedilmişti) ve gerekçe javadoc'ta duruyordu. Spec sözlüğü
**sekiz** saymaya devam etti: § 18.1'in sayım cümlesi, § 18.4'ün kod
parçacığı, iki tablo, § 08b ve EK D.

> **`B-nnn` açılmadı ve sebebi kayda değer:** frontend dalı `B-072`'nin
> cevabında zaten silmiş, neden listeleri orada yediye inmişti. **Geride kalan
> tek kopya spec'ti** — yani bu bir frontend aksiyonu değil, kendi
> gecikmemizdi. Açık `B-111`'in yanlış sayımı düzeltildi, çünkü çeviri
> dosyaları ona bakılarak yazılacak.

**Ekleme — `FakeLatexCompiler` yazıldı; § 54.2 onu bir aşamadır listeliyordu.**
Tek yol `LatexCompilerClient`'tı ve `make dev` latex konteynerini kaldırmıyor,
yani sahte LLM'le çalışan bir klon Faz E'de dinleyen kimsenin olmadığı bir
adrese gidiyordu. Sayfa sayısı sabit 1 (hareket eden bir sayı, kimsenin
ölçmediği ikinci bir sayfa modeli olurdu); ölçüm probları kutudaki
karakterlerden aritmetikle cevaplanıyor; **kalibrasyon bilerek cevapsız**,
çünkü uydurulmuş bir kapasite `template_capacities`'e yazılır ve onu uyduran
oturumdan sonra da yaşardı.

> **İlk hâli `latexTest` hattını kırdı, ve bunu yalnız hattı koşturmak
> gösterdi.** `@Profile("!local-fake")` idi; dört IT o profil altında koşuyor
> çünkü **sahte modeli** istiyor ve **gerçek derleyiciyi** ölçüyor. "Gerçek bir
> tek sayfalık PDF iki bin bayttan büyüktür" diyen beş iddia 669 baytlık yer
> tutucuyla karşılaştı. Ayrım `atomcv.latex.fake` anahtarına taşındı;
> `AbstractLatexTest` tek yerden kapatıyor, ki beşinci bir sınıf onsuz
> yazılamasın.
