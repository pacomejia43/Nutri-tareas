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
        // Every release build (local or CI) signs with the same key so that installing a newer
        // APK over an older one works as an update instead of failing with a signature mismatch.
        // This repo ships a committed, non-secret convenience keystore for that purpose (see
        // release-signing/README.md); set the RELEASE_KEYSTORE_* env vars to override it with a
        // real one without changing this file. The keystore itself is binary, so it's committed
        // as a base64 text file and decoded here on first use (git diffs/hosts text more safely).
        create("release") {
            // GitHub Actions substitutes an unset secret as an empty string, not null - so an
            // env-var lookup must treat blank the same as absent, not just null, or an unset
            // secret would silently override every one of these with "".
            fun envOrDefault(name: String, default: String): String =
                System.getenv(name)?.takeIf { it.isNotBlank() } ?: default

            val explicitPath = System.getenv("RELEASE_KEYSTORE_PATH")?.takeIf { it.isNotBlank() }
            val keystoreFile = if (explicitPath != null) {
                file(explicitPath)
            } else {
                val decoded = file("${rootProject.projectDir}/release-signing/nutri-tareas-release.keystore")
                if (!decoded.exists()) {
                    val encoded = file("${rootProject.projectDir}/release-signing/nutri-tareas-release.keystore.base64")
                    if (encoded.exists()) {
                        decoded.writeBytes(java.util.Base64.getDecoder().decode(encoded.readText().trim()))
                    }
                }
                decoded
            }
            storeFile = keystoreFile
            storePassword = envOrDefault("RELEASE_KEYSTORE_PASSWORD", "nutritareas-update-key")
            keyAlias = envOrDefault("RELEASE_KEY_ALIAS", "nutritareas")
            keyPassword = envOrDefault("RELEASE_KEY_PASSWORD", "nutritareas-update-key")
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

    kotlinOptions {
        jvmTarget = "17"
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
