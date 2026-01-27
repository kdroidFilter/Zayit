package io.github.kdroidfilter.buildsrc

import org.gradle.api.Project
import org.gradle.api.artifacts.type.ArtifactTypeDefinition

object NativeCleanupTransformHelper {
    private val hostOs: String = System.getProperty("os.name") ?: error("Unable to detect OS")
    private val hostArch: String = System.getProperty("os.arch") ?: error("Unable to detect architecture")

    // Convention JNA (com/sun/jna/os-arch)
    private const val JNA_PREFIX = "com/sun/jna"

    private val jnaFolderToKeep: String = when {
        hostOs == "Mac OS X" && hostArch == "aarch64" -> "$JNA_PREFIX/darwin-aarch64"
        hostOs == "Mac OS X" -> "$JNA_PREFIX/darwin-x86-64"
        hostOs.startsWith("Windows") && hostArch == "amd64" -> "$JNA_PREFIX/win32-x86-64"
        hostOs.startsWith("Windows") && hostArch == "aarch64" -> "$JNA_PREFIX/win32-aarch64"
        hostOs.startsWith("Windows") -> "$JNA_PREFIX/win32-x86"
        hostOs == "Linux" && hostArch == "aarch64" -> "$JNA_PREFIX/linux-aarch64"
        hostOs == "Linux" -> "$JNA_PREFIX/linux-x86-64"
        else -> error("Unsupported platform: $hostOs / $hostArch")
    }

    private val allJnaFolders: Set<String> = setOf(
        "$JNA_PREFIX/aix-ppc", "$JNA_PREFIX/aix-ppc64",
        "$JNA_PREFIX/darwin-aarch64", "$JNA_PREFIX/darwin-x86-64",
        "$JNA_PREFIX/dragonflybsd-x86-64",
        "$JNA_PREFIX/freebsd-aarch64", "$JNA_PREFIX/freebsd-x86", "$JNA_PREFIX/freebsd-x86-64",
        "$JNA_PREFIX/linux-aarch64", "$JNA_PREFIX/linux-arm", "$JNA_PREFIX/linux-armel",
        "$JNA_PREFIX/linux-loongarch64", "$JNA_PREFIX/linux-mips64el",
        "$JNA_PREFIX/linux-ppc", "$JNA_PREFIX/linux-ppc64le",
        "$JNA_PREFIX/linux-riscv64", "$JNA_PREFIX/linux-s390x",
        "$JNA_PREFIX/linux-x86", "$JNA_PREFIX/linux-x86-64",
        "$JNA_PREFIX/openbsd-x86", "$JNA_PREFIX/openbsd-x86-64",
        "$JNA_PREFIX/sunos-sparc", "$JNA_PREFIX/sunos-sparcv9",
        "$JNA_PREFIX/sunos-x86", "$JNA_PREFIX/sunos-x86-64",
        "$JNA_PREFIX/win32-aarch64", "$JNA_PREFIX/win32-x86", "$JNA_PREFIX/win32-x86-64"
    )

    // Convention zstd-jni (hierarchical: os/arch)
    private val zstdJniFolderToKeep: String = when {
        hostOs == "Mac OS X" && hostArch == "aarch64" -> "darwin/aarch64"
        hostOs == "Mac OS X" -> "darwin/x86_64"
        hostOs.startsWith("Windows") && hostArch == "amd64" -> "win/amd64"
        hostOs.startsWith("Windows") && hostArch == "aarch64" -> "win/aarch64"
        hostOs.startsWith("Windows") -> "win/x86"
        hostOs == "Linux" && hostArch == "aarch64" -> "linux/aarch64"
        hostOs == "Linux" -> "linux/amd64"
        else -> error("Unsupported platform: $hostOs / $hostArch")
    }

    private val allZstdJniFolders: Set<String> = setOf(
        "aix/ppc64",
        "darwin/aarch64", "darwin/x86_64",
        "freebsd/amd64", "freebsd/i386",
        "linux/aarch64", "linux/amd64", "linux/arm", "linux/i386",
        "linux/loongarch64", "linux/mips64", "linux/ppc64", "linux/ppc64le",
        "linux/riscv64", "linux/s390x",
        "win/aarch64", "win/amd64", "win/x86"
    )

