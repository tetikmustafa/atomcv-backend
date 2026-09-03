# Aşama 3 · dilim 14 — `F-027`, `F-025`, `F-026`

> 2026-09-02 kapandı, 2026-09-03'te arşive alındı: `current.md` sınırındaydı ve
> uca kadar dolmuştu. Kalıcı kararları `spec/`'te; hâlâ canlı olan üç "tamir
> etmeye kalkma" maddesi ile BOM pini `current.md`'de kaldı.

## Aşama 3 · dilim 14 — `F-027`, `F-025`, `F-026` (2026-09-02)

Kalıcı kararlar `spec/05-pipeline-a-c.md` § 18.4.1 (`F-025`) ve
`spec/07-subsystems.md` § 34.4.2 (`F-026`)'de; `F-027`'nin gerekçesi
`SessionCurrentUser.pointsAtALiveAccount`'un javadoc'unda. Burada yalnız
**canlı** olanlar:

**Sapma — `DELETE /account` iki kez basılınca ikincisi `204` değil `401`.**
Uç hâlâ idempotent (§ 57.4); değişen, ikinci basışın uca *ulaşamaması*. Telde
zaten böyleydi — ilk yanıt çerezi siliyor — eski `204`'ü üreten dev stub'ıydı.

**Ekleme — `revokeAllFor` yutmuyor, fırlatıyor.** Redis hatası `warn` + `0`
dönüyordu, ki "hiç oturumu yoktu"dan ayırt edilemez, ve hesap o cevabın
üstüne siliniyordu. Oturumları silinemeyen hesap artık silinmiyor.

**Tamir etmeye kalkma:**
- **Kayıtlı beş `cover_letter` fixture'ının üçü sentetik girdiyle koşulmuş** —
  içlerinde `synthetic-631` geçiyor. Ölçüm diye okunmasınlar; gerçek olan iki
  tanesi `6b34bdf1ae6e` ve `a57ecb1d54d1`.
- **Yazıyla yazılmış sayıyı hiçbir muhafız görmüyor** (§ 34.4.2'nin son
  paragrafı). Ölçülmüş, bilerek kapatılmadı.
- **Prompt'lar `job_analysis` ve `cover_letter` için hâlâ eski cümleyi
  taşıyor.** İkisi de `v2` bekliyor, ve `v2` model seçimini bekliyor.

**Ders — bir sınıf yıktığını geri koymuyorsa, onu ayakta tutan şey sıradır.**
`AccountDeletionIT` acting user'ı siliyor ve suite tek bağlam paylaşıyor;
`SecondImportIT` üç sınıf sonra `409`'unu **silinmiş bir kullanıcıdan**
alıyordu — yani o sınıf tam da `F-027`'nin kusuru sayesinde geçiyormuş, ve
`401` inince ortaya çıktı. `@AfterEach` artık geri koyuyor.

**Geçici — `build.gradle.kts`'te iki BOM geçersizleştirmesi var.**
`postgresql.version` 42.7.12 ve `netty.version` 4.1.136.Final; ikisi de
Trivy'nin `main`'de Deploy'u düşürdüğü HIGH CVE'ler için, ve ikisi de Spring
Boot'un BOM'u yetişince **kaldırılmalı**. Gerekçesini aşan bir pin, kütüphaneyi
sessizce geride tutar — aynı kusurun ters yönü.
