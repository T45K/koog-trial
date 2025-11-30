plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.koog.agents)
    implementation(libs.koog.a2a.core)
    implementation(libs.koog.a2a.server)
    implementation(libs.koog.a2a.transport.server)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)

    implementation(libs.logback.classic)
}

kotlin {
    jvmToolchain(25)
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
