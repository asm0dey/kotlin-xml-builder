plugins {
  kotlin("jvm") version "1.9.0" apply false
  jacoco
}

extra["kotlinVersion"] = "1.9.0"

allprojects {
  group = "org.redundent"
  version = "1.9.0"

  repositories { mavenCentral() }
}

