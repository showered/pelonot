#!/usr/bin/env bash
# Fetch the rider avatar set and write it into the app's resources (PLAN 20.6.1).
#
# This runs ONCE, by hand, and its output is committed. The app never calls an
# API: it starts a ride with no network and that is not negotiable (19.4). If
# you want to change the set, change the lists below, run it, and commit both
# this file and the PNGs it writes.
#
# `./avatars/browse.sh` renders a sheet of candidates in a browser so faces can
# be picked by eye rather than by guessing at seeds — see README.md.
#
# The set is Open Peeps by Pablo Stanley, CC0 1.0.
set -euo pipefail

STYLE="open-peeps"
VERSION="9.x"
SIZE=256
OUT="$(dirname "$0")/../app/src/main/res/drawable-nodpi"

# ---------------------------------------------------------------------------
# What the faces are allowed to look like
# ---------------------------------------------------------------------------
#
# **Fitted to the household that will use this bike**, at the owner's
# instruction: this is a family's own tablet, not a product with a public
# audience, and a picker where nobody recognises themselves is a picker nobody
# uses. Two settings, both plain lists, both meant to be edited.
#
# SKIN — DiceBear picks uniformly from what it is given, so a repeated value is
# simply a heavier weight. The five here are Open Peeps' own palette; the
# ordering below is roughly 50 / 30 / 20 towards the lightest.
SKIN="ffdbb4,ffdbb4,ffdbb4,ffdbb4,ffdbb4,ffdbb4,edb98a,edb98a,edb98a,d08b5b"

# MASKS — off. The default is a 5% chance of a surgical mask or a respirator,
# which is a thing that happened to the world in 2020 rather than a face, and
# one in twenty is enough to put one in a set this size (it put one on `haze`).
MASK_PROBABILITY=0

# HEADS — every option Open Peeps offers *except* `hijab` and `turban`, which
# are religious dress, and `bear`, which is a bear. Naming the whole list
# rather than an exclusion is deliberate: the API has no "not this" and a new
# option added upstream would otherwise arrive in the set unasked.
HEADS="afro,bangs,bangs2,bantuKnots,bun,bun2,buns,cornrows,cornrows2,dreads1,dreads2,\
flatTop,flatTopLong,grayBun,grayMedium,grayShort,hatBeanie,hatHip,long,longAfro,\
longBangs,longCurly,medium1,medium2,medium3,mediumBangs,mediumBangs2,mediumBangs3,\
mediumStraight,mohawk,mohawk2,noHair1,noHair2,noHair3,pomp,shaved1,shaved2,shaved3,\
short1,short2,short3,short4,short5,twists,twists2"

# ---------------------------------------------------------------------------
# The set itself
# ---------------------------------------------------------------------------
#
# The seed *is* the id, and the id reaches the database (`profiles.avatar`), so
# these are words rather than indices — this project debugs in sqlite3 and
# `peep_lark` reads where `f11` does not. **Never rename or reorder one**: the
# column stores the id and a rename silently re-faces every rider who chose it.
# Swapping the *image* under an existing id is fine and is what browse.sh is
# for; adding one means adding it to `AvatarFace` too, and the exhaustive `when`
# in `RiderAvatar.kt` will fail the build until you do.
#
# An entry may carry its own extra query after a `|`, which overrides the lists
# above for that one face — this is the door left open for picking a face by
# hand rather than by seed:
#
#     lark|head=short1&facialHair=moustache3&skinColor=ffdbb4
#
SEEDS=(
  ash bay cove dune elm
  fern glen haze isle kite
  lark moss nova opal pine
  quill reed sage tide vale
)

mkdir -p "$OUT"
for entry in "${SEEDS[@]}"; do
  seed="${entry%%|*}"
  extra=""
  [[ "$entry" == *"|"* ]] && extra="&${entry#*|}"
  curl -fsS --max-time 30 \
    "https://api.dicebear.com/${VERSION}/${STYLE}/png?seed=${seed}&size=${SIZE}&backgroundColor=transparent&skinColor=${SKIN}&head=${HEADS}&maskProbability=${MASK_PROBABILITY}${extra}" \
    -o "${OUT}/avatar_${seed}.png"
  printf '  %s\n' "avatar_${seed}.png"
done
echo "${#SEEDS[@]} faces written to ${OUT}"
