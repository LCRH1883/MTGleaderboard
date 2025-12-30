package com.intagri.mtgleader.ui.game.options

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.DialogFragment
import com.intagri.mtgleader.R
import com.intagri.mtgleader.compose.ComposeTheme
import com.intagri.mtgleader.ui.roll.RollPanel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class GameDiceDialogFragment : DialogFragment() {
    companion object {
        fun newInstance(): GameDiceDialogFragment {
            val f = GameDiceDialogFragment()
            f.arguments = Bundle()
            return f
        }
    }

    private lateinit var closeButton: View
    private lateinit var title: TextView
    private lateinit var composeView: ComposeView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return LayoutInflater.from(context).inflate(R.layout.fragment_game_dice, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        title = view.findViewById(R.id.title)
        title.text = getString(R.string.dice_menu)
        closeButton = view.findViewById(R.id.close_button)
        closeButton.setOnClickListener { dismiss() }
        composeView = view.findViewById(R.id.dice_compose_view)
        composeView.setContent {
            ComposeTheme.ScComposeTheme {
                RollPanel()
            }
        }
    }

    override fun onDestroyView() {
        composeView.disposeComposition()
        super.onDestroyView()
    }
}
