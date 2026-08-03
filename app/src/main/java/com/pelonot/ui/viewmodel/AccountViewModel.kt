package com.pelonot.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pelonot.data.local.entity.UserEntity
import com.pelonot.data.remote.DeviceLinkRepository
import com.pelonot.data.repository.AccountRepository
import com.pelonot.data.repository.SettingsRepository
import com.pelonot.data.repository.UserRepository
import com.pelonot.data.repository.WorkoutRepository
import com.pelonot.data.worker.WorkoutSyncWorker
import com.pelonot.di.ServiceLocator
import com.pelonot.domain.cloud.AccountState
import com.pelonot.domain.cloud.AuthAttempt
import com.pelonot.domain.cloud.CredentialCheck
import com.pelonot.domain.cloud.PairingState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Which half of the account screen the rider is looking at.
 *
 * Sign-in and sign-up are one screen with a switch rather than two
 * destinations, because on a bike the commonest reason to be on either is
 * having chosen the wrong one.
 */
enum class AccountMode { SignIn, SignUp }

data class AccountUiState(
    /** The profile this action would attach to — always the selected rider. */
    val profile: UserEntity? = null,
    val session: AccountState = AccountState.Unknown,
    val mode: AccountMode = AccountMode.SignIn,
    val email: String = "",
    val password: String = "",
    val repeatedPassword: String = "",
    val busy: Boolean = false,
    /** Set only after a submit, so a half-typed address is not an error yet. */
    val problem: String? = null,
    /** The address a confirmation email has just gone to (15.1.2a). */
    val awaitingConfirmationFor: String? = null,
    val cloudConfigured: Boolean = true,
    /** How many of this profile's rides would go up on signing in (15.3.1). */
    val ridesWaiting: Int = 0,
    /** Signing in by scanning a code, if this build has a page to point at. */
    val pairing: PairingState = PairingState.Idle,
    val pairingAvailable: Boolean = false,
    /** Redrawn once a second while a code is up, for the countdown alone. */
    val nowMs: Long = 0L
) {
    val isGuest: Boolean get() = profile == null

    /**
     * True when this tablet's session belongs to the profile on screen.
     *
     * Not the same as "somebody is signed in" — see 15.2.8. A household bike
     * can be carrying Simon's session while Priya is the selected rider, and
     * that state has to be sayable.
     */
    val signedInAsThisProfile: Boolean
        get() = session is AccountState.SignedIn &&
            profile?.authUserId != null &&
            session.accountId == profile.authUserId

    /** Somebody else's session is on this tablet. */
    val signedInAsSomebodyElse: Boolean
        get() = session is AccountState.SignedIn && !signedInAsThisProfile

    val emailProblem: String? get() = if (problem == null) null else
        CredentialCheck.emailProblem(email)

    val canSubmit: Boolean
        get() = !busy && CredentialCheck.canSubmit(
            email,
            password,
            if (mode == AccountMode.SignUp) repeatedPassword else null
        )
}

/**
 * Backs the account screen (PLAN 15.1, 15.2).
 *
 * It holds the form because a form is screen state, and it holds nothing else:
 * whether there is a session is [AccountRepository]'s answer, and whose rides
 * are waiting is the database's. The one piece of judgement here is what to do
 * *after* a successful sign-in, which is 15.3.1 — start the backfill — and it
 * is one line because the backfill and the ordinary backlog drain are the same
 * worker (14.2.6).
 */
