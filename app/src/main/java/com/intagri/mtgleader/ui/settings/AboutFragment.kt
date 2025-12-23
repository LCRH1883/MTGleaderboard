package com.intagri.mtgleader.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import com.intagri.mtgleader.BuildConfig
import com.intagri.mtgleader.R

class AboutFragment : Fragment() {

    companion object {
        fun newInstance(): AboutFragment {
            val args = Bundle()
            val fragment = AboutFragment()
            fragment.arguments = args
            return fragment
        }

        const val TAG = "fragment_about"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        return inflater.inflate(R.layout.fragment_about, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressed()
        }
        val versionText: TextView = view.findViewById(R.id.version_text)
        versionText.text = getString(R.string.version_label, BuildConfig.VERSION_NAME)
    }
}
