plugins { id("com.android.application"); kotlin("android"); kotlin("plugin.serialization"); id("org.jetbrains.kotlin.plugin.compose"); id("com.google.devtools.ksp"); id("com.google.dagger.hilt.android") }
android {
 namespace = "dev.origin.ote"; compileSdk = 36
 defaultConfig { applicationId = "dev.origin.ote"; minSdk = 33; targetSdk = 36; versionCode = 1; versionName = "0.1.0-mvp"; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 buildFeatures { compose = true; buildConfig = true }
 buildTypes { release { isMinifyEnabled = true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro") } }
 // Release uses the developer's keystore, never a hardcoded/shared signing key.
 val keyFile = providers.environmentVariable("OTE_KEYSTORE").orNull
 if (keyFile != null) { signingConfigs { create("production") { storeFile=file(keyFile); storePassword=System.getenv("OTE_STORE_PASSWORD"); keyAlias=System.getenv("OTE_KEY_ALIAS"); keyPassword=System.getenv("OTE_KEY_PASSWORD") } }; buildTypes.getByName("release").signingConfig=signingConfigs.getByName("production") }
 packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
kotlin { jvmToolchain(17) }
dependencies {
 implementation(project(":core:model")); implementation(project(":core:root")); implementation(project(":engine:pack")); implementation(project(":engine:device")); implementation(project(":data"))
 implementation(platform("androidx.compose:compose-bom:2025.05.01"))
 implementation("androidx.compose.animation:animation"); implementation("androidx.compose.ui:ui"); implementation("androidx.compose.ui:ui-tooling-preview"); implementation("androidx.compose.foundation:foundation"); implementation("androidx.compose.material3:material3"); implementation("androidx.compose.material:material-icons-extended")
 implementation("androidx.activity:activity-compose:1.10.1"); implementation("androidx.core:core-ktx:1.16.0")
 implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0"); implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0"); implementation("androidx.navigation:navigation-compose:2.9.0"); implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
 implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1"); implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2"); implementation("com.google.dagger:hilt-android:2.56.2"); ksp("com.google.dagger:hilt-compiler:2.56.2")
 debugImplementation("androidx.compose.ui:ui-tooling"); testImplementation(kotlin("test-junit"))
 androidTestImplementation(platform("androidx.compose:compose-bom:2025.05.01")); androidTestImplementation("androidx.compose.ui:ui-test-junit4"); androidTestImplementation("androidx.test.ext:junit:1.2.1"); debugImplementation("androidx.compose.ui:ui-test-manifest")
}
