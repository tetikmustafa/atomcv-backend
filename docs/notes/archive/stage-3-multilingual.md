# Arşiv — Adım 3.5, çok dillilik

> Kapanan adımın inşa kaydı. **Kalıcı kararlar `spec/07-subsystems.md`
> § 32.2.1 ve § 32.3.1'de.** `current.md` sınırını aştığı için taşındı
> (2026-08-27).

---

## Adım 3.5 — çok dillilik · 2/2 · kapandı

On iki karar § 32.2.1 ve § 32.3.1'e işlendi.

**Bulgu — § 32.3'ün sıralaması zaten doğruydu, eksik olan iddiaydı.** Seçim
hedef dilin varyantından maliyet okuyor ve bunu hiçbir test tutmuyordu; var
olan test yalnız *hangi varyantın seçildiğini* kontrol ediyordu. **Ders
tekrarı: doğru davranan kod, korunan kod değildir.**

**"Yeniden üret" ucu açıldı:** `PATCH` gövdesinde açık `userEdited: false`.
`true` reddediliyor — `B-052` güncellenmeli, düğme artık bağlanabilir.

**Canlı — hedef dil sistem yarısında.** Ön ek dile göre değişiyor; bu bilinçli
(§ 32.2.1). **Yeni bir dil eklemek yeni bir cache ön eki demek**, çağrı başına
bir tane değil.

**Canlı — `RunMarking` artık `profile.domain.content`'te** ve iki aşama
kullanıyor. Faz D üçüncüsü olacak.
