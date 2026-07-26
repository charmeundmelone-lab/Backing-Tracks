#!/bin/bash
set -euo pipefail

# Lies den gewünschten Branch aus .claude/active-branch
BRANCH_FILE="$(dirname "$0")/../active-branch"

if [ ! -f "$BRANCH_FILE" ]; then
    echo "session-start: .claude/active-branch fehlt — kein Branch-Wechsel"
    exit 0
fi

EXPECTED_BRANCH=$(cat "$BRANCH_FILE" | tr -d '[:space:]')

if [ -z "$EXPECTED_BRANCH" ]; then
    echo "session-start: .claude/active-branch ist leer — kein Branch-Wechsel"
    exit 0
fi

CURRENT_BRANCH=$(git branch --show-current 2>/dev/null || echo "")

# 1. Auf den erwarteten Branch wechseln (falls nötig)
if [ "$CURRENT_BRANCH" = "$EXPECTED_BRANCH" ]; then
    echo "session-start: Branch korrekt ($EXPECTED_BRANCH)"
else
    echo "session-start: Falscher Branch ($CURRENT_BRANCH) → wechsle zu $EXPECTED_BRANCH"
    git fetch origin "$EXPECTED_BRANCH" 2>/dev/null || true
    if git checkout "$EXPECTED_BRANCH" 2>/dev/null; then
        echo "session-start: Branch gewechselt zu $EXPECTED_BRANCH"
    else
        git checkout -b "$EXPECTED_BRANCH" "origin/$EXPECTED_BRANCH"
        echo "session-start: Branch neu erstellt und gewechselt zu $EXPECTED_BRANCH"
    fi
fi

# 2. IMMER auf den neuesten Remote-Stand nachziehen (behebt "lokaler Branch veraltet").
#    Fast-Forward-only: lokale unpushte Commits werden NIE verworfen — dann bricht
#    es kontrolliert ab und meldet den Abweichungs-Grund, statt still zu divergieren.
if git fetch origin "$EXPECTED_BRANCH" 2>/dev/null; then
    BEHIND=$(git rev-list --count "HEAD..origin/$EXPECTED_BRANCH" 2>/dev/null || echo "0")
    AHEAD=$(git rev-list --count "origin/$EXPECTED_BRANCH..HEAD" 2>/dev/null || echo "0")

    if [ "$BEHIND" = "0" ]; then
        echo "session-start: Bereits auf neuestem Stand (origin/$EXPECTED_BRANCH)"
    elif [ "$AHEAD" = "0" ]; then
        if git merge --ff-only "origin/$EXPECTED_BRANCH" 2>/dev/null; then
            echo "session-start: $BEHIND Commit(s) von origin/$EXPECTED_BRANCH nachgezogen → jetzt aktuell"
        else
            echo "session-start: WARNUNG: Fast-Forward fehlgeschlagen trotz $BEHIND ausstehender Commits — bitte manuell prüfen"
        fi
    else
        echo "session-start: WARNUNG: lokaler Branch ist $AHEAD Commit(s) VORAUS und $BEHIND ZURÜCK (divergiert) — KEIN Auto-Merge, bitte manuell auflösen (git pull --rebase)"
    fi
else
    echo "session-start: WARNUNG: git fetch fehlgeschlagen (offline?) — arbeite auf lokalem Stand, evtl. veraltet"
fi
