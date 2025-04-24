package com.runicrealms.trove.client.user.player

import com.google.protobuf.ByteString
import com.runicrealms.trove.generated.api.schema.v1.players.PlayerBankData

class PlayerBank(val data: PlayerBankData.Builder): PlayerColumn(COLUMN_NAME) {

    companion object {
        const val COLUMN_NAME = "bank"
    }

    override fun getRawData(): ByteString = data.build().toByteString()

}