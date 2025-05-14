package com.runicrealms.trove.client.user

import com.runicrealms.trove.client.user.player.PlayerColumn
import com.runicrealms.trove.generated.api.trove.LockInfo
import com.runicrealms.trove.generated.api.trove.SaveRequest
import com.runicrealms.trove.generated.api.trove.TroveServiceGrpcKt
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

abstract class UserChangeStager(
    private val stub: TroveServiceGrpcKt.TroveServiceCoroutineStub,
    private val lock: LockInfo,
    protected val superKeys: Map<String, String>,
    protected val columns: Collection<UserColumn>
) {

    private val stageMutex = Mutex()

    suspend fun save(): Result<Unit> {
        stageMutex.withLock {
            val request = SaveRequest.newBuilder()
                .setTable(PlayerColumn.TABLE_NAME)
                .setLock(lock)
                .putAllSuperKeys(superKeys)
            for (column in columns) {
                val data = column.pendingChanges
                if (data != null) {
                    column.stagedChanges = data
                    request.putColumnData(column.column, data)
                }
            }
            if (request.columnDataCount > 0) {
                val response = stub.save(request.build())
                if (!response.success) {
                    return Result.failure(IllegalStateException(response.errorMessage))
                }
            }
            for (column in columns) {
                column.stagedChanges = null
                column.pendingChanges = null
            }
            return Result.success(Unit)
        }
    }

    fun stageChanges(column: UserColumn) {
        column.pendingChanges = column.getRawData()
    }

}