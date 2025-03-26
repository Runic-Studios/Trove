package com.runicrealms.trove.client

import com.runicrealms.trove.generated.api.*
import io.grpc.ManagedChannel
import kotlinx.coroutines.runBlocking
import org.springframework.stereotype.Service

@Service
class TroveClient(private val troveManagedChannel: ManagedChannel) {

    private val stub by lazy {
        TroveServiceGrpcKt.TroveServiceCoroutineStub(troveManagedChannel)
    }

    /**
     * Save data to the trove server
     * @param databaseName e.g. "players"
     * @param schemaVersion e.g. 2
     * @param dataBlob the serialized Protobuf for your domain data
     * @param recordId the unique ID e.g. "player-123"
     */
    fun saveData(
        databaseName: String,
        schemaVersion: Int,
        dataBlob: ByteArray,
        recordId: String
    ): SaveDataResponse = runBlocking {
        stub.saveData(
            SaveDataRequest.newBuilder()
                .setDatabaseName(databaseName)
                .setSchemaVersion(schemaVersion)
                .setDataBlob(com.google.protobuf.ByteString.copyFrom(dataBlob))
                .setRecordId(recordId)
                .build()
        )
    }

    /**
     * Load data from the trove server
     * @param databaseName e.g. "players"
     * @param recordId the unique ID e.g. "player-123"
     */
    fun loadData(
        databaseName: String,
        recordId: String
    ): LoadDataResponse = runBlocking {
        stub.loadData(
            LoadDataRequest.newBuilder()
                .setDatabaseName(databaseName)
                .setRecordId(recordId)
                .build()
        )
    }
}
