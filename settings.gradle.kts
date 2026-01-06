pluginManagement {
	repositories {
		maven { url = uri("https://repo.spring.io/snapshot") }
		gradlePluginPortal()
	}
}

rootProject.name = "hammerscale"

include("common")
include("controller")
include("agent")
