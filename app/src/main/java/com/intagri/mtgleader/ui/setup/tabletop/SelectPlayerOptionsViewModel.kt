package com.intagri.mtgleader.ui.setup.tabletop

import androidx.lifecycle.*
import com.intagri.mtgleader.model.player.PlayerColor
import com.intagri.mtgleader.model.player.PlayerSetupModel
import com.intagri.mtgleader.model.player.PlayerProfileModel
import com.intagri.mtgleader.BuildConfig
import com.intagri.mtgleader.persistence.ProfileRepository
import com.intagri.mtgleader.persistence.friends.FriendAvatarStore
import com.intagri.mtgleader.persistence.friends.FriendDao
import com.intagri.mtgleader.persistence.userprofile.UserProfileLocalStore
import com.intagri.mtgleader.ui.settings.profiles.manage.ProfileUiModel
import com.intagri.mtgleader.util.TimestampUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SelectPlayerOptionsViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val friendDao: FriendDao,
    private val friendAvatarStore: FriendAvatarStore,
    private val userProfileLocalStore: UserProfileLocalStore,
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

    private val _assignableUsers: MutableLiveData<List<AssignableUser>> =
        MutableLiveData(emptyList())
    val assignableUsers: LiveData<List<AssignableUser>> = _assignableUsers


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
                    loadAssignableUsers()
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

    fun updateAssignedUser(user: AssignableUser?) {
        if (user == null || user.userId.isNullOrBlank()) {
            _setupModel.value = _setupModel.value?.copy(
                assignedUserId = null,
                assignedUsername = null,
                assignedDisplayName = null,
                assignedAvatarUrl = null,
            )
            return
        }
        _setupModel.value = _setupModel.value?.copy(
            assignedUserId = user.userId,
            assignedUsername = user.username,
            assignedDisplayName = user.displayName,
            assignedAvatarUrl = user.avatarUrl,
        )
    }

    fun updateTempName(name: String) {
        val trimmed = name.trim()
        val tempName = if (trimmed.isBlank()) null else trimmed
        if (_setupModel.value?.tempName == tempName) {
            return
        }
        _setupModel.value = _setupModel.value?.copy(tempName = tempName)
    }

    private fun loadAssignableUsers() {
        viewModelScope.launch {
            val options = mutableListOf<AssignableUser>()
            options.add(AssignableUser(null, "", null, false, null))
            val selfEntity = userProfileLocalStore.getEntity()
            if (selfEntity != null) {
                options.add(
                    AssignableUser(
                        userId = selfEntity.id,
                        displayName = selfEntity.displayName ?: selfEntity.username ?: "",
                        username = selfEntity.username,
                        isSelf = true,
                        avatarUrl = resolveAvatarUrl(
                            selfEntity.avatarPath,
                            selfEntity.avatarUpdatedAt
                        ),
                    )
                )
            }
            val friends = friendDao.getAllAccepted().first()
            for (friend in friends) {
                if (selfEntity?.id != null && friend.userId == selfEntity.id) {
                    continue
                }
                options.add(
                    AssignableUser(
                        userId = friend.userId,
                        displayName = friend.displayName ?: friend.username ?: friend.userId,
                        username = friend.username,
                        isSelf = false,
                        avatarUrl = resolveFriendAvatarUrl(
                            friend.userId,
                            friend.avatarPath,
                            friend.avatarUpdatedAt
                        ),
                    )
                )
            }
            _assignableUsers.value = options
        }
    }

    private fun resolveFriendAvatarUrl(
        userId: String,
        avatarPath: String?,
        avatarUpdatedAt: String?,
    ): String? {
        val cached = friendAvatarStore.getCachedAvatarUri(userId, avatarUpdatedAt)
        if (!cached.isNullOrBlank()) {
            return cached
        }
        return resolveAvatarUrl(avatarPath, avatarUpdatedAt)
    }

    private fun resolveAvatarUrl(path: String?, updatedAt: String?): String? {
        if (path.isNullOrBlank()) {
            return null
        }
        if (path.startsWith("http") || path.startsWith("file:") || path.startsWith("content:")) {
            return path
        }
        val epochSeconds = TimestampUtils.parseInstantSafe(updatedAt)?.epochSecond
            ?: (System.currentTimeMillis() / 1000)
        return BuildConfig.API_BASE_URL.trimEnd('/') + "/app/avatars/" + path + "?v=" + epochSeconds
    }

}

data class AssignableUser(
    val userId: String?,
    val displayName: String,
    val username: String?,
    val isSelf: Boolean,
    val avatarUrl: String?,
)
