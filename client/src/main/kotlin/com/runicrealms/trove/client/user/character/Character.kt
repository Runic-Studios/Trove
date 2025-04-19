package com.runicrealms.trove.client.user.character

import com.runicrealms.trove.client.user.UserChangeStager
import com.runicrealms.trove.generated.api.schema.v1.character.CharacterInventoryData
import com.runicrealms.trove.generated.api.schema.v1.character.CharacterProfessionData
import com.runicrealms.trove.generated.api.schema.v1.character.CharacterQuestsData
import com.runicrealms.trove.generated.api.schema.v1.character.CharacterSkillsData
import com.runicrealms.trove.generated.api.schema.v1.character.CharacterSpellsData
import com.runicrealms.trove.generated.api.schema.v1.character.CharacterTraitsData
import com.runicrealms.trove.generated.api.trove.LoadRequest
import com.runicrealms.trove.generated.api.trove.LockInfo
import com.runicrealms.trove.generated.api.trove.TroveServiceGrpcKt
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class Character(
    stub: TroveServiceGrpcKt.TroveServiceCoroutineStub,
    lock: LockInfo,
    superKeys: Map<String, String>,
    val inventory: CharacterInventory,
    val profession: CharacterProfession,
    val quests: CharacterQuests,
    val skills: CharacterSkills,
    val spells: CharacterSpells,
    val traits: CharacterTraits
): UserChangeStager(
    stub,
    lock,
    superKeys,
    ConcurrentHashMap.newKeySet<CharacterColumn>().apply {
        add(inventory)
        add(profession)
        add(quests)
        add(skills)
        add(spells)
        add(traits)
    },
) {

    companion object {
        internal suspend fun load(
            user: UUID,
            slot: Int,
            stub: TroveServiceGrpcKt.TroveServiceCoroutineStub,
            lock: LockInfo
        ): Result<Character> {
            val superKeys = mapOf("user_id" to user.toString(), "slot" to slot.toString())

            val response = stub.load(LoadRequest.newBuilder()
                .setTable(CharacterColumn.TABLE_NAME)
                .setLock(lock)
                .putAllSuperKeys(superKeys)
                .addAllColumns(listOf<String>(
                    CharacterInventory.COLUMN_NAME,
                    CharacterProfession.COLUMN_NAME,
                    CharacterQuests.COLUMN_NAME,
                    CharacterSkills.COLUMN_NAME,
                    CharacterSpells.COLUMN_NAME,
                    CharacterTraits.COLUMN_NAME
                ))
                .build()
            )
            if (!response.success || response.columnDataMap == null || response.columnDataCount == 0) {
                return Result.failure(IllegalStateException(response.errorMessage))
            }
            val character = Character(
                stub,
                lock,
                superKeys,
                CharacterInventory(CharacterInventoryData.parseFrom(response.columnDataMap[CharacterInventory.COLUMN_NAME])),
                CharacterProfession(CharacterProfessionData.parseFrom(response.columnDataMap[CharacterProfession.COLUMN_NAME])),
                CharacterQuests(CharacterQuestsData.parseFrom(response.columnDataMap[CharacterQuests.COLUMN_NAME])),
                CharacterSkills(CharacterSkillsData.parseFrom(response.columnDataMap[CharacterSkills.COLUMN_NAME])),
                CharacterSpells(CharacterSpellsData.parseFrom(response.columnDataMap[CharacterSpells.COLUMN_NAME])),
                CharacterTraits(CharacterTraitsData.parseFrom(response.columnDataMap[CharacterTraits.COLUMN_NAME])),
                )
            return Result.success(character)
        }
    }

}