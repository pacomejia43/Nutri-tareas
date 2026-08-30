import kotlin.random.Random
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.nutritareas.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.nutritareas.app"
        minSdk = 26
        targetSdk = 36
        // The release workflow overrides both from the pushed git tag (e.g. v1.2.3) so the
        // APK's own version matches what UpdateChecker compares it against; see
        // .github/workflows/android-release.yml.
        versionCode = (project.findProperty("versionCodeOverride") as String?)?.toIntOrNull() ?: 1
        versionName = (project.findProperty("versionNameOverride") as String?) ?: "1.0.0"

        // Public repo consumed by the in-app update checker (see data/update).
        buildConfigField("String", "UPDATE_REPO_OWNER", "\"pacomejia43\"")
        buildConfigField("String", "UPDATE_REPO_NAME", "\"Nutri-tareas\"")
    }

    signingConfigs {
        // Every release build signs with the same key so that installing a newer APK over an
        // older one works as an update instead of failing with a signature mismatch. The real key
        // lives only as RELEASE_KEYSTORE_* GitHub Actions secrets (see release-signing/README.md)
        // - nothing secret is committed to this repo. Without those secrets (i.e. any local build),
        // this falls back to a throwaway keystore generated once per checkout, gitignored, so
        // `assembleRelease` still works for local testing; its signature won't match an official
        // release, which only matters if you're trying to install a local build over one from CI.
        create("release") {
            val explicitPath = System.getenv("RELEASE_KEYSTORE_PATH")?.takeIf { it.isNotBlank() }
            if (explicitPath != null) {
                storeFile = file(explicitPath)
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            } else {
                val localDir = file("${rootProject.projectDir}/release-signing/local")
                val localKeystore = file("$localDir/local-release.keystore")
                val localPasswordFile = file("$localDir/local-release.password")
                if (!localKeystore.exists()) {
                    localDir.mkdirs()
                    val password = Random.nextBytes(24).joinToString("") { "%02x".format(it) }
                    localPasswordFile.writeText(password)
                    val javaHome = System.getProperty("java.home")
                    val keytoolExe = if (org.gradle.internal.os.OperatingSystem.current().isWindows) "keytool.exe" else "keytool"
                    val keytool = file("$javaHome/bin/$keytoolExe").takeIf { it.exists() }?.absolutePath ?: "keytool"
                    val exitCode = ProcessBuilder(
                        keytool, "-genkeypair",
                        "-keystore", localKeystore.absolutePath,
                        "-alias", "local",
                        "-keyalg", "RSA", "-keysize", "2048", "-validity", "10000",
                        "-storepass", password, "-keypass", password,
                        "-dname", "CN=Local Dev, OU=Nutri-Tareas, O=Nutri-Tareas, L=NA, S=NA, C=MX",
                    ).redirectErrorStream(true).start().waitFor()
                    check(exitCode == 0) { "keytool failed generating the local dev keystore (exit $exitCode)." }
                }
                storeFile = localKeystore
                storePassword = localPasswordFile.readText().trim()
                keyAlias = "local"
                keyPassword = localPasswordFile.readText().trim()
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            // Not distributed through Play Store; keeping R8 off avoids shrinking/obfuscation
            // surprises in a release build that can't be verified against a device locally.
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/license.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/notice.txt",
                "META-INF/INDEX.LIST",
                "META-INF/*.kotlin_module",
            )
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.pdfbox.android)
    implementation(libs.anthropic.java)

    testImplementation(libs.junit)
}
