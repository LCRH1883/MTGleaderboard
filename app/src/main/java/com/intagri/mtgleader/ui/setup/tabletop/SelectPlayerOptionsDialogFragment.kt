package com.intagri.mtgleader.ui.setup.tabletop

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.SpinnerAdapter
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import com.intagri.mtgleader.R
import com.intagri.mtgleader.databinding.FragmentSelectPlayerOptionsBinding
import com.intagri.mtgleader.model.player.PlayerColor
import com.intagri.mtgleader.model.player.PlayerSetupModel
import com.intagri.mtgleader.util.NetworkUtils
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SelectPlayerOptionsDialogFragment : DialogFragment() {

    companion object {
        fun newInstance(playerSetupModel: PlayerSetupModel): SelectPlayerOptionsDialogFragment {
            val args = Bundle()
            args.putParcelable(ARGS_MODEL, playerSetupModel)
            val fragment = SelectPlayerOptionsDialogFragment()
            fragment.arguments = args
            return fragment
        }

        const val ARGS_MODEL = "args_model"
        const val REQUEST_CUSTOMIZE = "request_customize"
        const val RESULT_MODEL = "result_model"
        const val TAG = "tag_select_player_options_fragment"
    }

    private var _binding: FragmentSelectPlayerOptionsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SelectPlayerOptionsViewModel by viewModels()

    private var spinnerAdapter: SpinnerAdapter? = null
    private val assignButtons = mutableListOf<AssignButton>()

    private val spinnerItemListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(
            parent: AdapterView<*>?,
            view: View?,
            position: Int,
            id: Long
        ) {
            viewModel.profiles.value?.get(position)?.name?.let {
                viewModel.updateProfile(it)
            }
        }

        override fun onNothingSelected(parent: AdapterView<*>?) {}
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)
        _binding = FragmentSelectPlayerOptionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener {
            dismiss()
        }

        val allColors = PlayerColor.allColors()
        val allColorInts = allColors.map {
            ContextCompat.getColor(requireContext(), it.resId!!)
        }.toIntArray()
        val colorMap = mutableMapOf<Int, PlayerColor>()
        allColorInts.forEachIndexed { index, color ->
            colorMap[color] = allColors[index]
        }

        binding.colorPickerView.colors = allColorInts

        binding.colorPickerView.setOnColorChangedListener {
            colorMap[it]?.let { color ->
                viewModel.updateColor(color)
            }
        }

        viewModel.setupModel.observe(viewLifecycleOwner) {
            binding.colorPickerView.setSelectedColor(
                allColorInts[allColors.indexOf(it.color)]
            )
            setProfileSpinnerSelection()
            updateAssignButtonSelection()
            if (binding.tempNameEditText.text.toString() != it.tempName.orEmpty()) {
                binding.tempNameEditText.setText(it.tempName.orEmpty())
            }
        }

        viewModel.profiles.observe(viewLifecycleOwner) { profiles ->
            val spinnerOptions = profiles.map {
                it.name
            }
            spinnerAdapter = ArrayAdapter(
                requireContext(),
                R.layout.item_spinner_text,
                spinnerOptions
            )
            binding.profileSpinner.adapter = spinnerAdapter
            setProfileSpinnerSelection()
        }

        viewModel.assignableUsers.observe(viewLifecycleOwner) { users ->
            renderAssignableUsers(users)
        }

        binding.saveButton.setOnClickListener {
            viewModel.setupModel.value?.let {
                val b = Bundle()
                b.putParcelable(RESULT_MODEL, it)
                setFragmentResult(REQUEST_CUSTOMIZE, b)
                dismiss()
            }
        }

        binding.tempNameEditText.doAfterTextChanged { text ->
            viewModel.updateTempName(text?.toString().orEmpty())
        }
    }

    private fun setProfileSpinnerSelection() {
        binding.profileSpinner.onItemSelectedListener = null
        val index = viewModel.profiles.value?.let { profiles ->
            profiles.indexOfFirst { viewModel.setupModel.value?.profile?.name == it.name }
        } ?: -1
        if (index != -1) {
            binding.profileSpinner.setSelection(index)
        }
        binding.profileSpinner.onItemSelectedListener = spinnerItemListener
    }

    private fun renderAssignableUsers(users: List<AssignableUser>) {
        val inflater = LayoutInflater.from(requireContext())
        assignButtons.clear()
        binding.assignPlayerList.removeAllViews()
        users.forEach { user ->
            val button = inflater.inflate(
                R.layout.item_assign_player_button,
                binding.assignPlayerList,
                false
            )
            val label = button.findViewById<TextView>(R.id.assign_player_label)
            val avatar = button.findViewById<ImageView>(R.id.assign_player_avatar)
            label.text = formatAssignableUserLabel(user)
            bindAvatar(avatar, user.avatarUrl)
            button.setOnClickListener {
                viewModel.updateAssignedUser(user)
                updateAssignButtonSelection()
            }
            binding.assignPlayerList.addView(button)
            assignButtons.add(AssignButton(user.userId, button))
        }
        updateAssignButtonSelection()
        val hasCachedFriends = users.any { !it.userId.isNullOrBlank() && !it.isSelf }
        val showOfflineHint = !hasCachedFriends && !NetworkUtils.isOnline(requireContext())
        binding.assignPlayerOfflineHint.visibility =
            if (showOfflineHint) View.VISIBLE else View.GONE
    }

    private fun updateAssignButtonSelection() {
        if (assignButtons.isEmpty()) {
            return
        }
        val assignedId = viewModel.setupModel.value?.assignedUserId
        assignButtons.forEach { assignButton ->
            assignButton.button.isSelected = assignButton.userId == assignedId
        }
        binding.tempNameEditText.isEnabled = assignedId == null
    }

    private fun formatAssignableUserLabel(user: AssignableUser): String {
        if (user.userId.isNullOrBlank()) {
            return getString(R.string.assign_player_unassigned)
        }
        val baseName = user.displayName.ifBlank {
            user.username ?: getString(R.string.assign_player_self)
        }
        val usernameLabel = user.username?.takeIf { it.isNotBlank() }?.let { "@$it" }
        val suffix = if (usernameLabel != null && usernameLabel != baseName) {
            " ($usernameLabel)"
        } else {
            ""
        }
        val selfPrefix = if (user.isSelf && baseName != getString(R.string.assign_player_self)) {
            getString(R.string.assign_player_self) + " - "
        } else {
            ""
        }
        return selfPrefix + baseName + suffix
    }

    private fun bindAvatar(avatarView: ImageView, avatarUrl: String?) {
        if (avatarUrl.isNullOrBlank()) {
            avatarView.setImageResource(R.drawable.ic_user)
            avatarView.imageTintList = ContextCompat.getColorStateList(
                requireContext(),
                R.color.option_button_text_color
            )
            return
        }
        avatarView.imageTintList = null
        com.bumptech.glide.Glide.with(avatarView)
            .load(avatarUrl)
            .circleCrop()
            .placeholder(R.drawable.ic_user)
            .error(R.drawable.ic_user)
            .into(avatarView)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private data class AssignButton(
        val userId: String?,
        val button: View,
    )
}
