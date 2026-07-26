#!/bin/bash
# Stop-Hook: sichert nach jeder Antwort automatisch alles nach origin/main.
# Läuft komplett in der Shell -> kostet keine Modell-Tokens.
# Bewusst robust: schlägt NIE hart fehl (kein set -e), damit ein Netzwerk-
# oder Git-Problem die Session nicht blockiert. Meldet nur kurz den Ausgang.

cd "$CLAUDE_PROJECT_DIR" 2>/dev/null || exit 0

# Nur auf main auto-sichern (Projekt-Regel: alles direkt auf main).
BRANCH=$(git branch --show-current 2>/dev/null || echo "")
if [ "$BRANCH" != "main" ]; then
    echo "auto-save: nicht auf main ($BRANCH) — übersprungen"
    exit 0
fi

# Nichts zu tun, wenn keine Änderungen anstehen.
if [ -z "$(git status --porcelain 2>/dev/null)" ]; then
    # Trotzdem prüfen, ob lokale Commits noch ungepusht sind (z.B. aus /ende).
    if [ "$(git rev-list --count origin/main..main 2>/dev/null || echo 0)" = "0" ]; then
        exit 0
    fi
else
    git add -A 2>/dev/null
    git commit -q -m "auto-save: $(date '+%Y-%m-%d %H:%M:%S')" 2>/dev/null
fi

# Push mit kurzem Timeout, ein Retry — blockiert die Session nicht lange.
if timeout 30 git push origin main >/dev/null 2>&1; then
    echo "auto-save: nach origin/main gesichert"
elif timeout 30 git push origin main >/dev/null 2>&1; then
    echo "auto-save: nach origin/main gesichert (2. Versuch)"
else
    echo "auto-save: Commit lokal gesichert, Push fehlgeschlagen (offline?) — wird beim nächsten Mal nachgeholt"
fi
exit 0
