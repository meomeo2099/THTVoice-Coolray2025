import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val releaseStorePath = System.getenv("THTVOICE_KEYSTORE_FILE")?.takeIf { it.isNotBlank() }
val releaseStorePassword = System.getenv("THTVOICE_STORE_PASSWORD")?.takeIf { it.isNotBlank() }
val releaseKeyAlias = System.getenv("THTVOICE_KEY_ALIAS")?.takeIf { it.isNotBlank() }
val releaseKeyPassword = System.getenv("THTVOICE_KEY_PASSWORD")?.takeIf { it.isNotBlank() }
val releaseSigningReady = listOf(
    releaseStorePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }

val licenseServerProps = Properties().apply {
    val file = rootProject.file("config/license-server.properties")
    if (file.isFile) file.inputStream().use { load(it) }
}
val releaseCertSha256 = System.getenv("THTVOICE_RELEASE_CERT_SHA256").orEmpty()
    .lowercase().filter { it in '0'..'9' || it in 'a'..'f' }
    .takeIf { it.length == 64 }.orEmpty()

val licenseServerUrl = (System.getenv("THTVOICE_LICENSE_SERVER_URL")?.takeIf { it.isNotBlank() }
    ?: licenseServerProps.getProperty("serverUrl", "")).trim().trimEnd('/')
val licensePublicKeys = (System.getenv("THTVOICE_LICENSE_PUBLIC_KEYS")?.takeIf { it.isNotBlank() }
    ?: licenseServerProps.getProperty("publicKeys", "")).trim()
require(Regex("^https://[A-Za-z0-9.-]+(:[0-9]+)?$").matches(licenseServerUrl)) {
    "config/license-server.properties: serverUrl must be an https origin, got '$licenseServerUrl'"
}
// Coolray build must always verify signed licenses: an empty key list would disable the check.
require(licensePublicKeys.isNotEmpty()) {
    "config/license-server.properties: publicKeys is empty; the Coolray build must verify signed licenses"
}
require(Regex("^[a-z0-9]{1,8}:[A-Za-z0-9+/=]{40,}(,[a-z0-9]{1,8}:[A-Za-z0-9+/=]{40,})*$").matches(licensePublicKeys)) {
    "config/license-server.properties: publicKeys must look like kid:BASE64[,kid:BASE64]"
}

android {
    namespace = "com.tht.voice"
    compileSdk = 32

    defaultConfig {
        applicationId = "com.tht.voice.coolray2025"
        minSdk = 28
        targetSdk = 32
        versionCode = System.getenv("THTVOICE_VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("THTVOICE_VERSION_NAME")?.takeIf { it.isNotBlank() } ?: "1.0"
        buildConfigField("String", "LICENSE_SERVER_URL", "\"$licenseServerUrl\"")
        buildConfigField("String", "LICENSE_PUBLIC_KEYS", "\"$licensePublicKeys\"")
        // Empty = no runtime signing check (debug / hardware-test builds). Release sets it below.
        buildConfigField("String", "RELEASE_CERT_SHA256", "\"\"")
    }

    if (releaseSigningReady) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseStorePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        getByName("debug") {
            // Owner decision 14/10/2026: hardware-test builds are signed with the official key when the
            // signing env is present (CI), so test and release APKs install over each other.
            // Local builds without the env keep the default debug key.
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
        getByName("release") {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            isMinifyEnabled = false
            // Official certificate SHA-256 for the runtime signing check (not secret: it is in every APK).
            buildConfigField("String", "RELEASE_CERT_SHA256", "\"$releaseCertSha256\"")
        }
    }

    testOptions { unitTests.isReturnDefaultValues = true }
    lint {
        // Sideloaded to car head units, not published on Google Play; targetSdk 32 is deliberate
        // (head-unit behaviour), so Play's minimum-target rule must not block release builds.
        disable += "ExpiredTargetSdkVersion"
    }
    buildFeatures { compose = false; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    useLibrary("android.car")
    kotlinOptions { jvmTarget = "1.8" }
    packaging { jniLibs.useLegacyPackaging = true }
    androidResources { noCompress += listOf("onnx", "model") }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    implementation("androidx.core:core:1.7.0")
    implementation("androidx.media:media:1.6.0")
    implementation("androidx.appcompat:appcompat:1.3.1")
    implementation("com.google.android.material:material:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.4")
    implementation("com.airbnb.android:lottie:6.0.0")
    // Support QR is generated at runtime from the VietQR payload (no binary asset to corrupt).
    implementation("com.google.zxing:core:3.5.3")
    // Reads an update APK's signer when the head unit's archive parser returns none.
    implementation("com.android.tools.build:apksig:8.13.2")

    // Fetched and SHA-256 verified by scripts/fetch-sherpa-android.sh.
    // Binary stays out of normal Git history.
    implementation(files("libs/sherpa-onnx-1.13.8.aar"))
}
