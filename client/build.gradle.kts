import com.google.protobuf.gradle.id
import org.jetbrains.kotlin.gradle.plugin.mpp.pm20.util.archivesName

plugins {
    kotlin("jvm") version "1.9.25"
    id("org.springframework.boot") version "3.4.3"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.google.protobuf") version "0.9.4"
    `maven-publish`
}

group = "com.runicrealms.trove"
version = "0.0.1-SNAPSHOT"
archivesName = "trove-client"

java {
    withJavadocJar()
    withSourcesJar()
}

repositories {
    mavenCentral()
}

dependencies {
    // Spring + Kotlin basics
    implementation(platform("org.springframework.boot:spring-boot-dependencies:3.4.4"))
    implementation("org.springframework.boot:spring-boot-starter")

    // gRPC + protobuf
    implementation("io.grpc:grpc-kotlin-stub:1.4.1")
    implementation("io.grpc:grpc-netty:1.71.0")
    implementation("com.google.protobuf:protobuf-java:4.30.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    implementation("io.grpc:grpc-protobuf:1.71.0")

    // For logging, config, etc.
    implementation("org.jetbrains.kotlin:kotlin-stdlib")

    // Test
    testImplementation(kotlin("test"))
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

sourceSets {
    main {
        proto {
            // Does not include proto schemas
            srcDir("../api/trove")
        }
    }
}

tasks.named("compileJava") {
    dependsOn("generateProto")
}

tasks.named("bootJar") {
    enabled = false
}
tasks.named("jar") {
    enabled = true
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            groupId = project.group.toString()
            artifactId = project.archivesName.get()
            version = project.version.toString()
        }
    }
    repositories {
        maven {
            name = "nexus"
            url = uri("https://nexus.runicrealms.com/repository/maven-releases/")
        }
    }
}