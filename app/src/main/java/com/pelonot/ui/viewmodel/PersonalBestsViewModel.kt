package com.pelonot.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pelonot.data.repository.SettingsRepository
import com.pelonot.data.repository.WorkoutRepository
import com.pelonot.di.ServiceLocator
import com.pelonot.domain.progress.PersonalBests
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The rider's bests by duration, computed when the screen asks for them
 * (PLAN 16.3.3).
 *
 * Its own ViewModel rather than another flow on `AppViewModel`, because this
 * one **reads every measured ride's samples**. On `AppViewModel` it would run
 * at every launch for every rider whether or not anybody ever opened the
 * screen, which is the wrong side of the trade for a screen most riders visit
 * occasionally.
 *
 * Off the main thread, once per entry, same as the ride charts (16.2.3): the
 * FTP trend is already on screen and this arrives underneath it.
 */
class PersonalBestsViewModel(
    private val workoutRepository: WorkoutRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _bests = MutableStateFlow(PersonalBests(isLoading = true))
    val bests: StateFlow<PersonalBests> = _bests.asStateFlow()

    init {
        viewModelScope.launch {
            val profileId = settingsRepository.settings.first().lastProfileId
            if (profileId == null) {
                // A guest's rides are filed against nobody, so there is no
                // "their best" to compute — the same reason the FTP trend on
                // this screen is empty for them.
                _bests.value = PersonalBests()
                return@launch
            }
            val computed = withContext(Dispatchers.Default) {
                workoutRepository.personalBests(profileId)
            }
            _bests.value = computed
        }
    }

    companion object {
        val Factory = viewModelFactory {
            PersonalBestsViewModel(
                workoutRepository = ServiceLocator.workoutRepository,
                settingsRepository = ServiceLocator.settingsRepository
            )
        }
    }
}
