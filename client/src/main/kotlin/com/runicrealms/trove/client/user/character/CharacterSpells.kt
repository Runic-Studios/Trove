package com.runicrealms.trove.client.user.character

import com.google.protobuf.ByteString
import com.runicrealms.trove.generated.api.schema.v1.character.CharacterSpellsData

class CharacterSpells(val data: CharacterSpellsData.Builder): CharacterColumn(COLUMN_NAME) {

    companion object {
        const val COLUMN_NAME = "spells"
    }

    override fun getRawData(): ByteString = data.build().toByteString()

}