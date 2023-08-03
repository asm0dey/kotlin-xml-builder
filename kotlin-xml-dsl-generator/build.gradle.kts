import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar


plugins {
	kotlin("jvm")
	id("com.github.johnrengelman.shadow") version "8.1.1"
	application
}

val kotlinVersion: String by rootProject.extra
application {
	mainClass.set("org.redundent.kotlin.xml.gen.DslGeneratorKt")
}


tasks {

	named<ShadowJar>("shadowJar") {
		archiveClassifier.set("shadow")

	}

	register<Jar>("sourceJar") {
		from(sourceSets["main"].allSource)
		archiveClassifier.set("sources")
	}
}

dependencies {
	implementation(kotlin("stdlib", kotlinVersion))
	implementation(kotlin("reflect", kotlinVersion))
	implementation("com.sun.xml.bind:jaxb-impl:4.0.3")
	implementation("org.glassfish.jaxb:jaxb-xjc:4.0.3")
	implementation("org.relaxng:jing:20220510")
	// xerces without known vulnerabilities
	implementation("xerces:xercesImpl:2.12.2")
	implementation("com.squareup:kotlinpoet:1.14.2")

	implementation(project(":kotlin-xml-builder"))
	testImplementation("junit:junit:4.13.1")
	testImplementation(kotlin("test-junit", kotlinVersion))
}

