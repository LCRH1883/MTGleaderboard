package com.intagri.mtgleader.ui.game.options

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.intagri.mtgleader.R
import com.intagri.mtgleader.ui.game.GameViewModel
import com.intagri.mtgleader.ui.setup.theme.ScThemeUtils
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
    private lateinit var playerRotationLabel: TextView
    private lateinit var playerRotationSwitch: SwitchCompat
    private lateinit var turnTimerLabel: TextView
    private lateinit var turnTimerSwitch: SwitchCompat
    private lateinit var turnTimerInputContainer: View
    private lateinit var turnTimerMinutesInput: EditText
    private lateinit var turnTimerSecondsInput: EditText
    private lateinit var turnTimerColon: TextView
    private lateinit var keepScreenAwakeCheckbox: CheckBox
    private lateinit var hideNavigationCheckbox: CheckBox
    private var updatingTimerInputs = false
    private var isEditingTimerInputs = false

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

        playerRotationLabel = view.findViewById(R.id.player_rotation_label)
        playerRotationSwitch = view.findViewById(R.id.player_rotation_switch)
        playerRotationSwitch.setOnCheckedChangeListener(this)

        turnTimerLabel = view.findViewById(R.id.turn_timer_label)
        turnTimerSwitch = view.findViewById(R.id.turn_timer_switch)
        turnTimerSwitch.setOnCheckedChangeListener(this)
        turnTimerInputContainer = view.findViewById(R.id.turn_timer_input_container)
        turnTimerMinutesInput = view.findViewById(R.id.turn_timer_minutes_input)
        turnTimerSecondsInput = view.findViewById(R.id.turn_timer_seconds_input)
        turnTimerColon = view.findViewById(R.id.turn_timer_colon)
        val timerFocusListener = View.OnFocusChangeListener { _, _ ->
            isEditingTimerInputs =
                turnTimerMinutesInput.hasFocus() || turnTimerSecondsInput.hasFocus()
            if (!isEditingTimerInputs) {
                viewModel.turnTimerDurationSeconds.value?.let { renderTurnTimerInputs(it) }
            }
        }
        turnTimerMinutesInput.onFocusChangeListener = timerFocusListener
        turnTimerSecondsInput.onFocusChangeListener = timerFocusListener

        keepScreenAwakeCheckbox = view.findViewById(R.id.keep_screen_awake_checkbox)
        keepScreenAwakeCheckbox.setOnCheckedChangeListener(this)

        hideNavigationCheckbox = view.findViewById(R.id.hide_navigation_checkbox)
        hideNavigationCheckbox.setOnCheckedChangeListener(this)

        val toggleTextColor = resolveToggleTextColor()
        playerRotationLabel.setTextColor(toggleTextColor)
        playerRotationSwitch.setTextColor(toggleTextColor)
        turnTimerLabel.setTextColor(toggleTextColor)
        turnTimerSwitch.setTextColor(toggleTextColor)
        turnTimerMinutesInput.setTextColor(toggleTextColor)
        turnTimerSecondsInput.setTextColor(toggleTextColor)
        turnTimerColon.setTextColor(toggleTextColor)

        val timerTextWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (updatingTimerInputs) {
                    return
                }
                val minutes = turnTimerMinutesInput.text.toString().toIntOrNull() ?: 0
                val seconds = turnTimerSecondsInput.text.toString().toIntOrNull() ?: 0
                viewModel.setTurnTimerDuration(minutes, seconds)
            }
        }
        turnTimerMinutesInput.addTextChangedListener(timerTextWatcher)
        turnTimerSecondsInput.addTextChangedListener(timerTextWatcher)

        viewModel.playerRotationClockwise.observe(viewLifecycleOwner) { isClockwise ->
            val clockwise = isClockwise != false
            playerRotationSwitch.setOnCheckedChangeListener(null)
            playerRotationSwitch.isChecked = clockwise
            playerRotationSwitch.text =
                getString(if (clockwise) R.string.clockwise else R.string.counter_clockwise)
            playerRotationSwitch.setOnCheckedChangeListener(this)
        }

        viewModel.turnTimerEnabled.observe(viewLifecycleOwner) { isEnabled ->
            val enabled = isEnabled == true
            turnTimerSwitch.setOnCheckedChangeListener(null)
            turnTimerSwitch.isChecked = enabled
            turnTimerSwitch.text =
                getString(if (enabled) R.string.turn_timer_on else R.string.turn_timer_off)
            turnTimerSwitch.setOnCheckedChangeListener(this)
            turnTimerInputContainer.alpha = if (enabled) 1f else 0.4f
            turnTimerMinutesInput.isEnabled = enabled
            turnTimerSecondsInput.isEnabled = enabled
        }

        viewModel.turnTimerDurationSeconds.observe(viewLifecycleOwner) { totalSeconds ->
            if (isEditingTimerInputs) {
                return@observe
            }
            renderTurnTimerInputs(totalSeconds)
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

    private fun renderTurnTimerInputs(totalSeconds: Int) {
        val safeSeconds = totalSeconds
        val minutes = safeSeconds / 60
        val seconds = safeSeconds % 60
        val formattedSeconds = String.format(Locale.US, "%02d", seconds)
        updatingTimerInputs = true
        turnTimerMinutesInput.setText(minutes.toString())
        turnTimerSecondsInput.setText(formattedSeconds)
        updatingTimerInputs = false
    }

    override fun onCheckedChanged(buttonView: CompoundButton?, isChecked: Boolean) {
        when (buttonView) {
            playerRotationSwitch -> viewModel.setPlayerRotationClockwise(isChecked)
            turnTimerSwitch -> viewModel.setTurnTimerEnabled(isChecked)
            keepScreenAwakeCheckbox -> viewModel.setKeepScreenOn(isChecked)
            hideNavigationCheckbox -> viewModel.setHideNavigation(isChecked)
        }
    }

    private fun resolveToggleTextColor(): Int {
        return if (ScThemeUtils.isLightTheme(requireContext())) {
            ContextCompat.getColor(requireContext(), R.color.black)
        } else {
            ContextCompat.getColor(requireContext(), R.color.white)
        }
    }
}
