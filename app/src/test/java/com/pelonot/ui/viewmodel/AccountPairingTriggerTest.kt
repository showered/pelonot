package com.pelonot.ui.viewmodel

import com.pelonot.data.local.entity.UserEntity
import com.pelonot.domain.cloud.AccountState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When the bike may ask the server for a pairing code (PLAN 20.4.7, 15.6.6).
 *
 * **The rule under test is that the profile is part of the trigger and not
 * merely a precondition.** `AccountViewModel.startPairing` returns silently
 * without a profile, so a screen that asks once — before the profile flow has
 * emitted — asks nobody again and shows a spinner with no end. The property
 * therefore has to *change value* when the profile lands, because that change
 * is what re-runs the `LaunchedEffect` on both screens that draw a QR.
 *
 * The last two cases are the states `AccountScreen` was already right about and
 * the offer step never asked: a session still loading, and a rider already
 * signed in on this profile. They are here so the two call sites cannot drift
 * apart again — that drift is what 20.4.7 was.
 */
class AccountPairingTriggerTest {

    private val profile = UserEntity(localUserId = 1, name = "Robin")

    private fun state(
        profile: UserEntity? = this.profile,
        session: AccountState = AccountState.SignedOut,
        pairingAvailable: Boolean = true,
        cloudConfigured: Boolean = true,
        awaitingConfirmationFor: String? = null
    ) = AccountUiState(
        profile = profile,
        session = session,
        cloudConfigured = cloudConfigured,
        pairingAvailable = pairingAvailable,
        awaitingConfirmationFor = awaitingConfirmationFor
    )

    @Test
    fun `no profile yet does not want a code`() {
        assertFalse(state(profile = null).wantsPairingCode)
    }

    @Test
    fun `the profile arriving flips the trigger`() {
        // The whole defect in two lines: the value before and after the profile
        // flow emits must differ, or nothing re-asks.
        assertFalse(state(profile = null).wantsPairingCode)
        assertTrue(state().wantsPairingCode)
    }

    @Test
    fun `a build with no cloud never asks`() {
        assertFalse(state(cloudConfigured = false).wantsPairingCode)
        assertFalse(state(pairingAvailable = false).wantsPairingCode)
    }

    @Test
    fun `a session still loading is not signed out`() {
        assertFalse(state(session = AccountState.Unknown).wantsPairingCode)
    }

    @Test
    fun `a rider already signed in on this profile is not offered a code`() {
        val signedIn = profile.copy(authUserId = "acc-1")
        assertFalse(
            state(
                profile = signedIn,
                session = AccountState.SignedIn(accountId = "acc-1", email = "robin@example.com")
            ).wantsPairingCode
        )
    }

    @Test
    fun `somebody else's session on this tablet still wants a code`() {
        // 15.2.8: a household bike can be carrying Simon's session while Robin
        // is the profile on screen, and Robin has an account to sign in to.
        assertTrue(
            state(
                session = AccountState.SignedIn(accountId = "acc-simon", email = null)
            ).wantsPairingCode
        )
    }

    @Test
    fun `a rider waiting on a confirmation email is not asked to scan`() {
        assertFalse(state(awaitingConfirmationFor = "robin@example.com").wantsPairingCode)
    }
}
