package com.runicrealms.trove.client.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("trove.client")
class TroveClientProperties {
    var host: String = "localhost"
    var port: Int = 9090
}
