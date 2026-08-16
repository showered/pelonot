# The rider avatar set

Twenty faces, vendored into `app/src/main/res/drawable-nodpi/avatar_*.png`.
**PLAN 20.6.1.**

## Licence and attribution

**Open Peeps**, by **Pablo Stanley** — <https://www.openpeeps.com/> — released
under **CC0 1.0 Universal** (public domain dedication):
<https://creativecommons.org/publicdomain/zero/1.0/>.

The particular images here were rendered by **DiceBear**'s Open Peeps
collection (<https://www.dicebear.com>), which is a remix of the above and
carries the same CC0 dedication. Each file's own metadata says so; the
`fetch.sh` beside this file is exactly how they were produced.

CC0 asks for nothing, including attribution. It is here anyway because 20.2.1's
rule is that whatever is used is *credited in the repo*, and because the next
person to open this directory should be able to answer "can we ship this?"
without going and looking.

## Why this set

Five CC0 candidates were rendered on the app's own disc and looked at side by
side at the two sizes that matter — the picker at about 100 dp, and **32 dp**,
which is the household row and the dashboard greeting:

| Set | Author | Verdict |
|-----|--------|---------|
| **Open Peeps** | Pablo Stanley | **Chosen.** Flat bold colour with a hard outline — the only one of the five still legible as *a particular person* at 32 dp |
| Lorelei | Lisa Wischofsky | Line art. The best-looking of the five large, and at 32 dp only the hair silhouette carries |
| Notionists | Zoish | Palest of the five and the weakest small |
| Pixel Art | DiceBear | Very legible small, and reads as a game character — a whole-app style choice rather than an avatar choice |
| Thumbs | DiceBear | The most legible of all and not a person; it also brings its own background colour, which fights `AvatarPalette` |

Three further sets were ruled out on licence before they were looked at — Big
Smile, Big Ears, Fun Emoji and Croodles are CC BY 4.0, and Bottts is "free for
personal and commercial use", which is a permission rather than a licence.
20.2.1's list is CC0, SIL OFL or MIT.

## Regenerating

```bash
./avatars/fetch.sh
```

It writes straight into the app's resources. **The app never calls the API** —
it starts a ride with no network and that is not negotiable (19.4). This script
runs by hand and its output is committed.

**The seed is the id and the id reaches the database.** `profiles.avatar`
stores `<colour>:<face>`, so a row reads `rose:lark`, and renaming or reordering
a seed silently re-faces every rider who chose it. Adding one is free; changing
one is not.
