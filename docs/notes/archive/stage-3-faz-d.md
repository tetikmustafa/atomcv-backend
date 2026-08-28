# Adım 3.8 — Faz D ve cover letter · inşa kaydı

> Kapandı 2026-08-28, beş dilimde. Kalıcı kararlar `spec/`'te: § 21.1'in
> altındaki not, § 21.3.1, § 21.5.1, § 21.6.1, § 21.7.1, § 34.4.1.
> Hâlâ canlı olan uyarılar `notes/current.md`'de duruyor.

## Dilimler

1. **Sözcükleme seçimi + eşikler.** § 21.1'in seçimi ücretlendirmenin önüne
   taşındı: seçim, seçtiği varyantın ölçülmüş maliyetini bütçeye yazıyor, yani
   sonradan başka bir varyantı bastıran bir Faz D sayfaya yüksekliği hiç
   ölçülmemiş bir satır koyardı. `AlternativeWording` seçim paketinde.
2. **Rewrite prompt + doğrulayıcı.** Beş kontrol, en kritiği desteklenmeyen
   iddia. Servis `Result` değil içerik döndürüyor — bu katmanın çağırana
   bildirebileceği bir başarısızlık yok.
3. **Paralel yürütme + boru hattı.** `StructuredTaskScope` kullanılmadı: preview
   API olmasının yanında, `ShutdownOnFailure` bir görev düşünce kardeşlerini
   iptal ediyor ve § 21.6 başarısız yeniden yazımın cevabını zaten veriyor.
   Faz D üretim başına bir kez, derleme döngüsünün her turunda değil.
4. **About sentezi.** Yalnız zaten var olan bir About paragrafı yeniden
   yazılıyor; girdi profil değil **sayfa**. Yeni kontrol: uydurulmuş sayı yok.
5. **Cover letter.** Faz D'de başarısızlığı bildirebilen tek parça — mektubun
   arkasında orijinal yok. `COVER_LETTER_REJECTED` (422).

## Dersler

**Ekilen ihlal hiçbir testi düşürmüyorsa eksik olan testtir.** Beş dilimde on
bir ihlal ekildi, on biri de doğru testi düşürdü. İki tanesi gerçek bir açık
ortaya çıkardı: alias sözlüğünün yalnız sol tarafını okuyan doğrulayıcı
("Kubernetes" hiçbir kontrole takılmıyordu, madde yeniden yazımında da), ve
`usage_counters`'ın cascade'e takılmaması (o, 3.9'da).

**Bir korumanın üç kopyası, kimsenin güncellemediği kopyada bir delik demek.**
`ClaimVocabulary` ve kelime-sınırı kontrolü `shared.text`'e çıkarıldı.
