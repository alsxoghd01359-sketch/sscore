package com.sscore

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class SscoreServerApplication

fun main(args: Array<String>) {
	runApplication<SscoreServerApplication>(*args)
}