@Suppress("OPT_IN_USAGE") // flatMapLatest
class AccountViewModel(
    private val accountRepository: AccountRepository,
    private val userRepository: UserRepository,
    private val settingsRepository: SettingsRepository,
    private val workoutRepository: WorkoutRepository,
    private val deviceLinkRepository: DeviceLinkRepository
) : ViewModel() {

    private val form = MutableStateFlow(FormState())

    private val pairing = MutableStateFlow<PairingState>(PairingState.Idle)

    /**
     * A clock, running only while a code is on screen.
     *
     * It exists for the countdown and for nothing else, and it stops the moment
     * the pairing does — a ViewModel ticking once a second for the life of the
     * process is a battery cost on a tablet that spends its life awake.
     */
    private val now = MutableStateFlow(0L)

    private var pollJob: Job? = null

    private val profile = settingsRepository.settings
        .map { it.lastProfileId }
        .flatMapLatest { id ->
            if (id == null) flowOf(null) else userRepository.observeUser(id)
        }

    private val backlog = settingsRepository.settings
        .map { it.lastProfileId }
        .flatMapLatest { id ->
            if (id == null) flowOf(0) else workoutRepository.observeBacklog(id).map { it.pending }
        }

    private val pairingState = combine(pairing, now) { state, tick -> state to tick }

    val uiState: StateFlow<AccountUiState> = combine(
        profile,
        accountRepository.accountState,
        form,
        backlog,
        pairingState
    ) { profile, session, form, waiting, (pairing, tick) ->
        AccountUiState(
            profile = profile,
            session = session,
            mode = form.mode,
            email = form.email,
            password = form.password,
            repeatedPassword = form.repeatedPassword,
            busy = form.busy,
            problem = form.problem,
            awaitingConfirmationFor = form.awaitingConfirmationFor,
            cloudConfigured = accountRepository.cloudConfigured,
            ridesWaiting = waiting,
            pairing = pairing,
            pairingAvailable = deviceLinkRepository.pairingAvailable,
            nowMs = tick
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = AccountUiState(cloudConfigured = accountRepository.cloudConfigured)
    )

    fun setMode(mode: AccountMode) = form.update {
        // The problem belongs to the form the rider has left. Carrying it over
        // means switching to Sign up to read "that email and password do not
        // match an account", which is now a sentence about nothing.
        it.copy(mode = mode, problem = null, awaitingConfirmationFor = null)
    }

    fun setEmail(value: String) = form.update { it.copy(email = value, problem = null) }

    fun setPassword(value: String) = form.update { it.copy(password = value, problem = null) }

    fun setRepeatedPassword(value: String) =
        form.update { it.copy(repeatedPassword = value, problem = null) }

    /**
     * @param onSignedIn called only when a session exists **and** it is
     *   attached, so the screen can leave. Sign-up usually does not reach it —
     *   that path ends on the confirm-your-email state instead.
     */
    fun submit(onSignedIn: () -> Unit) {
        val snapshot = form.value
        val localUserId = uiState.value.profile?.localUserId ?: return

        val problem = CredentialCheck.emailProblem(snapshot.email)
            ?: CredentialCheck.passwordProblem(
                snapshot.password,
                if (snapshot.mode == AccountMode.SignUp) snapshot.repeatedPassword else null
            )
        if (problem != null) {
            form.update { it.copy(problem = problem) }
            return
        }

        form.update { it.copy(busy = true, problem = null) }
        viewModelScope.launch {
            val outcome = when (snapshot.mode) {
                AccountMode.SignIn ->
                    accountRepository.signIn(localUserId, snapshot.email, snapshot.password)

                AccountMode.SignUp ->
                    accountRepository.signUp(localUserId, snapshot.email, snapshot.password)
            }
            resolve(outcome, localUserId, onSignedIn)
        }
    }

    fun resendConfirmation() {
        val address = form.value.awaitingConfirmationFor ?: return
        form.update { it.copy(busy = true) }
        viewModelScope.launch {
            val outcome = ServiceLocator.authRepository.resendConfirmation(address)
            form.update {
                it.copy(
                    busy = false,
                    problem = (outcome as? AuthAttempt.Failed)?.message
                )
            }
        }
    }

    /**
     * Asks for a code and starts watching for the phone (15.6.6).
     *
     * The loop it starts owns three responsibilities and they are deliberately
     * in one place: tick the countdown, poll, and **stop**. A pairing that
     * outlives its code is a screen inviting a rider to scan something the
     * server has already forgotten.
     */
    fun startPairing(onSignedIn: () -> Unit) {
        val localUserId = uiState.value.profile?.localUserId ?: return
        pollJob?.cancel()
        pairing.value = PairingState.Starting

        pollJob = viewModelScope.launch {
            val started = deviceLinkRepository.begin()
            pairing.value = started
            val waiting = started as? PairingState.Waiting ?: return@launch

            while (isActive) {
                now.value = System.currentTimeMillis()

                if (waiting.hasExpired(now.value)) {
                    deviceLinkRepository.forget()
                    pairing.value = PairingState.Expired
                    return@launch
                }

                val handover = deviceLinkRepository.poll(waiting.code)
                if (handover != null) {
                    pairing.value = PairingState.Completing
                    val adopted = deviceLinkRepository.adopt(handover)
                    // The same attach as the typed path, deliberately: if
                    // pairing had its own copy of 15.2's rules, one of the two
                    // would drift and it would be this one.
                    resolve(
                        accountRepository.adoptSession(localUserId, adopted),
                        localUserId,
                        onSignedIn
                    )
                    if (adopted is AuthAttempt.Failed) {
                        pairing.value = PairingState.Failed(adopted.message)
                    } else {
                        pairing.value = PairingState.Idle
                    }
                    return@launch
                }

                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun cancelPairing() {
        pollJob?.cancel()
        pollJob = null
        deviceLinkRepository.forget()
        pairing.value = PairingState.Idle
    }

    fun signOut() {
        val localUserId = uiState.value.profile?.localUserId ?: return
        form.update { it.copy(busy = true) }
        viewModelScope.launch {
            val outcome = accountRepository.signOut(localUserId)
            form.update {
                it.copy(
                    busy = false,
                    problem = (outcome as? AuthAttempt.Failed)?.message,
                    // A rider who has just signed out is looking at a sign-in
                    // form, not at the sign-up one they may have arrived by.
                    mode = AccountMode.SignIn,
                    password = "",
                    repeatedPassword = ""
                )
            }
        }
    }

    private suspend fun resolve(outcome: AuthAttempt, localUserId: Int, onSignedIn: () -> Unit) {
        when (outcome) {
            is AuthAttempt.Success -> {
                form.update {
                    it.copy(busy = false, password = "", repeatedPassword = "", problem = null)
                }
                // 15.3.1. The backfill is the ordinary backlog drain with a
                // sign-in as its trigger — one implementation rather than two
                // that drift, which is the 18.9 argument applied early.
                WorkoutSyncWorker.enqueueIfAllowed(
                    context = ServiceLocator.appContext,
                    workoutId = "first-sign-in",
                    userId = localUserId
                )
                onSignedIn()
            }

            is AuthAttempt.ConfirmationRequired -> form.update {
                it.copy(
                    busy = false,
                    password = "",
                    repeatedPassword = "",
                    awaitingConfirmationFor = outcome.email
                )
            }

            AuthAttempt.SignedOut -> form.update { it.copy(busy = false) }

            AuthAttempt.Disabled -> form.update {
                it.copy(
                    busy = false,
                    // Not "the cloud is disabled", which is a fact about a
                    // developer's local.properties (23.1.5). It is a build with
                    // no cloud in it, and the screen should not be reachable.
                    problem = "This copy of Pelonot has no cloud to sign in to"
                )
            }

            is AuthAttempt.Failed -> form.update {
                it.copy(busy = false, problem = outcome.message)
            }
        }
    }

    private data class FormState(
        val mode: AccountMode = AccountMode.SignIn,
        val email: String = "",
        val password: String = "",
        val repeatedPassword: String = "",
        val busy: Boolean = false,
        val problem: String? = null,
        val awaitingConfirmationFor: String? = null
    )

    private inline fun MutableStateFlow<FormState>.update(block: (FormState) -> FormState) {
        value = block(value)
    }

    override fun onCleared() {
        // The loop runs in viewModelScope so it is cancelled anyway; forgetting
        // the secret is the part that matters, because a secret still in memory
        // is one a later poll could use against a code the rider abandoned.
        deviceLinkRepository.forget()
        super.onCleared()
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L
        private const val POLL_INTERVAL_MS = DeviceLinkRepository.POLL_INTERVAL_MS

        val Factory = viewModelFactory {
            AccountViewModel(
                accountRepository = ServiceLocator.accountRepository,
                userRepository = ServiceLocator.userRepository,
                settingsRepository = ServiceLocator.settingsRepository,
                workoutRepository = ServiceLocator.workoutRepository,
                deviceLinkRepository = ServiceLocator.deviceLinkRepository
            )
        }
    }
}
