plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// L'appli web (index.html à la racine du dépôt) est embarquée telle quelle.
// Sa version (APP_VERSION) est aussi celle de l'APK.
val webRoot: File = rootDir.parentFile
val webVersion: String = Regex("APP_VERSION\\s*=\\s*'([0-9]+\\.[0-9]+)'")
    .find(webRoot.resolve("index.html").readText())!!.groupValues[1]
val webCode: Int = webVersion.split(".").let { it[0].toInt() * 1000 + it[1].toInt() }

val webOut = layout.buildDirectory.dir("web")
val copyWeb by tasks.registering(Copy::class) {
    from(webRoot) { include("index.html", "chart.umd.min.js") }
    into(webOut.map { it.dir("www") })
}

android {
    namespace = "com.koala.kbg"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.koala.kbg"
        minSdk = 26
        targetSdk = 35
        versionCode = webCode
        versionName = webVersion
    }

    signingConfigs {
        create("release") {
            storeFile = file("kbg.jks")
            storePassword = "koalabudget"
            keyAlias = "kbg"
            keyPassword = "koalabudget"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    sourceSets["main"].assets.srcDir(webOut)

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures { buildConfig = true }
}

tasks.named("preBuild") { dependsOn(copyWeb) }

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.webkit:webkit:1.12.1")
    testImplementation("junit:junit:4.13.2")
}
