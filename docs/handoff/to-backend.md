# → Backend

> **Kanal kuralları**
> - Frontend yazar, backend okur ve `OPEN` → `ACK` taşır.
> - Her madde bir ID taşır (`F-nnn`), numaralar tekrar kullanılmaz.
> - **Dosya 100 satırı geçerse arşivleme gecikmiştir.**
> - Bir spec değişikliği gerekiyorsa burada iste — `spec/`'i frontend reposunda düzenleme,
>   bir sonraki senkronda kaybolur.

---

## OPEN

### F-003 · `PUT /profile` yazma **öncesindeki** `completeness`'i döndürüyor
**Since:** frontend commit `1baa295` · **Spec:** `spec/08-api.md` § 35.6
Yanıttaki `completeness` isteğin uyguladığı değişiklikten önceki durumu taşıyor.
Ölçüldü — aynı `PUT`, tek fark `selfDescription`:

```
                         GET öncesi   PUT dedi   GET sonrası
selfDescription eklendi      80          80          90   ← PUT eski değeri verdi
silindi                      90          90          80   ← yine eski değer
değişim olmayan iki tur      =           =           =    ← uyuşuyor; hatayı gizleyen bu
```

**Bizde görünür:** `CompletenessBar` tam bu sayıyı çiziyor, yani çubuk her baş
düzenlemesinden sonra bir önceki yüzdeyi gösteriyordu.
**İstenen:** yazmadan **sonraki** değer. `PUT` zaten kaydın tamamını dönüyor; tutarsız
tek alan bu.
**Frontend:** geçici olarak baş `PUT` sonrası yeniden okunuyor. Kapandığında kaldırılacak
— kodda ve mock'ta `F-003` diye işaretli, testi var.

### F-004 · `PUT /profile` omitted alanları tekdüze temizlemiyor
**Since:** frontend commit `1baa295` · **Spec:** `spec/08-api.md` § 35.6
Yalnız `headline` + `enabledLanguages` gönderen bir gövdede `contact` `{}`,
`selfDescription` `null` oluyor — ama **`sourceLanguage` olduğu gibi kalıyor**, aynı
istekte. (`preferences` de kalıyor; kendi ucu olduğu için beklenen.)
**İstenen:** ya `sourceLanguage` da temizlensin, ya da hangi alanların temizlendiği
`spec/08-api.md`'ye yazılsın. Şema "replace" diyor, davranış kısmen "merge".
**Bizi kırmıyor:** baş formu dokuz alanı da her seferinde gönderiyor. Kural yazılı
olmadığı için buna güvenmiyoruz.

### F-005 · Entry `PATCH`'te `params.fields` hep `endDate` diyor
**Since:** frontend commit `1baa295` · **Spec:** `spec/08b-api-contract.md`
`F-002`'nin tarih kuralı doğru çalışıyor, ama ihlali hangi alan tetiklerse tetiklesin:

```
PATCH {"endDate":   "1990-01-01"} → 400 fields: ["endDate"]   ← doğru
PATCH {"startDate": "2099-01-01"} → 400 fields: ["endDate"]   ← kullanıcı startDate yazdı
```

**İstenen:** yamanan alan neyse `params.fields` onu adlandırsın.
**Bugün kırmıyor:** entry düzenleme formu yok, create formunda iki alan da ekranda.
Tek alanlı bir `PATCH` yüzeyinde `params.fields`'ı input'a çevirmek yanlış alanı işaretler.

### F-006 · "Birincil sözcükleme silinemez" kuralı `spec/`'te yok
**Since:** frontend commit `1baa295` · **Spec:** `spec/08-api.md` § 35
`DELETE …/variants/{vid}` birincili reddediyor — tek sözcüklemeyken de, ikincisi
varken de; ikisi de `400` + `params.fields: ["variantId"]`. Davranış makul, kayıtlı değil.
**İstenen:** kural `spec/`'e yazılsın. Bir de teyit: bu tek kural mı ("birincil
silinemez"), yoksa "son kalan" + "birincil" iki ayrı kural mı? Ölçüm ikisini de
kapsıyor, ayırt edemiyor.
**Frontend:** sözcükleme silme kontrolü kısmen bu yüzden çizilmedi.

### F-007 · Kota gün dönümü — karar hâlâ bekliyor
**Since:** Adım 1 · karar `STATUS.md` · açık kararlar tablosunda, tek kopya orada.
Aşama 1'in devrettiği dört maddeden kapanmayan tek madde.
**İstenen:** `resetsAt` gönderilmeye başlamadan **önce** karar. Tercihimiz mutlak bir an
(ISO-8601, offset'li): istemci kullanıcının yerelinde biçimlendirir ve soru kapanır.
Gün sınırı sunucu yerelinde kalıp yalnız saat gelirse istemci onu doğru gösteremez.

<!-- Şablon:
### F-001 · Kısa başlık
**Since:** frontend commit <sha> · Adım <n>
**Neden:** <sorunun ne olduğu>
**İstenen:** <backend'den beklenen somut şey>
**Spec:** <ilgili dosya ve bölüm, varsa>
-->

---

## ACK — backend tamamladı, frontend arşivleyebilir

*(boş — `F-001` ve `F-002` `resolved/to-backend-2026-08.md`'de)*
