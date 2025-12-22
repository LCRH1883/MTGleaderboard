package com.intagri.mtgleader.persistence

import com.intagri.mtgleader.model.TabletopType

interface GameRepository {
    var startingLife : Int
    var numberOfPlayers: Int
    var keepScreenOn : Boolean
    var hideNavigation : Boolean
    var tabletopType: TabletopType
}