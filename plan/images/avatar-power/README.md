# Avatar power badge — 24 September 2026

`concept.png` and `refined-concept.png` were generated with the built-in ChatGPT
image-generation tool. They are design references, not shipped bitmap UI.
`RiderAvatar` implements the badge in native Compose and keeps the existing
portrait assets and FTP confirmation rules. The generated refinement adds
unwanted glow and badges at tiny sizes; neither is implemented.

Final treatment: bolt + number, optional green confirmation seal, a dark
60%-opacity capsule with a subtle border, bottom-centred inside the square
avatar. No extra component height. The selector shows the number once.

## Emulator evidence

1920 × 1080 px at 240 dpi, matching the bike's logical width and density.
Temporary activity rendered the real components with in-memory fixtures, then
was removed. No database edits, no ride or profile deletion, no release.

- `review-100.png`: both themes; confirmed 215 and unconfirmed 600; portraits
  at 114, 100, 80, 66 and 40 dp. Badges deliberately stop below 80 dp.
- `review-130.png`: same comparison at 130% system text.
- `selector-100.png`: one rider; one power badge and no duplicate caption.
- `selector-household-130.png`: six riders at 130%; only the confirmed fixture
  gets a green seal. Guest and new-rider actions remain visible.

## Final refinement prompt

Reference input: `review-100.png`, the implemented component comparison.

> Use case: ui-mockup. Refine this actual Pelonot Android avatar component design, following the user's feedback: the number capsule must be overlaid ON the lower part of the circular avatar, optically centred horizontally, NOT dangling beneath it or making the component taller. Preserve the existing cartoon portrait style, palette, circular progress ring, and square footprint. Make the capsule feel light and modern with a dark 60%-opacity translucent background and a subtle thin edge, allowing the portrait's lower shoulders/ring to show through. Keep white lightning icon and bold '215', with a small emerald green circular checkmark seal on the right only when confirmed. Show another example with lightning and '180' without the green check. Keep the entire badge centred on the avatar, all white symbols and numbers crisp and fully legible. Do not show FTP or W. Produce a focused comparison with dark and light surfaces and realistic small sizes. No extra captions or slogans. This is a refinement of an existing component, not a redesign of portraits or app.
