package com.runicrealms.trove.client

import com.google.inject.Inject
import com.google.inject.Provider
import io.grpc.ManagedChannelBuilder

class TroveClientProvider @Inject constructor(private val config: TroveClientConfig): Provider<TroveClient> {

    override fun get(): TroveClient {
        val channel = ManagedChannelBuilder
            .forAddress(config.host, config.port)
            .usePlaintext()
            .build()
        return TroveClient(channel)
    }

}