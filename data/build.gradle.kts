plugins { id("com.android.library"); kotlin("android"); kotlin("plugin.serialization"); id("com.google.devtools.ksp"); id("com.google.dagger.hilt.android") }
android { namespace = "dev.origin.ote.data"; compileSdk = 36
 defaultConfig { minSdk = 33; consumerProguardFiles("consumer-rules.pro") }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
kotlin { jvmToolchain(17) }
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
dependencies {
 api(project(":core:model")); implementation(project(":engine:pack"))
 implementation("androidx.room:room-runtime:2.7.1"); implementation("androidx.room:room-ktx:2.7.1"); ksp("androidx.room:room-compiler:2.7.1")
 implementation("androidx.datastore:datastore-preferences:1.1.7")
 implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
 implementation("com.google.dagger:hilt-android:2.56.2"); ksp("com.google.dagger:hilt-compiler:2.56.2")
 implementation("com.github.bumptech.glide:gifdecoder:4.16.0")
 implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
 testImplementation("junit:junit:4.13.2")
}
