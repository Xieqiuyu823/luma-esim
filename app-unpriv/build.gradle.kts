import im.angry.openeuicc.build.*
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

signingKeystoreProperties {
    keyAliasField = "unprivKeyAlias"
    keyPasswordField = "unprivKeyPassword"
}

apply {
    plugin<MyVersioningPlugin>()
    plugin<MySigningPlugin>()
}

android {
    namespace = "im.angry.easyeuicc"
    compileSdk = 37
    ndkVersion = "26.1.10909125"

    defaultConfig {
        applicationId = "io.luma.euicc.easy"
        versionCode = 4
        versionName = "0.1.3"
        minSdk = 28
        targetSdk = 37

    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        defaultConfig {
            versionNameSuffix = "-unpriv"
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

dependencies {
    implementation(project(":app-common"))
    testImplementation("junit:junit:4.13.2")
}
