import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

/**
 * Supabase credentials, never from source (PLAN 14.10).
 *
 * Four places, highest first: the environment, `local.properties`
 * (git-ignored), the checked-in `cloud.properties`, and then nothing — and
 * **nothing is a supported configuration**, not a failure. When both values are
 * absent the app builds and runs entirely locally and cloud sync reports itself
 * disabled, which is the whole of the offline tier (14.10.3).
 *
 * `cloud.properties` is in the repository because `local.properties` is not: a
 * fresh clone used to have no cloud and no in-repo record of what the endpoint
 * even was. It ships empty on purpose — read its comments before filling it in.
 */
fun properties(name: String) = Properties().apply {
    val file = rootProject.file(name)
    if (file.exists()) file.inputStream().use { load(it) }
}

val localProperties = properties("local.properties")
val cloudDefaults = properties("cloud.properties")

// A blank is an absence at every level, so an exported-but-empty environment
// variable falls through to the file rather than blanking the build.
fun secret(key: String, envKey: String): String =
    listOf(
        System.getenv(envKey),
        localProperties.getProperty(key),
        cloudDefaults.getProperty(key)
    ).firstOrNull { !it.isNullOrBlank() } ?: ""

val supabaseUrl = secret("supabase.url", "SUPABASE_URL")
val supabaseAnonKey = secret("supabase.anonKey", "SUPABASE_ANON_KEY")

/**
 * A value that is **not** a credential, resolved the same three ways.
 *
 * Deliberately a different function from [secret] rather than a third call to
 * it, because `CloudConfigFenceTest` counts `secret()` calls and that count is
 * the fence: `local.properties` also holds an `sbp_` personal access token that
 * can delete every project on the account, and it is one `buildConfigField`
 * away from an APK. Widening the fence to "three secrets now" would give up
 * the property that makes it useful.
 *
 * What goes here instead is public by nature. `pelonot.webUrl` is where the
 * companion web app is served from (PLAN 15.6, 17.14) — it is printed on the
 * bike's own screen as part of a QR code, so it is not merely non-secret, it is
 * *published by the feature that uses it*. It stays configurable rather than
 * hard-coded for the same reason the endpoint does: a self-hoster's is not
 * ours.
 */
fun publicConfig(key: String, envKey: String): String =
    listOf(
        System.getenv(envKey),
        localProperties.getProperty(key),
        cloudDefaults.getProperty(key)
    ).firstOrNull { !it.isNullOrBlank() } ?: ""

val pelonotWebUrl = publicConfig("pelonot.webUrl", "PELONOT_WEB_URL")

android {
    namespace = "com.pelonot"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.pelonot"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
        buildConfigField("String", "PELONOT_WEB_URL", "\"$pelonotWebUrl\"")

        // PLAN 24.3.11. The single-rival ghost, superseded by the live
        // leaderboard and kept rather than deleted: the owner asked for it to
        // be flagged off, not binned, and a comparison that cannot be turned
        // on cannot be judged. Flip to `true` to get the picker back on the
        // class detail screen and the one-number gap card on the ride screen.
        buildConfigField("boolean", "RIVAL_GHOST", "false")

        ksp {
            // Export Room schemas so migrations can be written and verified.
            arg("room.schemaLocation", "$projectDir/schemas")
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-opt-in=kotlin.RequiresOptIn")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            // Pure-logic classes avoid android.util.Log, but Room/Compose types
            // pulled in transitively still need stubbed returns rather than throws.
            isReturnDefaultValues = true
        }
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/INDEX.LIST",
            "/META-INF/DEPENDENCIES"
        )
    }

    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
}

dependencies {
    // Compose
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)

    // AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.graphics.shapes)
    implementation(libs.androidx.exifinterface)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // WorkManager
    implementation(libs.work.runtime.ktx)

    // Kotlin
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // Supabase
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.auth)
    implementation(libs.ktor.client.android)

    // Drawing a QR code for the sign-in hand-off (15.6.6). Pure Java, no
    // Android dependency, and nothing in the app ever scans one.
    implementation(libs.zxing.core)

    // Debug
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    // Unit tests
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    // Instrumented tests
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
