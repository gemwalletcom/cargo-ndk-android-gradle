package com.github.willir.rust

import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path

class CargoNdkConfigTest extends Specification {

    def "normalizeCargoExecutable expands leading tilde"() {
        given:
        def tmpHome = Files.createTempDirectory("cargo-home-test")
        def previousHome = System.getProperty("user.home")
        System.setProperty("user.home", tmpHome.toString())

        expect:
        CargoNdkConfig.normalizeCargoExecutable("~/bin/cargo") == tmpHome.resolve("bin/cargo").toString()
        CargoNdkConfig.normalizeCargoExecutable("~") == tmpHome.toString()

        cleanup:
        if (previousHome != null) {
            System.setProperty("user.home", previousHome)
        } else {
            System.clearProperty("user.home")
        }
        tmpHome.toFile().deleteDir()
    }

    def "detectCargoExecutable prefers ~/.cargo/bin before PATH fallback"() {
        given:
        def tmpHome = Files.createTempDirectory("cargo-home-test")
        def cargoPath = tmpHome.resolve(".cargo/bin")
        Files.createDirectories(cargoPath)
        def cargoBinary = cargoPath.resolve(CargoNdkConfig.getExecutableFileName())
        Files.writeString(cargoBinary, "")
        cargoBinary.toFile().setExecutable(true)
        def previousHome = System.getProperty("user.home")
        System.setProperty("user.home", tmpHome.toString())

        expect:
        CargoNdkConfig.detectCargoExecutable() == cargoBinary.toString()

        cleanup:
        if (previousHome != null) {
            System.setProperty("user.home", previousHome)
        } else {
            System.clearProperty("user.home")
        }
        tmpHome.toFile().deleteDir()
    }
}
