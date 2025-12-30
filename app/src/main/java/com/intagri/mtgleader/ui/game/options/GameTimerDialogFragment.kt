package com.intagri.mtgleader.ui.game.options

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.intagri.mtgleader.R
import com.intagri.mtgleader.ui.game.GameViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale

@AndroidEntryPoint
class GameTimerDialogFragment : DialogFragment() {
    companion object {
        const val TAG_TIMER_MENU = "tag_timer_menu"

        fun newInstance(): GameTimerDialogFragment {
            val f = GameTimerDialogFragment()
            f.arguments = Bundle()
            return f
        }
    }

    private val viewModel: GameViewModel by activityViewModels()

    private lateinit var closeButton: View
    private lateinit var backButton: Button
    private lateinit var pauseButton: Button
    private lateinit var currentTurnValue: TextView
    private lateinit var gameClockValue: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return LayoutInflater.from(context).inflate(R.layout.fragment_game_timer, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        closeButton = view.findViewById(R.id.close_button)
        closeButton.setOnClickListener { dismiss() }

        currentTurnValue = view.findViewById(R.id.current_turn_value)
        gameClockValue = view.findViewById(R.id.game_clock_value)

        backButton = view.findViewById(R.id.back_turn_button)
        backButton.setOnClickListener {
            viewModel.goBackTurn()
        }

        pauseButton = view.findViewById(R.id.pause_button)
        pauseButton.setOnClickListener {
            viewModel.togglePause()
        }

        viewModel.turnCount.observe(viewLifecycleOwner) { turn ->
            currentTurnValue.text = getString(R.string.turn_number, turn)
        }
        viewModel.gameElapsedSeconds.observe(viewLifecycleOwner) { seconds ->
            gameClockValue.text = formatElapsedTime(seconds)
        }
        viewModel.gamePaused.observe(viewLifecycleOwner) { paused ->
            pauseButton.setText(if (paused) R.string.resume_game else R.string.pause_game)
        }
    }

    private fun formatElapsedTime(seconds: Long): String {
        val totalSeconds = seconds.coerceAtLeast(0L)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val remainingSeconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, remainingSeconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, remainingSeconds)
        }
    }
}
