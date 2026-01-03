package com.intagri.mtgleader.persistence.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = PlayerProfileCounterTemplateCrossRefEntity.TABLE_PLAYER_COUNTER_CROSS_REFS,
    indices = [
        Index(
            value = [PlayerProfileCounterTemplateCrossRefEntity.COLUMN_COUNTER_TEMPLATE_ID]
        )
    ],
    primaryKeys = [PlayerProfileCounterTemplateCrossRefEntity.COLUMN_PLAYER_PROFILE_ID,
        PlayerProfileCounterTemplateCrossRefEntity.COLUMN_COUNTER_TEMPLATE_ID]
)
data class PlayerProfileCounterTemplateCrossRefEntity(
    @ColumnInfo(name = COLUMN_PLAYER_PROFILE_ID)
    val playerProfileId: String,
    @ColumnInfo(name = COLUMN_COUNTER_TEMPLATE_ID)
    val counterTemplateId: Int
) {
    companion object {
        const val TABLE_PLAYER_COUNTER_CROSS_REFS = "player_counter_cross_refs"
        const val COLUMN_PLAYER_PROFILE_ID = "x_player_profile_id"
        const val COLUMN_COUNTER_TEMPLATE_ID = "x_counter_template_id"
    }
}
