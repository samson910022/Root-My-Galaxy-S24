plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.samson.s24root"
    compileSdk = 37
    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "dev.samson.s24root"
        minSdk = 33
        targetSdk = 36
        versionCode = 18
        versionName = "0.3.4-s24.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Self-update is intentionally off. The updater downloads a release APK,
        // but the app does not declare REQUEST_INSTALL_PACKAGES, so the system
        // package installer aborts the hand-off (targetSdk 36 >= 26). It also
        // does not verify the downloaded APK against a published digest, nor
        // whether its signer matches the installed app's signer, and the flow
        // has not been validated end to end. Re-enabling the flag alone is not
        // enough: add the permission, obtain the user's "install unknown apps"
        // grant, and complete a verified update flow first.
        buildConfigField("boolean", "SELF_UPDATE_ENABLED", "false")

        ndk {
            abiFilters += "arm64-v8a"
        }

        externalNativeBuild {
            cmake {
                arguments += "-DANDROID_STL=none"
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    packaging {
        // Required: libcve43499root.so is a PIE executable, not a shared library.
        // It must be extracted to nativeLibraryDir and marked executable by
        // PackageManager so the app can run it with ProcessBuilder.
        //
        // This also fixes the packaging mode to COMPRESSED, which is why
        // app/src/main/assets/**/*.so is deflated even though AGP would
        // otherwise exempt .so from compression. Payload assets are staged
        // out of the APK with AssetManager.open(), which serves stored and
        // deflated entries alike, so their compression state is irrelevant
        // and must not be relied on. Do not add noCompress for them.
        jniLibs.useLegacyPackaging = true
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
        )
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.05.01"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3:1.5.0-alpha24")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("com.materialkolor:material-kolor:4.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")

    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    // AGP's unit-test android.jar only has org.json stubs that throw; the real
    // implementation is required to exercise the support-manifest parser.
    testImplementation("org.json:json:20250517")
    androidTestImplementation("androidx.test:core-ktx:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
}
