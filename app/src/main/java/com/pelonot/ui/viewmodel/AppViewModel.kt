package com.pelonot.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pelonot.data.local.entity.FtpChangeSource
import com.pelonot.domain.identity.Avatar
import com.pelonot.domain.model.NewProfile
import com.pelonot.data.local.entity.UserEntity
import com.pelonot.data.local.entity.WorkoutEntity
import com.pelonot.data.repository.AppSettings
import com.pelonot.data.repository.ClassPlan
import com.pelonot.data.repository.ClassRepository
import com.pelonot.data.repository.DashboardStats
import com.pelonot.domain.backup.BackupReminder
import com.pelonot.domain.progress.FtpPoint
import com.pelonot.domain.progress.FtpTrend
import com.pelonot.domain.progress.RiderLevel
import com.pelonot.domain.progress.RidingHistory
import com.pelonot.domain.progress.RidingTotals
import com.pelonot.domain.social.HouseholdRider
import com.pelonot.domain.chart.ClassProfile
import com.pelonot.domain.suggest.ClassSuggestion
import com.pelonot.domain.suggest.ClassToRide
import com.pelonot.domain.suggest.RiderRides
import com.pelonot.domain.suggest.SuggestableClass
import com.pelonot.data.repository.SettingsRepository
import com.pelonot.data.repository.UserRepository
import com.pelonot.data.repository.WorkoutRepository
import com.pelonot.data.service.ActiveRide
import com.pelonot.data.service.RideInProgress
import com.pelonot.di.ServiceLocator
import com.pelonot.data.remote.SupabaseSyncRepository
import com.pelonot.domain.model.ClassLeaderboard
import com.pelonot.domain.social.ClassRival
import com.pelonot.domain.model.RideInterruption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * An orphaned ride and how long it has been one (8.3d).
 *
 * The two travel together because the second decides what may be offered for
 * the first: *resume* is only an honest answer while picking the ride up is
 * still the same ride, which is a question about elapsed time rather than about
 * the row.
 *
 * [interruption] is nullable because the row can go between the two reads that
 * produce this, and an absent answer must not read as a resumable one.
 */
data class InterruptedRide(
    val workout: WorkoutEntity,
    val interruption: RideInterruption?
) {
    /** Whether *resume* is one of the answers the rider may give. */
    val canResume: Boolean get() = interruption?.isResumable == true
}

/**
 * Everything the navigation graph needs, sourced from Room and DataStore.
 *
 * @property isLoading True until the first emission from every source, so the
 *   UI can tell "no profiles yet" apart from "profiles not loaded yet". The
 *   previous code could not distinguish these and briefly rendered an empty
 *   profile grid on every launch.
 */
