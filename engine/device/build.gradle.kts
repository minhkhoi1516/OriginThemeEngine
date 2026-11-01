plugins { id("com.android.library"); kotlin("android"); kotlin("plugin.serialization"); id("com.google.devtools.ksp"); id("com.google.dagger.hilt.android") }
android { namespace = "dev.origin.ote.device"; compileSdk = 36
 defaultConfig { minSdk = 33; consumerProguardFiles("consumer-rules.pro") }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 testOptions { unitTests.isReturnDefaultValues = true }
}
kotlin { jvmToolchain(17) }
dependencies { api(project(":core:model")); api(project(":core:root")); implementation(project(":engine:pack")); implementation(project(":data")); implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2"); implementation("com.google.dagger:hilt-android:2.56.2"); ksp("com.google.dagger:hilt-compiler:2.56.2"); testImplementation(kotlin("test-junit")) }
