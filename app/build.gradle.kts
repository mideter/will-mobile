import com.google.protobuf.gradle.id
import com.google.protobuf.gradle.proto

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.protobuf")
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
    }

    buildTypes {
        debug {
            // The emulator reaches the host machine's local server as 10.0.2.2.
            buildConfigField("String", "WILL_HOST", "\"10.0.2.2\"")
        }
        release {
            buildConfigField("String", "WILL_HOST", "\"83.217.202.145\"")
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
    // No AndroidX deps. gRPC over OkHttp with lite protobuf, Kotlin stubs on coroutines.
    implementation("io.grpc:grpc-okhttp:$grpcVersion")
    implementation("io.grpc:grpc-protobuf-lite:$grpcVersion")
    implementation("io.grpc:grpc-stub:$grpcVersion")
    implementation("io.grpc:grpc-kotlin-stub:$grpcKotlinVersion")
    implementation("com.google.protobuf:protobuf-kotlin-lite:$protobufVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    compileOnly("org.apache.tomcat:annotations-api:6.0.53")
}
