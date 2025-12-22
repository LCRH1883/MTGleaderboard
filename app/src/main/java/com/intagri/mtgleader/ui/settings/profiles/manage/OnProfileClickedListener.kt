package com.intagri.mtgleader.ui.settings.profiles.manage

interface OnProfileClickedListener {
    fun onProfileClicked(name: String)
    fun onProfileDeleteClicked(name: String)
    fun onProfileCreateClicked()
}