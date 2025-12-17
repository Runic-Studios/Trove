package com.runicrealms.trove.client.user

import com.runicrealms.trove.generated.api.trove.ClaimLockRequest
import com.runicrealms.trove.generated.api.trove.LockInfo
import com.runicrealms.trove.generated.api.trove.ReleaseLockRequest
import com.runicrealms.trove.generated.api.trove.TroveServiceGrpcKt
import java.util.UUID

class UserClaim internal constructor(
    private val user: UUID,
    private val lock: LockInfo,
    private val stub: TroveServiceGrpcKt.TroveServiceCoroutineStub,
    private val leaseMillis: Long
) {

    private var open = true

    suspend fun refreshLease(): Result<Unit> {
        if (!open) {
            return Result.failure(IllegalStateException("Lease has already been closed, please open new claim"))
        }
        val response = stub.claimLock(
            ClaimLockRequest.newBuilder()
                .setUserId(lock.userId)
                .setServerId(lock.serverId)
                .setLeaseMillis(leaseMillis)
                .build()
        )
        if (!response.success) {
            return Result.failure(IllegalStateException(response.errorMessage))
        }
        return Result.success(Unit)
    }

    suspend fun loadPlayer(): Result<UserPlayerData> {
        return UserPlayerData.loadOrCreate(UserPlayerData.Potential(user, stub, lock))
    }

    suspend fun loadCharacter(slot: Int): Result<UserCharacterData> {
        return UserCharacterData.loadOrCreate(UserCharacterData.Potential(user, slot, stub, lock))
    }

    suspend fun loadCharactersTraits(): Result<UserCharactersTraits> {
        return UserCharactersTraits.load(UserCharactersTraits.Potential(user, stub, lock))
    }

    suspend fun releaseAndClose(): Result<Unit> {
        val response = stub.releaseLock(
            ReleaseLockRequest.newBuilder()
                .setUserId(lock.userId)
                .setServerId(lock.serverId)
                .build()
        )
        if (!response.success) {
            return Result.failure(IllegalStateException(response.errorMessage))
        }
        open = false
        return Result.success(Unit)
    }

}