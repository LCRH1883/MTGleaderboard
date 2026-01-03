package com.intagri.mtgleader.ui.stats

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.intagri.mtgleader.R
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class StatsFragment : Fragment() {

    companion object {
        const val TAG = "fragment_stats"
        fun newInstance(): StatsFragment = StatsFragment()
    }

    private val viewModel: StatsViewModel by viewModels()
    private lateinit var ownerSpinner: Spinner
    private lateinit var summarySubtitle: TextView
    private lateinit var gamesPlayed: TextView
    private lateinit var wins: TextView
    private lateinit var losses: TextView
    private lateinit var winPercent: TextView
    private lateinit var friendsAdapter: HeadToHeadAdapter
    private lateinit var guestsAdapter: HeadToHeadAdapter

    private var ownerOptions: List<StatsOwnerOption> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_stats, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val toolbar = view.findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        ownerSpinner = view.findViewById(R.id.stats_owner_spinner)
        summarySubtitle = view.findViewById(R.id.stats_summary_subtitle)
        gamesPlayed = view.findViewById(R.id.stats_games_played)
        wins = view.findViewById(R.id.stats_wins)
        losses = view.findViewById(R.id.stats_losses)
        winPercent = view.findViewById(R.id.stats_win_percent)

        friendsAdapter = HeadToHeadAdapter()
        val friendsList = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.head_to_head_friends)
        friendsList.layoutManager = LinearLayoutManager(requireContext())
        friendsList.adapter = friendsAdapter

        guestsAdapter = HeadToHeadAdapter()
        val guestsList = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.head_to_head_guests)
        guestsList.layoutManager = LinearLayoutManager(requireContext())
        guestsList.adapter = guestsAdapter

        val emptyState = view.findViewById<TextView>(R.id.head_to_head_empty)

        ownerSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long,
            ) {
                val option = ownerOptions.getOrNull(position) ?: return
                viewModel.selectOwner(option)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        viewModel.ownerOptions.observe(viewLifecycleOwner) { options ->
            ownerOptions = options
            val labels = options.map { it.label }
            val adapter = ArrayAdapter(
                requireContext(),
                R.layout.item_spinner_text,
                labels,
            )
            ownerSpinner.adapter = adapter
            viewModel.ensureDefaultSelection()
        }

        viewModel.selectedOwnerOption.observe(viewLifecycleOwner) { selected ->
            if (selected == null) return@observe
            val index = ownerOptions.indexOfFirst {
                it.ownerType == selected.ownerType && it.ownerId == selected.ownerId
            }
            if (index >= 0 && ownerSpinner.selectedItemPosition != index) {
                ownerSpinner.setSelection(index)
            }
        }

        viewModel.summary.observe(viewLifecycleOwner) { summary ->
            gamesPlayed.text = summary.gamesPlayed.toString()
            wins.text = summary.wins.toString()
            losses.text = summary.losses.toString()
            winPercent.text = summary.winPercentLabel
            if (summary.subtitle.isNullOrBlank()) {
                summarySubtitle.visibility = View.GONE
            } else {
                summarySubtitle.text = summary.subtitle
                summarySubtitle.visibility = View.VISIBLE
            }
        }

        viewModel.headToHeadAccounts.observe(viewLifecycleOwner) { list ->
            friendsAdapter.submitList(list)
            updateEmptyState(emptyState)
        }

        viewModel.headToHeadGuests.observe(viewLifecycleOwner) { list ->
            guestsAdapter.submitList(list)
            updateEmptyState(emptyState)
        }
    }

    private fun updateEmptyState(emptyView: TextView) {
        val isEmpty = friendsAdapter.itemCount == 0 && guestsAdapter.itemCount == 0
        emptyView.visibility = if (isEmpty) View.VISIBLE else View.GONE
    }
}
