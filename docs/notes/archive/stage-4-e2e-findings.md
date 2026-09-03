# Aşama 4 · uçtan uca ölçüm — dilim kayıtları

> Aşama 3 kapandıktan sonra yapılan ilk gerçek uçtan uca denemede dört bulgu
> çıktı ve dördü de dört ayrı fazda yaşıyordu. Teşhis dört ayrı soruşturmayla,
> tek harita halinde yapıldı; **bulgular kusurlarla bire bir eşleşmedi.**
> `current.md` yalnız canlı olanı ve dilim listesini taşıyor, ayrıntı burada.

## Aşama 4 · uçtan uca ölçüm — dilim A: Faz C (2026-09-03)

Dört bulguluk uçtan uca ölçümün ilki. **Bulgular kusurlarla bire bir eşleşmedi**:
"eksik Tech Stack" render kusuru sanılıyordu, Faz C çıktı.

**Düzeltme — iade edilen bütçe bir daha teklif edilmiyordu.** Düşürülen entry
yerini iade ediyor ama greedy pass bitmişti; `improveBySwapping` de `needed <= 0`
ile **sığan** her adayı atlıyor. Ölçümde 352 pt'nin **133'ü boş**, on atom `BUDGET`
ile reddedilmişti — etiket yazıldığında doğru, koşu bitince yanlış.
`fillUntilStable()` sabit noktaya döndürüyor, ve **`BUDGET` artık gerçekten
doğru**: yeni ret kodu gerekmedi, telde değişiklik yok.

**Düzeltme — boşalan section başlığı iade edilmiyordu.** `remove()` yalnız
`entryFurniturePt`'yi veriyordu; ölçülen koşu **beş** başlık ödeyip **dört** bastı.
`closeSectionIfEmpty` iade ediyor, `downgradeFirstEntryOf` de liste kapanınca
`upgradeFirstEntryOf`'un taşıdığını geri koyuyor.

**Düzeltme — `min_atoms`'u import körlemesine 2 yazıyordu.** `ProfileWriter` hiç
`setMinAtoms` çağırmıyordu; extraction'ın tek madde verdiği her entry (bir dil, bir
derece, bir Tech Stack kategorisi) ulaşamayacağı bir taban taşıyor ve Faz C onu
**bütün** düşürüyordu. Gerçek profilde **11 entry** — kaybolan üç bölümün sebebi
bu; `V5` onarıyor.

**Kıskaç `SelectionPhase`'e konmadı.** Oraya konunca `GoldenSelectionTest` düştü:
fikstür *bilerek* atom sayısından büyük `min_atoms` taşıyor, çünkü "kısa
basmaktansa bütün düşür" **kullanıcının** kararı (`EntryPatchRequest`). Kusur
ulaşılamaz minimum değil, **import'un o kararı kullanıcı adına vermesi**.
Migration'ın körlemesine uygulanabilmesi de buradan: `minAtoms` frontend'de
yalnız mock ve üretilmiş tipte geçiyor, hiçbir bileşende değil.

**Ekleme — `trace.C` bütçesini taşıyor** (§ 14.6 istemiyor). Kısaltılmışı
`"rejected": 13` yazıp ne kadar yerden çevrildiğini yazmıyordu; seçim kusurunu
bütçeden ayırmak `selection_state`'i elle okumak demekti. `rejectionReasons` ve
`pinnedCostPt` zaten § 14.6'da isteniyordu.

**Ders — bir muhafız düşürdüğünde önce neyi koruduğuna bak:**
`GoldenSelectionTest` kıskacın yanlış değil, **yanlış katmanda** olduğunu diyordu.

## Dilim B — çıkarım tripwire'ı (Bulgu 1) · 2026-09-04

**Belirti:** 4 sayfalık CV yüklendi, profil oluşmadı, kullanıcıya
eyleme dönüştürülebilir bir hata gösterilmedi — **ama OpenRouter'da çağrı
faturalandı.**

**Düzeltme — `MAX_ATOM_TEXT` tür başına ayrıldı.** Çağrı başarılıydı
(`outcome=success`, 10617 çıkış jetonu, kesilme yok) ve koşu `local-record`
olduğu için cevap diskteydi: `v1-d506ef8f97bb.json`, **84 atom, 6 section,
`skills` dahil**. `StructuringAudit` onu **yedi karakterle** reddetti — en uzun
`textSource` 607, sınır 600 — ve `ProfileStructuring.gate` denetimi *ilk*
çağırdığı için 84 atomun 84'ü gitti.

Kusur sınırın kendisinde değil, **gerekçesinin taşınmasındaydı**: javadoc 600'ü
"bir insanın **tek bir maddeye** yazacağının ötesi" diye savunuyor, ama aynı
sayı `about_paragraph`'a da uygulanıyordu. Kayıtlı üç extraction fikstürünün
hepsi ölçüldü: **about dışındaki her tür en fazla 219 karakter** (600 onlar için
üç kat paylı ve doğru), about ise 607/541/519/506 — yani sistematik bir
kıl payı kaçırma, tesadüf değil. About tavanı 1500 (~250 kelime, ~20 satır);
tripwire'ın kendi ölçütü — "hiçbir CV alanının olamayacağı bir değer" —
paragraf şeklindeki bir alana uygulanmış hali.

**Ölçüm testi olarak yazıldı:** `RecordedExtractionAuditTest` reddedilen
**gerçek fikstürü** diske karşı okuyor, 84 atomu doğruluyor ve denetimden
geçmesini bekliyor. Düzeltme olmadan düşüyor.

**§ 43.2 zayıflatılmadı, ve bilerek.** `EXTRACTION_EMPTY`'nin belirsizliği
saldırgana "fark edildin" dememek için; meşru vakanın artık hiç tetiklenmemesi
doğru çözüm, mesajı yumuşatmak değil.

**SSE boşluğu yok — `B-nnn` açılmadı.** İki taraf da doğrulandı: `SseRegistry`
`"failed"` yayınlıyor (`terminalEvent`, geç abone olana da), `useJob.ts`
dinliyor, `ImportScreen` onu kullanıyor. Olay gitti; yanlış olan taşıdığı
cümleydi, ve o cümlenin sebebi yukarıdaki yanlış pozitifti.

