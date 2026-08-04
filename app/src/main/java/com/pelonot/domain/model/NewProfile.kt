package com.pelonot.domain.model

import com.pelonot.data.local.entity.FtpChangeSource

/**
 * Everything a new profile carries out of `ProfileCreationScreen`.
 *
 * A single value rather than six parameters, because 7.9/7.10.3's rule applies
 * to the call as well as to the write: **one tap is one write**, and a screen
 * that hands its fields back one at a time invites a caller to save them one at
 * a time. That defect has already happened here once, in Settings, where two
 * coroutines doing read-modify-write on one row ate each other's field.
 */
data class NewProfile(
    val name: String,
    val weightKg: Double?,
    val ftpWatts: Int,
    /**
     * [FtpChangeSource.Estimated] when the app worked the number out, or
     * [FtpChangeSource.ProfileCreated] when the rider opened the escape hatch
     * and typed one (20.3.4). The distinction is the point of the screen and
     * must survive the trip to `UserRepository.save`.
     */
    val ftpSource: FtpChangeSource,
    val birthDate: Long?,
    val fitnessLevel: FitnessLevel?
)
