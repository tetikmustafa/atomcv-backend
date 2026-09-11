# Arşiv — Aşama 4 · sayfa garantisi ve bütçeler

> Kapandı 2026-09-11: beş kusurun beşi de düzeltildi, classic/compact/modern
> yedi golden profilde de %3 içinde, `B-095` `resolved/`'a indi. Hâlâ geçerli
> olan dersler `current.md`'nin "hâlâ canlı" bölümünde; buradaki tam metin
> onların gerekçesidir.

---

## Aşama 4 · bütçeler ve golden set (2026-09-10/11)

**Düzeltme — golden set üç şablona genişletilince sayfa garantisi compact'te
tutmuyor çıktı** (`B-095`): az tahmin, yani taşma yönü, ve bir profil taştı.
Beş sebep, **beşi de aynı cümle — sayfanın dizdiği ama ölçümün hiç görmediği
bir şey** — ve beşi de yalnız gerçek derleyiciye sorunca göründü:

1. **Listeden sonraki bölüm başlığı compact'te 10pt pahalı** (`nosep` üstte
   boşluk bırakmıyor); kalibrasyon yalnız *ilk* başlığın konumunu ölçüyordu,
   diğerinin probu belgede duruyordu ama okunmuyordu. Pahalı sayı her başlığa
   yazılıp okuma sırasındaki ilkine iade ediliyor (`retuneFirstSectionHeader`)
   — "pahalıyı her yere yaz" ilk denemem bölüm başına 10pt israftı, ölçtüm.
2. **Başlık bloğu artık ölçülen sayı** (V13, `profiles.header_costs`): metin
   sarmalıyor, compact başlığı 65.2pt iken sabit 48.99'du. Geometri **ve dil**
   ile anahtarlanıyor, metni değişince siliniyor.
3. **`\resumeItem`, ifadeyle ardındaki negatif `\vspace` arasına bir kelime
   arası boşluk koyuyordu** (compact'te yok). Ölçüm kutusu o boşluksuz diziyor,
   yani genişliği satıra bir boşluk kadar yakın madde kutuda sığıp sayfada
   sarmalıyordu; stress_long_career'ın altmış maddesi o bantta ve modern'deki
   ikinci sayfanın tamamı buydu. Maliyetler aynı çıktı — değişen kutu değil sayfa.
4. **Compact aynı boşluğu iki kez yazıyordu:** `SECTION_LIST_CLOSE` ile
   `SECTION_HEADER_AFTER_LIST` classic'te iki ayrı şey, compact'te aynı 10 puan.
   Kalibrasyon artık primi düşerek saklıyor.
5. **Aynı makro çok satırlı yazılmıştı, ve makro gövdesindeki her satır sonu
   bir boşluktur** — yani (3) kaldırıldıktan sonra ikinci bir boşluk kalmıştı.
   Bu ikisi ancak listenin **son** maddesini vuruyor: aynı ifade 1. ve 2.
   konumda kısa olanla aynı, sonuncuda bir satır fazla, ve arkasına herhangi
   bir madde koyunca primin tamamı geri geliyor. Tek maddelik bir listenin tek
   maddesi aynı zamanda sonuncusu olduğu için uzun süre "ilk madde" sandım;
   `EntryFurnitureIT`'in kural probu ("iki konum eşit genişlikte") baştan beri
   haklıymış, **soru yanlıştı.**

**Durum: üçü de doğrulandı** — classic, compact ve modern, yedi profilin
hepsinde %3 içinde (`TEMPLATES_WITH_A_CONFIRMED_PAGE_PROMISE`, `ids()` değil;
ikinci bir test listede eksik olmadığını iddia ediyor). `B-095` kapandı.
Sürümler: `classic:v6`, `compact:v2`, `modern:v3`.

**Ders — heredoc'un yediği yarım ters bölü bir kontrol karakteridir.**
CLAUDE.md heredoc'ların ters bölüyü yarıya indirdiğini söylüyor; söylemediği,
geriye kalanın BEL/VT/CR olduğu — `\resumeItem` yorumda `^M` + `esumeItem`,
`\vspace` `^K` + `space` oluyor. Yorum olduğu için derleyici susuyor, diff'te ve
terminalde görünmüyor. Dört kaynak dosyada ve iki arşiv notunda bulundu. İki
tarama yakalıyor: kaynakta herhangi bir kontrol karakteri, ve blok yorum içinde
yıldızla başlamayan satır (CR zaten satır sonuna dönüştüyse ilki kaçırır).

**Ders — ölçüm belgesiyle sayfa arasındaki her fark bir hatadır** (§ 22.4'ün
üçüncü kuralı üç ayrı ayrıntıda kırılmıştı). Sapma testi yetmiyor: bir maddedeki
bir satır yedi yüz puanın içinde kaybolur — `WordingCostIT` bir ifadenin,
`EntryFurnitureIT` bir girdinin marjinal bedelini ölçüyor. Ve `\pagetotal`
sayfa kırıldıktan sonra okunmaz (730.6 beklenirken 39.8): sayı küçük değil
**anlamsız**, artık drift probunda da reddediliyor.

**Ders — görev girdisi olmayan bir bütçe dosyası kimseyi bağlamaz.** Oranı 1.0'a
çekince test düşmedi; Gradle dosyayı göremediği için `:test UP-TO-DATE` deyip
koşturmadı. `inputs.file` ile kapatıldı, § 52.2'nin sorgu tavanı da dosyadan
okunuyor. Ölçekleme oranı medyan değil **en hızlı** koşuyu alıyor — medyan
gürültüyü oranın içine iki kez taşıyordu.

---

