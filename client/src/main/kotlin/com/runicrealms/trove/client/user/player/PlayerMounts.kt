package com.runicrealms.trove.client.user.player

import com.google.protobuf.ByteString
import com.runicrealms.trove.generated.api.schema.v1.players.PlayerMountsData

class PlayerMounts(val data: PlayerMountsData): PlayerColumn(COLUMN_NAME) {

    companion object {
        const val COLUMN_NAME = "mounts"
    }

    override fun getRawData(): ByteString = data.toByteString()

}