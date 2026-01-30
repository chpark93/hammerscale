import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
	id("org.springframework.boot") version "3.5.7"
}

dependencies {
	// Project modules
	implementation(project(":common"))

	// Spring Boot
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-validation")

	// Kotlin
	implementation("org.jetbrains.kotlin:kotlin-stdlib")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	
	// Kotlin Coroutines
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

	// Exposed ORM
	implementation("org.jetbrains.exposed:exposed-core:0.50.0")
	implementation("org.jetbrains.exposed:exposed-jdbc:0.50.0")
	implementation("org.jetbrains.exposed:exposed-java-time:0.50.0")
	implementation("org.jetbrains.exposed:exposed-spring-boot-starter:0.50.0")
	
	// PostgreSQL Driver
	implementation("org.postgresql:postgresql:42.7.3")

	// InfluxDB Client
	implementation("com.influxdb:influxdb-client-kotlin:7.2.0")

	// gRPC
	implementation("io.grpc:grpc-netty-shaded:1.77.1")
	implementation("io.grpc:grpc-protobuf:1.77.1")
	implementation("io.grpc:grpc-stub:1.77.1")
	implementation("io.grpc:grpc-kotlin-stub:1.4.1")
	implementation("net.devh:grpc-spring-boot-starter:3.1.0.RELEASE")

	// Security & JWT
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("io.jsonwebtoken:jjwt-api:0.12.6")
	runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
	runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

	// Redis
	implementation("org.springframework.boot:spring-boot-starter-data-redis")

	// Test
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

val compileKotlin: KotlinCompile by tasks
compileKotlin.compilerOptions {
    freeCompilerArgs.set(listOf("-Xannotation-default-target=param-property"))
}