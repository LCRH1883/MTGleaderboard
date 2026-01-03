package com.intagri.mtgleader.ui.game.options

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.intagri.mtgleader.R
import com.intagri.mtgleader.persistence.Datastore
import com.intagri.mtgleader.ui.game.GameViewModel
import com.intagri.mtgleader.ui.setup.theme.ScThemeUtils
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class GameOptionsDialogFragment : DialogFragment() {
    companion object {
        private const val TAG_GAME_SETTINGS = "tag_game_settings"
        private const val TAG_GAME_DICE = "tag_game_dice"

        fun newInstance(): GameOptionsDialogFragment {
            val f = GameOptionsDialogFragment()
            f.arguments = Bundle()
            return f
        }
    }

    private var listener: Listener? = null
    private val viewModel: GameViewModel by activityViewModels()

    private lateinit var title: TextView
    private lateinit var closeButton: View
    private lateinit var optionsButton: View
    private lateinit var diceButton: View
    private lateinit var selectPlayerButton: View
    private lateinit var randomPlayerButton: View
    private lateinit var completeMatchButton: View
    private lateinit var resetButton: View
    private lateinit var exitButton: View

    @Inject
    lateinit var datastore: Datastore

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is Listener) {
            listener = context
        }
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return LayoutInflater.from(context).inflate(R.layout.fragment_game_options, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        title = view.findViewById(R.id.title)
        title.text = ScThemeUtils.resolveThemedTitle(requireContext(), datastore.theme)

        closeButton = view.findViewById(R.id.close_button)
        closeButton.setOnClickListener {
            dismiss()
        }
        optionsButton = view.findViewById(R.id.options_button)
        optionsButton.setOnClickListener {
            dismiss()
            GameSettingsDialogFragment.newInstance()
                .show(parentFragmentManager, TAG_GAME_SETTINGS)
        }
        selectPlayerButton = view.findViewById(R.id.select_player_button)
        selectPlayerButton.setOnClickListener {
            viewModel.startStartingPlayerSelection()
            dismiss()
        }
        randomPlayerButton = view.findViewById(R.id.random_player_button)
        randomPlayerButton.setOnClickListener {
            viewModel.selectRandomStartingPlayer()
            dismiss()
        }
        diceButton = view.findViewById(R.id.dice_button)
        diceButton.setOnClickListener {
            dismiss()
            GameDiceDialogFragment.newInstance()
                .show(parentFragmentManager, TAG_GAME_DICE)
        }
        completeMatchButton = view.findViewById(R.id.complete_match_button)
        completeMatchButton.setOnClickListener {
            listener?.onOpenCompletePrompt()
        }
        resetButton = view.findViewById(R.id.reset_game_button)
        resetButton.setOnClickListener {
            listener?.onOpenResetPrompt()
        }
        exitButton = view.findViewById(R.id.exit_game_button)
        exitButton.setOnClickListener {
            listener?.onOpenExitPrompt()
        }

        viewModel.startingPlayerSelected.observe(viewLifecycleOwner) { selected ->
            val enabled = selected != true
            val alpha = if (enabled) 1f else 0.4f
            selectPlayerButton.isEnabled = enabled
            randomPlayerButton.isEnabled = enabled
            selectPlayerButton.alpha = alpha
            randomPlayerButton.alpha = alpha
        }
    }

    interface Listener {
        fun onOpenExitPrompt()
        fun onOpenResetPrompt()
        fun onOpenCompletePrompt()
    }
}
