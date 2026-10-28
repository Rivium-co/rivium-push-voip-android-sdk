plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.vanniktech.maven.publish") version "0.30.0"
}

// SDK Version
val sdkVersion = "0.1.1"

android {
    namespace = "co.rivium.push.voip"
    compileSdk = 34

    defaultConfig {
        minSdk = 21
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    }
}

mavenPublishing {
    publishToMavenCentral(
        com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL,
        automaticRelease = true
    )
    signAllPublications()

    coordinates("co.rivium", "rivium-push-voip", sdkVersion)

    pom {
        name.set("Rivium Push VoIP SDK")
        description.set("VoIP call handling SDK for Android with incoming call UI - part of Rivium Push")
        inceptionYear.set("2026")
        url.set("https://rivium.co")

        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
                distribution.set("repo")
            }
        }

        developers {
            developer {
                id.set("rivium")
                name.set("Rivium")
                email.set("founder@rivium.co")
                url.set("https://rivium.co")
            }
        }

        scm {
            url.set("https://github.com/Rivium-co/rivium-push-voip-android-sdk")
            connection.set("scm:git:git://github.com/Rivium-co/rivium-push-voip-android-sdk.git")
            developerConnection.set("scm:git:ssh://git@github.com/Rivium-co/rivium-push-voip-android-sdk.git")
        }
    }
}

dependencies {
    // AndroidX
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")

    // JSON parsing
    implementation("com.google.code.gson:gson:2.10.1")

    // Image loading for caller avatar
    implementation("io.coil-kt:coil:2.5.0")

    // Coroutines for async image loading
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
