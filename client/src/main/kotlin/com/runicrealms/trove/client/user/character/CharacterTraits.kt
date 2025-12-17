package com.runicrealms.trove.client.user.character

import com.google.protobuf.ByteString
import com.runicrealms.trove.generated.api.schema.v1.character.CharacterTraitsData

class CharacterTraits(val data: CharacterTraitsData.Builder): CharacterColumn(COLUMN_NAME) {

    companion object {
        const val COLUMN_NAME = "traits"
    }

    override fun getRawData(): ByteString = data.build().toByteString()

}