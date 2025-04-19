package com.runicrealms.trove.client.user.player

import com.google.protobuf.ByteString
import com.runicrealms.trove.generated.api.schema.v1.players.PlayerSettingsData

class PlayerSettings(val data: PlayerSettingsData): PlayerColumn(COLUMN_NAME) {

    companion object {
        const val COLUMN_NAME = "settings"
    }

    override fun getRawData(): ByteString = data.toByteString()

}