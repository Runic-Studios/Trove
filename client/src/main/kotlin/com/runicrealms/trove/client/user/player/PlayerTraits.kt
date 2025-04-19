package com.runicrealms.trove.client.user.player

import com.google.protobuf.ByteString
import com.runicrealms.trove.generated.api.schema.v1.players.PlayerTraitsData

class PlayerTraits(val data: PlayerTraitsData): PlayerColumn(COLUMN_NAME) {

    companion object {
        const val COLUMN_NAME = "traits"
    }

    override fun getRawData(): ByteString = data.toByteString()

}