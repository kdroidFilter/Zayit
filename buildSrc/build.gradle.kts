plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    // Bytecode patches of Compose (ComposePatches)
    implementation(libs.asm)
}
