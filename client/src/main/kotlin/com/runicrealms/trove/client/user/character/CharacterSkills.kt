package com.runicrealms.trove.client.user.character

import com.google.protobuf.ByteString
import com.runicrealms.trove.generated.api.schema.v1.character.CharacterSkillsData

class CharacterSkills(val data: CharacterSkillsData.Builder): CharacterColumn(COLUMN_NAME) {

    companion object {
        const val COLUMN_NAME = "skills"
    }

    override fun getRawData(): ByteString = data.build().toByteString()

}