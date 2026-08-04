package com.pelonot.core

import com.pelonot.BuildConfig

/**
 * Things that are built but not switched on.
 *
 * One place rather than `BuildConfig` scattered through the call sites, for
 * the reason that makes a flag worth having at all: a reader needs to be able
 * to find out what is hidden and why without grepping for a constant. A flag
 * whose reasoning lives in three different files is a dead branch nobody dares
 * delete.
 *
 * Read through a `val` rather than referred to as a constant on purpose. A
 * `BuildConfig` boolean is a compile-time `false`, and Kotlin will happily
 * fold `if (false)` away and then tell you the code behind it is unreachable —
 * which turns a flagged-off feature into warnings and, in time, into a
 * deletion nobody meant to make.
 */
object Features {

    /**
     * The single-rival ghost — the picker on the class detail screen and the
     * one-number `+18 kJ` card on the ride screen (PLAN 24.3.3–24.3.9).
     *
     * **Off, because the live leaderboard replaces it** (24.3.11). The owner's
     * own reasoning: *"It has scope for including unlimited number of people
     * whereas rivals is (i think) just one person you race against. Let's not
     * waste all the effort though, let's feature flag the Rivals feature and
     * keep it hidden away."*
     *
     * Almost none of the ghost is behind this. The elapsed-second alignment,
     * `RivalTrace`, the measured-power gate and `active_ride_rival` surviving
     * a crash are all still live — the leaderboard is built on top of every
     * one of them. What this hides is the *presentation*: choosing one person
     * before the ride, and reading the race as a single number.
     */
    val singleRivalGhost: Boolean = BuildConfig.RIVAL_GHOST
}
