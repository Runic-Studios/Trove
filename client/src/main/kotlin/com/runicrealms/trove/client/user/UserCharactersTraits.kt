package com.runicrealms.trove.client.user

import com.runicrealms.trove.client.user.character.CharacterColumn
import com.runicrealms.trove.client.user.character.CharacterTraits
import com.runicrealms.trove.generated.api.schema.v1.character.CharacterTraitsData
import com.runicrealms.trove.generated.api.trove.LoadRequest
import com.runicrealms.trove.generated.api.trove.LockInfo
import com.runicrealms.trove.generated.api.trove.TroveServiceGrpcKt
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * Specifically used to load the character traits of all characters owned by a user.
 * This is used to grab information for the character select screen only.
 */
class UserCharactersTraits(
    val characters: Map<Int, CharacterTraits> // Map from slot -> traits
) {

    internal data class Potential(
        val user: UUID,
        val stub: TroveServiceGrpcKt.TroveServiceCoroutineStub,
        val lock: LockInfo
    ) {
        val superKeys = mapOf("user_id" to user.toString())
    }

    companion object {
        internal suspend fun load(potential: Potential): Result<UserCharactersTraits> {
            val response = potential.stub.load(
                LoadRequest.newBuilder()
                    .setTable(CharacterColumn.Companion.TABLE_NAME)
                    .setLock(potential.lock)
                    .putAllSuperKeys(potential.superKeys)
                    .addAllColumns(
                        listOf<String>(
                            "slot",
                            CharacterTraits.Companion.COLUMN_NAME
                        )
                    )
                    .build()
            )
            if (!response.success) {
                return Result.failure(IllegalStateException(response.errorMessage))
            }
            val characters = mutableMapOf<Int, CharacterTraits>()
            for (row in response.rowsList) {
                val rawSlot = row.columnDataMap["slot"]
                val slotBytes = rawSlot?.toByteArray()
                    ?: return Result.failure(IllegalStateException("Couldn't read row with slot $rawSlot"))
                val padded = ByteArray(4)
                System.arraycopy(slotBytes, 0, padded, 4 - slotBytes.size, slotBytes.size)
                val slot = ByteBuffer.wrap(padded).order(ByteOrder.BIG_ENDIAN).int
                val traits = CharacterTraits(
                    CharacterTraitsData.parseFrom(row.columnDataMap[CharacterTraits.Companion.COLUMN_NAME]).toBuilder()
                )
                characters[slot] = traits
            }
            return Result.success(UserCharactersTraits(characters))
        }
    }

}