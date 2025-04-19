package com.runicrealms.trove.client.user.player

import com.runicrealms.trove.client.user.UserChangeStager
import com.runicrealms.trove.generated.api.schema.v1.players.PlayerAchievementsData
import com.runicrealms.trove.generated.api.schema.v1.players.PlayerBankData
import com.runicrealms.trove.generated.api.schema.v1.players.PlayerGatheringData
import com.runicrealms.trove.generated.api.schema.v1.players.PlayerMountsData
import com.runicrealms.trove.generated.api.schema.v1.players.PlayerSettingsData
import com.runicrealms.trove.generated.api.schema.v1.players.PlayerTraitsData
import com.runicrealms.trove.generated.api.trove.LoadRequest
import com.runicrealms.trove.generated.api.trove.LockInfo
import com.runicrealms.trove.generated.api.trove.TroveServiceGrpcKt
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class Player(
    stub: TroveServiceGrpcKt.TroveServiceCoroutineStub,
    lock: LockInfo,
    superKeys: Map<String, String>,
    val achievements: PlayerAchievements,
    val bank: PlayerBank,
    val gathering: PlayerGathering,
    val mounts: PlayerMounts,
    val settings: PlayerSettings,
    val traits: PlayerTraits
): UserChangeStager(
    stub,
    lock,
    superKeys,
    ConcurrentHashMap.newKeySet<PlayerColumn>().apply {
        add(achievements)
        add(bank)
        add(gathering)
        add(mounts)
        add(settings)
        add(traits)
    },
) {

    companion object {
        internal suspend fun load(
            user: UUID,
            stub: TroveServiceGrpcKt.TroveServiceCoroutineStub,
            lock: LockInfo
        ): Result<Player> {
            val superKeys = mapOf("user_id" to user.toString())

            val response = stub.load(LoadRequest.newBuilder()
                .setTable(PlayerColumn.TABLE_NAME)
                .setLock(lock)
                .putAllSuperKeys(superKeys)
                .addAllColumns(listOf<String>(
                    PlayerAchievements.COLUMN_NAME,
                    PlayerBank.COLUMN_NAME,
                    PlayerGathering.COLUMN_NAME,
                    PlayerMounts.COLUMN_NAME,
                    PlayerSettings.COLUMN_NAME,
                    PlayerTraits.COLUMN_NAME
                ))
                .build()
            )
            if (!response.success || response.columnDataMap == null || response.columnDataCount == 0) {
                return Result.failure(IllegalStateException(response.errorMessage))
            }
            val player = Player(
                stub,
                lock,
                superKeys,
                PlayerAchievements(PlayerAchievementsData.parseFrom(response.columnDataMap[PlayerAchievements.COLUMN_NAME])),
                PlayerBank(PlayerBankData.parseFrom(response.columnDataMap[PlayerBank.COLUMN_NAME])),
                PlayerGathering(PlayerGatheringData.parseFrom(response.columnDataMap[PlayerGathering.COLUMN_NAME])),
                PlayerMounts(PlayerMountsData.parseFrom(response.columnDataMap[PlayerMounts.COLUMN_NAME])),
                PlayerSettings(PlayerSettingsData.parseFrom(response.columnDataMap[PlayerSettings.COLUMN_NAME])),
                PlayerTraits(PlayerTraitsData.parseFrom(response.columnDataMap[PlayerTraits.COLUMN_NAME]))
            )
            return Result.success(player)
        }
    }

}