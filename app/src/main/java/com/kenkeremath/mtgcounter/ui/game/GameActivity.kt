package com.kenkeremath.mtgcounter.ui.game

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.rongi.rotate_layout.layout.RotateLayout
import com.kenkeremath.mtgcounter.R
import com.kenkeremath.mtgcounter.model.TabletopType
import com.kenkeremath.mtgcounter.model.player.PlayerSetupModel
import com.kenkeremath.mtgcounter.ui.BaseActivity
import com.kenkeremath.mtgcounter.ui.game.options.GameOptionsDialogFragment
import com.kenkeremath.mtgcounter.ui.game.rv.GamePlayerRecyclerAdapter
import com.kenkeremath.mtgcounter.ui.game.tabletop.GameTabletopLayoutAdapter
import com.kenkeremath.mtgcounter.ui.setup.theme.ScThemeUtils
import com.kenkeremath.mtgcounter.view.TableLayoutPosition
import com.kenkeremath.mtgcounter.view.TabletopLayout
import com.kenkeremath.mtgcounter.view.counter.edit.PlayerMenuListener
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import kotlin.math.roundToInt


@AndroidEntryPoint
class GameActivity : BaseActivity(), OnPlayerUpdatedListener,
    PlayerMenuListener, GameOptionsDialogFragment.Listener {

    companion object {
        const val TAG_GAME_OPTIONS = "tag_game_options"
        const val ARGS_SETUP_PLAYERS = "args_setup_players"
        fun startIntentFromSetup(context: Context, players: List<PlayerSetupModel>): Intent {
            return Intent(context, GameActivity::class.java).putParcelableArrayListExtra(
                ARGS_SETUP_PLAYERS, ArrayList(players)
            )
        }
    }

    private lateinit var gameContainer: FrameLayout

    private lateinit var tabletopContainer: RotateLayout
    private lateinit var tabletopLayout: TabletopLayout
    private lateinit var tabletopLayoutAdapter: GameTabletopLayoutAdapter

    private lateinit var playersRecyclerViewContainer: ViewGroup
    private lateinit var playersRecyclerView: RecyclerView
    private lateinit var playersRecyclerAdapter: GamePlayerRecyclerAdapter

    private val viewModel: GameViewModel by viewModels()
    private var currentPlayers: List<GamePlayerUiModel> = emptyList()
    private var menuButtonContainer: FrameLayout? = null
    private var menuButtonRotatingContainer: FrameLayout? = null
    private var menuButtonIcon: ImageView? = null
    private var menuButtonTurnText: TextView? = null
    private var menuButtonTimerText: TextView? = null
    private var latestTurnCount: Int = 1
    private var latestTurnTimerSeconds: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)

        hideSystemUI()

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener {
            openGameMenu()
        }
        //Remove default theme tinting for game button
        toolbar.navigationIcon?.setTintList(null)
        toolbar.title = ScThemeUtils.resolveThemedTitle(this, datastore.theme)

        gameContainer = findViewById(R.id.game_container)

        tabletopContainer = findViewById(R.id.tabletop_container)
        tabletopLayout = findViewById(R.id.tabletop_layout)
        tabletopLayoutAdapter = GameTabletopLayoutAdapter(tabletopLayout, this, this)
        tabletopLayoutAdapter.setPositions(viewModel.tabletopType)

        playersRecyclerViewContainer = findViewById(R.id.recycler_view_container)
        playersRecyclerView = findViewById(R.id.players_recycler_view)
        playersRecyclerView.layoutManager = LinearLayoutManager(this)
        playersRecyclerAdapter = GamePlayerRecyclerAdapter(this, this)
        playersRecyclerView.adapter = playersRecyclerAdapter
        val decoration = DividerItemDecoration(
            this,
            RecyclerView.VERTICAL
        )
        decoration.setDrawable(
            ContextCompat.getDrawable(
                this,
                R.drawable.player_divider
            )!!
        )
        playersRecyclerView.addItemDecoration(
            decoration
        )

        if (viewModel.tabletopType == TabletopType.LIST) {
            playersRecyclerView.visibility = View.VISIBLE
            tabletopContainer.visibility = View.GONE
            playersRecyclerViewContainer.visibility = View.VISIBLE
        } else {
            playersRecyclerView.visibility = View.GONE
            tabletopContainer.visibility = View.VISIBLE
            playersRecyclerViewContainer.visibility = View.GONE
            addMenuButton()
        }

        latestTurnCount = viewModel.turnCount.value ?: 1
        latestTurnTimerSeconds = viewModel.turnTimerSeconds.value
            ?: viewModel.turnTimerDurationSeconds.value
            ?: 0

        viewModel.players.observe(this) {
            renderPlayers(it)
        }
        viewModel.currentTurnPlayerId.observe(this) {
            updateMenuButtonRotation()
        }
        viewModel.turnCount.observe(this) {
            updateTurnCounterText(it)
        }
        viewModel.turnTimerSeconds.observe(this) {
            updateTurnTimerText(it)
        }

        viewModel.keepScreenOn.observe(this) {
            if (it) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    private fun renderPlayers(players: List<GamePlayerUiModel>) {
        currentPlayers = players
        tabletopLayoutAdapter.updateAll(viewModel.tabletopType, currentPlayers)
        playersRecyclerAdapter.setData(currentPlayers)
        updateMenuButtonRotation()
    }

    private fun addMenuButton() {
        tabletopLayout.viewTreeObserver.addOnPreDrawListener(object :
            ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                tabletopLayout.viewTreeObserver.removeOnPreDrawListener(this)
                val container = FrameLayout(this@GameActivity)
                val containerSize =
                    resources.getDimensionPixelSize(R.dimen.game_menu_button_container_diameter)
                val timerGap = resources.getDimensionPixelSize(R.dimen.game_menu_button_timer_gap)
                val timerTextSize = containerSize * 0.6f
                val timerTextWidth = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = timerTextSize
                }.measureText("000:00").roundToInt()
                val containerWidth = containerSize + timerGap + timerTextWidth
                val containerLp = FrameLayout.LayoutParams(
                    containerWidth,
                    containerSize,
                )
                container.clipChildren = false
                container.clipToPadding = false

                val rotatingContainer = FrameLayout(this@GameActivity)
                rotatingContainer.layoutParams = FrameLayout.LayoutParams(
                    containerWidth,
                    containerSize
                )
                rotatingContainer.pivotX = containerSize / 2f
                rotatingContainer.pivotY = containerSize / 2f
                rotatingContainer.clipChildren = false
                rotatingContainer.clipToPadding = false

                val circleContainer = FrameLayout(this@GameActivity)
                circleContainer.layoutParams = FrameLayout.LayoutParams(
                    containerSize,
                    containerSize
                )
                circleContainer.clipToPadding = false
                circleContainer.clipChildren = false

                val menuButton = ImageView(this@GameActivity)
                menuButton.setImageResource(R.drawable.ic_skull)
                menuButton.scaleX = 1.15f
                menuButton.scaleY = 1.15f
                circleContainer.addView(
                    menuButton, FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        Gravity.CENTER
                    )
                )

                val turnText = TextView(this@GameActivity)
                turnText.setTextColor(ContextCompat.getColor(this@GameActivity, R.color.light_red))
                turnText.setTextSize(TypedValue.COMPLEX_UNIT_PX, containerSize * 0.4f)
                turnText.setTypeface(turnText.typeface, Typeface.BOLD)
                turnText.gravity = Gravity.CENTER
                turnText.includeFontPadding = false
                circleContainer.addView(
                    turnText, FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER
                    )
                )
                rotatingContainer.addView(circleContainer)

                val dividerHeight =
                    resources.getDimensionPixelSize(R.dimen.player_divider_width)
                val dividerView = View(this@GameActivity)
                dividerView.setBackgroundColor(
                    ScThemeUtils.resolveThemeColor(
                        this@GameActivity,
                        R.attr.scPlayerDividerColor
                    )
                )
                rotatingContainer.addView(
                    dividerView, FrameLayout.LayoutParams(
                        containerWidth - containerSize,
                        dividerHeight,
                        Gravity.CENTER_VERTICAL
                    ).apply {
                        leftMargin = containerSize
                    }
                )

                val timerText = TextView(this@GameActivity)
                timerText.setTextColor(
                    ScThemeUtils.resolveThemeColor(
                        this@GameActivity,
                        R.attr.scTextColorPrimary
                    )
                )
                timerText.setTextSize(TypedValue.COMPLEX_UNIT_PX, timerTextSize)
                timerText.gravity = Gravity.CENTER
                timerText.includeFontPadding = false
                timerText.setSingleLine(true)
                timerText.minWidth = timerTextWidth
                rotatingContainer.addView(
                    timerText, FrameLayout.LayoutParams(
                        timerTextWidth,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER_VERTICAL
                    ).apply {
                        leftMargin = containerSize + timerGap
                    }
                )
                container.addView(rotatingContainer)
                val containerPadding =
                    resources.getDimensionPixelSize(R.dimen.game_menu_button_container_padding)
                circleContainer.setPadding(
                    containerPadding,
                    containerPadding,
                    containerPadding,
                    containerPadding
                )


                when (viewModel.tabletopType) {
                    TabletopType.NONE,
                    TabletopType.LIST -> {
                        //Hide (use toolbar instead)
                        container.visibility = View.GONE
                    }
                    TabletopType.ONE_VERTICAL -> {
                        //Top left
                        circleContainer.background = ContextCompat.getDrawable(
                            this@GameActivity,
                            R.drawable.game_button_container_bg
                        )
                        containerLp.topMargin = containerPadding
                        containerLp.leftMargin = containerPadding
                    }
                    TabletopType.ONE_HORIZONTAL -> {
                        //Top right (appears as top left)
                        containerLp.topMargin = containerPadding
                        containerLp.leftMargin =
                            tabletopLayout.width - containerWidth - containerPadding
                        circleContainer.background = ContextCompat.getDrawable(
                            this@GameActivity,
                            R.drawable.game_button_container_bg
                        )
                    }
                    TabletopType.FOUR_ACROSS,
                    TabletopType.SIX_CIRCLE,
                    -> {
                        //Center in screen
                        containerLp.topMargin = tabletopLayout.height / 2 - containerSize / 2
                        containerLp.leftMargin = tabletopLayout.width / 2 - containerSize / 2
                        circleContainer.background = ContextCompat.getDrawable(
                            this@GameActivity,
                            R.drawable.game_button_container_bg
                        )
                    }
                    TabletopType.TWO_HORIZONTAL,
                    TabletopType.FIVE_ACROSS,
                    TabletopType.THREE_ACROSS -> {
                        //Center top (appears as center left)
                        containerLp.leftMargin = tabletopLayout.width / 2 - containerSize / 2
                        circleContainer.background = ContextCompat.getDrawable(
                            this@GameActivity,
                            R.drawable.game_button_container_side_bg
                        )
                    }
                    TabletopType.TWO_VERTICAL -> {
                        //Center left
                        containerLp.topMargin = tabletopLayout.height / 2 - containerSize / 2
                        circleContainer.background = ContextCompat.getDrawable(
                            this@GameActivity,
                            R.drawable.game_button_container_side_bg
                        )
                    }
                    TabletopType.FOUR_CIRCLE -> {
                        //Center offset from topmost (appears leftmost) intersection so no center portion of player is cut off
                        containerLp.topMargin =
                            tabletopLayout.panels[TableLayoutPosition.TOP_PANEL]!!.height
                        containerLp.leftMargin = tabletopLayout.width / 2 - containerSize / 2
                        circleContainer.background = ContextCompat.getDrawable(
                            this@GameActivity,
                            R.drawable.game_button_container_side_bg
                        )
                    }
                    TabletopType.SIX_ACROSS -> {
                        //Center in topmost (appears leftmost) intersection
                        containerLp.topMargin =
                            tabletopLayout.panels[TableLayoutPosition.LEFT_PANEL_1]!!.height - containerSize / 2
                        containerLp.leftMargin = tabletopLayout.width / 2 - containerSize / 2
                        circleContainer.background = ContextCompat.getDrawable(
                            this@GameActivity,
                            R.drawable.game_button_container_bg
                        )
                    }
                    TabletopType.FIVE_CIRCLE -> {
                        //Center in bottom (appears rightmost) intersection
                        containerLp.topMargin =
                            tabletopLayout.height - tabletopLayout.panels[TableLayoutPosition.LEFT_PANEL_1]!!.height - containerSize / 2
                        containerLp.leftMargin = tabletopLayout.width / 2 - containerSize / 2
                        circleContainer.background = ContextCompat.getDrawable(
                            this@GameActivity,
                            R.drawable.game_button_container_bg
                        )
                    }
                    TabletopType.THREE_CIRCLE -> {
                        //Center in intersection
                        containerLp.topMargin =
                            tabletopLayout.panels[TableLayoutPosition.TOP_PANEL]!!.height - containerSize / 2
                        containerLp.leftMargin = tabletopLayout.width / 2 - containerSize / 2
                        circleContainer.background = ContextCompat.getDrawable(
                            this@GameActivity,
                            R.drawable.game_button_container_bg
                        )
                    }
                }
                gameContainer.addView(container, containerLp)
                circleContainer.foreground = RippleDrawable(
                    ColorStateList.valueOf(
                        ScThemeUtils.resolveThemeColor(this@GameActivity, R.attr.scAccentColor)
                    ), null, circleContainer.background
                )
                circleContainer.isClickable = true
                circleContainer.setOnClickListener {
                    openGameMenu()
                }
                rotatingContainer.setOnClickListener {
                    openGameMenu()
                }
                updateTurnCounterText(latestTurnCount)
                updateTurnTimerText(latestTurnTimerSeconds)
                menuButtonContainer = container
                menuButtonRotatingContainer = rotatingContainer
                menuButtonIcon = menuButton
                menuButtonTurnText = turnText
                menuButtonTimerText = timerText
                updateMenuButtonRotation()
                return false
            }
        })
    }

    private fun updateMenuButtonRotation() {
        val rotatingContainer = menuButtonRotatingContainer ?: return
        val icon = menuButtonIcon ?: return
        val turnText = menuButtonTurnText
        val timerText = menuButtonTimerText
        if (viewModel.tabletopType == TabletopType.LIST) {
            rotatingContainer.rotation = 0f
            icon.rotation = 0f
            turnText?.rotation = 0f
            timerText?.rotation = 0f
            return
        }
        val currentTurnId = viewModel.currentTurnPlayerId.value ?: run {
            rotatingContainer.rotation = 0f
            icon.rotation = 0f
            turnText?.rotation = 0f
            timerText?.rotation = 0f
            return
        }
        var targetRotation: Float? = null
        for (panel in tabletopLayout.panels.values) {
            if (panel.childCount == 0) {
                continue
            }
            val childTag = panel.getChildAt(0).tag as? Int ?: continue
            if (childTag == currentTurnId) {
                targetRotation = panel.angle.toFloat()
                break
            }
        }
        if (targetRotation == null) {
            rotatingContainer.rotation = 0f
            icon.rotation = 0f
            turnText?.rotation = 0f
            timerText?.rotation = 0f
            return
        }
        val containerRotation = menuButtonContainer?.rotation ?: 0f
        val finalRotation = -targetRotation - containerRotation
        rotatingContainer.rotation = finalRotation
        icon.rotation = 0f
        turnText?.rotation = 0f
        timerText?.rotation = 0f
    }

    private fun updateTurnCounterText(turnCount: Int) {
        latestTurnCount = turnCount
        menuButtonTurnText?.text = turnCount.toString()
    }

    private fun updateTurnTimerText(remainingSeconds: Int) {
        latestTurnTimerSeconds = remainingSeconds
        menuButtonTimerText?.text = formatTurnTimer(remainingSeconds)
    }

    private fun formatTurnTimer(remainingSeconds: Int): String {
        val safeSeconds = remainingSeconds.coerceAtLeast(0)
        val minutes = safeSeconds / 60
        val seconds = safeSeconds % 60
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
    }

    private fun openGameMenu() {
        GameOptionsDialogFragment.newInstance()
            .show(supportFragmentManager, TAG_GAME_OPTIONS)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
            //Refresh layouts for new screen size
            playersRecyclerAdapter.invalidateMeasurement()
            viewModel.players.value?.let {
                renderPlayers(it)
            }
        }
    }

    private fun hideSystemUI() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                // Set the content to appear under the system bars so that the
                // content doesn't resize when the system bars hide and show.
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_FULLSCREEN)

        if (viewModel.hideNavigation.value == true) {
            // Conditionally hide nav bar
            window.decorView.systemUiVisibility = (window.decorView.systemUiVisibility
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)
        }
    }

    override fun onBackPressed() {
        openExitPrompt()
    }

    private fun openExitPrompt() {
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.exit_game)
            .setMessage(R.string.are_you_sure_exit)
            .setPositiveButton(R.string.yes) { _, _ ->
                closeGameOptionsDialog()
                finish()
            }
            .setNegativeButton(R.string.no) { dialog, _ -> dialog.dismiss() }

        dialog.show()
    }

    private fun openResetPrompt() {
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.reset_game)
            .setMessage(R.string.are_you_sure_reset)
            .setPositiveButton(R.string.yes) { _, _ ->
                closeGameOptionsDialog()
                viewModel.resetGame()
            }
            .setNegativeButton(R.string.no) { dialog, _ -> dialog.dismiss() }

        dialog.show()
    }

    private fun closeGameOptionsDialog() {
        supportFragmentManager.findFragmentByTag(TAG_GAME_OPTIONS)?.let {
            if (it is DialogFragment) {
                it.dismiss()
            }
        }
    }

    override fun onLifeIncremented(playerId: Int, amountDifference: Int) {
        viewModel.incrementPlayerLife(playerId, amountDifference)
    }

    override fun onLifeAmountSet(playerId: Int, amount: Int) {
        //TODO
    }

    override fun onCounterIncremented(playerId: Int, counterId: Int, amountDifference: Int) {
        viewModel.incrementCounter(playerId, counterId, amountDifference)
    }

    override fun onCounterAmountSet(playerId: Int, counterId: Int, amount: Int) {
        //TODO
    }

    override fun onEditCountersOpened(playerId: Int) {
        viewModel.editCounters(playerId)
    }

    override fun onRearrangeCountersOpened(playerId: Int) {
        viewModel.rearrangeCounters(playerId)
    }

    override fun onRollOpened(playerId: Int) {
        viewModel.roll(playerId)
    }

    override fun onCloseSubMenu(playerId: Int) {
        viewModel.closeSubMenu(playerId)
    }

    override fun onCounterSelected(playerId: Int, templateId: Int) {
        viewModel.selectCounter(playerId, templateId)
    }

    override fun onCounterDeselected(playerId: Int, templateId: Int) {
        viewModel.deselectCounter(playerId, templateId)
    }

    override fun onCounterRearranged(
        playerId: Int,
        templateId: Int,
        oldPosition: Int,
        newPosition: Int
    ) {
        viewModel.moveCounter(playerId, oldPosition, newPosition)
    }

    override fun onCancelCounterChanges(playerId: Int) {
        viewModel.closeSubMenu(playerId)
    }

    override fun onConfirmCounterChanges(playerId: Int) {
        viewModel.confirmCounterChanges(playerId)
    }

    override fun onStartingPlayerSelected(playerId: Int) {
        viewModel.selectStartingPlayer(playerId)
    }

    override fun onEndTurn(playerId: Int) {
        viewModel.endTurn(playerId)
    }

    override fun onOpenExitPrompt() {
        openExitPrompt()
    }

    override fun onOpenResetPrompt() {
        openResetPrompt()
    }
}
