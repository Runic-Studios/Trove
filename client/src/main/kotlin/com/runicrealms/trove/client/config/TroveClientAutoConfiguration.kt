package com.runicrealms.trove.client.config

import com.runicrealms.trove.client.TroveClient
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(TroveClientProperties::class)
open class TroveClientAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    open fun troveManagedChannel(props: TroveClientProperties): ManagedChannel {
        return ManagedChannelBuilder
            .forAddress(props.host, props.port)
            .usePlaintext()
            .build()
    }

    @Bean
    @ConditionalOnMissingBean
    open fun troveClient(channel: ManagedChannel): TroveClient {
        return TroveClient(channel)
    }
}
