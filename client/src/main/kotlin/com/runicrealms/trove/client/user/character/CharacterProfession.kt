package com.runicrealms.trove.client.user.character

import com.google.protobuf.ByteString
import com.runicrealms.trove.generated.api.schema.v1.character.CharacterProfessionData

class CharacterProfession(val data: CharacterProfessionData.Builder): CharacterColumn(COLUMN_NAME) {

    companion object {
        const val COLUMN_NAME = "profession"
    }

    override fun getRawData(): ByteString = data.build().toByteString()

}