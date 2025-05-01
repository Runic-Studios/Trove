package com.runicrealms.trove.client.user

import com.runicrealms.trove.client.user.character.CharacterColumn
import com.runicrealms.trove.client.user.character.CharacterInventory
import com.runicrealms.trove.client.user.character.CharacterProfession
import com.runicrealms.trove.client.user.character.CharacterQuests
import com.runicrealms.trove.client.user.character.CharacterSkills
import com.runicrealms.trove.client.user.character.CharacterSpells
import com.runicrealms.trove.client.user.character.CharacterTraits
import com.runicrealms.trove.generated.api.schema.v1.character.CharacterInventoryData
import com.runicrealms.trove.generated.api.schema.v1.character.CharacterProfessionData
import com.runicrealms.trove.generated.api.schema.v1.character.CharacterQuestsData
import com.runicrealms.trove.generated.api.schema.v1.character.CharacterSkillsData
import com.runicrealms.trove.generated.api.schema.v1.character.CharacterSpellsData
import com.runicrealms.trove.generated.api.schema.v1.character.CharacterTraitsData
import com.runicrealms.trove.generated.api.trove.ExistsRequest
import com.runicrealms.trove.generated.api.trove.LoadRequest
import com.runicrealms.trove.generated.api.trove.LockInfo
import com.runicrealms.trove.generated.api.trove.TroveServiceGrpcKt
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class UserCharacterData internal constructor(
    potential: Potential,
    var empty: Boolean,
    val inventory: CharacterInventory,
    val profession: CharacterProfession,
    val quests: CharacterQuests,
    val skills: CharacterSkills,
    val spells: CharacterSpells,
    val traits: CharacterTraits
): UserChangeStager(
    potential.stub,
    potential.lock,
    potential.superKeys,
    ConcurrentHashMap.newKeySet<CharacterColumn>().apply {
        add(inventory)
        add(profession)
        add(quests)
        add(skills)
        add(spells)
        add(traits)
    },
) {

    val slot = potential.slot

    internal data class Potential(
        val user: UUID,
        val slot: Int,
        val stub: TroveServiceGrpcKt.TroveServiceCoroutineStub,
        val lock: LockInfo
    ) {
        val superKeys = mapOf("user_id" to user.toString(), "slot" to slot.toString())
    }

    companion object {
        internal suspend fun exists(potential: Potential): Result<Boolean> {
            val response = potential.stub.exists(
                ExistsRequest.newBuilder()
                .setTable(CharacterColumn.Companion.TABLE_NAME)
                .setLock(potential.lock)
                .putAllSuperKeys(potential.superKeys)
                .build())

            if (!response.success) {
                return Result.failure(IllegalStateException(response.errorMessage))
            }
            return Result.success(response.exists)
        }

        internal suspend fun load(potential: Potential): Result<UserCharacterData> {
            val response = potential.stub.load(
                LoadRequest.newBuilder()
                .setTable(CharacterColumn.Companion.TABLE_NAME)
                .setLock(potential.lock)
                .putAllSuperKeys(potential.superKeys)
                .addAllColumns(listOf<String>(
                    CharacterInventory.Companion.COLUMN_NAME,
                    CharacterProfession.Companion.COLUMN_NAME,
                    CharacterQuests.Companion.COLUMN_NAME,
                    CharacterSkills.Companion.COLUMN_NAME,
                    CharacterSpells.Companion.COLUMN_NAME,
                    CharacterTraits.Companion.COLUMN_NAME
                ))
                .build()
            )
            if (!response.success || response.columnDataMap == null || response.columnDataCount == 0) {
                return Result.failure(IllegalStateException(response.errorMessage))
            }
            val userCharacterData = UserCharacterData(
                potential,
                false,
                CharacterInventory(
                    CharacterInventoryData.parseFrom(response.columnDataMap[CharacterInventory.Companion.COLUMN_NAME])
                        .toBuilder()
                ),
                CharacterProfession(
                    CharacterProfessionData.parseFrom(response.columnDataMap[CharacterProfession.Companion.COLUMN_NAME])
                        .toBuilder()
                ),
                CharacterQuests(
                    CharacterQuestsData.parseFrom(response.columnDataMap[CharacterQuests.Companion.COLUMN_NAME])
                        .toBuilder()
                ),
                CharacterSkills(
                    CharacterSkillsData.parseFrom(response.columnDataMap[CharacterSkills.Companion.COLUMN_NAME])
                        .toBuilder()
                ),
                CharacterSpells(
                    CharacterSpellsData.parseFrom(response.columnDataMap[CharacterSpells.Companion.COLUMN_NAME])
                        .toBuilder()
                ),
                CharacterTraits(
                    CharacterTraitsData.parseFrom(response.columnDataMap[CharacterTraits.Companion.COLUMN_NAME])
                        .toBuilder()
                ),
                )
            return Result.success(userCharacterData)
        }

        internal fun createEmpty(potential: Potential) = UserCharacterData(
            potential,
            true,
            CharacterInventory(CharacterInventoryData.newBuilder()),
            CharacterProfession(CharacterProfessionData.newBuilder()),
            CharacterQuests(CharacterQuestsData.newBuilder()),
            CharacterSkills(CharacterSkillsData.newBuilder()),
            CharacterSpells(CharacterSpellsData.newBuilder()),
            CharacterTraits(CharacterTraitsData.newBuilder())
        )

        internal suspend fun loadOrCreate(potential: Potential): Result<UserCharacterData> {
            val existsResult = exists(potential)
            if (!existsResult.isSuccess) return Result.failure(existsResult.exceptionOrNull()!!)
            val exists = existsResult.getOrNull()!!
            return if (!exists) {
                Result.success(createEmpty(potential))
            } else {
                load(potential)
            }
        }
    }

}