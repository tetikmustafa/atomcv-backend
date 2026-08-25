# Kapanmış aşamaların inşa notları

Bir aşama bitince `notes/current.md` buraya `stage-<n>.md` olarak taşınır ve
`current.md` boş başlar.

Aşama **kapanmadan** da inilebilir: `current.md` 200 satırı zorlarsa kapanmış
adımların kayıtları `stage-<n>-<konu>.md` olarak buraya taşınır ve `current.md`
bir işaretçi satırıyla kalır. Açık kısıtlar ve devredilen kararlar **taşınmaz** —
devam eden oturum onları okumak zorunda.

**Rutin okunmaz.** Yalnız arkeoloji için: `rg -n "<konu>" docs/notes/archive/`
