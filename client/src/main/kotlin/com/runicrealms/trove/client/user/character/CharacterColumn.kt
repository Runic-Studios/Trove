package com.runicrealms.trove.client.user.character

import com.runicrealms.trove.client.user.UserColumn

sealed class CharacterColumn(
    override val column: String
): UserColumn(TABLE_NAME, column) {

    companion object {
        const val TABLE_NAME = "characters"
    }

}