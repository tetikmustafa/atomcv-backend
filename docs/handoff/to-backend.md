# → Backend

> **Kanal kuralları**
> - Frontend yazar, backend okur ve `OPEN` → `ACK` taşır.
> - Her madde bir ID taşır (`F-nnn`), numaralar tekrar kullanılmaz.
> - **Dosya 100 satırı geçerse arşivleme gecikmiştir.**
> - Bir spec değişikliği gerekiyorsa burada iste — `spec/`'i frontend reposunda düzenleme,
>   bir sonraki senkronda kaybolur.

---

## OPEN

*(şu an açık madde yok — `F-003`…`F-007` kapandı)*

<!-- Şablon:
### F-001 · Kısa başlık
**Since:** frontend commit <sha> · Adım <n>
**Neden:** <sorunun ne olduğu>
**İstenen:** <backend'den beklenen somut şey>
**Spec:** <ilgili dosya ve bölüm, varsa>
-->

---

## ACK — backend tamamladı, frontend arşivleyebilir

### F-003 · Yazma yanıtındaki `completeness` — kapandı
Ölçümünüz birebir doğruydu ve sebebi tam olarak tarif ettiğiniz yerdeydi:
`ProfileService.replace()` rakamı hiç hesaplamıyordu, yalnız `readOwn()`
hesaplıyor. `PUT /profile` ve `PUT /profile/preferences` artık kaydetmeden
önce yeniden hesaplıyor, yani yanıt **yazmadan sonrasını** taşıyor.

Kural `spec/08-api.md` § 35.6'da: **`completeness` taşıyan bir yanıt güncel bir
değer taşır** — kolonun her yazmadan sonra güncel olduğu değil; bölüm/entry/atom
uçları başı döndürmüyor ve rakamı bir sonraki okumaya bırakıyor.
**Aksiyonunuz:** `PUT` sonrası yeniden okuma kaldırılabilir.

Bir not, çünkü sizde de aynı şekilde saklanır: "değişim olmayan iki tur uyuşuyor"
dediğiniz maskeleme testte de çıktı. Tercihleri ölçen testimiz düzeltmesiz de
geçti — etag'i almak için yaptığı `GET` saklı rakamı tazeliyor, yani iki yazma
arasındaki her okuma bayatlığı onarıyor. ETag'i önceki yazmanın **yanıtından**
alınca düştü.

### F-004 · Omitted alanların tekdüze temizlenmesi — kapandı, davranış değişti
İki seçeneğinizden "temizlensin" tarafını seçtik ama uygulaması farklı oldu:
`source_language` kolonu `NOT NULL DEFAULT 'en'`, yani temizlenecek bir değer
yok ve `DEFAULT`'a düşürmek Türkçe yazılmış bir profili herhangi bir baş
düzenlemesinde sessizce İngilizceye çevirirdi. Alan **gövdede zorunlu** oldu.
Artık başın hiçbir alanı merge edilmiyor; `preferences` haklı olarak
beklediğiniz gibi kendi ucunda kalıyor.

**Aksiyonunuz var — `B-035`.** Şema değişti, `gen:api` sonrası tip de.

### F-005 · Entry `PATCH`'te `params.fields` — kapandı
Kural artık isteğin gönderdiği ucu adlandırıyor; tam tablo `spec/08-api.md`
§ 35.2'de. Kontrol yine **yamanın sonucu** üzerinde, çünkü aralığı bozan tek
uç da olabilir — değişen yalnız hangi alanın raporlandığı.

Yanına, sormadığınız ama sizi ilgilendiren bir davranış: hiçbir tarihe
dokunmayan bir `PATCH` artık hiç denetlenmiyor. Aksi hâlde F-002'den önce
ters kaydedilmiş bir satır, ilgisiz bir başlık düzenlemesini düzeltilecek
alanı adlandıramadan reddederdi.

**Aksiyonunuz var — `B-036`**, create'in iki alan birden döndürmesiyle birlikte.

### F-006 · Birincil sözcükleme kuralı — kapandı, ve ölçümünüz eksikti
Kural `spec/08-api.md` § 35.2'ye yazıldı. Sorduğunuz ayrımın cevabı: **iki ayrı
kural**, ve ikisi zaten farklı `params.fields` döndürüyor.

```
son sözcükleme            400 fields: ["variantId"]   → atomu sil
birincil, başkası var     400 fields: ["primary"]     → önce başkasını birincil yap
```

İkincisini `variantId` ölçmüşsünüz; gerçek uçta `primary` dönüyor ve bunun
Aşama 1'den beri entegrasyon testi var. Muhtemelen mock'unuzdan ölçüldü.
Bizim tarafta eksik olan şuydu: **birinci durumun testi yalnız 400'ü kontrol
ediyordu**, yani ayrımın kendisi test edilmemişti — artık ikisi de sabit.
Sözcükleme silme kontrolünü çizerken ayırmanız gereken şey tam olarak bu.

### F-007 · Kota gün dönümü — karar verildi
**Gün sınırı UTC**; Türkiye'de sayaç 03:00'te döner. `usage_counters.period`
zaten saat dilimsiz bir `DATE` ve UTC onu tek anlamlı kılan okuma: sunucunun
dilimi değişse de aynı satır aynı günü gösterir, yaz saati sınırı yok. Gömülü
bir `Europe/Istanbul` o dilimin dışına ilk çıkan kullanıcıda sessizce yanlış
olurdu; istemcinin bildirdiği dilim ise kota kaçırmak için ayarlanabilirdi.

**Tercihiniz kabul:** `resetsAt` telde her zaman offset taşıyan bir ISO-8601
**anı** (`2026-08-22T00:00:00Z`), yalnız saat değil — `capabilities.quotaResetsAt`
ve `QUOTA_EXCEEDED` / `PROFILE_QUOTA_EXCEEDED` `params`'ı için de aynı. Metni
kullanıcının yerelinde yazacak taraf sizsiniz; `Retry-After` yanında saniye
cinsinden kalıyor, istemci saati yanlışsa doğru olan tek değer o.

Karar `spec/08b-api-contract.md` EK D.6.5'te, `period` kolonunun yorumu
`spec/04-data-model.md`'de; `STATUS.md`'nin açık kararlar tablosundan düştü.
**Henüz kod yok** — `resetsAt` gönderen uç Aşama 2, Adım 2.7 ile geliyor.

