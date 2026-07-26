Wir beenden die Session und sichern alles, sodass ein frisches Chatfenster garantiert auf dem neuesten Stand ist. Führe diese Schritte der Reihe nach aus und lass keinen aus:

1. **Alles committen.** `git status` prüfen, dann alle offenen Änderungen committen (nichts uncommitted lassen). Lösche nichts unwiderruflich — alles muss per git wiederherstellbar bleiben.

2. **Auf main sichern.** Sicherstellen, dass der gute Stand auf `main` liegt. Falls auf einem Seiten-Branch gearbeitet wurde, sauber auf `main` zusammenführen.

3. **CLAUDE.md aktualisieren.** Datum, neue Build-Nummer und was zuletzt gemacht wurde eintragen. Bei echtem Feature-/Fix-Abschluss einen kurzen Sprint-Eintrag ergänzen; sonst reicht der Stand-Block. Committen.

4. **.status.md aktualisieren.** Genau 3 Punkte für den nahtlosen Einstieg der nächsten Session: (a) Hauptziel/letzter Stand, (b) betroffene Dateipfade, (c) konkreter nächster Schritt. Committen.

5. **PUSH nach origin/main (PFLICHT — das ist die eigentliche Sicherung).** `git push -u origin main`. Bei Netzwerkfehler bis zu 4x mit Backoff (2s/4s/8s/16s) wiederholen.

6. **Push verifizieren.** `git fetch origin main`, dann prüfen dass `git rev-list --count origin/main..main` = 0 UND `git rev-list --count main..origin/main` = 0 ist (lokal und Remote identisch). Erst wenn beide 0 sind, gilt die Session als gesichert. Falls nicht 0: Grund nennen und erneut pushen, NICHT als "gesichert" melden.

7. **Bestätigen.** In Alltagssprache melden, dass wirklich alles auf `origin/main` gesichert ist (nicht nur lokal), und die neue Build-Nummer nennen.
