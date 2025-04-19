package com.runicrealms.trove.client.user

import com.runicrealms.trove.client.user.player.Player
import com.runicrealms.trove.generated.api.trove.ClaimLockRequest
import com.runicrealms.trove.generated.api.trove.LockInfo
import com.runicrealms.trove.generated.api.trove.ReleaseLockRequest
import com.runicrealms.trove.generated.api.trove.TroveServiceGrpcKt
import com.runicrealms.trove.client.user.character.Character
import kotlinx.coroutines.runBlocking
import java.util.UUID

class UserClaim internal constructor(
    private val user: UUID,
    private val lock: LockInfo,
    private val stub: TroveServiceGrpcKt.TroveServiceCoroutineStub,
    private val leaseMillis: Long
) {

    private var open = true

    fun refreshLease(): Result<Unit> = runBlocking {
        if (!open) {
            return@runBlocking Result.failure(IllegalStateException("Lease has already been closed, please open new claim"))
        }
        val response = stub.claimLock(
            ClaimLockRequest.newBuilder()
                .setUserId(lock.userId)
                .setServerId(lock.serverId)
                .setLeaseMillis(leaseMillis)
                .build()
        )
        if (!response.success) {
            return@runBlocking Result.failure(IllegalStateException(response.errorMessage))
        }
        Result.success(Unit)
    }

    fun loadPlayer(): Result<Player> = runBlocking {
        return@runBlocking Player.load(user, stub, lock)
    }

    fun loadCharacter(slot: Int): Result<Character> = runBlocking {
        return@runBlocking Character.load(user, slot, stub, lock)
    }

    fun releaseAndClose(): Result<Unit> = runBlocking {
        val response = stub.releaseLock(
            ReleaseLockRequest.newBuilder()
                .setUserId(lock.userId)
                .setServerId(lock.serverId)
                .build()
        )
        if (!response.success) {
            return@runBlocking Result.failure(IllegalStateException(response.errorMessage))
        }
        open = false
        Result.success(Unit)
    }

}