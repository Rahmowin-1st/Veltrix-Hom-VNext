plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
}

kotlin { jvmToolchain(17) }
application { mainClass.set("com.veltrix.hom.vnext.server.MainKt") }

dependencies {
    implementation(project(":core"))
    implementation("io.ktor:ktor-server-core:3.5.1")
    implementation("io.ktor:ktor-server-netty:3.5.1")
    implementation("io.ktor:ktor-server-content-negotiation:3.5.1")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.5.1")
    implementation("io.ktor:ktor-server-status-pages:3.5.1")
    implementation("io.ktor:ktor-server-call-id:3.5.1")
    implementation("io.ktor:ktor-server-rate-limit:3.5.1")
    implementation("io.ktor:ktor-server-auth:3.5.1")
    implementation("io.ktor:ktor-server-call-logging:3.5.1")
    implementation("io.ktor:ktor-server-cors:3.5.1")
    implementation("io.ktor:ktor-server-sse:3.5.1")
    implementation("org.postgresql:postgresql:42.7.7")
    implementation("com.zaxxer:HikariCP:7.0.2")
    implementation("org.flywaydb:flyway-core:11.13.0")
    implementation("org.flywaydb:flyway-database-postgresql:11.13.0")
    implementation("org.apache.tika:tika-core:3.3.2")
    implementation("org.apache.tika:tika-parsers-standard-package:3.3.2")
    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-test-host:3.5.1")
}

tasks.test { useJUnitPlatform() }

sourceSets {
    main { resources.srcDir(rootProject.file("database")) }
}
