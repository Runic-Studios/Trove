import com.google.protobuf.gradle.id

plugins {
    kotlin("jvm") version "2.1.20"
    id("com.google.protobuf") version "0.9.4"
    `maven-publish`
}

group = "com.runicrealms.trove"
version = "0.0.13-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // gRPC + protobuf
    implementation("io.grpc:grpc-kotlin-stub:1.4.1")
    implementation("io.grpc:grpc-netty:1.71.0")
    implementation("com.google.protobuf:protobuf-java:4.30.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("io.grpc:grpc-protobuf:1.71.0")

    // For logging, config, etc.
    implementation("org.jetbrains.kotlin:kotlin-stdlib")

    // For library injection
    implementation("com.google.inject:guice:7.0.0")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:4.30.1"
    }
    plugins {
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.71.0"
        }
        id("grpckt") {
            artifact = "io.grpc:protoc-gen-grpc-kotlin:1.4.1:jdk8@jar"
        }
    }
    generateProtoTasks {
        all().forEach {
            it.plugins {
                id("grpc")
                id("grpckt")
            }
        }
    }
}

val currentPlayersSchemaVersion = "v1"

sourceSets {
    main {
        proto {
            srcDir("../api")
            srcDir("../api/trove")
            srcDir("../api/schema/$currentPlayersSchemaVersion")
        }
    }
}

tasks.named("compileJava") {
    dependsOn("generateProto")
}

val archiveName = "trove-client"

tasks.jar {
    enabled = true
    archiveBaseName = archiveName
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            groupId = project.group.toString()
            artifactId = archiveName
            version = project.version.toString()
        }
    }
    repositories {
        maven {
            name = "nexus"
            url = uri("https://nexus.runicrealms.com/repository/maven-snapshots/")
            credentials {
                username = System.getenv("NEXUS_USERNAME")
                password = System.getenv("NEXUS_PASSWORD")
            }
        }
    }
}