plugins {
    kotlin("jvm")
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.kotlinpoet)
    implementation(libs.jdbc.driver)
    implementation(libs.sqlite.driver)
    implementation("io.github.kdroidfilter.seforimlibrary:dao")
    implementation(libs.kotlinx.coroutines.core)
}

application {
    mainClass.set("io.github.kdroidfilter.seforimapp.cataloggen.GenerateKt")
}

val defaultDbPath =
    rootProject.layout.projectDirectory
        .dir("SeforimLibrary/generator/build")
        .file("seforim.db")
        .asFile
        .absolutePath
val defaultOutputDir =
    rootProject.layout.projectDirectory
        .dir("SeforimApp/src/commonMain/kotlin")
        .asFile
        .absolutePath

tasks.register<JavaExec>("generatePrecomputedCatalog") {
    group = "codegen"
    description = "Generates PrecomputedCatalog.kt from the seforim.db database"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("io.github.kdroidfilter.seforimapp.cataloggen.GenerateKt")
    val dbPathProvider =
        providers
            .gradleProperty("catalogDb")
            .orElse(providers.environmentVariable("SEFORIM_DB"))
            .orElse(defaultDbPath)
    val outputDirProvider =
        providers
            .gradleProperty("catalogOutputDir")
            .orElse(defaultOutputDir)

    doFirst {
        if (args.isEmpty()) {
            val dbPath = dbPathProvider.get()
            val outDir = outputDirProvider.get()
            args(dbPath, outDir)
            println("generatePrecomputedCatalog using dbPath=$dbPath")
            println("generatePrecomputedCatalog outputDir=$outDir")
        } else {
            println("generatePrecomputedCatalog using custom args=$args")
        }
    }
}
