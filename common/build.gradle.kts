import com.google.protobuf.gradle.id

plugins {
	id("com.google.protobuf") version "0.9.6"
}

dependencies {
	// Kotlin
	implementation("org.jetbrains.kotlin:kotlin-stdlib")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	
	// Kotlin Coroutines
	api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

	// gRPC
	api("io.grpc:grpc-kotlin-stub:1.4.1")
	api("io.grpc:grpc-protobuf:1.77.1")
	api("io.grpc:grpc-stub:1.77.1")
	api("com.google.protobuf:protobuf-kotlin:4.33.2")
	api("com.google.protobuf:protobuf-java:4.33.2")

	// Test
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

protobuf {
	protoc {
		artifact = "com.google.protobuf:protoc:4.33.2"
	}

	plugins {
		id("grpc") {
			artifact = "io.grpc:protoc-gen-grpc-java:1.77.1"
		}
        id("grpckt") {
			artifact = "io.grpc:protoc-gen-grpc-kotlin:1.5.0:jdk8@jar"
		}
	}

	generateProtoTasks {
		all().forEach { task ->
			task.plugins {
				create("grpc")
				create("grpckt")
			}
			task.builtins {
				create("kotlin")
			}
		}
	}
}

sourceSets {
	main {
		proto {
			srcDir("src/main/proto")
		}
		java {
			srcDirs(
				"build/generated/sources/proto/main/java",
				"build/generated/sources/proto/main/grpc",
				"build/generated/sources/proto/main/grpckt"
			)
		}
		kotlin {
			srcDirs(
				"build/generated/sources/proto/main/kotlin"
			)
		}
	}
}

tasks.processResources {
	duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

