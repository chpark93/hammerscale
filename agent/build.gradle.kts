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

	// gRPC
	implementation("io.grpc:grpc-netty-shaded:1.77.1")
	implementation("io.grpc:grpc-protobuf:1.77.1")
	implementation("io.grpc:grpc-stub:1.77.1")
	implementation("io.grpc:grpc-kotlin-stub:1.4.1")

	// Test
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

