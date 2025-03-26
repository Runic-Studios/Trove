
# Trove
This project serves as a layer on top of the relational database that the Runic Realms project runs on.
The server component is written in Go and is designed to run on top of ScyllaDB.
The client component is written in Kotlin and is designed to run on top of PaperMC or Velocity.

## Background
- Runic Realms stores data serialized using protobuf schemas.
- As with many MMO games, these schemas can evolve over time to include different fields, remove others, and have modified structure for existing ones.
- Each time the database schema evolves, it is not ideal to have to write a one-time migration script that handles the evolution.

## Solution
- Instead of migrating data every time we update the schema, we will:
  - Version each different database schema
  - Write <b>transformers</b> (in Go) that can transform existing data from one version to another
  - Then we can run our transformer on outdated player data when we load it
    - For instance, if a player has data from before our most recent update (which involved some schema evolution), when they log in after the update, their data will be sent through the transformer to ensure that it is compatible the latest schema.

(As a bonus: we can <b>link</b> multiple transformers together in a chain! If we have versions v1, v2, and v3 for our data, then all we have to do is write transformers from v1 -> v2, and v2 -> v3. Then if a player that logs in with v1 data while our latest schema is v3, we can run their data through the first transformer to get v2 data, and through the second transformer to get v3 data).

## Implementation
- The `Trove/server` is written in Go and hosts a gRPC server to handle requests, wrapping around our ScyllaDB
  - This program will run as a separate deployment without our K8s cluster, and game servers and proxies will be able to send requests directly to it
  - Structs for a transformer chain exist in `server/internal/service/transformer.go`. Implementations of database transformers are in `server/internal/transformers`
  - By default, the ScyllaDB connection details, and port that we host the trove-server on are provided by environment variables `SCYLLA_HOSTS`, `SCYLLA_KEYSPACE`, and `TROVE_SERVER_PORT`.
- The `Trove/client` is written in Kotlin and connects to the gRPC server that handles requests to the database.
  - This library provides basic utilities for loading and saving data from the trove-server running in the cluster.
  - It is written as a Spring Boot Library for easy integration into other Spring apps.
  - Configuration properties include `trove.client.host` and `trove.client.port` for defining where to find the trove-server.
- The `Trove/api` is a set of protobuf files that define:
  - The evolving database schemas for different databases, in `api/db_schema/DATABASE_NAME/v?/*.proto`
  - RPC specs for the trove-server gRPC communication

## Building
### Trove Server
Install go 1.24.1, and protoc v29.4
```sh
cd server
go mod init
cd ..
./gen-proto.sh
cd server
go mod tidy
```
You can also build the docker image using `trove-server.Dockerfile`

### Trove Client
A simple `gradle build` should be enough.