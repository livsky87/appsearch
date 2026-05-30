#!/usr/bin/env bash
set -euo pipefail

BUMP_TYPE="${1:-patch}"
FILE="${2:-version.properties}"

if [[ ! -f "$FILE" ]]; then
  echo "Version file not found: $FILE" >&2
  exit 1
fi

if [[ "$BUMP_TYPE" != "patch" && "$BUMP_TYPE" != "minor" && "$BUMP_TYPE" != "major" ]]; then
  echo "Unsupported bump type: $BUMP_TYPE (expected patch|minor|major)" >&2
  exit 1
fi

major="$(grep '^major=' "$FILE" | cut -d= -f2)"
minor="$(grep '^minor=' "$FILE" | cut -d= -f2)"
patch="$(grep '^patch=' "$FILE" | cut -d= -f2)"
versionCode="$(grep '^versionCode=' "$FILE" | cut -d= -f2)"

case "$BUMP_TYPE" in
  patch)
    patch=$((patch + 1))
    ;;
  minor)
    minor=$((minor + 1))
    patch=0
    ;;
  major)
    major=$((major + 1))
    minor=0
    patch=0
    ;;
esac

versionCode=$((versionCode + 1))

cat > "$FILE" <<EOF
major=${major}
minor=${minor}
patch=${patch}
versionCode=${versionCode}
EOF

echo "${major}.${minor}.${patch}"
