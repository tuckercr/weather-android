import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.devtoolsKsp)
    alias(libs.plugins.daggerHiltAndroid)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ktlint)
}

// Apply Firebase plugins only when google-services.json is present.
// Without it the build succeeds and Firebase gracefully becomes a no-op at runtime.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
}

val localProps = Properties().also { props ->
    rootProject
        .file("local.properties")
        .takeIf { it.exists() }
        ?.inputStream()
        ?.use { props.load(it) }
}
val owmApiKey: String = localProps.getProperty("OWM_API_KEY") ?: ""

// What a release APK or bundle would be missing. Without the key the app shows no weather, and
// without the keystore it's unsigned, which Play won't take, so packaging a release stops here
// (before R8) instead of quietly producing one. Debug builds, tests and lint don't need either.
val releaseProblems: List<String> =
    buildList {
        if (owmApiKey.isBlank()) add("OWM_API_KEY is missing, so the app would show no weather")
        val keystorePath = localProps.getProperty("KEYSTORE_PATH")
        when {
            keystorePath.isNullOrBlank() -> add("KEYSTORE_PATH is missing, so the release would be unsigned")
            !file(keystorePath).exists() ->
                add("KEYSTORE_PATH points to a file that doesn't exist ($keystorePath)")
        }
        listOf("KEYSTORE_PASSWORD", "KEY_ALIAS", "KEY_PASSWORD")
            .filter { localProps.getProperty(it).isNullOrBlank() }
            .forEach { add("$it is missing") }
    }

gradle.taskGraph.whenReady {
    val packagesRelease = allTasks.any { it.project == project && it.name in setOf("packageRelease", "packageReleaseBundle") }
    if (packagesRelease && releaseProblems.isNotEmpty()) {
        throw GradleException(
            "Can't build a release, local.properties isn't complete:\n" +
                releaseProblems.joinToString("\n") { "  - $it" } +
                "\nSee the README's Release builds section.",
        )
    }
}

android {
    namespace = "com.fangjet.weather"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.fangjet.weather"
        minSdk = 29
        targetSdk = 37
        // Build N is version 1.0.N: bump this for every Play upload.
        val build = 4
        versionCode = build
        versionName = "1.0.$build"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "OWM_API_KEY", "\"${owmApiKey.replace("\"", "\\\"")}\"")
    }

    signingConfigs {
        create("release") {
            val path = localProps["KEYSTORE_PATH"] as String?
            if (path != null) {
                storeFile = file(path)
                storePassword = localProps["KEYSTORE_PASSWORD"] as String? ?: ""
                keyAlias = localProps["KEY_ALIAS"] as String? ?: ""
                keyPassword = localProps["KEY_PASSWORD"] as String? ?: ""
            }
        }
    }
    buildTypes {
        debug {
            // Installs side by side with the Play build and reports to its own Firebase app
            // (com.fangjet.weather.debug is registered in google-services.json).
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            // R8 shrinks, optimizes, and obfuscates release builds; resource shrinking drops
            // unused resources. The libraries in use ship their own keep rules.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            val releaseConfig = signingConfigs.getByName("release")
            if (releaseConfig.storeFile != null) signingConfig = releaseConfig
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
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
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/*.kotlin_module",
                "META-INF/INDEX.LIST",
                "META-INF/io.netty.versions.properties",
            )
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    // Android X
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    // Use modern datastore rather than shared prefs
    implementation(libs.androidx.datastore.preferences)

    // Location services
    implementation(libs.play.services.location)

    // Logging
    implementation(libs.timber)

    // Weather API module
    implementation(project(":weather-api"))

    // Networking (image loading only — HTTP stack lives in :weather-api)
    implementation(libs.coil.compose)

    // Serialization — used by PreferencesRepository to JSON-encode SavedLocation list
    implementation(libs.kotlinx.serialization.json)

    // Background updates
    implementation(libs.androidx.work.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.hilt.ext.compiler)

    // Firebase (project cats-dogs-weather) — optional. With app/google-services.json present the
    // plugins above are applied; without it the app falls back to the local defaults in
    // res/xml/remote_config_defaults.xml and analytics/crash reporting become no-ops.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.config)
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)

    // DI
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
