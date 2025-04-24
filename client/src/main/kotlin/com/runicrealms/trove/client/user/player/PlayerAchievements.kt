package com.runicrealms.trove.client.user.player

import com.google.protobuf.ByteString
import com.runicrealms.trove.generated.api.schema.v1.players.PlayerAchievementsData

class PlayerAchievements(val data: PlayerAchievementsData.Builder): PlayerColumn(COLUMN_NAME) {

    companion object {
        const val COLUMN_NAME = "achievements"
    }

    override fun getRawData(): ByteString = data.build().toByteString()

}