# → Frontend

> **Kanal kuralları**
>
> - Backend yazar, frontend okur ve `OPEN` → `ACK` taşır.
> - Her madde bir ID taşır (`B-nnn`), numaralar tekrar kullanılmaz.
> - **Dosya 100 satırı geçerse arşivleme gecikmiştir.** `ACK` maddeleri `resolved/`'a taşınır.
> - API _şekli_ için otorite OpenAPI şemasıdır. Burası **neden değişti + ne yapman lazım** taşır.
> - Kalıcı kural niteliğindeki maddeler `spec/`'e işlenir ve buradan silinir.

---

## OPEN

### B-076 · EK C.1'in AI sağlayıcı listesi — yayımlanacak gerçekler

**Since:** commit `<bu PR>` · **Spec:** EK C.1 (*"AI sağlayıcı listesi güncel ve
açık"*) · `llm/telemetry/ProcessorAudit`

**Neden:** madde model seçimini bekliyordu; model belli, liste yazılabilir.
Aşağıdakiler ölçüldü — sağlayıcının endpoint API'sinden ve gönderdiğimiz gövdeden.

**Kimler.** Biz → **OpenRouter** (broker) → yukarı akış. Bu modelin **yedi
endpoint'i** var: **OpenAI** (üç varyant), **Microsoft Azure** (`azure`,
`azure/us`, `azure/eu`), **Amazon Bedrock** (`us-east-1`) — istek yalnız modeli
adlandırdığı için dördü de listede olmalı, hangisinin karşıladığı yanıtta yazmaz.
Zincirin ikinci halkası **Gemini**, yani anahtarı olan dağıtımda **Google** da
listede.

**Ne gidiyor.** CV metni (`profile_extraction`), ilan metni (`job_analysis`),
madde metinleri (`bullet_rewrite`), özet ve ön yazı girdileri — yani **kişisel
veri**: ad, iletişim, iş geçmişi.

**Eğitim.** Her istekte `provider.data_collection: "deny"` gidiyor. Metinde
*"sağlayıcılar eğitmiyor"* değil **"eğitebilecek sağlayıcıya yönlendirilmiyor"**
demek doğru: OpenRouter'ın kendi loglama politikası ayrı, hesap ayarında.

**Liste yapılandırmadan türüyor, sabit değil** (karar 2026-09-09: model
kısıtlanmıyor — yarın Claude ya da DeepSeek olabilir). Metni **"şu an kullanılan
model"** diye yazın ve model değişince sayfayı gözden geçirin. Backend açılışta
kendi listesini logluyor (`ProcessorAudit`: *"Content may be sent to […]"*) —
**yayımlanan sayfayı o satıra karşı kontrol edin.**

**Bugünkü model:** `openai/gpt-5.6-sol`, bağlam 1.050.000; fiyat (standart OpenAI
endpoint'i, %50 kampanyalı) milyon token başına 2 / 10 / 0.2 USD.

**Action:** gizlilik politikasının alt işleyen bölümünü bu adlarla ve "ne
gidiyor" listesiyle yazın.

### B-075 · `contentGrant.accessedAt` telden kalktı — tutulamayan bir sözdü

**Since:** commit `<bu PR>` · **Spec:** `spec/…` § 48.4 ·
`generation/api/dto/FeedbackResponse.Grant`

**Ne oldu:** `contentGrant` artık `open`, `expiresAt` ve `revokedAt` taşıyor;
**`accessedAt` yok.** Kolon (`support_grants.accessed_at`) yerinde duruyor.

**Neden:** o alanı **hiçbir şey yazmıyor** ve yapısal olarak yazamaz. Başka bir
kullanıcının içeriğini okumak destek tarafına bakan bir yol gerektiriyor, mutlak
kural 3 ise (her okuma sahibine göre kapsanmış bir repository'den geçer, IDOR
savunması) böyle bir yolu kasten bırakmıyor. Yani alan her zaman null: ekran,
gerçekten bakılmış olsa da **"kimse bakmadı"** diyor. Bir denetim izi değil,
denetim izi kılığında bir varsayım.

**Action:** `Feedback.tsx`'te `grant.accessedAt`'ten üretilen dalı ve
`feedbackGrantRead` metnini **kaldırın** (mock'takini de). Kullanıcıya izin
hakkında söylenebilecek doğru şeyler duruyor: açık mı, ne zaman doluyor, geri
çekildi mi. "Okundu / okunmadı" bunlardan biri değil — ve "okunmadı" demek,
söyleyemediğimiz şeyi söylemek. `npm run gen:api` alanı tipten de düşürecek.

**Söz geri gelecek:** destek okuma yolu (kimlik doğrulamalı, grant açıkken
okuyan ve `accessed_at`'i damgalayan) indiği gün alan da geri geliyor, aynı adla.
O yol bir ürün kararı bekliyor: destek kim, nasıl kimlik doğruluyor, uç mu
çevrimdışı dışa aktarma mı.

## ACK — frontend tamamladı, backend arşivleyebilir

_(`B-071`…`B-074`'ün dördü de karşılandı ve `resolved/to-frontend-2026-09.md`'ye
indi 2026-09-09'da, dosya sınırı; `B-037`…`B-070` bir öncekinde. Aşağıdakiler
**hâlâ canlı olan** kayıtlar.)_

**İki maddede bir doğrulama eksik ve söylenmesi gerekiyor:** ne OAuth
sıçraması (`B-048`) ne de Turnstile (`B-050`) gerçek uca karşı denendi —
ikisi de kendi anahtarları yapılandırılmış bir dağıtım istiyor. Bugün
doğrulanan şey mock'a karşı.

**EK C.1'in sağlayıcı listesi maddesi artık `B-076`'da** — model seçildi ve
gerekler orada yazılı. Yayın öncesi kontrol listesi o madde kapanınca kapanıyor.

---

## Kalıcı kurallar

Eski maddelerin `spec/`'e işlendiği yerlerin tablosu
`resolved/to-frontend-2026-08.md`'ye taşındı (2026-08-24) — dosya sınırı.
