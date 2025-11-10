plugins {
    kotlin("jvm")
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.squareup:kotlinpoet:1.15.3")
    implementation(libs.jdbc.driver)
    implementation(libs.sqlite.driver)
    implementation("io.github.kdroidfilter.seforimlibrary:dao")
    implementation(libs.kotlinx.coroutines.core)
}

application {
    mainClass.set("io.github.kdroidfilter.seforimapp.cataloggen.GenerateKt")
}

val dbPath = rootProject.layout.projectDirectory.dir("SeforimLibrary/generator/build").file("seforim.db").asFile.absolutePath
val outputDir = rootProject.layout.projectDirectory.dir("SeforimApp/src/commonMain/kotlin").asFile

tasks.register<JavaExec>("generatePrecomputedCatalog") {
    group = "codegen"
    description = "Generates PrecomputedCatalog.kt from the seforim.db database"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("io.github.kdroidfilter.seforimapp.cataloggen.GenerateKt")
    args(dbPath, outputDir.absolutePath)
}
