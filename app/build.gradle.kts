import org.gradle.api.Project
import org.gradle.kotlin.dsl.extra
import java.util.Properties
import java.io.FileInputStream
import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Load keystore.properties file if it exists
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

// Load secrets.properties file if it exists
val secretsPropertiesFile = rootProject.file("secrets.properties")
val secretsProperties = Properties()
if (secretsPropertiesFile.exists()) {
    secretsProperties.load(FileInputStream(secretsPropertiesFile))
}

fun getSecret(key: String): String {
    return secretsProperties.getProperty(key, "")
}

android {
    namespace = "com.flysafeweather.app"
    compileSdk = 34

    buildFeatures {
        compose = true
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.flysafeweather.app"
        minSdk = 29
        targetSdk = 34
        versionCode = 30
        versionName = "26.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // Add secure API key configuration
        buildConfigField("String", "MAPS_API_KEY", "\"${getSecret("MAPS_API_KEY")}\"")
        buildConfigField("String", "FAA_CLIENT_ID", "\"${getSecret("FAA_CLIENT_ID")}\"")
        buildConfigField("String", "FAA_CLIENT_SECRET", "\"${getSecret("FAA_CLIENT_SECRET")}\"")
        
        // Add resValue for Maps API key
        resValue("string", "maps_api_key", getSecret("MAPS_API_KEY"))
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            
            // Add additional security for release builds
            buildConfigField("String", "MAPS_API_KEY", "\"${getSecret("MAPS_API_KEY")}\"")
            buildConfigField("String", "FAA_CLIENT_ID", "\"${getSecret("FAA_CLIENT_ID")}\"")
            buildConfigField("String", "FAA_CLIENT_SECRET", "\"${getSecret("FAA_CLIENT_SECRET")}\"")
            resValue("string", "maps_api_key", getSecret("MAPS_API_KEY"))
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
            
            // Use development keys for debug builds if needed
            val debugMapsKey = getSecret("MAPS_API_KEY_DEBUG").ifEmpty { getSecret("MAPS_API_KEY") }
            val debugFaaId = getSecret("FAA_CLIENT_ID_DEBUG").ifEmpty { getSecret("FAA_CLIENT_ID") }
            val debugFaaSecret = getSecret("FAA_CLIENT_SECRET_DEBUG").ifEmpty { getSecret("FAA_CLIENT_SECRET") }
            
            buildConfigField("String", "MAPS_API_KEY", "\"$debugMapsKey\"")
            buildConfigField("String", "FAA_CLIENT_ID", "\"$debugFaaId\"")
            buildConfigField("String", "FAA_CLIENT_SECRET", "\"$debugFaaSecret\"")
            resValue("string", "maps_api_key", debugMapsKey)
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    val composeBomVersion = "2024.04.01"
    val material3Version = "1.1.2"
    
    implementation(platform("androidx.compose:compose-bom:$composeBomVersion"))
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3:$material3Version")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("com.google.android.gms:play-services-location:21.1.0")
    implementation("com.google.maps.android:maps-compose:4.3.0")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.retrofit2:converter-scalars:2.9.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Test dependencies
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.7.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:$composeBomVersion"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test:rules:1.5.0")

}

tasks.withType<Test> {
    useJUnit()
    
    // Add JVM arguments for ByteBuddy agent warnings
    jvmArgs(
        "-XX:+EnableDynamicAgentLoading",
        "-Djdk.instrument.traceUsage"
    )
    
    testLogging {
        events("passed", "skipped", "failed")
    }
}

// Ensure test tasks are properly configured
tasks.register("testClasses") {
    dependsOn("compileDebugUnitTestKotlin")
    description = "Assembles test classes"
} 