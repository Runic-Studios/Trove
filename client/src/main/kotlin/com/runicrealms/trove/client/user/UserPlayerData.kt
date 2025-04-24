package com.runicrealms.trove.client.user

import com.runicrealms.trove.client.user.player.PlayerAchievements
import com.runicrealms.trove.client.user.player.PlayerBank
import com.runicrealms.trove.client.user.player.PlayerColumn
import com.runicrealms.trove.client.user.player.PlayerGathering
import com.runicrealms.trove.client.user.player.PlayerMounts
import com.runicrealms.trove.client.user.player.PlayerSettings
import com.runicrealms.trove.client.user.player.PlayerTraits
import com.runicrealms.trove.generated.api.schema.v1.players.PlayerAchievementsData
import com.runicrealms.trove.generated.api.schema.v1.players.PlayerBankData
import com.runicrealms.trove.generated.api.schema.v1.players.PlayerGatheringData
import com.runicrealms.trove.generated.api.schema.v1.players.PlayerMountsData
import com.runicrealms.trove.generated.api.schema.v1.players.PlayerSettingsData
import com.runicrealms.trove.generated.api.schema.v1.players.PlayerTraitsData
import com.runicrealms.trove.generated.api.trove.ExistsRequest
import com.runicrealms.trove.generated.api.trove.LoadRequest
import com.runicrealms.trove.generated.api.trove.LockInfo
import com.runicrealms.trove.generated.api.trove.TroveServiceGrpcKt
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class UserPlayerData internal constructor(
    potential: Potential,
    var empty: Boolean,
    val achievements: PlayerAchievements,
    val bank: PlayerBank,
    val gathering: PlayerGathering,
    val mounts: PlayerMounts,
    val settings: PlayerSettings,
    val traits: PlayerTraits
) : UserChangeStager(
    potential.stub,
    potential.lock,
    potential.superKeys,
    ConcurrentHashMap.newKeySet<PlayerColumn>().apply {
        add(achievements)
        add(bank)
        add(gathering)
        add(mounts)
        add(settings)
        add(traits)
    },
) {

    internal data class Potential(
        val user: UUID,
        val stub: TroveServiceGrpcKt.TroveServiceCoroutineStub,
        val lock: LockInfo
    ) {
        val superKeys = mapOf("user_id" to user.toString())
    }

    companion object {
        internal suspend fun exists(potential: Potential): Result<Boolean> {
            val response = potential.stub.exists(
                ExistsRequest.newBuilder()
                .setTable(PlayerColumn.Companion.TABLE_NAME)
                .setLock(potential.lock)
                .putAllKeys(potential.superKeys)
                .build())

            if (!response.success) {
                return Result.failure(IllegalStateException(response.errorMessage))
            }
            return Result.success(response.exists)
        }

        internal suspend fun load(potential: Potential): Result<UserPlayerData> {
            val response = potential.stub.load(
                LoadRequest.newBuilder()
                    .setTable(PlayerColumn.Companion.TABLE_NAME)
                    .setLock(potential.lock)
                    .putAllSuperKeys(potential.superKeys)
                    .addAllColumns(
                        listOf<String>(
                            PlayerAchievements.Companion.COLUMN_NAME,
                            PlayerBank.Companion.COLUMN_NAME,
                            PlayerGathering.Companion.COLUMN_NAME,
                            PlayerMounts.Companion.COLUMN_NAME,
                            PlayerSettings.Companion.COLUMN_NAME,
                            PlayerTraits.Companion.COLUMN_NAME
                        )
                    )
                    .build()
            )
            if (!response.success || response.columnDataMap == null || response.columnDataCount == 0) {
                return Result.failure(IllegalStateException(response.errorMessage))
            }
            val userPlayerData = UserPlayerData(
                potential,
                false,
                PlayerAchievements(
                    PlayerAchievementsData.parseFrom(response.columnDataMap[PlayerAchievements.Companion.COLUMN_NAME])
                        .toBuilder()
                ),
                PlayerBank(
                    PlayerBankData.parseFrom(response.columnDataMap[PlayerBank.Companion.COLUMN_NAME]).toBuilder()
                ),
                PlayerGathering(
                    PlayerGatheringData.parseFrom(response.columnDataMap[PlayerGathering.Companion.COLUMN_NAME])
                        .toBuilder()
                ),
                PlayerMounts(
                    PlayerMountsData.parseFrom(response.columnDataMap[PlayerMounts.Companion.COLUMN_NAME]).toBuilder()
                ),
                PlayerSettings(
                    PlayerSettingsData.parseFrom(response.columnDataMap[PlayerSettings.Companion.COLUMN_NAME])
                        .toBuilder()
                ),
                PlayerTraits(
                    PlayerTraitsData.parseFrom(response.columnDataMap[PlayerTraits.Companion.COLUMN_NAME]).toBuilder()
                )
            )
            return Result.success(userPlayerData)
        }

        internal fun createEmpty(potential: Potential) = UserPlayerData(
            potential,
            true,
            PlayerAchievements(PlayerAchievementsData.newBuilder()),
            PlayerBank(PlayerBankData.newBuilder()),
            PlayerGathering(PlayerGatheringData.newBuilder()),
            PlayerMounts(PlayerMountsData.newBuilder()),
            PlayerSettings(PlayerSettingsData.newBuilder()),
            PlayerTraits(PlayerTraitsData.newBuilder())
        )

        internal suspend fun loadOrCreate(potential: Potential): Result<UserPlayerData> {
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