plugins { `java-library`; kotlin("jvm") }
kotlin { jvmToolchain(17) }
dependencies { api(project(":core:model")); api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2"); testImplementation(kotlin("test-junit")); testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2") }
