package com.runicrealms.trove.client

/**
 * @param host The remote host of the gRPC server
 * @param port The port on which to connect to the gRPC server
 * @param clientName The unique identifier of this client among all database clients
 *
 * clientName should correspond to the resource name of the service pod running the Trove Client.
 */
data class TroveClientConfig(
    val host: String = "trove-server", // Uses coredns service resolution within the same namespace
    val port: Int = 9090,
    val clientName: String
)