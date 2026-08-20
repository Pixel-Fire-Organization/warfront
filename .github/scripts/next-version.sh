#!/usr/bin/env bash
# Computes the next release version (X.Y.Z, no prefix/suffix) from git history.
#
# Base: the highest vX.Y.Z tag reachable (release tags only; -dev.N tags are ignored).
# Bump: scans commit subjects since that tag for a Conventional Commits prefix —
#   any `feat:`/`feat(scope):` bumps minor (and resets patch to 0); otherwise any
#   `fix:`/`fix(scope):` bumps patch; anything else also bumps patch as the default.
#
# Used as-is by release-main.yml to tag the actual release, and by release-dev.yml
# to compute the version a dev pre-release would become on release.
set -euo pipefail

LAST_TAG=$( { git tag -l 'v[0-9]*.[0-9]*.[0-9]*' | grep -vE -- '-dev\.' | sort -V | tail -n1; } || true )
LAST_TAG=${LAST_TAG:-v0.0.0}
LAST_VERSION=${LAST_TAG#v}

IFS='.' read -r MAJOR MINOR PATCH <<< "$LAST_VERSION"

if git rev-parse "$LAST_TAG" >/dev/null 2>&1; then
    RANGE="${LAST_TAG}..HEAD"
else
    RANGE="HEAD"
fi

if git log "$RANGE" --pretty=%s | grep -qE '^feat(\(.+\))?:'; then
    MINOR=$((MINOR + 1))
    PATCH=0
else
    PATCH=$((PATCH + 1))
fi

echo "${MAJOR}.${MINOR}.${PATCH}"
