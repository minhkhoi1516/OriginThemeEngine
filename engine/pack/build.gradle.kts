plugins { `java-library`; kotlin("jvm"); kotlin("plugin.serialization") }
kotlin { jvmToolchain(17) }
dependencies { api(project(":core:model")); testImplementation(kotlin("test-junit")); testImplementation("junit:junit:4.13.2") }
