package com.pelonot.data.repository

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pelonot.data.local.AppDatabase
import com.pelonot.data.local.entity.FtpChangeSource
import com.pelonot.data.local.entity.UserEntity
import com.pelonot.data.remote.CloudAccess
import com.pelonot.data.remote.SupabaseSyncRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The one funnel, and the rules that hang off it (PLAN 7.9).
 *
 * Written against `UserRepository` rather than against the DAO on purpose:
 * 7.9.4's whole claim is that **no path can change a rider's FTP without a
 * history row appearing**, and that is a property of the funnel, not of the
 * table. A test against `FtpHistoryDao.insert` would pass happily while the
 * feature quietly stopped working the next time somebody added a call site.
 *
 * The cloud never runs here: no profile has an `auth_user_id`, so `CloudAccess`
 * answers no and `syncProfile` returns `Disabled` without touching the network.
 * That is rule 1 of the connectivity model doing its job inside a test.
 */
@RunWith(AndroidJUnit4::class)
class FtpHistoryTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: UserRepository
    private var now = 1_000L

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        )
            // Foreign-key actions have to be live: 7.9.3 is one.
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .build()
        repository = UserRepository(
            database = database,
            userDao = database.userDao(),
            ftpHistoryDao = database.ftpHistoryDao(),
            syncRepository = SupabaseSyncRepository(CloudAccess(database.userDao())),
            clock = { now }
        )
    }

    @After
    fun tearDown() = database.close()

    private suspend fun newRider(ftp: Int = 200) =
        repository.save(UserEntity(name = "Test Rider", weightKg = 72.0, ftpWatts = ftp))

    @Test
    fun creatingAProfileRecordsTheFtpItStartedAt() = runBlocking {
        val rider = newRider(ftp = 205)

        val history = repository.ftpHistory(rider.localUserId)
        assertEquals(1, history.size)
        assertEquals(205, history.first().ftpWatts)
        // Whatever the caller said: a brand-new profile's first FTP is where the
        // number came from.
        assertEquals(FtpChangeSource.ProfileCreated.name, history.first().source)
        assertEquals(1_000L, history.first().changedAt)
    }

    @Test
    fun eachChangeIsRecordedInOrderWithItsReason() = runBlocking {
        val rider = newRider(ftp = 200)

        now = 2_000
        repository.updateFtp(rider.localUserId, 215, FtpChangeSource.ManualEdit)
        now = 3_000
        repository.updateFtp(
            userId = rider.localUserId,
            ftpWatts = 232,
            source = FtpChangeSource.AutoBreakthrough
        )

        val history = repository.ftpHistory(rider.localUserId)
        assertEquals(listOf(200, 215, 232), history.map { it.ftpWatts })
        assertEquals(
            listOf(
                FtpChangeSource.ProfileCreated.name,
                FtpChangeSource.ManualEdit.name,
                FtpChangeSource.AutoBreakthrough.name
            ),
            history.map { it.source }
        )
        assertEquals(listOf(1_000L, 2_000L, 3_000L), history.map { it.changedAt })
    }

    /**
     * 7.9.5. A re-save in Settings, a rename, or an idempotent pull from another
     * device must not fill the trend chart with vertical noise at the same
     * height.
     */
    @Test
    fun savingTheSameFtpAgainIsNotAChange() = runBlocking {
        val rider = newRider(ftp = 200)

        now = 2_000
        repository.updateFtp(rider.localUserId, 200, FtpChangeSource.ManualEdit)
        repository.updateFtp(rider.localUserId, 200, FtpChangeSource.PulledFromCloud)

        assertEquals(1, repository.ftpHistory(rider.localUserId).size)
    }

    /**
     * 7.9.4, and the reason the write lives in `save` rather than at the call
     * sites: **a path that changes FTP without knowing about the history still
     * produces a row.** Losing the reason is survivable; losing the change is
     * not, because it cannot be recovered afterwards from a column that was
     * overwritten.
     */
    @Test
    fun aPathThatChangesFtpWithoutSayingWhyStillLeavesARow() = runBlocking {
        val rider = newRider(ftp = 200)

        now = 2_000
        // No source named — this is the shape every future call site takes
        // before somebody remembers to pass one.
        repository.save(rider.copy(ftpWatts = 244))

        val history = repository.ftpHistory(rider.localUserId)
        assertEquals(listOf(200, 244), history.map { it.ftpWatts })
        assertEquals(FtpChangeSource.Unknown.name, history.last().source)
    }

    /** Changing something that is not the FTP is not an FTP change. */
    @Test
    fun renamingARiderDoesNotRecordAnFtpChange() = runBlocking {
        val rider = newRider(ftp = 200)

        now = 2_000
        repository.updateName(rider.localUserId, "Renamed")
        repository.updateWeight(rider.localUserId, 68.0)
        repository.setHouseholdVisible(rider.localUserId, false)

        assertEquals(1, repository.ftpHistory(rider.localUserId).size)
    }

    /**
     * The bug 7.9's own history found, kept as a test.
     *
     * Settings used to fire two coroutines off one tap of Save — one for FTP,
     * one for weight — each doing read-modify-write on the same profile row.
     * The weight write read the profile before the FTP write committed and put
     * the *old* FTP back on its way past, so typing a new FTP and pressing Save
     * left the old number in the database with the screen still showing the new
     * one. Invisible until a history table recorded two identical changes
     * twenty-three seconds apart.
     *
     * This asserts the property that made it impossible: **one save, one
     * write**, carrying both fields.
     */
    @Test
    fun savingFtpAndWeightTogetherKeepsBoth() = runBlocking {
        val rider = newRider(ftp = 200)

        now = 2_000
        repository.save(
            rider.copy(ftpWatts = 215, weightKg = 68.0),
            ftpSource = FtpChangeSource.ManualEdit
        )

        val stored = repository.getUser(rider.localUserId)!!
        assertEquals(215, stored.ftpWatts)
        assertEquals(68.0, stored.weightKg, 0.001)
        assertEquals(listOf(200, 215), repository.ftpHistory(rider.localUserId).map { it.ftpWatts })
    }

    /** One rider's history is theirs; a housemate's changes do not appear in it. */
    @Test
    fun historiesDoNotLeakBetweenRiders() = runBlocking {
        val rider = newRider(ftp = 200)
        val housemate = repository.save(
            UserEntity(name = "Housemate", weightKg = 64.0, ftpWatts = 180)
        )

        now = 2_000
        repository.updateFtp(housemate.localUserId, 195, FtpChangeSource.ManualEdit)

        assertEquals(listOf(200), repository.ftpHistory(rider.localUserId).map { it.ftpWatts })
        assertEquals(
            listOf(180, 195),
            repository.ftpHistory(housemate.localUserId).map { it.ftpWatts }
        )
    }
}
