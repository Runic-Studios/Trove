package com.runicrealms.trove.client

import com.runicrealms.trove.client.user.UserClaim
import com.runicrealms.trove.generated.api.trove.LockInfo
import com.runicrealms.trove.generated.api.trove.TroveServiceGrpcKt
import io.grpc.ManagedChannel
import kotlinx.coroutines.runBlocking
import java.util.UUID

class TroveClient(private val troveManagedChannel: ManagedChannel, private val serverId: String) {

    private val stub by lazy {
        TroveServiceGrpcKt.TroveServiceCoroutineStub(troveManagedChannel)
    }

    fun createClaim(user: UUID, leaseExpiryMillis: Long): Result<UserClaim> = runBlocking {
        val lockInfo = LockInfo.newBuilder().setUserId(user.toString()).setServerId(serverId).build()
        val claim = UserClaim(user, lockInfo, stub, leaseExpiryMillis)
        val result = claim.refreshLease()
        if (result.isFailure) return@runBlocking Result.failure(result.exceptionOrNull()!!)
        Result.success(claim)
    }

}
