plugins {
    alias(libs.plugins.kotlin.jvm)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.koog.agents)
    implementation(libs.koog.a2a.core)
    implementation(libs.koog.a2a.client)
    implementation(libs.koog.a2a.transport.client)

    implementation(libs.logback.classic)
}

kotlin {
    jvmToolchain(25)
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
