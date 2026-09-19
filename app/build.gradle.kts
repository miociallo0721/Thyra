import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.hilt)
  alias(libs.plugins.ksp)
}

val releaseSigningPropertiesFile = rootProject.file("keystore.properties")
val releaseSigningProperties = Properties().apply {
  if (releaseSigningPropertiesFile.isFile) {
    releaseSigningPropertiesFile.inputStream().use(::load)
  }
}

fun releaseSigningValue(propertyName: String, environmentName: String): String? =
  releaseSigningProperties.getProperty(propertyName)?.takeIf(String::isNotBlank)
    ?: providers.environmentVariable(environmentName).orNull?.takeIf(String::isNotBlank)

val releaseSigningValues = mapOf(
  "storeFile" to releaseSigningValue("storeFile", "THYRA_RELEASE_STORE_FILE"),
  "storePassword" to releaseSigningValue("storePassword", "THYRA_RELEASE_STORE_PASSWORD"),
  "keyAlias" to releaseSigningValue("keyAlias", "THYRA_RELEASE_KEY_ALIAS"),
  "keyPassword" to releaseSigningValue("keyPassword", "THYRA_RELEASE_KEY_PASSWORD"),
)
val hasReleaseSigningValue = releaseSigningValues.values.any { it != null }

android {
    namespace = "dev.thyra.android"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.thyra.android"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "1.0.2"
    }

    signingConfigs {
        if (hasReleaseSigningValue) {
            require(releaseSigningValues.values.all { it != null }) {
                "Set all release signing values in keystore.properties or THYRA_RELEASE_* environment variables"
            }
            create("release") {
                storeFile = rootProject.file(requireNotNull(releaseSigningValues["storeFile"]))
                storePassword = requireNotNull(releaseSigningValues["storePassword"])
                keyAlias = requireNotNull(releaseSigningValues["keyAlias"])
                keyPassword = requireNotNull(releaseSigningValues["keyPassword"])
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = false
      shaders = false
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
  implementation(project(":core:model"))
  implementation(project(":core:network"))
  implementation(project(":core:data"))
  implementation(project(":core:designsystem"))
  implementation(project(":feature:connection"))
  implementation(project(":feature:agents"))
  implementation(project(":feature:sessions"))
  implementation(project(":feature:chat"))

  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.hilt.android)
  ksp(libs.hilt.compiler)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)
}
