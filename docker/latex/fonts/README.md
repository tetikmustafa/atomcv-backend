# Whitelisted fonts

Fonts a template names but Debian does not ship (Bölüm 33). The font family is
chosen from a whitelist rather than from user input, so anything here has been
picked deliberately and is licensed for redistribution.

Empty for now: the classic template uses TeX Gyre, which `fonts-texgyre`
provides. A file dropped in here is picked up by `fc-cache` at build time.

**Measurement depends on this directory.** Render costs are measured in points
against a specific font; adding or replacing one changes them, so a font change
is a template version change (Bölüm 16.3).
