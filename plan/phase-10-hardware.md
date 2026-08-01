> Part of the Pelonot plan — the index is [PLAN.md](../PLAN.md).

## Phase 10: Hardware Validation — partly done, 31 July 2026

Done on a real Gen 1 (`PLTN-RB1VQ`) over wireless adb, with a rider pedalling.
Note that the bike is **stock, not jailbroken**, which turned out to be the
finding rather than an obstacle — see 2.1a.

- [x] **10.1** Sensor board device path confirmed: `/dev/ttyO0`, `system:system`
      `0660`. **Not readable by this app, and not made readable** — there is no
      root on a stock bike. `/dev/ttyS1` does not exist and `/dev/ttyS2` is the
      Bluetooth UART
- [x] **10.2** Moot as written, and answered: the raw byte protocol never
      reaches us. Peloton's service owns the port, decodes the packets
      (`F5,41,36,F6` for RPM, `F5,44,39,F6` watts, `F5,49,3E,F6` resistance,
      19200 baud) and hands over a float. `SerialProtocolParser` is unexercised
      on this hardware and stays only for the rooted-tablet path
- [x] **10.3** Superseded for real rides: the board reports watts directly, so
      there is nothing to calibrate on the bike itself. The remaining
      calibration question is about simulated rides — see 2.2.5
- [x] **10.4** **HUD renders over a video app** — verified over Netflix on the
      bike, 31 July 2026. Full-width strip docked to the top edge, middle of
      the screen clear, every figure live (cadence 84, resistance 32, power
      71 W, heart rate 98, interval countdown and next-interval preview). The
      overlay window is present as an `appop=SYSTEM_ALERT_WINDOW` window and
      never takes focus from the video app. Overlay permission turned out to be
      granted already
- [x] **10.5** **BLE strap connects and streams** — Wahoo TICKR FIT, found and
      connected on the first scan, a heart rate on all 314 rows of a ride. See
      2.3.5 for the two manifest and UI defects that had to be fixed first
- [ ] **10.6** Full-length ride: battery, thermals, memory, no dropped samples.
      The longest run so far is 8 minutes

> **Screenshots do not work over a playing film.** Netflix's player sets
> `FLAG_SECURE`, so `adb exec-out screencap` returns an empty image and the HUD
> cannot be captured over DRM video — it captured fine over Netflix's own
> non-secure PIN dialog. Anything about readability over moving video (11.1b.2)
> has to be judged by the rider's eyes, not from a screenshot. Likewise a
> spoken cue lasts a second or two, so polling `dumpsys audio` every five
> seconds slides straight past the duck; the rider hearing it is the
> measurement.