data class AppUiState(
    val settings: AppSettings = AppSettings(),
    val profiles: List<UserEntity> = emptyList(),
    val classes: List<ClassPlan> = emptyList(),
    val dashboardStats: DashboardStats = DashboardStats(),
    /**
     * Who else on this bike has ridden in the last 30 days (24.2, 22.5.4). Empty for a
     * household of one, for a household that has opted out, and for a week
     * nobody rode — all of which draw nothing.
     */
    val householdRecent: List<HouseholdRider> = emptyList(),
    /**
     * The selected rider's FTP over time (PLAN 7.10.2 / 22.1.4). Empty for a
     * guest, and a single point for a rider whose FTP has never moved — both of
     * which draw no trend.
     */
    val ftpTrend: FtpTrend = FtpTrend(),
    /** How much and how often, for the dashboard's card and its screen (16.3.2, 16.3.5). */
    val ridingHistory: RidingHistory = RidingHistory(),
    /**
     * Every profile's level (26.4), keyed by profile.
     *
     * The one figure in this state that is not windowed, and the only one that
     * cannot go down. A profile with no finished rides is **absent** rather
     * than present at level 1 — [levelFor] is what turns each absence into the
     * answer a screen should draw, and the two absences differ: level 1 for a
     * rider who has not ridden, no level at all for a guest.
     */
    val riderLevels: Map<Int, RiderLevel> = emptyMap(),
    /**
     * The class the dashboard offers, and why (22.8.6). Null only while the
     * library is still loading — a rider with no history has a suggestion too,
     * and is the one who needs it most.
     */
    val suggestion: ClassSuggestion? = null,
    /**
     * The shape of that same class — blocks, at their zone, across its length
     * (22.9.4).
     *
     * **Derived beside [suggestion] and from its own id**, never looked up
     * again by the screen that draws it. A profile fetched a second time is a
     * second answer to *which class is this*, and the failure it produces is
     * the worst kind: a card naming one class and drawing the shape of
     * another, which looks like a working feature. Same family as 20.4.7's two
     * pairing triggers and 12.7's two effort cards.
     *
     * Null when the library has not loaded, and empty-blocked when the class's
     * `intervals_json` would not decode — `ClassProfileChart` draws nothing for
     * either, which is the honest answer rather than a placeholder.
     */
    val suggestionProfile: ClassProfile? = null,
    /**
     * How much riding a backup would be protecting (PLAN 23.3.1). Only *due*
     * once ten rides have gone by unprotected, and the dashboard draws nothing
     * until then.
     */
    val backupReminder: BackupReminder = BackupReminder.None,
    val isLoading: Boolean = true,
    /**
     * A ride the app was killed in the middle of. Non-null means the rider is
     * about to be asked what to do with it.
     */
    val recoverableWorkout: InterruptedRide? = null,
    /**
     * A ride recording right now (11.1a.5). Non-null on a cold start means the
     * app was opened while a class was already running — from the notification,
     * the launcher, or the strip after the task was swiped away — and the rider
     * is looking for the ride, not the profile picker.
     */
    val activeRide: ActiveRide? = null
) {
    val selectedProfile: UserEntity?
        get() = profiles.firstOrNull { it.localUserId == settings.lastProfileId }

    /**
     * One profile's level, with both absent cases answered here rather than at
     * every call site (26.4).
     *
     * **A profile with no rides is level 1; a guest has no level at all**, and
     * the difference is the whole of it. Level 1 is *the start* — a real answer
     * to a rider whose first ride will move it. A guest's rides are filed
     * against nobody, so a guest can never leave level 1 however much they
     * ride: drawing the badge for them would promise a ladder that does not
     * exist. Absent is a claim, and it is a different claim from 1.
     */
    fun levelFor(profileId: Int?): RiderLevel? =
        if (profileId == null) null else riderLevels[profileId] ?: RiderLevel.of(RidingTotals())

    /** The rider whose dashboard is on screen, or null for a guest. */
    val selectedRiderLevel: RiderLevel? get() = levelFor(settings.lastProfileId)
}

/**
 * Replaces reading Room directly from inside composables, which ran database
 * queries on every recomposition path and lost all its state on rotation.
 */
