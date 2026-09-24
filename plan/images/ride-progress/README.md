# Live heart-rate ring — 24 September 2026

The full ride layout at 1920 × 1080, 240 dpi. Temporary fixtures exercised the
long zone labels with three-digit heart rates; no rider data was changed.

- [Normal text, H1 Recovery](ring-100.png)
- [130% text, H4 Threshold](ring-130.png)

The doughnut and the BPM value share a measured row. The label occupies the
following row, after a 4 dp gap; it cannot flow behind the doughnut. The prior
absolute positioning was removed after the owner's explicit overlap check.

[Low strap battery at 130% text](battery-130.png) shows the warning in the ride
header. This is a synthetic battery reading for layout verification, not a
claim that a physical strap's Battery Service has been verified.
