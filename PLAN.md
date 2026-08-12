# Pelonot — Implementation Plan

> **Open-Source Peloton Client** — A subscription-free fitness app for Peloton
> bikes (Gen 1/Gen 2). **A stock, un-jailbroken bike is the supported target**;
> telemetry comes from Peloton's own sensor service, not from root (see 2.1a).

---

## How to use this plan

1. **Each checkbox is one focused task** — small enough for a single session, large enough to matter.
2. **A box is only ticked when the behaviour has been observed working**, not when the code was written. Several items in this plan were previously ticked while the feature was non-functional (see [plan/corrections.md](plan/corrections.md)); that is the failure mode this rule exists to prevent.
3. **Work phases in order** where later ones build on earlier ones. See *Where the work stands* immediately below for the current priority.
4. When switching models or sessions, paste the current plan state so the next session knows where to pick up.

---

## The owner's inbox — ideas between sessions

**The owner writes here directly, without opening a session.** It is a way of
handing over a thought at the moment of having it rather than at the moment of
being able to act on it: an idea does not have to wait for a prompt, and it does
not have to interrupt work already in flight. One heading per idea.

**How a session handles it.** Read this section before picking work. Take an
entry, decide where in the plan it belongs, write it up as numbered items with
the reasoning kept rather than summarised — and then **empty the entry out of
this section**. An idea still sitting here has not been dealt with; an idea that
has moved has.

**Emptying it is urgent; building it is not.** The owner set this rule directly,
in the twentieth sitting: *"don't necessarily action them first. They should
have plan entries created and then triaged with just the same weighting as any
other plan items."* So an inbox entry jumps the queue **into the plan** and then
takes its place in it — what it buys is a written-down item with the reasoning
attached, not a promotion past work that matters more. (This replaces the older
line that said the inbox outranks *What to do next*.)

**Where an emptied entry goes.** One line here saying which item it became, and
nothing else. The reasoning belongs in the item, and the story of the sitting
belongs in the sitting — [plan/session-log.md](plan/session-log.md) has
every earlier write-up in full, verbatim, including the owner's own words.
*This paragraph is itself the answer to an inbox entry:* the section kept
growing not because entries were going unhandled but because each one left a
paragraph of write-up behind, and thirteen handled entries make a long section
that reads exactly like a backlog. The live inbox is now the last heading on
this page and nothing else.

