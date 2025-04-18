package com.runicrealms.trove.client

import com.runicrealms.trove.generated.api.*
import com.runicrealms.trove.generated.api.schema.players.PlayerData
import io.grpc.ManagedChannel
import kotlinx.coroutines.runBlocking
import java.util.UUID

class TroveClient(private val troveManagedChannel: ManagedChannel) {

    companion object {
        const val PLAYERS_DATABASE_NAME = "players"
        const val LATEST_PLAYERS_SCHEMA_VERSION = "v3"
    }

    private val stub by lazy {
        TroveServiceGrpcKt.TroveServiceCoroutineStub(troveManagedChannel)
    }

    /**
     * Saves a player's data to the database.
     *
     * @param data Protobuf-generated schema for the player's data
     * @return Response from the database
     */
    fun savePlayerData(data: PlayerData) = runBlocking {
        stub.saveData(
            SaveDataRequest.newBuilder()
                .setDatabaseName(PLAYERS_DATABASE_NAME)
                .setRecordId(data.uuid)
                .setSchemaVersion(LATEST_PLAYERS_SCHEMA_VERSION)
                .setDataBlob(data.toByteString())
                .build()
        )
    }

    /**
     * Loads a player's data from the database.
     *
     * @return Response from the database
     */
    fun loadPlayerData(uuid: UUID) = runBlocking {
        LoadDataRequest.newBuilder()
            .setDatabaseName(PLAYERS_DATABASE_NAME)
            .setRecordId(uuid.toString())
            .build()
    }
}
