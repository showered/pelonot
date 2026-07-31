# The bike's tablet — measured facts

Everything here was read off the real device (`PLTN-RB1VQ`) over wireless adb
on 31 July 2026, not from a spec sheet. The point of the file is that **UI work
can be checked on an emulator that actually matches the target**, instead of on
a phone-shaped AVD that hides every layout problem this device has.

## Display

| | |
|---|---|
| Resolution | **1920 × 1080** px, landscape, fixed |
| Density | **240 dpi** (`hdpi`, scale factor 1.5) |
| **Logical size** | **1280 × 720 dp** |
| App area | **1920 × 1008** px = **1280 × 672 dp** |
| System bars | **Navigation bar only**, 72 px / 48 dp, at the **bottom**. There is no top status bar |
| Refresh rate | 60 Hz, single mode, no variable refresh |
| Reported physical dpi | 160 × 160 (note this disagrees with the 240 density — layout follows the density) |

Design to **1280 × 720 dp with 48 dp of bottom inset**. That is a lot of
horizontal room and not much vertical, which is why a phone-shaped single
column looks so wrong here (11.3.1, 20.1.1).

## Matching emulator

```bash
~/Library/Android/sdk/tools/bin/avdmanager create avd -n Pelonot_Bike -k "system-images;android-30;google_apis;arm64-v8a" --device "10.1in WXGA (Tablet)"
```

Then set these in the AVD's `config.ini`, because the device profile does not
get all of them right:

```ini
hw.lcd.width=1920
hw.lcd.height=1080
hw.lcd.density=240
hw.initialOrientation=landscape
```

An AVD at 1920 × 1080 but the *wrong density* is the trap: at 320 dpi the same
panel is 960 × 540 dp and everything looks plausible while being half the size
it will be on the bike.

## System

| | |
|---|---|
| Model | `PLTN-RB1VQ` |
| Manufacturer | Peloton Interactive LLC |
| Android | **11** (API **30**) |
| SoC | MediaTek (`mtk-*` input drivers) |
| Build type | `user`, release-keys, `ro.secure=1` — **not rooted**, no `su`, no Magisk |

## Volume buttons — no usable ones

The owner reports **no physical volume rocker**, and the software picture is
consistent with that but does not prove it on its own:

```
ACCDET      KEY_VOLUMEDOWN KEY_VOLUMEUP KEY_PLAYPAUSE ...   ← headset jack remote
mtk-kpd     KEY_VOLUMEDOWN KEY_VOLUMEUP                      ← SoC keypad driver
mtk-tpd / mtk-pmic-keys / ssusb   KEY_POWER
```

Two devices *declare* volume keys, and neither is a button on the bike:

- **`ACCDET` is the headphone jack.** Those are inline headset-remote buttons,
  present only when something is plugged in.
- **`mtk-kpd` is the MediaTek keypad driver**, which declares the capability
  whether or not the board populates the buttons. A declaration is not a
  switch.

**To settle it definitively** (ten seconds, needs someone at the bike): run
`adb shell getevent -l` and press every physical button on the tablet. If no
`KEY_VOLUMEUP` / `KEY_VOLUMEDOWN` event appears, there is nothing to honour.

Either way it does not change the conclusion behind PLAN.md **11.5**: with
Peloton sideloaded there is **no status bar to pull down and therefore no
system volume UI at all**, so the app has to provide one. Honouring volume keys
if they ever arrive is cheap and worth doing, but nothing may depend on them.

## Two things that will waste your time

**`screencap` returns an empty image over DRM video.** Netflix's player sets
`FLAG_SECURE`, so the HUD cannot be screenshotted over a playing film — it
captures fine over non-secure dialogs. Anything about how the HUD *looks* over
video has to come from the rider.

**The ride screen's big metric cards carry no semantics**, so `uiautomator
dump` will not show cadence, resistance or power. Use `screencap` and read the
numbers, except per the above.

## Software on the tablet

Not a stock Peloton image — it has been used. Relevant packages:

| Package | What it is |
|---|---|
| `com.peloton.service.SensorData` | The sensor service Pelonot binds for telemetry (PLAN.md 2.1a) |
| `com.onepeloton.weasel` | Peloton's own member app |
| `com.onepeloton.tts` | The only TTS engine installed. `tts_default_synth` is unset, and speech works anyway via the engine default |
| `com.netflix.mediaclient` | Side-loaded |
| `com.teslacoilsw.launcher` | Nova Launcher, side-loaded, is the active launcher |

## Driving it from a session

```bash
adb devices -l                                    # identifies as PLTN-RB1VQ
adb shell input tap <x> <y>                       # coordinates are in the 1920x1080 px space
adb exec-out screencap -p > shot.png
adb shell dumpsys notification --noredact | grep -A25 "pkg=com.pelonot"   # live ride state
```

The ride notification is the most reliable read on an in-flight ride —
`Pelonot — Riding` / `Pelonot — Paused` with elapsed, watts and rpm — and it
works while another app is in the foreground, which `screencap` may not.
