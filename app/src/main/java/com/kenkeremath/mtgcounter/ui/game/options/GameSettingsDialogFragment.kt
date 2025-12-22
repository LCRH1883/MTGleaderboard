package com.kenkeremath.mtgcounter.ui.game.options

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.EditText
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.kenkeremath.mtgcounter.R
import com.kenkeremath.mtgcounter.ui.game.GameViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale

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
    private lateinit var turnTimerMinutesInput: EditText
    private lateinit var turnTimerSecondsInput: EditText
    private lateinit var keepScreenAwakeCheckbox: CheckBox
    private lateinit var hideNavigationCheckbox: CheckBox
    private var updatingTurnTimerInputs = false

    private val turnTimerTextWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) {
            if (updatingTurnTimerInputs) {
                return
            }
            val minutes = turnTimerMinutesInput.text?.toString()?.toIntOrNull() ?: 0
            val seconds = turnTimerSecondsInput.text?.toString()?.toIntOrNull() ?: 0
            viewModel.setTurnTimerDuration(minutes, seconds)
        }
    }

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

        turnTimerMinutesInput = view.findViewById(R.id.turn_timer_minutes)
        turnTimerSecondsInput = view.findViewById(R.id.turn_timer_seconds)
        turnTimerMinutesInput.addTextChangedListener(turnTimerTextWatcher)
        turnTimerSecondsInput.addTextChangedListener(turnTimerTextWatcher)

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

        viewModel.turnTimerDurationSeconds.observe(viewLifecycleOwner) { duration ->
            val safeDuration = duration
            val minutes = safeDuration / 60
            val seconds = safeDuration % 60
            updatingTurnTimerInputs = true
            turnTimerMinutesInput.setText(minutes.toString())
            turnTimerSecondsInput.setText(
                String.format(Locale.getDefault(), "%02d", seconds)
            )
            turnTimerMinutesInput.setSelection(turnTimerMinutesInput.text?.length ?: 0)
            turnTimerSecondsInput.setSelection(turnTimerSecondsInput.text?.length ?: 0)
            updatingTurnTimerInputs = false
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
