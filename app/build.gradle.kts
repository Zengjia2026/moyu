plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.zengjia.moyu"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.zengjia.moyu"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
    }

    packaging {
        jniLibs.useLegacyPackaging = false
    }
}

val mihomoVersion = "0.3.7"
val mihomoAar = layout.buildDirectory.file("libs/libmihomo-android-v$mihomoVersion.aar")

val downloadMihomo by tasks.registering {
    inputs.property("mihomoVersion", mihomoVersion)
    outputs.file(mihomoAar)
    doLast {
        val target = mihomoAar.get().asFile
        target.parentFile.mkdirs()
        val url = "https://github.com/oviron/libmihomo-android/releases/download/v$mihomoVersion/libmihomo-android-v$mihomoVersion.aar"
        uri(url).toURL().openStream().use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
    }
}

dependencies {
    implementation(files(mihomoAar).builtBy(downloadMihomo))
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.12.0")
}
