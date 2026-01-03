package com.intagri.mtgleader.ui.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.intagri.mtgleader.R
import com.intagri.mtgleader.ui.matchdetails.MatchDetailsActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MatchHistoryFragment : Fragment() {

    companion object {
        const val TAG = "fragment_match_history"
        fun newInstance(): MatchHistoryFragment = MatchHistoryFragment()
    }

    private val viewModel: MatchHistoryViewModel by viewModels()
    private lateinit var adapter: MatchHistoryAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_match_history, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val toolbar = view.findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        val list = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.match_history_list)
        val emptyState = view.findViewById<android.widget.TextView>(R.id.match_history_empty)
        adapter = MatchHistoryAdapter { item ->
            startActivity(MatchDetailsActivity.startIntent(requireContext(), item.localMatchId))
        }
        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = adapter
        val decoration = DividerItemDecoration(
            requireContext(),
            DividerItemDecoration.VERTICAL
        )
        decoration.setDrawable(
            requireContext().getDrawable(R.drawable.player_divider)!!
        )
        list.addItemDecoration(decoration)

        viewModel.matches.observe(viewLifecycleOwner) { matches ->
            adapter.submitList(matches)
            emptyState.visibility = if (matches.isEmpty()) View.VISIBLE else View.GONE
        }
    }
}