**Fifty-four entries have passed through it.** In order: standing and seated
riding (**Phase 25**), max panel width (**22.4**), the initial FTP (**20.3**),
in-ride targets (**11.7**), resuming an interrupted ride (**8.3d**), the zone
ladder's bounce (**11.6.11**), whole watts (**11.6.12**), the beating heart
(**21.3.4**), heart-rate zones (**21.3.1**), the pre-ride countdown
(**11.6.13**), this section's own rule (in CLAUDE.md and above), the inbox's
growth (the paragraph above), inferring the maximum heart rate (**21.1.6**, and
**20.3.9** for what it shares with the FTP question), signing in by QR code
(**15.6**), and whether this is a monorepo (**17.1a** — it already is one; the
recommendation is not to move the Gradle root, and the two rules that actually
keep the apps independent are written down there instead), and one design
system across native and web (**17.15** — `web/tokens.css`, transcribed from
`Color.kt` and `Theme.kt`, with 17.15.1 for typography and 17.15.2 for the fact
that nothing keeps the two in step), and finding friends (**18.11** — there is
no friend graph; everyone registered is on everyone's board, and **18.11.1** is
the prerequisite that makes that safe: public sign-up has to be off), the
website being live (**17.16**, with **17.16.1–17.16.5** for what hosting it
changed — and it is what turned 18.11.1 from a prerequisite into a
measurement), and one page saying where the project actually is
(**19.1.7** — [STATUS.md](STATUS.md)), the live URL reaching the bike
(**17.16.1**, which the owner did themselves in `local.properties`), a week
being the wrong window for a rider who rides once of them (**22.5**), and *less
is more* (**Phase 26**, and a line in CLAUDE.md — it is a standing rule about
every screen rather than a job on one), ten answers where three will do
(**26.3**), no single card at full width (**22.6**, and the rule is in
CLAUDE.md), and inferring the effort from the heart rate instead of asking
(**21.5**), branded email instead of Supabase's own (**15.7**), a score shown
consistently like a game's level (**26.4**), two answers to questions this plan
asked the owner (**22.6.3**, closed as *not* to be enforced, and **26.3.3**,
settled as written), the overlay prompt landing after the countdown instead of
during it (**11.6.14**, and **11.6.15** found beside it), the ride summary
against the ride record (**12.6**), history's alignment (**22.7.1**), the Start
Class screen (**22.7.2**), the line across the overlay (**11.1b.10**, which
already existed and was waiting on exactly this decision), and heart-rate zones
on every chart that shows a heart rate (**21.4.2** — which already existed too;
what the note adds is *any chart*, and **21.4.2a** is the decision it forces
about what the bands are drawn from), the account being a thing a rider has to
go and find (**15.8**), the pairing page with no way to sign in on it
(**17.16.6** — two faults stacked, and **17.16.7** for the one that let the
first reach the owner), the live ghost (**24.3.3–24.3.9** for the household
half and **18.12** for the network's — the note is right that nothing social
happens *during* a ride today), and alerts worth being given (**Phase 27**,
promoted out of 19.3.2's one line the way Phase 21 was promoted out of
19.3.3's), the live site being up to date (**17.16.8** — the redeploy the
previous sitting asked the owner for, confirmed by the command that sitting
wrote rather than taken on trust), auto-FTP going down as well as up
(**7.11**, and this entry's own text is the interesting part — the owner
arrived at 7.11's asymmetry argument independently, without having seen it
written down, which is confirmation rather than new instruction), and the
three ride targets being hard to hit at once (**11.7**, flagged uncertainly by
the owner as possibly-already-covered — it is, word for word, and the new
sentence about power zone needing no resistance target restates 11.7.3
exactly), and the birth-year grid looking ridiculous (**21.1.1b** — already one
shared component on both screens that use it, so only the layout needed
fixing, a `LazyColumn` in place of the grid), and rivals against a leaderboard
(**24.3.11–24.3.14** — the owner's choice is the leaderboard, the rival goes
behind a flag rather than into the bin, and **24.3.14 is a question back**:
*watts* has now been used twice for the score and it decides whether the board
re-sorts several times a second), the countdown pushing the totals off the
bottom of the screen (**11.6.16**, and the note names the fix as well as the
fault), what the opponents on the leaderboard should be called (**24.3.12a** —
**an open item with the owner's name on it**, at their own request, and a
session that finds it still open should say so rather than invent an answer),
and the leaderboard on the overlay (**24.3.16**, which **overrules 24.1.5 and
18.6** — nothing social on the strip — and the write-up says so plainly rather
than letting two rules quietly disagree), and the board saying less
(**24.3.17a–c**, three notes that between them undo two of 24.3.13's three
decisions — the gap, the unit and the rank), the ride screen's three notes
(**11.6.17** the totals overflowing, **11.6.18** the rest of the class
scrolling — which is also 11.6.16's fix — and **11.6.19** tapping the distance
to change its units for one ride), the Start Class screen with the board on it
(**22.7.3**, and it is 22.7.2's own admission coming true: that screen was
designed on an AVD where the card could not draw), and the static board having
no ceiling (**24.1.8**), and auto-generated leaderboard ghosts to ride
against (**24.3.18** — five candidates written out with the argument
attached, four of them chosen and built, and it forced **24.3.12a** shut
after a fortnight open because ghosts would otherwise have put a second
family of invented name on a board that already had `12 MONTHS` on it), and
**the three notes of the first rider who had never seen it** — the onboarding
inputs (**20.4**, with the linking half at **15.6.11**, **15.6.12** and
**17.16.9**, and the `localhost` confirmation link at **15.7.6**, which is the
one that *ends* the journey rather than roughening it), the Start Class
screen's "million panels" (**22.7.5**, and **23.2.7** for the authored
description asked for beside it — the first prose in the library a build rule
cannot check for truth), and what a power zone actually is (**11.8**, a gap
this plan has never had an item for: the app is built on zones end to end and
never once says what one is), and **the same two onboarding controls reported a
second time** (**20.4.5** — and the interesting part is that 20.4.1 fixed them
by measuring and it was not enough), and **the back button during sign-up**
(**15.6.13**, with **20.4.6** for the profile-creation half of the same
journey), and **the three dots on the pairing page** (**15.6.14**, and the
owner's own follow-up — *"because i was already signed in"* — is what turned a
report into a diagnosis; **15.7.7** and **15.7.8** are what the same journey
turned up underneath it, which is that the mailer sending two emails an hour is
now the thing in the way), and **the code shown by default** (**15.6.15**), and
**the dashboard being stretched** (**22.8** — the note's three parts are the
primary action at 22.8.1, the density at 22.8.2–22.8.5 and the social feed at
22.8.7, and the measurement it asked for was taken before the write-up: the
screen is **993 dp of content in a 664 dp viewport**), and **achievements**
(**Phase 28**, at the owner's own weighting of *"one for the backlog"* — and
28's opening section is the reconciliation between *"gamify it all even
further"* and 26.4, which the owner and this plan agreed to leave).

*Empty.*

---

## Where the rest of this plan lives

**This file is the index.** It holds the owner's inbox, where the work stands,
what to do next and the status table — the things every session reads. The
phases themselves live in `plan/`, one file each, so a session can open the two
it needs instead of sixty thousand tokens of everything.

Item numbers are unchanged and still the way to refer to anything: **2.7c**,
**11.1b.9**, **24.3.1** mean what they always did, and the table says which
file to open for each.

| File | What is in it |
|------|---------------|
| [STATUS.md](STATUS.md) | **Not part of the plan — the page for a person who does not want to read it.** What works, what is outstanding, what is wrong today ranked by severity, and an explicit answer to *how close to done* (19.1.7). It is a summary of this plan and never a source for it |
| [plan/session-log.md](plan/session-log.md) | Every sitting before the latest one, the 31 July snag list, and the three narratives that changed the shape of the project |
| [plan/connectivity.md](plan/connectivity.md) | **The connectivity model** — the four rules, the identity ladder, what the model makes false today |
| [plan/fundamentals.md](plan/fundamentals.md) | What is fundamental and what is not |
| [plan/corrections.md](plan/corrections.md) | Items previously ticked that were not working — and why the house rule exists |
| [plan/storage-budget.md](plan/storage-budget.md) | What a workout costs, and whether 500 MB runs out |
| [plan/phases-complete.md](plan/phases-complete.md) | Phases **0, 1, 3, 4, 5, 6, 9** — scaffolding, Room, the service, the overlay window, the HUD UI, the main app UI, ride integration |
| [plan/phase-02-telemetry.md](plan/phase-02-telemetry.md) | **Phase 2** — the sensor service, the frame parser, BLE, calibration, and **2.7**, the corruption defect and its fix |
| [plan/phase-07-ftp.md](plan/phase-07-ftp.md) | **Phase 7** — auto-FTP, the FTP a ride was ridden at (7.8), FTP history (7.9) |
| [plan/phase-08-polish.md](plan/phase-08-polish.md) | **Phase 8** — polish, testing, edge cases, Material Expressive |
| [plan/phase-10-hardware.md](plan/phase-10-hardware.md) | **Phase 10** — hardware validation on the real bike |
| [plan/phase-11-hud.md](plan/phase-11-hud.md) | **Phase 11** — the HUD-first experience, the overlay's design, volume, and the full ride screen (11.6) |
| [plan/phase-12-history.md](plan/phase-12-history.md) | **Phase 12** — ride history, the rider's record, migrations (12.5) |
| [plan/phase-13-units.md](plan/phase-13-units.md) | **Phase 13** — units and display preferences |
| [plan/phase-14-cloud.md](plan/phase-14-cloud.md) | **Phase 14** — cloud sync that actually reaches the cloud |
| [plan/phase-15-accounts.md](plan/phase-15-accounts.md) | **Phase 15** — accounts, the thing that unlocks the cloud tier |
| [plan/phase-16-visualisation.md](plan/phase-16-visualisation.md) | **Phase 16** — data visualisation |
| [plan/phase-17-18-across-bikes.md](plan/phase-17-18-across-bikes.md) | **Phases 17 and 18** — the companion web app, and social across bikes |
| [plan/phase-19-ideas.md](plan/phase-19-ideas.md) | **Phase 19** — ideas worth having, ranked |
| [plan/phase-20-profiles.md](plan/phase-20-profiles.md) | **Phase 20** — who's riding: the profile selector and avatars |
| [plan/phase-21-hr-zones.md](plan/phase-21-hr-zones.md) | **Phase 21** — heart-rate zones |
| [plan/phase-22-dashboard.md](plan/phase-22-dashboard.md) | **Phase 22** — the dashboard |
| [plan/phase-23-offline.md](plan/phase-23-offline.md) | **Phase 23** — offline by default, and the bundled class library |
| [plan/phase-24-household.md](plan/phase-24-household.md) | **Phase 24** — household social, the tier that needs no cloud |
| [plan/phase-25-position.md](plan/phase-25-position.md) | **Phase 25** — out of the saddle |
| [plan/phase-26-voice.md](plan/phase-26-voice.md) | **Phase 26** — the app's voice: less is more, and where jargon belongs |
| [plan/phase-27-alerts.md](plan/phase-27-alerts.md) | **Phase 27** — being told something worth knowing: records, streaks, and being beaten |
| [plan/phase-28-achievements.md](plan/phase-28-achievements.md) | **Phase 28** — achievements: things the rider owns, not things the app says |
| [plan/reference.md](plan/reference.md) | The Coggan zone table and the ride-intent multipliers |

**Adding to the plan:** put the item in its phase's file. Only the four
sections of *this* file move with each session — the inbox, the latest sitting,
*What to do next*, and the status table. When a sitting's narrative stops being
the latest, it goes to the top of `plan/session-log.md`.

---

## Where the work stands — read this first

### Latest session — 13 August 2026 (forty-second sitting): the way out of the cloud, and why deleting the copy has to stop the backing up

**The inbox was empty and *What to do next* was, by its own admission, entirely
owner-and-bike work — so the brief pointed at the unfinished half of a
half-built phase: 15.4, *Leaving*.** A rider could sign in and sign out and had
**no way to take anything back down**, which is a gap with a legal name on it
as well as a plain one. **731 JVM tests and 99 instrumented, 0 failures**, and
everything below was watched on the 1280 × 720 dp AVD **against the real
endpoint** — a throwaway account made through the admin API, so nothing went
near the mailer that 15.7.7 says is in the way.

**15.4.2 turned out to be a data-loss trap wearing a privacy feature's
clothes.** Deleting the rows is one statement; what the tablet must do
afterwards is the whole item. Leave the account attached and `synced_at` records
a backup that no longer exists — and **the trimmer reads that column as
permission to throw seconds away** (23.4.6), so *delete my cloud copy* would
have licensed destroying the seconds whose only other copy it had just deleted.
Clear `synced_at` instead and the backlog drains at the next ride, putting the
copy straight back. So it is **delete *and* stop**: the rows go, the tablet
forgets any cloud ever had them, the profile drops to the offline rung, and the
dialog says so before the rider presses anything.

**The measurement is four states of two tables, with signing back in as the
control.** Cloud **2 workouts + 1 profile → 0 and 0**; tablet **2 rides and
1,020 samples, identical before and after**; `synced_at` null on both rows and
`auth_user_id` gone. Signing in again put every row back, which is the dialog's
*"you can sign in again whenever you like"* being **true** rather than
soothing. 15.4.1 was ticked in the same pass for the same reason it had not
been: built ages ago, never watched. **15.4.3 is designed and deliberately
unbuilt** — self-deletion needs an Edge Function and therefore a deploy, and an
app button calling a function nobody has deployed looks exactly like a broken
feature.

**One thing was found on the way and fixed, and it is 23.4.3's rule broken on a
surface nobody had counted as a drawing: an upload is an export.** The payload
carried a condensed ride's samples without carrying the fact that there were
fewer of them than seconds, so **the cloud copy of an outline was
indistinguishable from a ride recorded second by second**. `"d"` goes inside the
versioned payload — no cloud migration, nothing for the owner to apply — and it
is read off the row rather than inferred from the samples' spacing, because a
gap in a series is a rider who stopped. Measured where 14.4.3 says to measure a
wire format: `metrics_payload->>'d'` is **null with 900 samples** for the intact
ride and **10 with 120** for the outline (23.4.14).

**Two things the screen found that the diff could not.** The dialog's third
sentence agreed with itself only in the plural — *"1 older ride is kept here as
an outline… the seconds behind **them**"* — which is 26.x's sort of fault, small
and only visible at the size a rider reads it. And **the emulator had silently
lost DNS**: it had been up for eight days, its resolvers are fixed at launch
from the host's, and every cloud call was failing with *"Unable to resolve
host"* — indistinguishable from a broken feature, and now a line in CLAUDE.md.

### The sitting before — 12 August 2026 (forty-first sitting): old rides condensed to an outline, and the outline says so on every surface it reaches

**The inbox was empty and the top of *What to do next* was still the five things
needing the owner or the bike, so the brief — continue, no owner, no bike —
pointed at item 9: 23.4 retention, the thing the owner asked for on 3 August and
the only substantial item with nothing in software in front of it.** Both
prerequisites landed in the two sittings before (16.3.3a's efforts, 23.4.12's
provenance), so this is the trimmer itself: **23.4.2, 23.4.3, 23.4.4, 23.4.5,
23.4.6 and 23.4.10**, ticked. **728 JVM tests and 94 instrumented, 0 failures**,
and everything below was watched on the 1280 × 720 dp AVD.

**Nothing is averaged, and that is the design rather than a detail.**
`MetricTrim` keeps the **lowest and highest watt of every ten seconds as real
rows**, so a 25-minute ride of 1,500 samples comes back as 300 — five-fold, not
the item's estimated thirty, because thirty was the figure for a trace of means.
A mean is the one thing a trimmer must not write: a chart redraws and a trim does
not, so an average written back over the samples is a number the bike never
measured, filed permanently where the measurements used to be. The 612 W second
in the ride that was watched is still 612 W afterwards, and the first and last
seconds are kept because they are the axis.

**The half the item under-read is that two charts are *counts of seconds*, not
lines through them.** Recomputing time in zone or the cadence spread from a fifth
of the rows says a 25-minute ride pedalled for five — wrong in the way this
project keeps meeting, which is that nothing about it looks wrong. So
`workouts.distributions_json` holds what the seconds counted, written at the one
moment they are still there: 7.8, 21.2.3, 16.3.3a and 23.4.12 for the fifth time.
The measurement is the zone table read **before** the trim — *Z2 00:31, Z3 07:59,
Z4 07:44, Z5 07:30, Z6 01:15, Z7 00:01* — and read again afterwards, identical,
under a caption saying it was counted before the ride was condensed.

**Three defects the AVD found that reading the diff would not have.** The dialog
offering *After 6 months* said *"nothing is old enough yet"* about a
fourteen-month-old ride, because the counts were computed for the rider's
*current* setting — `Never`, which is what everybody has the first time they see
it. **The figure went up after condensing**, 436 kB to 782 kB with 1,200 samples
gone, because `VACUUM` in WAL mode writes the rewritten database through the log
and the first measurement counts those pages twice; with a checkpoint it is
432 kB down to 368 kB. And the snackbar said *"Trimmed"* on a screen that says
*condense* on the chip, in the heading and on the button — 11.6.5's HUD/overlay
mistake in miniature.

**23.4.10 is closed by choosing the first of its two options rather than by
building both.** The offline-safe half is what exists, and the dialog says which
rider it is talking to: *"this tablet is the only copy of it"* for an offline
rider, *"your account has a copy of the rides it has taken, but Pelonot cannot
yet bring one back down to the bike"* for a signed-in one. Nothing in
`RetentionRepository` is called a cache. **23.4.13** is the rehydration item that
falls out of saying so.

**And 23.4.1 no longer needs adb.** Settings → Storage answers its three
questions in one line — *2 rides · 432 kB* — because there is no honest way to
offer condensing without first saying what there is to condense. The trip to the
bike is now *looking at a screen*, and it is still owed: the one thing an AVD
cannot contradict is the model, since its database is one this session seeded.

### What to do next, in order

**The owner's inbox is still empty. The top of this list is unchanged for the
eighth sitting running, because none of it is work: what stands between the QR
fix and the owner is one deploy, and what stands between the *journey* and
anybody is the mailer.** What moved this sitting is **the way out of the
cloud**: 15.4.1, 15.4.2 and 15.4.4 are built and watched against the real
endpoint, so a rider can now take their copy back down as well as put it up.
**15.4.3 — deleting the account itself — is item 10 below, and it is a deploy
rather than a decision**, which makes it the second thing on this list waiting
on the same one-line habit 17.16.2 keeps asking for.

1. **Redeploy the web app — `link.js` and `link.html` carry 15.6.14's fix and
   17.16.9's voice, and reach nobody until they are pushed.** The owner
   redeployed at the start of this sitting and `./web/check-deployed.sh`
   reported seven files the same; this sitting has drifted two of them on
   purpose, and one of the changes is the fix for the fault they reported.
   17.16.9 was done in the same change deliberately, so this is **one deploy
   for both** — which is the whole reason it jumped the queue. **17.16.2 is
   what makes this a recurring line in this section**: the deploy command is
   still written down nowhere, and it has now delayed three fixes.
2. **15.7.7 — the mailer is the thing in the way, and it is the owner's.**
   Two confirmation emails an hour through a sender meant for testing is not a
   flow anybody can finish, let alone test repeatedly, and it is what
   *"nothing happened"* looks like from the rider's side. Two ways forward and
   both need the owner: **custom SMTP** (15.7.3, a domain and credentials), or
   **turning confirmation off deliberately** (15.7.8, which trades against
   18.11.1's reason for leaving sign-up open). Until then, **`+` addressing
   solves running out of addresses** and needs nothing deleted — worth trying
   before anything is removed from the live project.
3. **15.7.6 and 15.6.11 both still want one real sign-up.** The settings are
   measured against the link `generate_link` mints and the *linked* panel is
   built and observed, but its trigger has never fired. One completed sign-up
   on a real phone closes both, and 15.6.14's fix is what should now let it
   happen.
4. **19.1.4 — CI on every PR.** Written and never yet green. One run on GitHub
   ticks it, and it is still the cheapest item in the plan.
5. **20.5.2 — should the estimate show its working?** Written up with the
   argument on both sides and deliberately not built: naming the *values* of
   the three inputs would catch a mistyped weight on the last screen that
   could, and it is also three numbers on a screen whose whole job is to say
   one. **It wants the owner's eye, not more reasoning.**
6. **22.2.5 — every Phase 22 change is owed a look on the bike, and 23.4.1 now
   wants the same trip.** The whole of Phase 22 is measured on the 1280 × 720 dp
   AVD, which is the right geometry and the wrong furniture: the tablet has a
   48 dp bottom navigation bar and no top status bar (`HARDWARE.md`). The
   arithmetic says the rebuilt dashboard is comfortable there — 609 dp of
   content against 672 dp, where the AVD gives 664 — but **arithmetic is what
   this item exists to distrust.** Neither needs anybody to pedal: **23.4.1 is
   three queries** — `SELECT COUNT(*) FROM workout_metrics`, the size of
   `pelonot_database`, and how many rides are on it — and everything in
   retention is sized off a model a real tablet can contradict.
7. **22.8.11's rule is built and is the owner's to overrule, not to make.**
   The dashboard now names a class, and the rule behind it was decided in a
   session rather than asked for: *something the length you usually ride, that
   you have ridden least — and an easy one if you rode hard in the last day*.
   It is written out with the argument at 22.8.11 and every claim it makes is
   an observation about the rider's own history rather than coaching. **What
   would be worth the owner's eye is one look at what it offers them** on a
   profile with real riding on it, which is 22.2.5's trip anyway. 22.8.12 is
   the one open corner and is deliberately unbuilt.
8. **22.8.7 — the social feed — is the owner's own *"maybe"*, and it should
   stay a maybe.** It is written up with the two rules it
   cannot break (the household tier is a Room query and never touches the
   network; the first screen must not wait on one) and with the tension named:
   **a feed is a thing you scroll, on a screen whose complaint was that it
   scrolled.** The honest shape is three lines and a door, not a timeline.
9. **What is left of 23.4, and none of it is the trimmer.** **23.4.1** is item
   6's trip and now needs no adb — Settings → Storage says *"N rides · X MB"*
   on the tablet itself, and the thing an AVD cannot check is precisely the
   model that everything here was sized off. **23.4.7** is the cloud
   counterpart, **23.4.11** is the question it forces about other people's data
   on a shared endpoint, and **23.4.13** is bringing a condensed ride's seconds
   back down for a rider with an account — deliberately its own item, because it
   is a download rather than a retention policy, and it changes one sentence in
   one dialog rather than unblocking anything. Two readers still degrade under a
   trim exactly as 23.4.9 said they would: a condensed rival draws no live ghost
   (24.3.3) and the maximum-heart-rate suggestion in Settings drops (21.1.3).
   Both are written down there rather than fixed, and both are honest losses
   rather than wrong numbers.
10. **15.4.3 — deleting the account itself — needs one deploy and nothing
    else.** It is designed at 15.4.3 with the reasoning: Supabase has no
    user-initiated self-delete, so it is an Edge Function taking the rider's own
    JWT, the same shape and the same one-line deploy as
    `supabase/functions/link-device`. It was deliberately **not** written
    speculatively, because an app button calling a function nobody has deployed
    fails in the way that reads exactly like a broken feature. The local half of
    it already exists and is watched — 15.4.2 does precisely the same thing to
    the tablet, and the two must share one implementation rather than two copies
    of the rule.

**Left deliberately undone, and both are written up:** 24.3.18d's *mark* — the
moment is built, a permanent marker on a beaten row is not — and 11.8.4, the
subtitle line, which is answered with a recommendation (build it as captions for
the coach that already exists, not as a slot that must be filled) rather than
built.

**24.3.16 is open, is not simply *unbuilt*, and its measurement has been
corrected.** The overlay's leaderboard was built in the thirty-first sitting
and removed on the owner's word: *"there is simply no room for the leaderboard
on that HUD… it shouldn't feel so crowded. Particularly the power numbers it's
all crammed in and clipping."* **That decision stands.** What does not stand is
the number under it: the four readouts were found clipping in this sitting with
no race chip on the band at all, because `MetricReadout` measured the value
before the unit. That is fixed, so *"there is no width that buys a fifth chip
on that row"* was measured against a row already over-committed by a bug and
should not be read as a measurement. **The open question is still where on the
overlay a race could live that is not the chip row** — but how much room that
row really has is now genuinely unknown rather than known to be none.

**Two things need the owner rather than a session:**

- **24.3.12a**, what the rows on the leaderboard should be called. They asked
  for it that way — *"Maybe put that as an action on me… You can remind me at a
  later date if i haven't decided yet"* — so this is the reminder, and
  **24.3.17c has made it more pressing**: with the rank gone, the name is the
  only identity a row has. `12 months` and `30 days` are placeholders and the
  owner has said the first is *"no good at all"*. The reason they are wrong is
  nameable: every other row on the board is a *person* and those two are
  *durations*.
- **The board watched moving under somebody actually pedalling.** Everything
  about the race has been seen on a simulated ride or on a still bike. That
  needs a rider, and CLAUDE.md is right that it is a perishable resource.

**Already done and not to be re-picked:**
- ~~**15.4.1, 15.4.2, 15.4.4 — the way out of the cloud.**~~ **Done in the
  forty-second sitting**, watched against the real endpoint with a throwaway
  admin-made account, and with signing back in as the control. **Do not make
  deleting the cloud copy leave the account attached** — the argument is
  mechanical and is written at 15.4.2 and in `AccountRepository
  .deleteCloudData`: `synced_at` would record a backup that no longer exists,
  and the trimmer reads that column as permission (23.4.6). Do not clear
  `synced_at` on *sign-out* either; only attaching an account does that, and
  after a sign-out those rides really are still up there. And do not soften the
  dialog's third sentence into a claim — the tablet knows the cloud **took** a
  condensed ride and cannot know whether what it took was intact, which is
  23.4.13's job.
- ~~**23.4.2–23.4.6, 23.4.10 — the trimmer.**~~ **Done in the forty-first
  sitting**, watched on the tablet AVD with the untrimmed ride as its own
  control. Do not replace the min/max sampling with means to hit the item's
  "~30×" — that number was for a trace of means, and a mean written back over
  the samples is a number the bike never measured filed where the measurements
  were; five-fold is the measured cost of keeping the peaks and it is the right
  trade. Do not recompute time in zone or the cadence spread from a condensed
  ride: they are counts of seconds and `distributions_json` is the answer. Do
  not give `metrics_detail_sec` a default of 1 in a migration — null means *the
  record is intact* and a guess there is the one thing 23.4.3 exists to prevent.
  And do not offer rehydration in words before 23.4.13 builds it.
- ~~**23.4.12 — a ride's power provenance written on the ride.**~~ **Done in the
  fortieth sitting**, verified as an *upgrade* with the previous build as the
  control in all four cells: untrimmed and trimmed, before and after. Do not
  put the backfill in the migration — 23.4.12's write-up says why the item's own
  guess about that was wrong, and the reason a re-runnable pass is better is the
  interrupted finalise rather than SQL's limits. Do not reintroduce the
  `EXISTS`/`NOT EXISTS` pair anywhere: seven queries used to spell that rule out
  for themselves and 22.1.7 is what happens when one of them gets it half right.
  `PowerProvenance` **is** still counted from samples in two places on purpose —
  `WorkoutService` mid-ride, where the row has no answer yet, and as the fallback
  for a row with no provenance on it.
- ~~**16.3.3a, 23.4.8, 23.4.9 — a ride's efforts kept rather than re-derived,
  and the audit that goes with them.**~~ **Done in the thirty-ninth sitting**,
  verified as an *upgrade* on the tablet AVD: a v17 database read on the
  previous build as the control, migrated, and drawn unchanged. Do not re-open
  the "wait until somebody feels it be slow" argument — 23.4.8 replaced it, and
  the measurement is that the build before this one loses all four bests on
  trimmed data and blames the wrong ride. **The backfill is deliberately not in
  the migration** and should not be moved there. What 23.4.9 opened is 23.4.12,
  and that is item 9 above rather than part of this.
- ~~**19.1.3a — restore refusing the app's own backups.**~~ **Done in the
  thirty-ninth sitting.** The lesson is the fix's shape rather than the bug: a
  number kept equal to another number by a comment is the mechanism, so the
  constant is gone rather than corrected. Do not reintroduce a `SCHEMA_VERSION`.
- ~~**22.8.6, 22.8.11 — the class the dashboard offers.**~~ **Done in the
  thirty-eighth sitting**, all four branches observed on the tablet AVD. The
  rule is at 22.8.11 with the reasoning attached; do not re-derive it, and do
  not grow it into a training plan — the refusals are the design. It cost
  nothing vertically on purpose, so **do not "make room" for it**: the row is
  the row 22.8.1 built. 22.8.12 is the only open corner and is deliberately
  unbuilt until a real riding pattern can judge it.
- ~~**22.8.1–22.8.5, 22.8.9, 22.8.10 — the dashboard's vertical axis.**~~
  **Done in the thirty-seventh sitting**, all observed on the tablet AVD:
  993 dp → 609 dp, Begin Class primary, the household beside the rider's own
  cards rather than below the fold. Do not re-open the layout to "add more
  above the fold" — **the screen was loose, not full**, and what is left below
  the cards is deliberate emptiness under 22.2.3's rule. 22.8.6 is the item
  that decides what goes in it, and its first candidate needs the owner.
  Two things found on the way that nobody had reported: `WideRow`'s narrow
  branch never stacked (22.8.10), and the FTP definition was setting the height
  of a whole row while *Your FTP* never spelled the acronym out at all.
- ~~**24.3.7a — the board narrowing, watched.**~~ **Done in the thirty-seventh
  sitting.** A simulated ride of `END-01` draws a board of nothing but
  generated targets with none of its thirteen real riders on it, and `YOU`
  climbs from fourth to third while it runs. Corroborated at 156 of 156
  modelled samples. The log line is guarded and correctly says nothing.
- ~~**15.6.14, 15.6.15 — the pairing page's deadlock, and the code by
  default.**~~ **Done in the thirty-fifth sitting.** The three dots were an
  auth-lock deadlock caused by calling Supabase from inside
  `onAuthStateChange`, reproduced on the live page with a stored-session
  fixture and confirmed by `navigator.locks.query()`. Do not re-diagnose it
  from the symptom: a signed-*out* phone never reproduces it. Two more on the
  same page — an auth fragment read as a pairing code, and a `describe()` that
  could wait for ever — and the lesson from the second attempt at the timeout
  is that **`client.rpc()` is a thenable with no `.catch`**.
- ~~**20.4.5, 15.6.13, 20.4.6, 15.7.6, 17.16.4, 15.6.11, 20.5.1 — the whole
  first-run journey.**~~ **Done in the thirty-fourth sitting**, all observed on
  the tablet AVD except 15.6.11's trigger, which needs a real sign-in. Two
  faults were found that nobody reported and both are worse than what was:
  a **pairing code outliving the screen showing it**, so a second new profile
  was offered the first rider's live QR under the first rider's id (15.6.13);
  and **profile creation writing `weight_kg` with no fence** while Settings has
  had one since 13.8 (20.5.1). The lesson to keep from 20.4.5 is that copying
  Material's *numbers* is not the same as calling Material's component.
- ~~**20.4, 22.7.5, 23.2.7, 11.8, 24.3.18d — the first-ride pass.**~~ **Done in
  the thirty-third sitting**, all observed on the tablet AVD. Two faults on that
  path were found that nobody had reported (20.4.2's dialog insets, 20.4.4's
  account offer), and both were invisible until the AVD's hardware keyboard was
  turned off — that is now a line in CLAUDE.md.
- ~~**The polish pass and the demo recording.**~~ **Done in the thirty-second
  sitting**, nine changes, all observed on the tablet AVD. Do not re-attempt
  the summary's equal-height row: it was diagnosed as clipping, rewritten with
  a `SubcomposeLayout`, found not to be broken at all — it was the scroll
  viewport — and reverted.
- ~~**24.3.17, 11.6.16–11.6.19, 24.1.8, 22.7.3 — the owner's four notes.**~~
  **Done in the thirty-first sitting**, all seven built items observed on the
  tablet AVD. [LEADERBOARD.md](LEADERBOARD.md) is updated to match.
- ~~**24.3.10–24.3.14 — the live leaderboard.**~~ **Done in the thirtieth
  sitting**, and described in plain English in
  [LEADERBOARD.md](LEADERBOARD.md).
- ~~**11.7 — one instruction at a time.**~~ **Done in the twenty-ninth
  sitting**, all five items. What is owed is the spoken half, which is the
  rider's to confirm.
- ~~**24.3.3–24.3.9 — the live ghost.**~~ **Done in the twenty-eighth
  sitting**, and **superseded by the leaderboard** (24.3.11): the picker and
  the single-gap card are behind `RIVAL_GHOST`, off. Nothing under it is
  wasted — almost all of it is still on the live path. `RIVALS.md` describes
  it and says which flag turns it back on.

**26.4 and Phase 27 are the two to leave.** The owner offered to leave 26.4 —
*"happy to leave it"* — and the honest answer is that a "score" built on FTP is
26.1.1's defect with the unit filed off. Phase 27 is the owner's own weighting:
*"definitely nice-to-have and low priority for now"*, and it is written down at
full length so it can be built well later rather than quickly now.

**The road to online — settled in the eighteenth sitting.** The owner's stated
destination is accounts, friends, a leaderboard and a companion web app. Those
are Phases **15**, **18** and **17**, and they were already in the plan. What
was *not* written down is the order and what has to be true first, so it is
here:

| # | What | Why it is where it is |
|---|------|----------------------|
| 1 | ~~**14.2.1** identity, **14.2.4–14.2.6** the backlog~~ | **Done.** Both are schema shapes that are free while the cloud is empty and a migration-with-backfill once four riders have a year up there. Everything below writes rows; these decide whose they are and whether losing one is noticed |
| 2 | ~~**14.2.1a** apply `003`, **15.5.4** verify it from a second account~~ | **Both done.** `003`, `004`, `005`, `006` and `007` are applied, and 15.5.4 was checked the way this row asked for — from a **second real account**, 21 probes, 0 failures, scripted as `supabase/verify_rls.py` so it is repeatable rather than a thing a person once sat and did. Original reasoning: | The only step in this list where being wrong is a **breach** rather than a bug. Nothing else may go online first, and "the SQL looks right" is not the check. **The wipe is authorised** (owner, 3 Aug); run `003` *before* 15.1, not after. And the endpoint now has friends on it, so this is other people's data |
| 3 | 🔶 **15.1** auth, **15.2** identity, ~~**15.3.1** the drain~~ | **Built and seen making the round trip** — three rides arrived attributed, and were read back in the web app rather than in a query (14.1.6, closed after nineteen sittings open). What is *not* built is the other direction and the exits: pull to a new device (15.3.2), sign out (15.4.1), delete the cloud copy (15.4.2) and delete the account (15.4.3) | The phase that unlocks everything. 15.3.1 is mostly built already — it is 14.2.6's drain with a sign-in trigger on it |
| 4 | ~~**14.2.3** sync state in Settings~~ | **Done in the same sitting**, because it belongs before riders trust the thing rather than after. Two of three states seen on the AVD; the failing one is tested and will be seen for free the first time 14.2.1a's endpoint refuses something |
| 4a | ~~**17.1**, **17.13** the web app's first page, **15.6** signing in by QR~~ | **Done, and hosted since 4 August (17.16)** — the owner's note put it at a URL, and the fragment-carrying redirect and the live `device_link_describe` were both measured against it. It did exactly what this row predicted: the round trip was *seen* in the web app, not queried. Original reasoning: | **Moved up from row 6 by the owner's note of 3 August**, and it is not a reshuffle of priorities so much as a correction: the web app was filed as a *destination* when it is also **the only way either of us can see what the bike actually put in the cloud** without writing SQL. Every cloud defect this project has had was found by query rather than by looking. The QR flow (15.6) needs a page to point at, which makes `link.html` the first thing built and the rest of 17 an easy follow-on |
| 5 | **20.3** the initial FTP, ~~**22.4** use the width~~ **Done in the twenty-third sitting** — the owner's rule settled it (*use the full width, no one card takes it all*) and seven screens were audited against it | The owner's own two, and 20.3's own words are that the current shape **cannot go into production**. Onboarding is the first thing a new rider meets and the online tier is what brings new riders. **20.3.9 changed its cost**: date of birth is already on the profile, so Route B is one extra question rather than two, and the same screen answers 21.1.6 |
| 6 | 🔶 **17** the rest of the web app, **18** social across bikes | **18's leaderboard landed early and by itself** (18.5, 18.9, 18.11) because the owner's note removed the friend graph it was waiting on. The rest stands as written | In that order: the bike's tablet is a bad place to type, so the web app is the natural home for friend requests and display names, and 18 is those features arriving back on the bike. **Read Phase 24 first** — the household half is built, needs no account from anybody, and 18.9 says every screen here is built *on top of* its 24 equivalent rather than beside it |

Two things that are **not** blockers and were checked rather than assumed: the
payload format is settled and versioned inside itself (14.4, incl. 14.4.7's
provenance), so 17.3 has something stable to read; and household social (24) is
complete enough that 18 has a floor to build on.

**14.10.4 was that list's one owner-blocker and it is now answered.** There is
no community endpoint: this build points at the owner's household project
through env vars, with one or two friends on it, and volume is not a concern at
that scale. `cloud.properties` still ships empty and `CloudConfigFenceTest`
still keeps it that way — the reason is now that the endpoint is *private*
rather than that the decision is *pending*. **Retention (23.4) came with the
answer** and is real work now rather than deferred; read 23.4.8 before starting
it, because trimming silently degrades personal bests until 16.3.3a lands.

---

**Everything below is reordered by the first real ride, 1 August 2026.** The
triage is the owner's snag list plus what the ride measured.

**Still first, but no longer stop-everything — the fence and the watchdog
landed in the tenth sitting and nothing impossible reaches the record now:**

| Next | Why now |
|------|---------|
| ~~**2.7.1b** Which side mislabels the stream~~ | **Done — root cause found and fixed, verified on the bike.** `msg.what` is assigned by position and slides; the frame in `responseHexString` is self-identifying and now decides the metric. Read **2.7c**. What is left of 2.7 is 2.7.7 / 2.7.8, from the serial-port leak underneath it (2.7d) |
| ~~**2.7.5** What to do about the rides already recorded~~ | **Done and observed on the bike's own database.** Marked, not rewritten: three corrupted rides found, the fourth (post-fix) clean. Read 2.7.5 |
| ~~**2.7.3** A plausibility fence~~ | Done and observed: 0 impossible values in 188 recorded samples across a ride carrying 30 s of the bike's own corruption signature |
| ~~**2.7.4** Telemetry dies and never recovers~~ | Done and observed: silence is an `IOException` now, the one retry policy rebuilds the source, and the ride picked up again at 122 s with no app restart |

**Then the ride-screen snags, which are what the owner actually feels:**

| Next | Why now |
|------|---------|
| ~~**11.6.7** The numbers change too fast to read~~ | **Done and observed.** `SensorRepository.displayReading`, 2 Hz, both surfaces, recorder untouched |
| ~~**11.6.8** The zone ladder shifts sideways~~ | **Done and observed.** Not the border — the zone name and the FTP percentage sized themselves to their own text. Both now reserve their widest string |
| ~~**11.6.9 / 11.6.10** The heart-rate dead end, and Settings mid-ride~~ | **Done and observed.** A sheet over the ride, a gear beside the telemetry chip, and the overlay's way in inside the volume panel. One snag found and left open — see the end of 11.6.10 |
| ~~**16.1.7 / 16.1.8** Axes on the charts~~ | **Done and observed.** `ChartScale` picks the round numbers, `ChartFrame` draws them for every trace |
| ~~**13.8** kg/lb at signup~~ | **Done and observed.** The old dialog stored a 77 kg rider as 34.9 kg |

**Then the substantial ones:**

| Next | Why now |
|------|---------|
| ~~**23.2.6** Rebuild the class library~~ | **Done and observed.** Designed rather than sliced: 20 block lengths against 101, 51 zone sequences against 12, cadence a real second axis. New id series, old ones retired rather than deleted (23.2.6c). Read `classlibrary/README.md` before touching a class |
| ~~**22.2.6** Width cap as a rule~~ | **Done and observed.** `Layout.readableWidth` + `Modifier.readableColumn()`, applied to Settings, History, ride detail and the class library |
| ~~**25.3** The overlay's stand/sit cue~~ | **Built and observed on the AVD.** Amber lozenge, arrow travelling in the direction of the instruction, six seconds and gone; driven by `PositionCallTracker`, which the spoken coach now asks too. **25.3.4 is still open and needs the owner** — how it reads over a *film* cannot be screenshotted (`FLAG_SECURE`) |
| ~~**25.4.1** Positions across the rest of the library~~ | **Done — an audit, and one block wanted one.** It also turned up 25.4.2, below |
| ~~**25.4.2** Three classes named after a position they cannot state~~ | **Done — the owner chose renaming, and it was four classes, not three.** R11's cap is untouched; `END-08`, `END-12`, `SWT-05` and `THR-06` are named off the axis the data carries. The rule it leaves behind is in `classlibrary/README.md` under R10: **a position word in a title is a promise that the blocks say it too**, and "big gear" is a position word. It opened **25.4.3** |
| ~~**24.2** The household, seen~~ | **Done and observed**, opt-out included |
| ~~**24.3.1** Riding against a housemate~~ | **Done and observed.** One query and no schema, as advertised. **24.3.2** — the live pace target *during* a ride — is the interesting half and is still open; read 11.6 first |
| ~~**7.8 / 7.9** The FTP a ride was ridden at, and FTP history~~ | **Both done and observed.** `workouts.ftp_watts` (migration 6→7) and `ftp_history` (7→8, seeded from the profiles that already exist). 16.3 is unblocked. Between them they found a live bug: **saving your FTP in Settings put the old one back**, invisibly, because a second coroutine carried a stale copy past it |
| ~~**16.3.1 / 7.10.1** The full FTP trend~~ | **Done and observed.** The screen it wanted exists — *Your FTP*, off the dashboard's card — with a mark per change that says whether the app measured it or the rider claimed it, and the ride behind each one a tap away. Read 7.10.1 for why the axis runs to *now* |
| **23.2.3 / 23.2.4** The class library as an update channel | The only remaining reason to read the cloud at all. Additive only — deleting a class takes a rider's history link with it |
| ~~**14.4** The payload format~~ | **Done, and the numbers are measured now rather than modelled: 228 KB → 49 KB** per ride, asserted in the round trip against the old shape built from the same samples. It also settled **14.4.6** off the bike's own database with no rider: the board's fractional power is real data, and the float-widening noise it feared went away with 2.7c. 14.4.5 still wants the cloud trip; **14.4.7** is new — the payload drops `power_is_measured` |
| ~~**23.3.1** The backup reminder~~ | **Done and observed.** Ten rides, counted across the whole tablet because the file is; "Not now" moves the line rather than silencing it; and the mark is written only when a backup actually succeeds. **23.3.1a** is new and belongs to Phase 15: cloud backup is per profile and the file is per tablet |

| ~~**14.4.7** The payload's last missing field~~ | **Done.** `pm`, per sample rather than a scalar on the row, because `PowerProvenance` reduces the samples to one answer *from* them and `Mixed` is samples disagreeing. `CompactBoolean` writes 1/0, which is what makes a per-sample column affordable: 55,635 bytes a 45-minute ride, budget 56 → 60 KB |
| ~~**25.4.3** The two near-twin classes~~ | **Done, and as `SWT-13` rather than an edited `SWT-05`** — changing what an id *is* while a ride points at it rewrites what that ride was, which is why 23.2.6 took a new series. Titles now separate them from the library list: *4-5-6* against *4×4* |
| ~~**14.10 / 14.11.3** What a fresh clone finds~~ | **Done.** `cloud.properties`, checked in and empty; precedence env → `local.properties` → it → offline; `CloudConfigFenceTest` fails the build if a key lands in it or a third `secret()` call appears. 14.10.4 stays open because the *decision* is 15.5's, but it is now fenced rather than remembered |
| ~~**16.1.5a** The prescribed cadence~~ | **Done and observed.** A cadence trace with the class's rpm blocks under it, and a compliance count of its own — the fixture ride is 63% on power and 0% on cadence, which is the case the item existed for |
| ~~**16.3.2 / 16.3.5** Weekly volume, and the calendar~~ | **Done and observed**, on one screen — *Your riding*, off a **This Week** card. It also settles where a trend lives: *Your FTP* is about the rider, this is about the riding |
| ~~**16.3.4** This ride against your previous best~~ | **Done and observed.** In the housemates' own picker, drawn in the power colour because it is still you. Previous best, not best-ever |
| ~~**16.3.3** Personal bests by duration~~ | **Done and observed — and it finishes Phase 16.** Mean-maximal power on *Your FTP*, measured rides only, and a gap breaks the window. **16.3.3a** carries what is left: the scan is instant now and has a ceiling |
| **19.1.4** CI on every PR | **Written, not yet green.** `.github/workflows/ci.yml` — build then the JVM tests, no secret (offline-first is the reason), no instrumented suite (order-dependent). One green run on GitHub ticks it |
| ~~**14.2.1** Who a cloud row belongs to~~ | **Done in the app.** Two holes: every ride ever uploaded arrived anonymous, and `profiles` was keyed by a per-device autoincrement so the **second bike to sign in would have overwritten the first rider's profile**. `profiles.id` **is** the auth user id now — 1:1 with an account by construction, so no cloud id has to be read back and stored. **14.2.1a** carries applying the SQL |
| ~~**14.2.3** Sync state in Settings~~ | **Done and observed on the tablet AVD** — *"3 rides waiting to go up since Jul 23"* and *"Nothing is waiting to go up"*. `SyncOutcome.Failed` no longer dies in a `Log.w`, which is how three cloud defects survived the project's whole history. What is true lives in `CloudSyncStatus`, pure and tested; the AVD changed one sentence — *"No rides have gone up yet"* is a claim the app cannot support, because that state is also a restored backup |
| ~~**14.2.4 / 14.2.5 / 14.2.6** The backlog~~ | **Done and observed on the tablet AVD.** `workouts.synced_at` (migration 9 → 10), not backfilled; the worker drains a profile's backlog oldest-first instead of posting one ride, so **a ride that exhausts its retries is not lost**. It is also 15.3.1's backfill, one implementation rather than two |

| ~~**7.10.4 / 7.10.5** The two halves of not editing the rider's record behind them~~ | **Both done and observed.** A declined breakthrough is written down on the ride (migration 8→9) instead of forgotten when the screen closes, and an accepted one can be put back in one action that **appends** a row — `AutoBreakthroughReverted`, its own source, because "I set this" and "the app was wrong" are different events |

| ~~**8.3d** Resume an interrupted ride~~ | **Done and observed over two resumes of one ride.** The owner's, and it contested 8.3a — whose reasoning did not survive: the gap it called unknown is arithmetic, and `elapsedSeconds()` has excluded paused time since Phase 3, so **a crash is a pause nobody got to press**. 8.3a's concern is kept as 8.3d.2 rather than discarded. It also turned up the rule now in CLAUDE.md: **the finalise writes defaults over any column `WorkoutSession` does not carry** |

**The twentieth sitting's own, four closed and one opened into a phase:**

| Next | Why now |
|------|---------|
| ~~**11.6.11** The zone ladder's elastic bounce~~ | **Done and observed.** The bug was the quantity being animated, not the animation: one continuous `ladderPosition` across all seven rungs, monotonic in power, swept at every watt from 1 to 400 |
| ~~**11.6.12** Whole watts and kilojoules~~ | **Done, and the snag was measured**: the OUTPUT tile was rendering `63.` with the tenth clipped off. The one decimal left is kJ/kg, where rounding would tie two housemates |
| ~~**11.6.13** A countdown before the ride~~ | **Done and observed.** A gate in front of `startRide`, not a curtain over a running ride — otherwise it moves the defect rather than fixing it. Ten seconds, skippable, and a resume skips it |
| ~~**21.3.4** The heart beats~~ | **Done and measured** — the glyph swells 110 → 132 px and rests between beats. The period is re-read at the top of each beat, and it stops dead when the reading does |
| ~~**21.1 / 21.2 / 21.3.1** Heart-rate zones~~ | **Phase 21 opens, and the owner's colour ask lands.** The honest answer to *"pretty sure this is already covered"* was no: the app had no maximum heart rate for anybody. Measured number first, Tanaka as the fallback, migration 11 → 12 with both columns nullable — a default maximum is a guess about a body. **21.2.3 is the one to read before 21.4.2**: nothing yet draws an HR zone for a *past* ride, which is the only reason the 7.8 trap has not bitten |

**Two of the owner's own are open, and both want the owner rather than a
session:**

| Next | Why now |
|------|---------|
| **11.1b.10** The grey line on the overlay | Diagnosed, not decided. One of three candidate fixes, and picking is the owner's call — read the item |
| ~~**24.3.14** What the leaderboard's score is~~ | **Answered this sitting: total kilojoules for the class.** Cumulative, so nothing reopens. The metric-agnostic shape they asked for alongside it is built (24.3.14a) and the toggle is queued as 24.3.15 |
| ~~**11.7.2 / 11.7.1a** Which metric governs, and the amber~~ | **Both done and observed.** The owner chose (b) — name it — and it is `governed_by` in the catalogue, seeded off the cadence intent. 231 blocks of 1071. Amber, the arrow, the `TARGET` line and the spoken cue now belong to the governing metric alone, and resistance has no band at all |

Then the table below, which was written before the fifth sitting and is kept
because its reasoning is still good:

| Next | Why now |
|------|---------|
| ~~**14.1.6** Finish the cloud round trip~~ | **Done in the twenty-second sitting, nineteen sittings after it opened.** It was blocked deliberately and correctly: nothing set `auth_user_id`, so every profile was offline and every cloud method returned `Disabled`, and `003` had revoked the anon key besides — the sighting genuinely needed Phase 15. A tablet signed itself in by QR, the drain fired, three rides arrived attributed, and they were read back **in the web app** rather than in a query, which is the version of this a person can have |
| **11.1b.3 / 11.1b.4** Resizing and side docking | The half of 11.1b still outstanding. Opacity and the two-band layout landed; a vertical dock down one side is probably the better default on a 16:9 tablet and needs a genuine re-flow, not a rotation |
| **11.2.2 / 11.2.3** Time in zone, and "ahead of your usual" | The two things still missing from the strip that are about the next sixty seconds |
| **11.1b.9** The chips as a piece of design | The HUD redesign is correct and not yet beautiful, and the owner has said he will come back to it. Read 11.1b.8 and 11.1b.4a first — they are the same conversation |

Still blocked on things not to hand: **10.6** needs a full-length ride, and the
four bike items listed above.

Two notes worth carrying into the next bike session:

- **The dashboard is fine in landscape** — re-confirmed on the matching AVD this
  session. It fills the width and shows none of the empty right-hand side
  11.3.1 describes. **11.3.1 is stale**; do not spend a session on it without
  re-checking first. (The sixth sitting then capped that width at 760 dp for a
  different reason — 22.2.1 — which does not contradict it: 11.3.1 is about
  dead space and 22.2.1 is about a card being too wide to read.)
- **Every ride is now a guest ride no longer**: the emulator has real profiles
  and the sync path runs for them, and **so does the bike — it has a `Simon`
  profile with six rides on it** as of the twentieth sitting, which corrects the
  older note here saying it had none. A guest ride still never syncs by design.

---

## Status at a glance

| Phase | Area | State |
|-------|------|-------|
| 0 | Scaffolding & build system | ✅ Complete |
| 1 | Local database (Room) + Supabase | 🔶 Room at schema version **20**, every step an explicit migration (12.5) with an exported schema and a `MigrationTest` each. The newest are 18 → 19, `workouts.power_provenance` (23.4.12), and 19 → 20, the trimmer's `metrics_detail_sec` and `distributions_json` (23.4.3). The class library is bundled, not fetched (23.2), and the cloud is gated behind an account — which Phase 15 now grants, so the gate is load-bearing rather than theoretical (23.1) |
| 2 | Telemetry engine (sensor service, BLE, simulated) | ✅ **2.7 solved and verified on the bike (2.7c).** The board's own frame decides the metric, so the service's positional `msg.what` can no longer mislabel anything; the raw-resistance intruder is dropped by identity. 1609 + 464 messages captured with zero mislabels, a recorded ride with zero impossible values and zero gaps. The three rides recorded before the fix are marked rather than rewritten (2.7.5). Open underneath it: the exclusive serial port leaks (2.7d → 2.7.7, 2.7.8) |
| 3 | Foreground service & workout lifecycle | ✅ Complete |
| 4 | Floating HUD overlay | ✅ **Exonerated.** It never corrupted anything: 464 messages captured with the overlay up and a rider pedalling, zero mislabels and zero dropouts (2.7c). What it correlated with was *leaving the app*, and on this tablet that can mean a second bike app taking the sensor's serial port (2.7d) |
| 5 | HUD Compose UI & power zones | ✅ Complete |
| 6 | Main app UI | ✅ Complete |
| 7 | Auto-FTP, workload JSON, cloud sync | 🔶 **Auto-FTP is upward-only, and the owner asked why (7.11).** Checked against the code rather than assumed: `PostWorkoutAnalyzer`'s gate is `proposal >= currentFtp × 1.02`, which cannot produce a downward number by construction, and `FtpChangeSource` has no case for an automatic decrease. `detectBiometricDecoupling` already looks for the *opposite* signal (low heart rate at threshold power, evidence FTP is too low) and is itself a complete no-op today — `maxHr` is one of three parameters the one production call site never passes. Written up rather than built: it needs a multi-ride trend rather than a per-ride check, unlike everything else in this phase, because a single hard-feeling ride is not reliable evidence of declining fitness the way a 20-minute peak is reliable evidence of a good one. See [AUTO_FTP.md](AUTO_FTP.md) for the full mechanism as it stands. Otherwise: detection, the update flow, the FTP a ride was ridden at (7.8), the history of every change (7.9), both ways of showing it — the dashboard card (7.10.2) and the full trend (7.10.1) — and both halves of *the app must not edit the rider's record behind them*: a declined breakthrough stays declined (7.10.5) and an accepted one can be put back in one action that appends rather than erases (7.10.4). A simulated ride cannot propose an FTP at all (7.10.7). Open otherwise only where it depends on phases that do not exist: the simulated-watts mark on the trend (7.10.6) and whether the history syncs (7.10.8, with 15) |
| 8 | Polish, testing, edge cases | 🔶 Functional items done; cosmetic backlog remains. **8.3d is closed: an interrupted ride can be resumed, not merely kept** — the owner asked for it and it contested 8.3a, whose reasoning did not survive being checked (the gap is arithmetic, and `timestamp_sec` has meant *seconds of riding* since Phase 3). The break is written down rather than smoothed over — `resume_count` / `interrupted_sec`, migration 10 → 11 — because a resumed series comes back contiguous and cannot show it. Observed on the tablet AVD over two resumes of one ride, with the series and the row's own averages cross-checked against the samples. It also found the defect in 8.3d.4 that **the finalise writes defaults over anything `WorkoutSession` does not carry**, which is now a rule in CLAUDE.md |
| 9 | Ride integration | ✅ Complete — a class runs |
| 10 | Hardware validation | 🔶 A **full 20-minute ride is done** — and it is what found 2.7. 10.6's remaining questions (battery, thermals, memory) are unanswered because the ride's telemetry was the story |
| 11 | **HUD-first experience — the current priority** | 🔶 **The ride screen's bottom row is a fixed point now (11.6.16–11.6.19).** The owner's three notes of 5 August, and two of them turned out to be one change: the rest of the class **scrolls** and holds every remaining block instead of three, and it is the weighted child of the effort column — so the countdown growing above it is paid for by the list getting shorter rather than by OUTPUT, DISTANCE and AVG POWER falling off the bottom in silence, which a `Column` does without complaining. `NextUpBlock` reserves the taller of its two states as well, measured rather than typed in. The totals **shrink rather than clip** (`ShrinkToFitText`, a `TextMeasurer` deciding the size once so a number changing twice a second does not pulse) — seen at four digits, `OUTPUT 1083 kJ` and `AVG POWER 1195 W`, on the same tile 11.6.12 caught rendering `63.`. And tapping the distance reads it the other way for one ride only, writing nothing, so Settings stays the single writer of the preference. **The line across the film is gone (11.1b.10)** — the owner reported the same hairline twice, once grey and once orange, which is the answer rather than two reports: a rule drawn edge to edge across somebody's film is a rule whatever colour it is. It still thickens and pulses before an interval change, which is the only part of it that was earning its place. **And the first ride nobody had watched is fixed (11.6.14)**: the overlay permission was raised by `startRide` on the far side of the countdown, so a rider's ten seconds of clipping in bought them a modal, a trip to Android's settings and a class already running. It is asked inside the countdown now and the count stops while the question is outstanding — including while the rider is away answering it, which the obvious implementation got wrong. 11.1 and 11.1a complete; volume (11.5) done. The HUD is now chips on a transparent band with the timeline on the opposite edge (11.1b.1, 11.1b.2, 11.1b.7); resizing and side docking (11.1b.3–11.1b.5) and the rest of 11.2 remain. **Three of the ride screen's own snags closed in the twentieth sitting**: the zone ladder is one continuous bar rather than seven that each bounce at their boundary (11.6.11), watts and kilojoules are whole numbers (11.6.12 — the tile was literally rendering `63.`), and a ride now starts on a ten-second countdown that sits **before** `startRide` rather than over a ride already running (11.6.13) |
| 12 | Ride history & the rider's own record | 🔶 **The summary and the record are one ride now (12.6)** — the owner asked whether they should be, and the answer was nearly yes with the difference unprincipled: charts were private to ride detail because 16.1 landed there first, so a rider who had just stopped pedalling got six figures and half a screen of black. One `RideChartsSection` and one `buildRideCharts` serve both, and that second extraction is the one that mattered — the rule deciding which FTP draws the zone bands (7.8) was inside a ViewModel, and a second copy is a second answer to the question this app has already got wrong once. **A ride ended by accident can be carried on (12.6.2)**: 8.3d's machinery had never met a *finished* ride, so the reopen now clears `is_complete` and `synced_at`, and it was checked in `sqlite3` rather than on screen because a resumed series comes back contiguous and cannot show any of it. **History's panels are centred where they do not fill the row (22.7.1)**, which is 22.5's assumption arriving on a second screen: at one ride a week most days hold exactly one ride. History, detail, delete and migrations done; export and housekeeping remain. **Both screens were rebuilt for the panel in the twenty-third sitting**: history is a two-across grid with the day headings still spanning it, and ride detail is one row of six figures with the charts two-up behind them (22.4.2, 22.4.3). The owner found the regression on the way — the charts had not disappeared, they had been pushed below the fold by a figures grid inside a 760 dp cap |
| 13 | Units and display preferences | ✅ Complete — miles, and the locale default that goes with them |
| 14 | Cloud sync that actually reaches the cloud | 🔶 **A row knows whose it is now (14.2.1)** — every ride the app ever uploaded arrived anonymous, and `profiles` was keyed by a per-device autoincrement, so the second bike to sign in would have overwritten the first rider's profile rather than creating its own. `profiles.id` **is** the auth user id; `CloudAccess.accountIdFor` answers the gate and the identity in one lookup because they are one question. **And the app knows what it has not backed up (14.2.4–14.2.6)**: `synced_at`, not backfilled, with the worker draining a profile's backlog oldest-first so a ride that exhausts its retries is still in the queue rather than lost. **14.2.1a is applied and 14.1.6 is finally closed** — after nineteen sittings open, a signed-in tablet drained its backlog and three rides arrived attributed (332, 50 and 1185 samples, `v=1`, 47,890 bytes for the twenty-minute one), read back in the web app rather than in a query. **Four defects were found on the way and not one was catchable by a test**: the cloud's class library and the bundled one were different libraries, so no ride against any bundled class could ever have been backed up (14.2.9); one unacceptable row blocked every ride behind it for ever (14.2.7); nothing drained the backlog on launch (14.2.10); and the payload's `v` never travelled, because `encodeDefaults` is off in production and was on in the tests (14.4.3a). What is left is the other direction — and **Settings now says whether the rides are actually arriving (14.2.3)**, which is the item that would have caught all three of the defects in 14.0 the day they appeared. **14.10.4 is closed by the owner**: there is no community endpoint to fund — this build points at their household project through env vars — so `cloud.properties` stays empty for the stronger reason that the endpoint is *private*. Otherwise: **gated, not shut** — every call still goes through `CloudAccess`, and a profile with no account still makes no request at all, which is rule 1 doing its job now that there is something on the other end of it. **The endpoint is configurable from a clone now (14.10)** — checked-in `cloud.properties`, empty and fenced that way. **The payload format is changed (14.4)** while the cloud still held one row: columnar, versioned inside itself, 228 KB → 49 KB measured — 54 KB since provenance joined it, which is **14.4.7 closed**: `pm` is per sample, because a scalar on the row would have to pick a side in a ride the board dropped out of |
| 15 | Accounts, login and multi-device sync | 🔶 **The pairing page's three dots were a deadlock, and it is fixed (15.6.14).** The owner could not finish a QR sign-in; the page sat on `…` for ever, and their own follow-up — *"because i was already signed in"* — is what made it reproducible. auth-js runs `onAuthStateChange` callbacks while holding an exclusive Web Lock on the session storage key, and `link.js` awaited `getSession()` inside one, so a phone that already had a session queued behind the very thing waiting for it: **one holder and two waiters** on `lock:sb-<ref>-auth-token`, measured on the live page against a stored-session fixture, with no request leaving the browser at all. The callback takes the session it is handed now and `route()` is synchronous. Two more on the same page: **a confirmed sign-up's fragment was read as a pairing code**, so a rider returning from their inbox was told a code they never had had expired; and `describe()` can no longer wait for ever — including through the *first* version of that timeout, which hung identically because `client.rpc()` is a thenable with no `.catch`. **And the code is on screen rather than behind a button (15.6.15)** — the owner's second note — on both screens that offer an account, replacing itself five times over so a rider who went to their inbox comes back to a live code; the account screen's two routes went side by side to fit it (20.4.4 on a second screen). **What is now in the way is the mailer, not the app (15.7.7)**: no custom SMTP and `rate_limit_email_sent = 2`, so two confirmation emails an hour leave through a sender Supabase documents as being for testing — which is both what *"I tried signing up and nothing happened"* looks like from the rider's side and why the owner has run out of addresses to test with. `+` addressing is the free answer; 15.7.3 and 15.7.8 are the real ones and both are the owner's. Previously: **The confirmation email no longer points at `localhost` (15.7.6), and the bike now says the link landed (15.6.11).** `site_url` was still Supabase's scaffold value and `uri_allow_list` was empty, so a first-time rider who signed up on their phone tapped a link to a page that does not exist on the device they were holding — the one defect on this path with no recovery inside the flow. Both fields are set and the change was measured against the link `generate_link` mints rather than against a 200 from the API; `supabase/auth_config.py` is what made it safe to touch a live auth config, backing the whole thing up first and reporting two changed fields out of 242. **What is left of it is an inbox**: the mail that actually arrives has not been read. Beside it, the pairing hand-off had two faults nobody had reported (15.6.13): **Android back left the journey entirely**, because a `Dialog` dismisses itself and no step ever saw the press, and underneath that **the pairing loop outlived the screen showing it** — a second new profile was offered the *first* rider's live QR, under the first rider's id, so a phone scanning it would have signed in the wrong rider. And the moment a link succeeds is now drawn rather than skipped past, naming the account, since on a household bike the interesting failure is signing in as somebody else. **Open and largely built.** auth-kt is installed, which is also what makes every request carry the rider's own JWT instead of the anon key — after `003` the anon role can read the class library and nothing else. *Back up my rides* is its own destination off Settings (15.1.4–15.1.6), with four states including the one that gets forgotten: **signed up, not confirmed, no session**. **15.6 is the owner's QR flow and it works up to the hand-off**: the bike invents a secret, sends only its SHA-256, shows a code and a countdown, and the live project describes that code back under the device's own name. **15.2.8 is the design decision to carry forward** — the SDK holds one session and a household holds several riders, so *having an account* and *this tablet carrying that rider's credentials* are different questions and only the second may send. It was also the defect: Settings said "Backed up to your account" on a tablet holding no session at all. **Signing in is now seen working** — a tablet signed itself in by QR against the live project and its rides went up under its own JWT. **And 15.5.4 is closed, the way it asked to be**: from a *second real account*, 21 probes, 0 failures — A cannot create, read, rename or delete B's profile, cannot record, see, edit or delete B's ride, and cannot hand their own ride to B, which is the `WITH CHECK`-without-`USING` hole 15.5.1 existed to close. It is `supabase/verify_rls.py`, scripted off the admin API rather than a password, so it is repeatable instead of something a person once sat and did. **And the account is offered rather than gone looking for (15.8)** — the two moments a rider is already thinking about identity, creating a profile and selecting one that has ridden offline, both observed on the tablet AVD with the dismissal checked in `sqlite3` across a relaunch. 15.8.2 was free, already done as 17.16.6; 15.8.5 is the one open corner, waiting on 23.3.1a to give the two backup reminders a count they can actually share. **The exits are open now (15.4)**: signing out keeps every ride and drops the profile a rung, and *Delete my cloud copy* removes the rider's rows under their own JWT and **signs them out with them** — not for tidiness but because `synced_at` would otherwise record a backup that no longer exists, and the trimmer reads that column as permission to throw seconds away (23.4.6). Watched against the real endpoint with signing back in as the control: cloud 2 workouts and 1 profile to 0 and 0, tablet 2 rides and 1,020 samples unchanged either side. **What is left is account deletion (15.4.3), which is designed and needs one Edge Function deploy**, and pull to a new device (15.3.2) |
| 16 | Data visualisation | ✅ **Complete.** Post-ride charts done, the power caption says where the watts came from (16.1.6), and every trace now carries a scale decided once for all four (16.1.7 / 16.1.8). **The first trend is built (16.3.1)** — FTP over time on its own screen, with the ride behind each change one tap away — which also settles where a trend lives. **Three more landed in the seventeenth sitting**: the prescribed cadence finally has a chart (16.1.5a), weekly volume and the ride-day calendar share a second screen — *Your riding* (16.3.2, 16.3.5) — and a ride can be drawn against the rider's own previous best at the same class (16.3.4). **Phase 16 is complete**: 16.3.3 is mean-maximal power on *Your FTP*, measured rides only, with a gap breaking the window. **And 16.3.3a closes it properly**: a ride's efforts are worked out once when it is recorded and stored in `workout_power_bests`, rather than re-derived by walking every measured ride's samples on every visit. The item said wait until somebody feels it be slow, and that is the one thing about it that did not survive — 23.4.8 is the reason, and it is not speed: **trimming destroys the samples a best is derived from, and a best that was never computed cannot be recovered from a trimmed ride**. The marker on the row carries the provenance, so the existence of the stored rows *is* the measured claim rather than a question re-asked of `workout_metrics`. Verified as an upgrade rather than a fresh install — a v17 database read on the previous build as the control, migrated, and drawn unchanged off nine stored rows, with the bottle-stop ride holding no five-minute effort because its longest unbroken run is 201 seconds. Then measured both ways: with every sample deleted this build's screen is identical, and the build before it loses all four bests and **gives the wrong reason**, blaming the one modelled ride |
| 17 | Companion web application | 🔶 **Deployed, then drifted again the same day — and this time what is out of date is the fix for the fault the owner reported.** The owner redeployed at the start of the thirty-fifth sitting and `./web/check-deployed.sh` reported seven files the same, closing the drift below; `link.js` and `link.html` then changed again, so the live page still deadlocks for a rider who is already signed in until it is pushed. **17.16.9 is closed and is in the same change on purpose** — the pairing page says *"Scanned — this will sign in"* and the device's name and then asks for a sign-in, with the echoed pairing code, the duplicated subtitle and a maintainer's aside about the hand-off all gone; the fallback warning stayed because a deployment without the Edge Function still needs it, and *this* project was probed rather than assumed (401, not 404, so the bike gets a session of its own). **17.16.2 has now delayed three fixes** and is the item to close with the next push. Previously: **Drifted again, and this time it is holding a fix back (17.16.2).** `link.js` carries 15.7.6's `emailRedirectTo` — the line that lands a confirmed rider back on the pairing page rather than on the site root — and `./web/check-deployed.sh` reports it DRIFTED, so it reaches nobody until the owner redeploys. That is the same gap the check exists to make visible and it has now blocked two fixes rather than one; the deploy command is still written down nowhere. **17.16.4 is closed by 15.7.6**: the project's Site URL points at the host. Previously: **Deployed, and the check is what says so (17.16.8).** The owner redeployed and `./web/check-deployed.sh` reports seven files the same, exit 0 — against `link.html` and `link.js` drifted twenty-four hours earlier. So 17.16.6 closes on both halves: the fix exists and a rider can meet it. **17.16.2 is what it hands forward** — the deploy command is still written down nowhere, and the gap was open for exactly one drift because the check exists, not because the deploy became reliable. Previously: **the pairing page had no way to sign in on it, and it was two faults stacked (17.16.6).** The owner scanned a QR and met a dead end. The deployed `link.js` is the **pre-17.16.5** version — that fix was observed against the live endpoint from a *local copy* and never shipped, which 17.16.2 predicted in the same sitting and which drifted inside a day. Underneath it, 17.16.5's own fix gated the **sign-in form** on the pairing code being recognised, collapsing two questions: handing a session to a bike has to know which bike (15.6.5, and that is what the confirm step is for), while signing in to Pelonot on your own phone is the same sign-in `index.html` offers and a five-minute code makes it no safer. The page inverts now — sign in whenever, confirm separately, device still named — and all four session/code states were measured against the live project. **`./web/check-deployed.sh` is 17.16.7**: curl and diff, no credentials, non-zero on drift, and its first run is the evidence for all of the above. **The fix is in the repo and not on the internet**; redeploying is the owner's. Otherwise: **built, running and hosted (17.16)** — https://pelonot.showered.workers.dev/, the owner's own deployment, with the host's `.html` → extensionless 307 measured to carry the QR's fragment intact. Hosting it is also what turned 18.11.1 from a prerequisite into a live setting, and 17.16.1–17.16.5 are what else it changed. Otherwise: **built and running, and it moved up the road rather than waiting at the end of it** — it is the only way either of us can see what the bike put in the cloud without writing SQL. `web/`, static, no build step, opens from `file://` (17.1); `link.html` is the QR flow's landing page and names the device before asking for anything (17.13); the endpoint comes from a git-ignored `config.js` (17.14). **17.1a answers the owner's monorepo question**: it already is one, and moving the Gradle root costs real paths for no benefit — the rule that keeps the apps independent is that no build depends on another's output. **17.15 is the owner's design system**: `tokens.css` is transcribed from `Color.kt` and `Theme.kt`, `app.css` holds no literal colour, and the metric accents deliberately do not flip with the theme. Ride history and detail read `metrics_payload` in **both** shapes, since one pre-14.4 row is still up there |
| 18 | Social **across bikes** — the networked tier | 🔶 **The leaderboard landed early, by itself, because the owner removed the graph it was waiting on (18.11).** No friends, no requests, no blocks: everyone registered is on everyone's board, and it is two narrow `SECURITY DEFINER` functions rather than a relaxed policy — `workouts` and `profiles` still hold "your own rows and nobody else's", and the ghost strips heart rate. 18.9 applied rather than quoted: one type, one ranking, one renderer, with the household half still a Room query, so the failure mode is a shorter board and never a missing one. **18.11.1 is closed as *not* to be done, on the owner's word** — *"Leave on public signup. It doesn't matter — it requires email validation anyway"*, and `mailer_autoconfirm` is `false`, so it does. What actually settled it was a collision neither item had spotted: **18.11.1 and 15.8.2 are direct contradictions**, since 15.8.2 says a rider creating their first profile has no account by definition and must be able to sign up from their phone. Two items that could not both be built, and the one that serves the rider won — so 15.8.2 is unblocked and **17.16.3, which key is on the internet, matters more now the door is deliberately open**. The rest of the phase sits on 15. **Phase 24 is the half that does not, and it is largely built** — which is 18.9's whole point: every screen here goes *on top of* its 24 equivalent rather than beside it, or one of the two leaderboards drifts and it will be the one nobody rides against |
| 19 | Ideas worth having, ranked | 🔶 Mixed, and not untouched: screen-on lock, auto-pause, local backup/restore and the README are done (19.1.1–19.1.3, 19.1.5), and **CI is written and waiting on its first green run** (19.1.4). **19.1.3a is 19.1.3's own bug a second time**: restore refused every backup this build made, because the app's half of the schema-version comparison was a `const` kept equal to `@Database(version = …)` by a comment and it drifted to 16 against 17 — so a rider was told to update to a version newer than the newest one. `BackupFileTest` stayed green throughout, exactly as it did when the SQLite magic bytes carried a trailing space: the arithmetic was right and one of its inputs was a lie. The constant is deleted rather than corrected — `AppDatabase.schemaVersion()` asks the open file — and `DatabaseBackupTest` writes a real backup and puts it straight back, checked against the bug as well as the fix. **19.1.7 is the owner's own**: [STATUS.md](STATUS.md), one page saying where the project is, with *done* defined three ways because the honest answer differs by a lot depending on who is asking |
| 20 | Who's riding — profile selector & avatars | 🔶 **The picker admits it has more in it (20.1.6).** Past about twenty tiles the grid must overflow — 20.1.2's floor doing its job — and the whole screen scrolled as one piece, so the overflow arrived as a row sliced off at the bottom of the display with the hint below the fold. The heading and the hint are fixed now and only the riders scroll, with a soft edge at whichever end has tiles past it; the last visible row is cut **against a line of text** rather than against the edge of the screen, which is the entire difference between a clipped screen and *more to come*. Checked at 22 tiles and then checked the other way, with the database cut back to a household of three: one centred row, no fades, unchanged. **The two controls the owner reported twice are now one component drawn twice (20.4.5).** 20.4.1 fixed them by measuring one against the other and every number was right; the pair still did not line up, because an `OutlinedTextField` reserves 8 sp above its box for the label to float onto the border, so its outline is the *bottom* 56 dp of a 64 dp control. `PickerField` is `OutlinedTextFieldDefaults.DecorationBox` now — the lesson being that copying Material's numbers is not the same as calling Material's component. **Android back no longer throws the whole screen away (20.4.6)**: it took the same lambda the on-screen *Back* has, so there is no step where the two can disagree. **And the first screen a rider meets now fences the weight (20.5.1)** — Settings has since 13.8 and profile creation never did, which is two writers of one column disagreeing about what a weight is; `RiderBounds` is the single answer. Honest about its limit: the `68`-in-a-pounds-field that found it is 31 kg and inside any defensible bound, so what the fence catches is the neighbouring failure and **20.5.2** carries the other half. **20.1.6** is new and unmeasured: past about twenty riders the selector clips its last tile with nothing saying it scrolls. **20.3 is done and the screen the owner said *cannot go into production* is gone.** Profile creation is three steps rather than an `AlertDialog` with three text boxes: a name; then weight, birth year and one of three sentences about your riding; then the number the app worked out, said once, with where it came from (20.3.4) and that the riding will correct it (20.3.5). **Route B, and what settled it was not in the item** — `PostWorkoutAnalyzer` only proposes an FTP *upward*, so an estimate that starts low is deleted by the first hard ride and one that starts high is permanent. Every coefficient is therefore pitched below the published mid-range, and a test pins it. **No Skip**: the escape is on the answer instead, reached only by a rider who has seen the estimate and disagrees. **Year of birth, not a date** — the owner's call, and both consumers reduce it to whole years, so 1 January costs 0.7 bpm on Tanaka and 0.6% on the FTP term while Material's picker opened on *August 2026*. Three of the four defects found were **sentences, not layout**: a lower-cased "i ride now and then", a caption naming an input it had not used, and Settings offering a full date picker over a column onboarding fills with 1 January. The fourth was in the funnel and invisible — `UserRepository` filed every new profile's FTP as `ProfileCreated` *"whatever the caller said"*, so an estimate would have been recorded as the rider's own claim. Migration 13 → 14 ran against the bike's own 7-ride database. Selector rebuilt for the tablet (20.1, incl. rename/remove); avatars (20.2) not started. Original note: One piece of it is closed — **20.3.6**, the prefill and the fallback both said `200` while the rest of the app said 150, so every profile made on that screen started 50 W high and nothing said so — but the question it opens, what a rider who cannot answer should be given instead, is untouched. The constraint that makes it interesting is that the app cannot simply stop having a number — FTP is the denominator of the whole zone system and is written onto the ride at its start |
| 21 | Heart-rate zones | 🔶 **The app asks for a year, not a date (21.1.1b)** — the owner's call, and the arithmetic agrees: Tanaka moves 0.7 bpm a year against a spread it already admits is 10–12, so 1 January is invisible to the only two things that read this. One `BirthYearPicker` serves profile creation and Settings, which also closed a live claim the app could not support — Settings offered a *full date* picker over a column onboarding fills with 1 January, so a rider who answered "1986" was shown "1 January 1986" as though they had said it. **21.1.1a is largely settled from the other end**: there is no full date left to leak, and what remains is whether the column itself becomes `birth_year`. **The owner's earlier note is built (21.4.2): the heart-rate trace carries its zones, on both screens that draw it.** It forced 21.2.3 with it — `workouts.max_hr_bpm`, migration 12 → 13, nullable and not backfilled — because the bands come from a maximum that moves and nothing recorded the one a ride was ridden at, which is 7.8's trap one denominator along. Taken as 21.4.2a recommended: the column **and** a caption, so a ride recorded since says *"zones from %HRmax"* and one recorded before says *"your maximum today — this ride did not record its own"*. Both seen on the AVD, and the migration ran against a real 11-ride database rather than only a test. **21.4.2b is what it opened**: the cloud copy of a ride carries neither denominator, so the web app draws every rider's zones from the reader's own profile. Otherwise: **open and useful, from the owner's earlier inbox note.** The honest answer to *"pretty sure this is already covered"* was no — the app had no maximum heart rate for anybody, so it had no boundaries to colour between. Now: `max_hr_bpm` asked for **first** and `birth_date` as the fallback (migration 11 → 12, both nullable, because a default maximum is a guess about a rider's body); Tanaka rather than 220 − age, labelled an estimate wherever it shows; `HeartRateZone`, five zones on its own palette because HR zone 4 and power zone 4 are not the same claim; the ride screen's bpm and its beating heart both take the zone's colour, observed live at the 114 bpm boundary. **21.2.3 is the gate on going further**: nothing draws a zone for a *past* ride yet, which is the only reason 7.8's trap has not bitten, and 21.4.2 must not land before it |
| 22 | The dashboard | 🔶 **The screen answers the second half of its own question now (22.8.6, 22.8.11).** 22.1.1 settled that sentence — *should I ride today, **and what should I ride*** — and for six sittings nothing on the surface said what to ride. Where *Begin Class* was there is the class itself: *"Ride this / Sweet Spot 3×5 / 30 min · Sweet Spot · new to you"*, with the library beside it as a full-size door and *Just Ride* untouched. **The rule was decided in a session rather than asked for**, and is written out at 22.8.11 so it can be argued with: *something the length you usually ride, that you have ridden least — and an easy one if you rode hard in the last day*. **The half that took the thinking is what it refuses**: there is no periodisation here, no fatigue model and one FTP that only moves upward, so a card saying *"today is your interval day"* would invent all three on the screen a rider trusts most. Everything it says is an observation about their own history, and the single advisory claim — *don't stack two hard days* — is Phase 28's rule about never rewarding what a coach would advise against, arriving from the other end. Hard is read off the class's **blocks** rather than its category name, and a **Just Ride tells it nothing**, because a free ride's intensity lives in `workout_metrics` — which this screen may not read (22.1.8) and which needs measured power to mean anything. **It cost nothing vertically, which was the constraint**: at 609 dp against a 664 dp viewport a card of its own would have put the screen back to scrolling and undone the sitting before it, so the primary card is the same card in the same row. All four branches seen on the AVD, and the interesting one checked against the rule rather than the fixture — a 30-minute Sprints ride an hour old turned *Sweet Spot 3×5* into *Recovery Flow · easy after a hard one*, and **moving that same row to thirty hours put Sweet Spot straight back**. The first-ride branch says *"Zone 2 Steady · 20 min · Endurance · a good place to start"* — Endurance rather than Recovery because **a recovery class only makes sense *after* something**. One behaviour settled rather than fixed: a rider who has only ridden Endurance is offered a Sprints class, and that stays, because every class here is prescribed in percentages of the rider's **own FTP** and an app with no fitness model has no basis for calling anybody not ready. **22.8.12 is left unbuilt with the argument written down**: an abandoned ride writes a short `duration_sec` and the rule reads it as a preference. Previously: **Re-opened by the owner's note of 10 August (22.8), the fourth on this screen and the first about its *vertical* axis.** *"Seems very stretched. Feel like more info can be shown 'above the fold'... Primary CTA should probably be Begin Class rather than 'Just Ride'."* The measurement was taken before the write-up and it is the item: **993 dp of content in a 664 dp viewport**, with the household panel — the only part of this surface about anybody else — entirely below the fold. The previous sitting's claim that the dashboard *"fits on one screen without scrolling"* was measured on a dashboard with no household on it. **"Stretched" is right and it is vertical**: the horizontal fix from 22.4 is holding and nothing bands across the panel; what is left is 152 dp cards carrying 43 dp of text, three navigation tiles at 111 dp, and 130 dp of greetings and headings introducing cards that name themselves. The screen is not full, it is loose. And it does not answer the second half of its own sentence — 22.1.1 settled that the dashboard answers *should I ride today, and **what should I ride***, and nothing on it says what to ride. **22.8.1–22.8.5, 22.8.9 and 22.8.10 are done and observed**: *Begin Class* is the primary action and *Just Ride* the secondary, on the owner's *"95%+ of usage will be classes"*, with both tapped through because swapping two lambdas between two call sites is the change that goes silently wrong; History and Settings are labelled doors in the greeting row rather than two of three 405 × 111 dp cards, since navigation is not content; and the household sits **beside** the rider's own three cards rather than under them — 18.2's rule is about *order*, not visibility, and a panel nobody scrolls to is a panel nobody has. **993 dp → 609 dp with the backup nag showing, 513 dp without**, against a 664 dp viewport, checked with a 700 px swipe that moved nothing. Three things fell out of it that the note did not ask for. **The FTP definition was setting the height of a whole row** — `WideRow` equalises heights, so a caption on the FTP card decided how tall *Just Ride* was — and *Your FTP*, the one screen where the number is genuinely **read**, had never spelled the acronym out at all; it moved rather than being deleted, which is Phase 26's rule about a *unit* applied to a *definition*. **`WideRow` did not stack** (22.8.10): its content is written against `RowScope`, so the narrow branch wrapped the children in a second `Row`, weights and all, while the comment above it said the opposite — invisible because the bike is 1280 dp and never took that branch. And **the no-household branch nearly shipped as the owner's rule broken by the change that cites it**, drawing three cards at `fillMaxWidth` across 1232 dp; it is a `WideGrid` now, seen by turning `household_visible` off for every profile but one. **22.8.6 is half-answered on purpose** — the room is made and nothing was invented to fill it — and its first candidate, *a class to ride*, is the best-argued unbuilt item on the screen: the dashboard's own sentence asks *what should I ride* and nothing on it answers. **22.2.5 is still the phase's one open box and this sitting added to its debt.** **The progress section finally shows progress (22.1).** The AVD said the thing this phase had argued about since the sixth sitting: *Your Progress* opened as `Today's Output 73 kJ` beside `Recent Ride 73 kJ` — the same number, twice, because the last ride was today — which is 22.1's own complaint that *"both are the same quantity on the same axis"* arriving as a screenshot. **And the today figure was 22.5's defect surviving on the card beside the one that fixed it**: at one ride a week it reads `0.0 kJ` six days out of seven, and 22.5.4's audit walked past it because that audit was looking for *weeks* and this card counts *hours*. So **22.1.1 is settled in one sentence, in `DashboardStats`' own KDoc** — *the dashboard answers should I ride today, and what should I ride* — and the section follows: `Today's Output` deleted, `Recent Ride` replaced by a real last-ride card that names the class, when and how long, and opens the ride. **It says no kilojoules on purpose** (Phase 26: a unit belongs where a measurement is being read, and this is a glance). Its one claim — *best you've ridden it* — has **three refusals to every assertion**: a free ride, a first ride of a class, and watts not measured on either side; `NotBest` and `Unclaimed` draw the same nothing and are kept apart for `PowerProvenance`'s reason about `Unknown` against `Modelled`. All four branches were seen on the AVD with the database edited by hand, **including the verdict disappearing again** when a 240 kJ measured ride was restored, which is what makes it a comparison rather than a constant. It also found a gap in the measured-power gate: `NOT EXISTS (a bad sample)` is passed trivially by a ride with **no samples at all**, so `ownTotalsForClassExcluding` carries the `EXISTS` beside it — `ownTotalsForClass` still does not. **History is one centred column now (22.7.6)**, on the owner's note: *"keep it all constrained to one narrower grid column in the middle, rather than expanding widthways for days with large number of workouts."* That overturns 22.4.3's verdict on this one screen, and the audit's prediction — *"History is a list of rides and may well want two columns"* — was wrong for a reason seven screens at once could not show: **a list is read down and a set of tiles is looked at**, and History was the first dressed as the second, drawing two columns across 1232 dp for a seven-ride day with a one-ride day centred at 616 above it. It **subsumes** 22.7.1 and 22.7.4 rather than sitting beside them. **22.7.4 was the owner's decision and they made it** — centre the heading over its row — built and measured at x = 498 against a card at x = 498, an hour before 22.7.6 replaced the mechanism entirely; the correct fix to the wrong thing is left in the plan on purpose. **22.5.5 is judged against the once-a-week fixture it insisted on**: `6 rides · 32 min · 8 weeks in a row`, where the weekly streak is 22.5.2's argument as a number — that rider scored **1** under the old day-counting streak and was shown nothing. The one state that does reach zero was measured too and is correct: a rider 60 days idle sees `0 rides` beside the *date of their last ride*, so the zero no longer stands alone. And **22.2.2, 22.2.3, 22.2.4 and 22.4.4 are closed as answered by 22.4.3** rather than built: there are no rails, only rows, and the residue is a rule — the number of *kinds of thing* on a surface decides which helper it takes. Previously: **The Start Class screen gets its column back (22.7.3).** The owner: *"we've added the leaderboard in there and the whole screen doesn't look good."* It is 22.7.2's own last paragraph coming true — that item shipped saying in as many words that the household card *"does not draw at all"* on the AVD it was judged on, so the screen was designed without the thing being complained about. **A card gated on data the test device cannot produce is a card that has not been designed.** The board was stacked *into* the description of the class, between the picture of it and the list of its blocks; it is not a fact about the class, so it is a column of its own beside it — absent entirely when there is nobody on it, so a class nobody has ridden still gets the whole width. Observed against a seeded household of twelve, with the chart and all six blocks on screen at once, which is 22.7.2's own criterion restored. **The Start Class screen shows the class now (22.7.2).** The owner's note, and the last screen between a rider and a ride: it was six full-width rows each spanning 1872 dp to carry four facts down their left edge, with the seventh block of a 30-minute class **below the fold on the one screen whose job is to show the whole class**. The visualisation is the class itself — height for zone, width for time — and it reads as two different workouts from across the room. No value axis, deliberately: the vertical is a zone *ordering* and the gap between Z1 and Z2 is not the gap between Z6 and Z7 in watts. Two facts fell out of drawing it that the item did not have — **zone 1 needs a height floor** or a warm-up reads as an empty edge rather than as riding, and **adjacent blocks at the hardest zone are one effort**, since the library splits a fifteen-minute block to change the cadence and calling that two describes a rest nobody gets. It is also **the first screen to use 22.4.3's "capped column inside a wider frame"**: the profile and the interval grid take the panel, the summary is `readableText`, the leaderboard is `loneCard`, and Start is a 420 dp control. Two things found by looking rather than planning: the content is **centred when it does not fill the panel** (22.7.1 on a third screen), and `WideGrid` grew an opt-in `equalHeightRows` — opt-in because equal heights need `IntrinsicSize.Min` and a `Canvas` throws rather than answering one. **22.5.4 closed the once-a-week audit and 22.6.3 was closed by the owner.** The household panel counted a *week*, and there the assumption did something worse than look wrong: a rider with no rides in the window has no row at all (24.2.4's inner join), so a housemate riding once a week was **absent from the household** rather than shown with a zero. It is the same rolling 30 days the rider's own card uses, and its streak counts weeks. The four types and functions naming a window that is no longer a week were renamed with it. **22.6.3 — the build-time fence around "no single card takes the panel" — is closed as *not to be built***, on the owner's own word: this project's fences guard things that are invisible when broken, and a card banded across 1232 dp is visible from across the room. **The panel is used, and the rule for using it is written down three ways.** The owner's two notes of 4 August settled it: *use the full width, and no ONE CARD goes full width; grids where they fit.* `readableColumn` caps a column, `WideGrid` tiles a set, `loneCard` caps a card with nothing beside it — and CLAUDE.md carries all three together, because reaching for the wrong one is how this project twice made a whole screen the wrong shape (22.4, 22.6). The dashboard fits on one screen without scrolling; history is two ride cards across, the class library three, *Your riding* and *Your FTP* two; Settings and the account screen keep the cap, which is what it was always for (22.4.3). **22.6.3 is what is left and it is the owner's word**: they said *enforce*, and a rule that lives only in a markdown file is the kind this project has already broken inside one session. **And *This Week* is *Last 30 days* (22.5)** — at one ride a week the old card said "0 rides" six days out of seven, and its streak counted *days*, so the most consistent rider the app can have scored 1 and was shown nothing. The older note follows. **A *This Week* card now opens the progress section** — rides, minutes and the streak, and the door to *Your riding* (16.3.2/16.3.5). It is the number **22.1.2** has been asking for since the sixth sitting, in the place it asked for it, though that item is still open: the two kJ cards below it are unchanged. **The FTP card is now a progress card (22.1.4)** — the number, a stepped sparkline of every value it has held, and how far it moved and who moved it. That is the first thing in the section that is a trend rather than a total; the two kJ cards below it are still what they were (22.1.2). The width cap is a theme token applied across the app rather than one screen's fix (22.2.6); what goes in the rails it opens up (22.2.2, 22.2.3) is still undecided |
| 23 | Offline by default — making the ungated tier complete | 🔶 **Retention (23.4) is built.** The owner asked for old rides condensed rather than kept sample by sample, and that is what the trimmer does: `MetricTrim` keeps the lowest and highest watt of every ten seconds **as real rows** — a 25-minute ride of 1,500 samples comes back as 300, five-fold rather than the item's estimated thirty, because thirty was the figure for a trace of means and a mean is the one thing a trim must not write. **Off by default** (`Never`, plus 6 months and a year — three answers, 26.3), never silent, and the backup is offered inside the dialog that would destroy the seconds. **`metrics_detail_sec` is the discipline** (23.4.3): the power caption, the two charts made of counted seconds, the summary sentence, the CSV comment and the TCX note all say the ride is an outline, and the compliance percentage is withdrawn rather than recomputed. **`distributions_json` is the half the item under-read** — time in zone and the cadence spread are counts of seconds, so they are written down while the seconds are still there; measured as *Z2 00:31 / Z3 07:59 / Z4 07:44 / Z5 07:30 / Z6 01:15 / Z7 00:01* before a trim and identically after it. The fences are 8.3b's (never an unfinished ride, three resume paths read samples), 23.4.6's (never a ride the cloud has not taken, enforced in the trimmer because by the time the worker runs the samples are gone) and idempotence. **23.4.10 is closed by building the offline-safe half and saying which rider the dialog is talking to**; 23.4.13 is the rehydration item that falls out of it. Three AVD-only defects were found and fixed on the way: a dialog counting for the setting rather than for the question, a size that went **up** after condensing (`VACUUM` in WAL mode, fixed with a checkpoint: 432 kB → 368 kB), and two names for one feature on one screen. **The consent gate (23.1), the class library (23.2) and the backup reminder (23.3.1) are done and observed**, and 23.4.8/23.4.9/23.4.12 landed in the two sittings before. What is left is 23.4.1 — now a glance at Settings → Storage on the real bike rather than three adb queries — plus the cloud counterpart (23.4.7), the policy it forces (23.4.11), rehydration (23.4.13) and the cloud as an update channel (23.2.3/23.2.4) |
| 24 | Household social — the tier that needs no cloud | 🔶 **The board says less, and it is bounded on every screen (24.3.17, 24.1.8).** The owner cut three things from the live board at once — the signed gap (*a gap is arithmetic the rider did not ask for*), the unit label, and the ranking entirely, including `4TH OF 6`. The last is a claim about the product rather than the pixels: four of the board's row kinds are the rider's own past rides, so a position describes a field that is mostly one person. The ranking still orders the board and picks the window and is simply not drawn. What goes with it — a rider cannot tell whether there are two more rows or twenty — is accepted rather than solved. **And the static board has a ceiling now**: `ClassLeaderboard.visible` keeps the podium and the rider's own neighbourhood, marks the skip and counts what is hidden, because 18.11 means the row count is *how many people use this app*. The two boards differ in which window and should. **The live leaderboard is built (24.3.10–24.3.13b) and it supersedes the single rival.** Start a class anybody on the bike has ridden and a board appears, ranked live on the class total in kilojoules, showing **three rows: the one you are chasing, you, and the one chasing you**. Nobody picks anybody. Four kinds of row — your best ever, your best of the last twelve months, your best of the last thirty days, and every housemate's — and the two windows are **rolling rather than calendar**, which is 22.5.1 applied: a month resets on the 1st and would take the reachable ghost away on the day a rider most wants one. One ride appears once, at its widest label. The window **slides rather than shrinking**, so leading and last are both three rows and the card never changes size under a rider. `RIVAL_GHOST` hides the picker and the single-gap card — off, and almost nothing is behind it, because the board is built on the ghost's own foundations. **24.3.6 is finally ticked**: the *finished* state seen both ways round, found cheaply by seeding a 90-second rival. Everything before it still stands — 24.1, 24.2 and 24.3.1, the per-class board, the household's thirty rolling days with streaks and an opt-out, and a housemate's trace behind your own on ride detail. **Seen on the real bike too** — `Racing 1 on END-03: Your best 238` with real measured watts and no lever, and the two-row `2ND OF 2` case the AVD could not produce. **What is owed is watching it move under somebody actually pedalling.** **A modelled ride narrows the board rather than emptying it (24.3.7a)**, on the owner's rule: *"There should ALWAYS be a leaderboard even if it's only CPU ghosts you're up against."* 24.3.7 is right about what it was written for — a comparison between a modelled number and a measured one — and was applied wider than its own argument, taking *the plan* and the milestone ladder down with the real rides for no reason anybody could state. `generatedOnly()` keeps what this app computed from the rider's own FTP and drops every real ride **including the rider's own**, which is the rule rather than an exception to it; the pacer's floor is recomputed from what survives, or the first rung sits above everything left on the board. The ordering was a defect in waiting — `loadRaceBoard` is asynchronous, so gating the flag on a race already existing let a board landing a tick later arrive un-narrowed and stay that way — and nothing written down changes, so the ride is still excluded afterwards from every static board, FTP proposal and calibration fit. **Not yet seen on a simulated ride.** **Two open items, and one of them is the owner's**: 24.3.12a, what the rows should be called (*"12 months is no good at all"*, and they asked for it as an action on themselves), and 24.3.16, the leaderboard on the overlay, which overrules 24.1.5 and 18.6. `LEADERBOARD.md` describes it all in plain English |
| 25 | Out of the saddle | 🔶 **The field, the ride screen, the spoken coach, the overlay's cue and the library's own use of it are done and observed (25.1–25.4.2).** The titles no longer claim a position the intervals do not give. What is left is how the cue reads over a playing film (25.3.4, needs the rider). **25.4.3 is closed**: the two near-twins the rename exposed are separated by their work as well as their titles, as `SWT-13` rather than an edited `SWT-05` — the id is the foreign key |
| 26 | The app's voice — less is more | 🔶 **A standing rule rather than a backlog**, and it is in CLAUDE.md: a unit belongs where a measurement is being read, not where a choice is being made. Landed: the profile tile is a name and a face (26.1.1), the post-ride summary reads as a screen rather than a spec sheet (26.1.2), the effort question is three answers instead of ten with the column still 1–10 — **the owner has now settled the wording as written (26.3.3)**, and their reason is the good one: a rider who stops a class early does not rate it at all — and **Settings has been audited row by row (26.1.4)**: nine cuts, each one a sentence answering a question nobody standing on that row was asking. *Units* defended not offering calories, *Use wallpaper colours* opened with Android's name for the mechanism, the maximum-heart-rate row printed the Tanaka formula, the opacity slider explained why it stops where it visibly stops, *Position* justified its own default rather than saying which edge to pick, *Backup* repeated the sentence on the card above it, and *Show me to the others* had two paragraphs where only the second answers the question a rider has. **One row was audited and left alone**, which is the other half of doing it honestly: the FTP field keeps both lines, because Settings is the one screen where that number is *typed*. A naming fault fell out of the opacity cut — it said *"strip"*, which is never the rider-facing word for the overlay. Open: the kilojoule audit (26.1.3), and **26.4, the owner's "score like a lvl"** — written up with a recommendation to leave the FTP out of it, because a score built on a number that goes down is a demotion nobody earned |
| 27 | Being told something worth knowing | ⬜ **Not started, and that is the owner's own weighting** — *"definitely nice-to-have and low priority for now"*. Promoted out of 19.3.2's one line the way Phase 21 was promoted out of 19.3.3's, because the one line is not one job: nothing in this app *remembers* anything, and an alert is a claim about a change, so 27.1.1's table is what everything else waits on. Three families that are not the same feature — your own record, your own consistency, and somebody else beating you, which is the only one needing the network. The rules were the point of writing it: `PowerProvenance` gates every power record (**no alert can fire on the emulator**, and that cost is worth paying); records are built on absolutes rather than on anything relative to a moving FTP or maximum heart rate (7.8, 21.2.3); **the first ten rides are all records**, which is the design problem rather than a detail; one per ride; nothing on the overlay and nothing spoken; and 16.3.3a is a hard prerequisite because retention would otherwise congratulate a rider for beating a record that only fell because its ride was trimmed |
| 28 | Achievements | ⬜ **Not started, at the owner's own weighting** — *"one for the backlog"* — and written at length for Phase 27's reason: the one sentence is not one job. **The opening section is the part that matters most and it is not a badge list.** An alert is an *event* and fails on frequency; an achievement is a *possession* and fails on meaning — which makes Phase 27 the delivery mechanism and this phase forbidden from building a second one, or it grows its own toast, its own dashboard card and its own one-per-ride rule before 27 arrives. It is also **the honest form of the thing 26.4 was right to refuse**: the owner asked to *"gamify it all even further"* and separately agreed to leave a game-style score, and those two only disagree if a score is what gamifying means — an achievement is a discrete, nameable, **true sentence about something the rider actually did**, with nothing in it to round off. Six rules underneath it, and the sharp ones are: **never revoked** (7.11 lets auto-FTP fall, 23.4 trims old rides, and a badge derived live would un-earn itself — the award is *recorded*, not derived); `PowerProvenance` gates anything from watts and **most of the catalogue is on the free side of that line**, since a count of rides and a duration are the same quantity whoever measured them; **no achievement may reward what a coach would advise against**, which rules out day-streaks and rode-twice-today and is 22.5's weekly-streak decision arriving as a rule; the set is finite and the unearned ones are visible, so nothing may depend on equipment the rider does not own; offline throughout, with the across-bikes family **absent** rather than greyed out (rule 3, not a trial of the paid tier); and prose names with no points, no levels, no total. The catalogue is ordered by how much already exists — volume and consistency need no new data, and **breadth is the family this app is unusually well placed for**, because 72 authored classes make *every class in this collection* and *the same class five times* joins onto `class_templates`; that last one rewards the behaviour that makes 24.1's per-class ranking work at all. Also settled in advance: the back-fill awards a year of history but **announces none of it** (forty badges through 27.3's path would poison the feature on day one), two devices earning one badge resolve to the **earlier** date, and the dashboard's share is one line — the nearest *unearned* badge, because **three rides to fifty** is the only thing in the phase that answers *should I ride today* (22.8.8, 28.5.2) |
