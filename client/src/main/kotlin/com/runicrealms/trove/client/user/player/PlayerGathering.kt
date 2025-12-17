package com.runicrealms.trove.client.user.player

import com.google.protobuf.ByteString
import com.runicrealms.trove.generated.api.schema.v1.players.PlayerGatheringData

class PlayerGathering(val data: PlayerGatheringData.Builder): PlayerColumn(COLUMN_NAME) {

    companion object {
        const val COLUMN_NAME = "gathering"
    }

    override fun getRawData(): ByteString = data.build().toByteString()

}