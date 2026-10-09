import com.google.protobuf.gradle.id
import com.google.protobuf.gradle.proto

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.protobuf")
    id("org.jetbrains.kotlin.plugin.compose")
}

val grpcVersion = "1.68.1"
val grpcKotlinVersion = "1.4.1"
val protobufVersion = "3.25.5"


android {
    namespace = "com.will.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.will.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    // The release key lives outside the repository: WILL_STORE_FILE, WILL_STORE_PASSWORD,
    // WILL_KEY_ALIAS, WILL_KEY_PASSWORD in ~/.gradle/gradle.properties. Without them the
    // release is built unsigned. Keep the key: an update signed by another one cannot
    // replace the app, and removing the app loses its device token.
    val storeFile = project.findProperty("WILL_STORE_FILE")?.toString()
    signingConfigs {
        if (storeFile != null) {
            create("release") {
                this.storeFile = file(storeFile)
                storePassword = project.findProperty("WILL_STORE_PASSWORD")?.toString()
                keyAlias = project.findProperty("WILL_KEY_ALIAS")?.toString()
                keyPassword = project.findProperty("WILL_KEY_PASSWORD")?.toString()
            }
        }
    }

    buildTypes {
        debug {
            // The emulator reaches the host machine's local server as 10.0.2.2.
            buildConfigField("String", "WILL_HOST", "\"10.0.2.2\"")
            // `-PwillPort=…` points a debug build at another local server.
            buildConfigField("int", "WILL_PORT", (project.findProperty("willPort") ?: "7770").toString())
        }
        release {
            buildConfigField("String", "WILL_HOST", "\"83.217.202.145\"")
            buildConfigField("int", "WILL_PORT", "7770")
            signingConfigs.findByName("release")?.let { signingConfig = it }
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

    // The wire protocol lives with the server, in the sibling will repository.
    sourceSets {
        named("main") {
            proto {
                srcDir("../../will/src/infra/transport")
            }
        }
    }
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:$protobufVersion"
    }
    plugins {
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:$grpcVersion"
        }
        id("grpckt") {
            artifact = "io.grpc:protoc-gen-grpc-kotlin:$grpcKotlinVersion:jdk8@jar"
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                id("java") { option("lite") }
                id("kotlin") { option("lite") }
            }
            task.plugins {
                id("grpc") { option("lite") }
                id("grpckt") { option("lite") }
            }
        }
    }
}

dependencies {
    // Jetpack Compose for the interface.
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    // Системная заставка с анимированным значком; до Android 12 — неподвижным.
    implementation("androidx.core:core-splashscreen:1.0.1")

    // gRPC over OkHttp with lite protobuf, Kotlin stubs on coroutines.
    implementation("io.grpc:grpc-okhttp:$grpcVersion")
    implementation("io.grpc:grpc-protobuf-lite:$grpcVersion")
    implementation("io.grpc:grpc-stub:$grpcVersion")
    implementation("io.grpc:grpc-kotlin-stub:$grpcKotlinVersion")
    implementation("com.google.protobuf:protobuf-kotlin-lite:$protobufVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    testImplementation("junit:junit:4.13.2")
    compileOnly("org.apache.tomcat:annotations-api:6.0.53")
}
