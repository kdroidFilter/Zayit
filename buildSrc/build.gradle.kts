plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    // Bytecode patch of Compose's SelectionManager (SelectionPinPatch)
    implementation(libs.asm)
}
