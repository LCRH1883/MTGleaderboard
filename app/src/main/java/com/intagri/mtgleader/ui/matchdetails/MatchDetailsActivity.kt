package com.intagri.mtgleader.ui.matchdetails

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.widget.Toolbar
import com.intagri.mtgleader.R
import com.intagri.mtgleader.ui.BaseActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MatchDetailsActivity : BaseActivity() {

    companion object {
        const val EXTRA_LOCAL_MATCH_ID = "extra_local_match_id"

        fun startIntent(context: Context, localMatchId: String): Intent {
            return Intent(context, MatchDetailsActivity::class.java)
                .putExtra(EXTRA_LOCAL_MATCH_ID, localMatchId)
        }
    }

    private val viewModel: MatchDetailsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_match_details)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }
        toolbar.setNavigationIcon(R.drawable.ic_back)

        val headerTitle = findViewById<TextView>(R.id.match_header_title)
        val headerSubtitle = findViewById<TextView>(R.id.match_header_subtitle)
        val winner = findViewById<TextView>(R.id.match_winner)
        val placementsContainer = findViewById<android.widget.LinearLayout>(R.id.placement_container)

        viewModel.matchDetails.observe(this) { details ->
            if (details == null) {
                finish()
                return@observe
            }
            val playerLabel = resources.getQuantityString(
                R.plurals.player_count,
                details.playersCount,
                details.playersCount,
            )
            headerTitle.text = getString(
                R.string.match_history_title_format,
                details.tabletopType,
                playerLabel,
            )
            val syncLabel = if (details.pendingSync) {
                getString(R.string.sync_pending)
            } else {
                getString(R.string.sync_synced)
            }
            headerSubtitle.text = getString(R.string.duration_label, details.durationLabel) +
                " · " + syncLabel
            winner.text = details.winnerLabel
            placementsContainer.removeAllViews()
            val inflater = LayoutInflater.from(this)
            details.placements.forEach { placement ->
                val row = inflater.inflate(
                    R.layout.item_match_placement,
                    placementsContainer,
                    false
                )
                row.findViewById<TextView>(R.id.placement_place).text = placement.placeLabel
                row.findViewById<TextView>(R.id.placement_name).text = placement.displayName
                row.findViewById<TextView>(R.id.placement_elimination).text =
                    placement.eliminationLabel
                row.findViewById<TextView>(R.id.placement_turn_stats).text =
                    placement.turnStatsLabel
                placementsContainer.addView(row)
            }
        }
    }
}
