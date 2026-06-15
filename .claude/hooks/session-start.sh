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

if [ "$CURRENT_BRANCH" = "$EXPECTED_BRANCH" ]; then
    echo "session-start: Branch korrekt ($EXPECTED_BRANCH)"
    exit 0
fi

echo "session-start: Falscher Branch ($CURRENT_BRANCH) → wechsle zu $EXPECTED_BRANCH"

git fetch origin "$EXPECTED_BRANCH" 2>/dev/null || true

if git checkout "$EXPECTED_BRANCH" 2>/dev/null; then
    echo "session-start: Branch gewechselt zu $EXPECTED_BRANCH"
else
    git checkout -b "$EXPECTED_BRANCH" "origin/$EXPECTED_BRANCH"
    echo "session-start: Branch neu erstellt und gewechselt zu $EXPECTED_BRANCH"
fi
