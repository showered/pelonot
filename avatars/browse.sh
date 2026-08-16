#!/usr/bin/env bash
# Look at candidate faces and pick the ones you want, by eye (PLAN 20.6.1).
#
# `fetch.sh` turns a *seed* into a face and there is no way to guess what a
# seed will look like, so this renders a sheet of them in the browser with the
# seed printed under each one. Pick the ones you like, paste the words into
# fetch.sh's SEEDS, run fetch.sh, commit.
#
#   ./avatars/browse.sh              # 60 candidates from a word list
#   ./avatars/browse.sh 120          # more of them
#
# It obeys the same SKIN and HEADS settings as fetch.sh, so what you see here
# is what you will get.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
COUNT="${1:-60}"
SHEET="$HERE/candidates.html"

# Everything after `set -euo` in fetch.sh that we want is the two lists; source
# it in a subshell with the download loop disabled rather than keeping a second
# copy of them here, because two copies of a rule is how they come to differ.
SKIN="$(sed -n 's/^SKIN="\(.*\)"$/\1/p' "$HERE/fetch.sh")"
HEADS="$(awk '/^HEADS="/{f=1} f{printf "%s", $0; if(!/\\$/) exit} ' "$HERE/fetch.sh" \
  | sed 's/^HEADS="//; s/"$//; s/\\//g')"

mkdir -p "$HERE/.candidates"
words=(amber anchor arbor aspen aster beacon birch bloom bramble brook cedar chalk
       cinder clay clover cobble comet coral crest dawn delta drift ember fable fell
       flint forge gale glade gorse grove harbor heath holly ivy juniper kelp lagoon
       larch ledge linden loam maple marsh meadow mesa mint north oak onyx orchard
       otter peak pebble quarry quill ridge rill river rowan rush saffron shale shore
       slate sorrel spruce stone summit thistle thorn tide timber vale verge vine
       willow winter wren yarrow yew zephyr)

: > "$HERE/.candidates/index"
i=0
for w in "${words[@]}"; do
  [ "$i" -ge "$COUNT" ] && break
  curl -fsS --max-time 30 \
    "https://api.dicebear.com/9.x/open-peeps/png?seed=${w}&size=96&backgroundColor=transparent&skinColor=${SKIN}&head=${HEADS}&maskProbability=0" \
    -o "$HERE/.candidates/${w}.png"
  echo "$w" >> "$HERE/.candidates/index"
  i=$((i + 1))
done

python3 - "$HERE" "$SHEET" <<'PY'
import base64, os, sys
here, sheet = sys.argv[1], sys.argv[2]
names = open(os.path.join(here, ".candidates", "index")).read().split()
def uri(n):
    with open(os.path.join(here, ".candidates", n + ".png"), "rb") as f:
        return base64.b64encode(f.read()).decode()
cells = "".join(
    '<figure><img src="data:image/png;base64,%s"><figcaption>%s</figcaption></figure>' % (uri(n), n)
    for n in names
)
open(sheet, "w").write(
    "<meta charset=utf-8><title>Avatar candidates</title><style>"
    "body{background:#101318;color:#e8eaed;font-family:system-ui;margin:0;padding:20px}"
    "h1{font-size:16px;font-weight:600}p{color:#9aa0a6;font-size:13px}"
    ".g{display:flex;flex-wrap:wrap;gap:14px}figure{margin:0;text-align:center;font-size:11px;color:#9aa0a6}"
    "img{width:96px;height:96px;border-radius:50%%;background:#8ea9db;display:block}"
    "</style><h1>Avatar candidates</h1>"
    "<p>The word under each face is its seed. Paste the ones you want into "
    "<code>SEEDS</code> in <code>avatars/fetch.sh</code>, then run it.</p>"
    '<div class="g">%s</div>' % cells
)
PY

echo "$SHEET"
command -v open >/dev/null && open "$SHEET"
