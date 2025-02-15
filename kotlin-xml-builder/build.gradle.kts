plugins { kotlin("jvm") }

val kotlinVersion: String by rootProject.extra

tasks {
  val jar by getting(Jar::class)

  register<Jar>("sourceJar") {
    from(sourceSets["main"].allSource)
    destinationDirectory.set(jar.destinationDirectory)
    archiveClassifier.set("sources")
  }
}

dependencies {
  compileOnly(kotlin("stdlib", kotlinVersion))
  compileOnly(kotlin("reflect", kotlinVersion))
  implementation("org.apache.commons:commons-lang3:3.5")

  testImplementation("junit:junit:4.12")
  testImplementation(kotlin("reflect", kotlinVersion))
  testImplementation(kotlin("test-junit", kotlinVersion))
}

artifacts { add("archives", tasks["sourceJar"]) }
