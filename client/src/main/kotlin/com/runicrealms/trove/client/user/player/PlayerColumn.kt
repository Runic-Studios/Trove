package com.runicrealms.trove.client.user.player

import com.runicrealms.trove.client.user.UserColumn

sealed class PlayerColumn(
    override val column: String
): UserColumn(TABLE_NAME, column) {

    companion object {
        const val TABLE_NAME = "players"
    }

}