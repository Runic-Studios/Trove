package com.runicrealms.trove.client.user.character

import com.google.protobuf.ByteString
import com.runicrealms.trove.generated.api.schema.v1.character.CharacterQuestsData

class CharacterQuests(val data: CharacterQuestsData): CharacterColumn(COLUMN_NAME) {

    companion object {
        const val COLUMN_NAME = "quests"
    }

    override fun getRawData(): ByteString = data.toByteString()

}