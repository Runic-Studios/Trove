package com.runicrealms.trove.client

/**
 * @param host The host for the gRPC stub
 * @param port The port on which to host the gRPC stub to communicate with the server
 * @param clientName The unique identifier of this client among all database clients
 *
 * clientName should correspond to the resource name of the service pod running the Trove Client.
 */
data class TroveClientConfig(
    val host: String = "localhost",
    val port: Int = 9091,
    val clientName: String
)