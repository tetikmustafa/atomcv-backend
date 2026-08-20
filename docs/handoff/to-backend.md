# → Backend

> **Kanal kuralları**
> - Frontend yazar, backend okur ve `OPEN` → `ACK` taşır.
> - Her madde bir ID taşır (`F-nnn`), numaralar tekrar kullanılmaz.
> - **Dosya 100 satırı geçerse arşivleme gecikmiştir.**
> - Bir spec değişikliği gerekiyorsa burada iste — `spec/`'i frontend reposunda düzenleme,
>   bir sonraki senkronda kaybolur.

---

## OPEN

*(şu an açık madde yok)*

<!-- Şablon:
### F-001 · Kısa başlık
**Since:** frontend commit <sha> · Adım <n>
**Neden:** <sorunun ne olduğu>
**İstenen:** <backend'den beklenen somut şey>
**Spec:** <ilgili dosya ve bölüm, varsa>
-->

---

## ACK — backend tamamladı, frontend arşivleyebilir

### F-002 · Ters tarih aralığı — kapandı
`endDate >= startDate` artık `EntryService`'te, hem `POST /profile/entries` hem
`PATCH /profile/entries/{id}` için. İhlal **400 `VALIDATION_FAILED`** +
`params.fields: ["endDate"]` — istediğiniz şekil.

`PATCH` karşılaştırmayı **yamanın sonucu** üzerinde yapıyor, gövdesi üzerinde değil:
`{"endDate": "2020-01-01"}` saklı `startDate`'e karşı, `{"startDate": "2026-01-01"}`
saklı `endDate`'e karşı ölçülüyor. İkisi de reddediliyor. `{"endDate": null}` hâlâ
geçiyor — "sürüyor"da karşılaştıracak ikinci tarih yok — ve eşit tarihler geçerli
(tek günlük sertifika/hackathon gerçek bir entry, `>` değil `>=`).

Kural `spec/08-api.md` § 35.2'ye yazıldı, yani sözleşmenin parçası.
**Sizde bir şey değişmiyor:** istemci kontrolünüz kalsın, artık tek savunma değil.
Üç entegrasyon testi: yaratmada ters aralık, yamayla iki uçtan ters çevirme, aynı gün.

### F-001 · Demote'ta sürüm artışı — kapandı, davranış değişti
Sürüm artıyor tarafını seçtik. Diğer seçenek `spec`'e "iyimser kilit bu satırda
çalışmıyor" istisnası yazmaktı; tarif ettiğiniz kayıp-yazma penceresi gerçekti.

Kök neden bulduğunuz yerdeydi: demote tek satırlık bir toplu JPQL `update` ve toplu
update `@Version`'ın yanından geçiyor. `update versioned` oldu, ayrıca yalnız
**gerçekten birincil olan satırı** hedefliyor.

```
başlangıç   tr primary=true  v=0   |  en primary=false v=2
{"primary":true} → en          tr primary=false v=1   |  en primary=true  v=3
```

**Aksiyonunuz var — `B-034` olarak açıldı.** Kendi cümlenizle: yerel demote'un
sürümü de artmalı, yoksa `usePatchVariant` sunucuyla artık hizalı değil.

Atomun promote'a karışmayan diğer sözcüklemeleri **sürümlenmiyor**; etag'leri
geçerli kalıyor. Bunu ayrıca test ettik, çünkü "hepsini artır" düzeltmesi promote'u
tamamen kırıyor. Kural `spec/08-api.md` § 35.6'da.
