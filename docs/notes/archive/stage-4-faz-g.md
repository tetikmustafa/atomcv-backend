# Arşiv — Aşama 4 · Faz G (düzenleme döngüsü)

> Kapandı 2026-09-11: dört dilim de indi ve prompt'un fixture'ı `local-record`
> turuyla alındı. Hâlâ uyulması gereken kararlar `current.md`'nin "hâlâ canlı"
> bölümüne taşındı; buradaki tam metin gerekçeleridir.

---

## Aşama 4 · Faz G — düzenleme döngüsü (2026-09-10)

Dört dilim, dördü de indi: yönlendirmeler (`313c3ee`), `rewritten_content`
kolonu (`1a8cca2`), manuel toggle (`9758764`), doğal dil (`d12dc07`), ve
gerçek derleyiciye karşı uçtan uca test (`b729525`).

**Sapma — § 24.2'nin değişiklik seti atom id'si taşımıyor.** Spec modele
`atomId` yazdırıyor; biz satırları **numaralandırıp indeks** istiyoruz
(`NumberedLines`). Bir UUID modelin uydurabileceği ya da yanlış kopyalayabileceği
tam o token, ve uydurulmuş bir UUID aranana kadar gerçeğinden ayırt edilemez.
İndeks aralık dışıysa bariz. Kalıcı, § 24.2'ye işlenmeli.

**Ekleme — yarım cümle hiç uygulanmıyor.** Biri aralıkta biri dışında iki sayı
döndüren model cümlenin yarısını anlamış; doğru anladığı yarıyı uygulamak kişiye
istediğinden başka bir şey göstermek olur. `understood: false` de bir cevap,
arıza değil — ve **sık dönecek**.

**Ekleme — `GENERATION_SUPERSEDED` (409) ve `EDIT_NOT_UNDERSTOOD` (422).**
İkisi de § D.6.1 tablosunda. 409, zaten değiştirilmiş bir üretimi düzenlemeyi
reddediyor: uygulamak soyağacını çatallardı — bir ebeveynin iki bitmiş çocuğu
ve hangisinin "güncel" olduğunu söyleyen hiçbir veri yok.

**Sapma — geçmiş `total`'ı artık satır değil CV sayıyor.** Liste `superseded`
gizliyor, sayı da gizlemek zorunda (yoksa "23 üretim" yazıp on bir satır
gösterir). Ama o sayıyı **hesap silme ekranı** okuyor (`F-020`). Silme yine
emekli taslakları götürüyor; metin "N CV" derse doğru.

**Ekleme — yeniden koşu Faz C'den, skorlar snapshot'tan.** § 24.1 "Faz C'den
itibaren" diyor, dolayısıyla Faz B koşmuyor ve skorlar `selection_state`'ten
okunuyor (`StoredSelection.scoresByCandidate`). **Sonucu:** yeniden koşu,
üretimden sonra profilde yapılanları görmez — yeni yazılmış bir atom skorsuzdur,
ama adıyla istenebilir (yönlendirme skoru yener).

**Ders — koşullu bir döngü içindeki iddia test değildir.** Devralmayı ölçen ilk
latexTest vakası `if (before.contains(...))` içinde iddia ediyordu ve **geçti**;
iddiayı önden isimlendirince düştü. Sebep kod değildi: bu lane'in sahte
sağlayıcısı `bullet_rewrite`'a şemadan üretilmiş bir cümle veriyor, doğrulayıcı
reddediyor, `rewritten_content` boş kalıyor — yani V11'in var olma sebebi olan
devralma **birim testlerinden başka hiçbir yerde koşmamıştı** (§ 51.7).
Vaka artık yazımı ebeveyn satıra ekip basılan belgede arıyor.

**Düzeltme — `jsonb` nesne anahtar sırasını korumaz** (uzunluk+bayt sırasına
diziyor). `RewrittenContent`'in ilk javadoc'u "iki koşu JSONB'ye iki farklı sıra
yazar" diyordu; yanlıştı, kolon zaten normalize ediyor. `Map.copyOf` →
`LinkedHashMap` değişikliği duruyor ama gerekçesi **bellekte** haritayı gezen şey
(trace, log, assertion), kolon değil. CLAUDE.md'nin kuralı `json` kolonları,
cevaplar ve assertion'lar için geçerli; `jsonb` **dizileri** sırayı korur.

---

