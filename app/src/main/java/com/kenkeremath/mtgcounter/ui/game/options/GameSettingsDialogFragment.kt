package com.kenkeremath.mtgcounter.ui.game.options

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.CompoundButton
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.kenkeremath.mtgcounter.R
import com.kenkeremath.mtgcounter.ui.game.GameViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class GameSettingsDialogFragment : DialogFragment(), CompoundButton.OnCheckedChangeListener {
    companion object {
        fun newInstance(): GameSettingsDialogFragment {
            val f = GameSettingsDialogFragment()
            f.arguments = Bundle()
            return f
        }
    }

    private val viewModel: GameViewModel by activityViewModels()

    private lateinit var closeButton: View
    private lateinit var playerRotationSwitch: SwitchCompat
    private lateinit var keepScreenAwakeCheckbox: CheckBox
    private lateinit var hideNavigationCheckbox: CheckBox

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return LayoutInflater.from(context).inflate(R.layout.fragment_game_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        closeButton = view.findViewById(R.id.close_button)
        closeButton.setOnClickListener { dismiss() }

        playerRotationSwitch = view.findViewById(R.id.player_rotation_switch)
        playerRotationSwitch.setOnCheckedChangeListener(this)

        keepScreenAwakeCheckbox = view.findViewById(R.id.keep_screen_awake_checkbox)
        keepScreenAwakeCheckbox.setOnCheckedChangeListener(this)

        hideNavigationCheckbox = view.findViewById(R.id.hide_navigation_checkbox)
        hideNavigationCheckbox.setOnCheckedChangeListener(this)

        viewModel.playerRotationClockwise.observe(viewLifecycleOwner) { isClockwise ->
            val clockwise = isClockwise != false
            playerRotationSwitch.setOnCheckedChangeListener(null)
            playerRotationSwitch.isChecked = clockwise
            playerRotationSwitch.text =
                getString(if (clockwise) R.string.clockwise else R.string.counter_clockwise)
            playerRotationSwitch.setOnCheckedChangeListener(this)
        }

        viewModel.keepScreenOn.observe(viewLifecycleOwner) {
            it?.let {

                //Remove listener to avoid infinite loop
                keepScreenAwakeCheckbox.setOnCheckedChangeListener(null)
                if (keepScreenAwakeCheckbox.isChecked != it) {
                    keepScreenAwakeCheckbox.isChecked = it
                }
                keepScreenAwakeCheckbox.setOnCheckedChangeListener(this)
            }
        }

        viewModel.hideNavigation.observe(viewLifecycleOwner) {
            it?.let {

                //Remove listener to avoid infinite loop
                hideNavigationCheckbox.setOnCheckedChangeListener(null)
                if (hideNavigationCheckbox.isChecked != it) {
                    hideNavigationCheckbox.isChecked = it
                }
                hideNavigationCheckbox.setOnCheckedChangeListener(this)
            }
        }
    }

    override fun onCheckedChanged(buttonView: CompoundButton?, isChecked: Boolean) {
        when (buttonView) {
            playerRotationSwitch -> viewModel.setPlayerRotationClockwise(isChecked)
            keepScreenAwakeCheckbox -> viewModel.setKeepScreenOn(isChecked)
            hideNavigationCheckbox -> viewModel.setHideNavigation(isChecked)
        }
    }
}
