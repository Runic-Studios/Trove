package com.runicrealms.trove.client.user.character

import com.google.protobuf.ByteString
import com.runicrealms.trove.generated.api.schema.v1.character.CharacterInventoryData

class CharacterInventory(val data: CharacterInventoryData.Builder): CharacterColumn(COLUMN_NAME) {

    companion object {
        const val COLUMN_NAME = "inventory"
    }

    override fun getRawData(): ByteString = data.build().toByteString()

}