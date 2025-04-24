package com.runicrealms.trove.client

import com.google.inject.AbstractModule
import com.google.inject.Singleton

class TroveClientModule(private val config: TroveClientConfig): AbstractModule() {

    override fun configure() {
        bind(TroveClientConfig::class.java).toInstance(config)
        bind(TroveClient::class.java).toProvider(TroveClientProvider::class.java).`in`(Singleton::class.java)
    }

}