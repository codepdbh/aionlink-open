plugins { kotlin("jvm") }
kotlin { jvmToolchain(21) }
dependencies { implementation(project(":protocol-api")) }
