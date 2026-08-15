#!/bin/sh
# Is the page on the internet the page in this repo? (PLAN 17.16.7)
#
# It took under a day for the answer to become no. 17.16.5 fixed a defect on the
# pairing page, observed it against the live endpoint from a local copy, and
# nothing deployed it — so the owner scanned a QR the next day and met the
# unfixed page. Nothing anywhere said the two had parted.
#
# No credentials, no dependencies, and it deploys nothing. It fetches and diffs,
# which is the whole of what was missing.
#
#   ./web/check-deployed.sh                                # the hosted app
#   ./web/check-deployed.sh https://staging.example.com    # somewhere else
#
# `config.js` is deliberately not checked: it is git-ignored on purpose (17.14),
# so the deployed one is *expected* to differ from the working copy.

set -eu

HOST="${1:-https://pelonot.showered.workers.dev}"
HERE=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
FILES="index.html link.html app.css tokens.css lib.js link.js app.js"

WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT

drifted=0

for file in $FILES; do
  # The host trims `.html` with a 307 (17.16), so follow redirects.
  if ! curl -fsSL "$HOST/$file" -o "$WORK/$file" 2>/dev/null; then
    printf 'MISSING  %s — the host did not serve it\n' "$file"
    drifted=$((drifted + 1))
    continue
  fi

  if diff -q "$WORK/$file" "$HERE/$file" >/dev/null 2>&1; then
    printf 'same     %s\n' "$file"
  else
    printf 'DRIFTED  %s — the deployed copy is not this one\n' "$file"
    drifted=$((drifted + 1))
  fi
done

# Which publishable key is on the internet (17.16.3). Not a diff — `config.js`
# is git-ignored and the deployed one is *meant* to differ — but a check that
# the project's decision about the key FORM has actually reached the host. Both
# forms are publishable and neither is a secret; they revoke separately, which
# is what makes having two of them a trap the day one is rotated.
if curl -fsSL "$HOST/config.js" -o "$WORK/config.js" 2>/dev/null; then
  if grep -q 'sb_publishable_' "$WORK/config.js"; then
    printf 'same     config.js — publishable key, sb_publishable_ form\n'
  elif grep -q 'eyJ' "$WORK/config.js"; then
    printf 'LEGACY   config.js — the deployed key is the old JWT (eyJ…) form.\n'
    printf '         This project uses sb_publishable_ (17.16.3). Both work;\n'
    printf '         they revoke separately, so rotating one leaves the other live.\n'
    drifted=$((drifted + 1))
  fi
fi

if [ "$drifted" -eq 0 ]; then
  printf '\nThe deployed app is this working tree.\n'
  exit 0
fi

printf '\n%d file(s) differ. Redeploy, or read one:\n' "$drifted"
printf '  curl -sL %s/link.js | diff - web/link.js\n' "$HOST"
exit 1
