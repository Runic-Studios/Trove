package com.runicrealms.trove.client.user

import com.runicrealms.trove.client.user.player.PlayerColumn
import com.runicrealms.trove.generated.api.trove.LockInfo
import com.runicrealms.trove.generated.api.trove.SaveRequest
import com.runicrealms.trove.generated.api.trove.TroveServiceGrpcKt
import kotlinx.coroutines.runBlocking

abstract class UserChangeStager(
    private val stub: TroveServiceGrpcKt.TroveServiceCoroutineStub,
    private val lock: LockInfo,
    protected val superKeys: Map<String, String>,
    protected val columns: Collection<UserColumn>
) {

    @Synchronized
    fun save(): Result<Unit> = runBlocking {
        val request = SaveRequest.newBuilder()
            .setTable(PlayerColumn.TABLE_NAME)
            .setLock(lock)
            .putAllSuperKeys(superKeys)
        for (column in columns) {
            if (column.stagedChanges != null) {
                request.putColumnData(column.column, column.stagedChanges!!)
            }
        }
        if (request.columnDataCount > 0) {
            val response = stub.save(request.build())
            if (!response.success) {
                return@runBlocking Result.failure(IllegalStateException(response.errorMessage))
            }
        }
        for (column in columns) {
            column.stagedChanges = null
        }
        return@runBlocking Result.success(Unit)
    }

    @Synchronized
    fun stageChanges(column: PlayerColumn) {
        column.stagedChanges = column.getRawData()
    }

}