@Suppress("OPT_IN_USAGE") // flatMapLatest
class AppViewModel(
    private val settingsRepository: SettingsRepository,
    private val userRepository: UserRepository,
    classRepository: ClassRepository,
    private val workoutRepository: WorkoutRepository,
    private val syncRepository: SupabaseSyncRepository
) : ViewModel() {

    /**
     * Checked once at launch rather than through `WorkoutService`.
     *
     * The service does expose this, but nothing binds to it on a cold start —
     * which is exactly the case a crash leaves behind — so the prompt would
     * never have appeared.
     */
    private val _recoverableWorkout = MutableStateFlow<InterruptedRide?>(null)

    init {
        viewModelScope.launch { refreshRecoverableWorkout() }
    }

    /**
     * Re-reads the orphan, if any, together with how long it has been sitting
     * there (8.3d).
     *
     * The two are read at the same moment and kept together because the second
     * decides what the rider may be offered for the first: *resume* is only an
     * honest option while picking the ride up is still the same ride, and that
     * is a question about elapsed time, not about the row.
     */
    private suspend fun refreshRecoverableWorkout() {
        val workout = workoutRepository.findRecoverableWorkout()
        _recoverableWorkout.value = workout?.let {
            InterruptedRide(
                workout = it,
                interruption = workoutRepository.interruptionFor(it.id)
            )
        }
    }

    private val dashboardStats = settingsRepository.settings
        .map { it.lastProfileId }
        .flatMapLatest { profileId ->
            // A guest has no history to summarise.
            if (profileId == null) flowOf(DashboardStats())
            else workoutRepository.observeDashboardStats(profileId)
        }

    /**
     * The two ride-shaped questions, paired so the state combine stays inside
     * the five-flow typed overload: is there a ride to recover, and is there
     * one running right now. They are mutually exclusive by construction —
     * 8.3b excludes the live ride from the first — and the UI treats them very
     * differently, so they travel together and are read apart.
     */
    private val rideStatus = combine(
        _recoverableWorkout,
        RideInProgress.active
    ) { recoverable, active -> recoverable to active }

    /** The selected rider's FTP over time (7.10.2, and 16.3.1's screen). */
    private val ftpTrend = settingsRepository.settings
        .map { it.lastProfileId }
        .flatMapLatest { profileId ->
            // A guest has no profile, so no FTP and no history of one.
            if (profileId == null) flowOf(FtpTrend())
            else userRepository.observeFtpHistory(profileId).map { entries ->
                FtpTrend(
                    entries.map { entry ->
                        FtpPoint(
                            watts = entry.ftpWatts,
                            atEpochMs = entry.changedAt,
                            source = entry.source,
                            workoutId = entry.workoutId
                        )
                    }
                )
            }
        }

    /** The selected rider's weeks (16.3.2, 16.3.5), for the same card-then-screen pair. */
    private val ridingHistory = settingsRepository.settings
        .map { it.lastProfileId }
        .flatMapLatest { profileId ->
            // A guest's ride is not filed against anybody, so there is no
            // "their riding" to draw — the same reason the FTP trend is empty.
            if (profileId == null) flowOf(RidingHistory())
            else workoutRepository.observeRidingHistory(profileId)
        }

    /**
     * What the rider has ridden, for the class the dashboard offers (22.8.6).
     *
     * A guest gets `RiderRides()` rather than nothing, and that is the
     * interesting case: with no history the rule returns its first-ride
     * suggestion, so a guest — and a brand-new profile — still gets an answer to
     * *what should I ride*. It is the rider who most needs one.
     */
    private val riderRides = settingsRepository.settings
        .map { it.lastProfileId }
        .flatMapLatest { profileId ->
            if (profileId == null) flowOf(RiderRides())
            else workoutRepository.observeRiderRides(profileId)
        }

    /**
     * How many rides have been recorded since the last backup — or since the
     * last "not now", whichever is later (23.3.1).
     *
     * Counted across the whole tablet rather than for the selected profile,
     * because the backup file is the whole database: a housemate's rides and a
     * guest's ride are equally in it and equally lost without it.
     */
    private val backupReminder = settingsRepository.settings
        .map { it.backupMarkAtMs to it.hasEverBackedUp }
        .distinctUntilChanged()
        .flatMapLatest { (markedAt, everBackedUp) ->
            workoutRepository.observeCompletedSince(markedAt ?: 0L).map { count ->
                BackupReminder(ridesSinceMark = count, hasEverBackedUp = everBackedUp)
            }
        }

    /**
     * The flows about the rider's own riding, travelling together for the same
     * reason [rideStatus] is a pair: the typed `combine` overload stops at five
     * and [dashboard] is already at it.
     */
    private val riding = combine(
        ridingHistory,
        riderRides,
        workoutRepository.observeRiderLevels()
    ) { history, rides, levels -> RiderState(history, rides, levels) }

    /** [riding]'s three flows, named rather than nested in a `Pair` (see [DashboardState]). */
    private data class RiderState(
        val ridingHistory: RidingHistory,
        val riderRides: RiderRides,
        /**
         * Every profile's level (26.4), keyed by profile — **one map for three
         * surfaces**: the greeting, the household panel and the profile
         * selector. A per-profile query beside it would be a second answer to
         * the same question, and two answers is how two screens on one tablet
         * come to show a rider two different numbers.
         */
        val riderLevels: Map<Int, RiderLevel>
    )

    private val dashboard = combine(
        dashboardStats,
        workoutRepository.observeHousehold(),
        ftpTrend,
        backupReminder,
        riding
    ) { stats, household, ftp, backup, rider ->
        DashboardState(stats, household, ftp, backup, rider.ridingHistory, rider.riderRides, rider.riderLevels)
    }

    /**
     * The dashboard-shaped flows, travelling together for the same reason
     * [rideStatus] does: the typed `combine` overload stops at five, and one
     * screen reads all of these. A named class rather than a `Triple` now there
     * are four of them — nesting a `Pair` inside a `Triple` to keep counting is
     * where a destructuring bug goes to hide.
     */
    private data class DashboardState(
        val stats: DashboardStats,
        val household: List<HouseholdRider>,
        val ftpTrend: FtpTrend,
        val backupReminder: BackupReminder,
        val ridingHistory: RidingHistory,
        val riderRides: RiderRides,
        val riderLevels: Map<Int, RiderLevel>
    )

    val uiState: StateFlow<AppUiState> = combine(
        settingsRepository.settings,
        userRepository.allUsers,
        classRepository.allPlans,
        dashboard,
        rideStatus
    ) { settings, profiles, classes, dashboard, (recoverable, active) ->
        // Computed here rather than in a flow of its own because it is a
        // function of two things the state already carries — the library and
        // the rider's rides — and a third flow that re-derives one of them is a
        // second answer to the same question.
        val suggested = ClassToRide.suggest(
            library = classes.map { it.toSuggestable() },
            rides = dashboard.riderRides,
            // Read once, at the moment the state is built. The rule's only use
            // of the clock is "did they ride hard in the last day", and that
            // must not change under a rider looking at the card.
            nowMs = System.currentTimeMillis()
        )
        AppUiState(
            settings = settings,
            profiles = profiles,
            classes = classes,
            dashboardStats = dashboard.stats,
            householdRecent = dashboard.household,
            ftpTrend = dashboard.ftpTrend,
            backupReminder = dashboard.backupReminder,
            ridingHistory = dashboard.ridingHistory,
            riderLevels = dashboard.riderLevels,
            suggestion = suggested,
            // The shape of the class the line above named, resolved from that
            // suggestion's own id so the two can never describe different
            // classes (22.9.4).
            suggestionProfile = suggested?.let { s ->
                classes.firstOrNull { it.id == s.classId }
                    ?.let { ClassProfile.of(it.intervals) }
            },
            isLoading = false,
            recoverableWorkout = recoverable,
            activeRide = active
        )
    }.stateIn(
        scope = viewModelScope,
        // Keeps the state alive briefly across configuration changes so a
        // rotation does not re-query the database.
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = AppUiState()
    )

    /**
     * The household's board for one class (24.1.2).
     *
     * Fetched on demand rather than carried in [AppUiState]: it belongs to
     * whichever class is on screen, not to the app, and the state combine is
     * already at the width of its typed overload.
     */
    /**
     * The board for one class: everyone on this bike, plus everyone registered
     * (PLAN 24.1, 18.5).
     *
     * The cloud half is a lambda the repository calls, so the repository can
     * stay free of the network and the gate stays where it belongs. A rider
     * with no account, or no wifi, gets the household board and no delay worth
     * noticing — `SyncOutcome.Disabled` returns without a request.
     */
    suspend fun householdLeaderboard(classId: String, youId: Int?): ClassLeaderboard =
        workoutRepository.classLeaderboard(
            classId = classId,
            youId = youId,
            yourAccountId = ServiceLocator.authRepository.currentAccountId(),
            cloudStandings = {
                syncRepository.classLeaderboard(classId, youId).valueOrNull().orEmpty()
                    .map { row ->
                        ClassLeaderboard.Standing(
                            // A cloud rider has no local profile, and saying so
                            // with null is what lets the merge in
                            // `ClassLeaderboard.of` recognise the overlap.
                            localUserId = null,
                            accountId = row.accountId,
                            name = row.name,
                            outputKj = row.outputKj,
                            weightKg = row.weightKg,
                            source = ClassLeaderboard.Source.Cloud
                        )
                    }
            }
        )

    /**
     * Rides of this class that can be raced live (24.3.3).
     *
     * Household only, and never the cloud: a ghost needs the rival's
     * second-by-second series, which only exists on this tablet for a ride
     * recorded on it. That is the same reason 24.3.1's chart comparison is
     * household-only, and it is not a gap to be filled later without saying
     * so — 18.12 is where the network's version of this belongs.
     */
    suspend fun classRivals(classId: String, youId: Int?): List<ClassRival> =
        workoutRepository.rivalsForClass(classId, youId)

    /**
     * One tap is one write (7.10.3). Every field the screen collected goes into
     * a single [UserEntity] and a single `save`, rather than a create followed
     * by three updates — which is the shape that let Settings' `setFtp` and
     * `setWeight` eat each other's field off one press of Save.
     */
    fun createProfile(profile: NewProfile, onCreated: (Int) -> Unit) {
        viewModelScope.launch {
            val saved = userRepository.save(
                UserEntity(
                    name = profile.name.trim(),
                    weightKg = profile.weightKg ?: DEFAULT_WEIGHT_KG,
                    ftpWatts = profile.ftpWatts,
                    birthDate = profile.birthDate,
                    fitnessLevel = profile.fitnessLevel?.id,
                    // Null when the rider walked past the face step without
                    // touching it (20.6.2). `Avatar.defaultFor` answers for
                    // them from the row id at read time, and the column keeps
                    // saying *never chose* — the distinction 20.2.2 exists for.
                    avatar = profile.avatar?.store()
                ),
                // 20.3.4: an estimate is not a claim the rider made, and the
                // funnel is where that distinction gets recorded.
                ftpSource = profile.ftpSource
            )
            settingsRepository.setLastProfileId(saved.localUserId)
            onCreated(saved.localUserId)
        }
    }

    fun selectProfile(userId: Int?) {
        viewModelScope.launch { settingsRepository.setLastProfileId(userId) }
    }

    /**
     * Renames a rider and sets their face, from the profile selector (20.1.5,
     * 20.2.3).
     *
     * **One call, because one tap of Save is one write.** Firing a rename and
     * an avatar change separately is precisely 7.9's defect — two coroutines
     * doing read-modify-write on one row, the second carrying a stale copy of
     * the field the first had just changed — and the reason it is worth naming
     * here is that the two look like independent edits from the dialog's side.
     */
    fun saveProfileIdentity(userId: Int, name: String, avatar: Avatar) {
        viewModelScope.launch { userRepository.updateIdentity(userId, name, avatar) }
    }

    /**
     * Removes a rider. Their rides survive as unattributed —
     * `workouts.user_id` is `ON DELETE SET NULL` — which the dialog that leads
     * here says out loud.
     */
    fun deleteProfile(userId: Int) {
        viewModelScope.launch {
            userRepository.delete(userId)
            // A selected profile that no longer exists would leave the
            // dashboard greeting a rider who has been removed.
            if (settingsRepository.settings.first().lastProfileId == userId) {
                settingsRepository.setLastProfileId(null)
            }
        }
    }

    /**
     * Keeps an interrupted ride, rebuilding its totals from the samples that
     * were written before the process died.
     */
    fun recoverWorkout(onRecovered: (String) -> Unit) {
        val workoutId = _recoverableWorkout.value?.workout?.id ?: return
        viewModelScope.launch {
            val recovered = workoutRepository.recoverWorkout(workoutId)
            // Re-query rather than clearing: a device that has crashed twice has
            // two orphaned rides, and answering for one should not silently
            // abandon the other.
            refreshRecoverableWorkout()
            if (recovered != null) onRecovered(recovered.id)
        }
    }

    /**
     * Carries on riding the interrupted ride (8.3d).
     *
     * The prompt is dismissed here rather than after the service has confirmed,
     * because the service's own answer arrives asynchronously and a dialog that
     * outlives the tap it answered is worse than one that closes optimistically:
     * the ride the rider has just chosen to resume is excluded from the recovery
     * query the moment `RideInProgress` knows about it (8.3d.4), so re-querying
     * would race that and could offer the same ride back.
     */
    fun resumeWorkout(onResuming: (String) -> Unit) {
        val interrupted = _recoverableWorkout.value ?: return
        if (interrupted.canResume) {
            _recoverableWorkout.value = null
            onResuming(interrupted.workout.id)
        }
    }

    fun discardRecoverableWorkout() {
        viewModelScope.launch {
            workoutRepository.clearRecoverableWorkouts()
            _recoverableWorkout.value = null
        }
    }


    /**
     * Puts back the value an auto-FTP change replaced (7.10.4).
     *
     * **Appends a row rather than erasing one.** The app moving somebody's FTP
     * by itself is the app editing their own record, and an undo that deleted
     * the row would be a second edit covering the first — leaving a history
     * that says nothing ever happened, which is exactly the state 7.9 exists to
     * make impossible.
     *
     * It carries no `workoutId`: the ride caused the change being undone, not
     * this one, and pointing at it would read as "this ride said 215".
     */
    fun revertFtpChange(toWatts: Int) {
        viewModelScope.launch {
            val profileId = settingsRepository.settings.first().lastProfileId ?: return@launch
            userRepository.updateFtp(
                userId = profileId,
                ftpWatts = toWatts,
                source = FtpChangeSource.AutoBreakthroughReverted
            )
        }
    }

    /**
     * "Not now" (23.3.1). Moves the line to today, so the rides already
     * recorded stop asking and the next ten earn the next reminder.
     *
     * It does not claim a backup happened, so a rider who has never made one is
     * still told so next time.
     */
    fun snoozeBackupReminder() {
        viewModelScope.launch { settingsRepository.snoozeBackupReminder() }
    }

    /**
     * "Don't ask me again" on the dashboard's account offer (15.8.4).
     *
     * Per profile — [UserRepository.dismissAccountOffer] — unlike
     * [snoozeBackupReminder], which is a device-wide mark. A household bike
     * has several riders and one of them dismissing this must not silence it
     * for the others.
     */
    fun dismissAccountOffer() {
        viewModelScope.launch {
            val profileId = settingsRepository.settings.first().lastProfileId ?: return@launch
            userRepository.dismissAccountOffer(profileId)
        }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L
        private const val DEFAULT_WEIGHT_KG = 70.0

        val Factory = viewModelFactory {
            AppViewModel(
                settingsRepository = ServiceLocator.settingsRepository,
                userRepository = ServiceLocator.userRepository,
                classRepository = ServiceLocator.classRepository,
                workoutRepository = ServiceLocator.workoutRepository,
                syncRepository = ServiceLocator.syncRepository
            )
        }
    }
}

/**
 * A class as the suggestion rule sees it (22.8.6).
 *
 * The hardest zone comes straight off the blocks rather than through
 * `ClassProfile`, which builds a whole drawable profile to answer it — this runs
 * over all 72 classes every time the dashboard state is rebuilt, and the rule
 * needs one integer.
 *
 * The mapping lives here rather than beside `ClassPlan` so that nothing in the
 * data layer has to know the rule exists.
 */
private fun ClassPlan.toSuggestable() = SuggestableClass(
    id = id,
    title = title,
    category = category,
    durationSec = durationSec,
    hardestZone = intervals.maxOfOrNull { it.powerZoneNumber }
)

/** Small helper so each ViewModel's factory is a single expression. */
inline fun <reified VM : ViewModel> viewModelFactory(
    crossinline create: () -> VM
): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
}