    // Convention sqlite-jdbc (org/sqlite/native/OS/arch)
    private const val SQLITE_PREFIX = "org/sqlite/native"

    private val sqliteJdbcFolderToKeep: String = when {
        hostOs == "Mac OS X" && hostArch == "aarch64" -> "$SQLITE_PREFIX/Mac/aarch64"
        hostOs == "Mac OS X" -> "$SQLITE_PREFIX/Mac/x86_64"
        hostOs.startsWith("Windows") && hostArch == "amd64" -> "$SQLITE_PREFIX/Windows/x86_64"
        hostOs.startsWith("Windows") && hostArch == "aarch64" -> "$SQLITE_PREFIX/Windows/aarch64"
        hostOs.startsWith("Windows") -> "$SQLITE_PREFIX/Windows/x86"
        hostOs == "Linux" && hostArch == "aarch64" -> "$SQLITE_PREFIX/Linux/aarch64"
        hostOs == "Linux" -> "$SQLITE_PREFIX/Linux/x86_64"
        else -> error("Unsupported platform: $hostOs / $hostArch")
    }

    private val allSqliteJdbcFolders: Set<String> = setOf(
        "$SQLITE_PREFIX/FreeBSD/aarch64", "$SQLITE_PREFIX/FreeBSD/x86", "$SQLITE_PREFIX/FreeBSD/x86_64",
        "$SQLITE_PREFIX/Linux/aarch64", "$SQLITE_PREFIX/Linux/arm", "$SQLITE_PREFIX/Linux/armv6",
        "$SQLITE_PREFIX/Linux/armv7", "$SQLITE_PREFIX/Linux/ppc64", "$SQLITE_PREFIX/Linux/riscv64",
        "$SQLITE_PREFIX/Linux/x86", "$SQLITE_PREFIX/Linux/x86_64",
        "$SQLITE_PREFIX/Linux-Android/aarch64", "$SQLITE_PREFIX/Linux-Android/arm",
        "$SQLITE_PREFIX/Linux-Android/x86", "$SQLITE_PREFIX/Linux-Android/x86_64",
        "$SQLITE_PREFIX/Linux-Musl/aarch64", "$SQLITE_PREFIX/Linux-Musl/x86", "$SQLITE_PREFIX/Linux-Musl/x86_64",
        "$SQLITE_PREFIX/Mac/aarch64", "$SQLITE_PREFIX/Mac/x86_64",
        "$SQLITE_PREFIX/Windows/aarch64", "$SQLITE_PREFIX/Windows/armv7",
        "$SQLITE_PREFIX/Windows/x86", "$SQLITE_PREFIX/Windows/x86_64"
    )

    val foldersToRemove: Set<String> = (allJnaFolders - jnaFolderToKeep) +
        (allZstdJniFolders - zstdJniFolderToKeep) +
        (allSqliteJdbcFolders - sqliteJdbcFolderToKeep)

    fun registerTransform(project: Project) {
        val foldersToRemoveSet = foldersToRemove

        // Register the transform: jar -> cleaned-jar
        project.dependencies.registerTransform(CleanNativesTransform::class.java) {
            from.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "jar")
            to.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "cleaned-jar")
            parameters {
                foldersToRemove.set(foldersToRemoveSet)
            }
        }

        // Request cleaned-jar for all runtime classpaths (existing and future)
        project.configurations.configureEach {
            if (name.contains("RuntimeClasspath", ignoreCase = true)) {
                attributes {
                    attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "cleaned-jar")
                }
            }
        }

        println("Native cleanup transform registered for $hostOs $hostArch")
        println("  Keeping: $jnaFolderToKeep, $zstdJniFolderToKeep, $sqliteJdbcFolderToKeep")
        println("  Removing ${foldersToRemoveSet.size} platform-specific native folders")
    }
}
