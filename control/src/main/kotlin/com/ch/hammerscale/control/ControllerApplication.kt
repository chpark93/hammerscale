package com.ch.hammerscale.control

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(scanBasePackages = ["com.ch.hammerscale.control"])
class ControllerApplication

fun main(args: Array<String>) {
	runApplication<ControllerApplication>(*args)
}

