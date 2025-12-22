package com.intagri.mtgleader.ui.setup.tabletop

import androidx.lifecycle.*
import com.intagri.mtgleader.model.player.PlayerColor
import com.intagri.mtgleader.model.player.PlayerSetupModel
import com.intagri.mtgleader.model.player.PlayerProfileModel
import com.intagri.mtgleader.persistence.ProfileRepository
import com.intagri.mtgleader.ui.settings.profiles.manage.ProfileUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SelectPlayerOptionsViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private var loading = false

    private var allProfiles: Set<PlayerProfileModel> = setOf()

    private val _profiles: MutableLiveData<List<ProfileUiModel>> =
        MutableLiveData(emptyList())
    val profiles: LiveData<List<ProfileUiModel>> = _profiles

    private val _setupModel: MutableLiveData<PlayerSetupModel> =
        MutableLiveData()
    val setupModel: LiveData<PlayerSetupModel> = _setupModel


    init {
        val setupModel =
            savedStateHandle.get<PlayerSetupModel>(SelectPlayerOptionsDialogFragment.ARGS_MODEL)
                ?: throw IllegalArgumentException("Model must be passed to ${javaClass.simpleName}")
        _setupModel.value = setupModel
        refresh()
    }

    fun refresh() {
        if (loading) {
            return
        }
        loading = true
        viewModelScope.launch {
            profileRepository.getAllPlayerProfiles()
                .catch {
                    loading = false
                    //TODO error handling?
                }
                .collect { data ->
                    loading = false
                    allProfiles = data.toSet()
                    _profiles.value = data.map {
                        ProfileUiModel(it)
                    }
                }
        }
    }

    fun updateColor(color: PlayerColor) {
        _setupModel.value = _setupModel.value?.copy(color = color)
    }

    fun updateProfile(profileName: String) {
        allProfiles.find {
            it.name == profileName
        }?.let {
            _setupModel.value = _setupModel.value?.copy(profile = it)
        }
    }

}