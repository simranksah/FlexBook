package com.flexbook

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class FlexBookApplication

fun main(args: Array<String>) {
    runApplication<FlexBookApplication>(*args)
}
