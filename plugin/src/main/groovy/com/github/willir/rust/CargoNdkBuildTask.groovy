package com.github.willir.rust

import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.process.internal.ExecException

import javax.inject.Inject
import java.nio.file.Files
import java.nio.file.Path

abstract class CargoNdkBuildTask extends DefaultTask {
    @Input String variant
    @Input CargoNdkExtension extension

    private CargoNdkConfig config
    private final Project projectReference

    CargoNdkBuildTask() {
        this.projectReference = super.getProject()
    }

    @Inject
    protected abstract ExecOperations getExecOperations()

    @TaskAction
    void buildRust() {
        config = new CargoNdkConfig(
                projectReference,
                extension.buildTypeContainer.findByName(variant),
                extension)

        RustTargetType rustTargetName = null

        if (projectReference.hasProperty("rust-target")) {
            rustTargetName = RustTargetType.fromId((String) projectReference.findProperty("rust-target"))
        }

        if (rustTargetName != null) {
            buildTarget(rustTargetName)
        } else {
            for (target in config.getTargetTypes()) {
                buildTarget(target)
            }
        }
    }

    private void buildTarget(RustTargetType target) {
        validateHasCargo()

        int ndkVersion = getNdkVersion(target)

        def cmd = [config.getCargoExecutable(), "ndk",
                   "--target", target.rustTarget,
                   "--platform", ndkVersion.toString(),
                   "--", "build"]
        if (config.offline) {
            cmd.add("--offline")
        }
        if (config.isRelease()) {
            cmd.add("--release")
        }
        if (config.isVerbose()) {
            cmd.add("--verbose")
        }
        if (config.extraCargoBuildArguments != null) {
            cmd.addAll(config.extraCargoBuildArguments)
        }

        Path cwd = config.getCargoPath()
        if (!cwd.toFile().isDirectory()) {
            throw new GradleException(
                    "Rust root directory doesn't exist, or not a directory: '${cwd}'")
        }

        def extraEnv = getExtraCargoNdkEnvironments()
        if (config.extraCargoEnv != null) {
            extraEnv.putAll(config.extraCargoEnv)
        }
        logger.info("Executing: ${cmd} from ${cwd} with extra env: ${extraEnv}")

        getExecOperations().exec {
            it.workingDir = cwd.toFile()
            it.commandLine = cmd
            it.environment extraEnv
        }.assertNormalExitValue()

        copyTarget(target)
    }

    private void copyTarget(RustTargetType target) {
        for (libName in listLibraryNames()) {
            def copyFrom = config.getRustLibOutPath(target, libName)
            def copyTo = config.getJniLibPath(target, libName)

            Files.createDirectories(copyTo.getParent())

            Files.deleteIfExists(copyTo)
            Files.copy(copyFrom, copyTo)
            logger.info("Copy ${copyFrom} -> ${copyTo}")
        }
    }

    private ArrayList<String> listLibraryNames() {
        if (config.librariesNames != null) {
            return config.librariesNames
        } else {
            return listCargoTargets(config.getCargoPath())
        }
    }

    private ArrayList<String> listCargoTargets(Path cargoDirPath) {
        def cmd = [config.getCargoExecutable(), "metadata", "--format-version", "1"]
        if (config.offline) {
            cmd.add("--offline")
        }

        def os = new ByteArrayOutputStream()

        getExecOperations().exec {
            it.workingDir = cargoDirPath.toFile()
            it.commandLine = cmd
            it.standardOutput = os
        }.assertNormalExitValue()

        def metadata = new JsonSlurper().parseText(os.toString())
        def rootId = metadata.resolve.root
        if (rootId == null) {
            throw new GradleException(
                    "cargo workspace at: '${cargoDirPath}' is a virtual workspace")
        }

        def rootPackage = metadata.packages.find { it.id == rootId }
        if (rootPackage == null) {
            throw new GradleException(
                    "Cannot find root package for cargo workspace at: '${cargoDirPath}'")
        }

        return rootPackage.targets
                .findAll { it.kind.contains("dylib") || it.kind.contains("cdylib") }
                .collect { "lib" + (String) it.name + ".so" }
    }

    private int getNdkVersion(RustTargetType target) {
        int ndkVersion = config.apiLevel
        if (target.is64Bit() && ndkVersion < 21) {
            logger.warn("${target} doesn't support ${ndkVersion} NDK version. Changing to 21")
            ndkVersion = 21
        }
        return ndkVersion
    }

    private Map<String, Object> getExtraCargoNdkEnvironments() {
        Properties properties = new Properties()
        properties.load(projectReference.rootProject.file('local.properties').newDataInputStream())
        def ndkDir = properties.getProperty('ndk.dir', null)

        if (ndkDir != null) {
            return ['ANDROID_NDK_HOME': ndkDir]
        } else {
            return [:]
        }
    }

    private void validateHasCargo() {
        def cmd = [config.getCargoExecutable(), "--version"]

        int exitValue
        def os = new ByteArrayOutputStream()
        try {
            def p = getExecOperations().exec {
                it.commandLine = cmd
                it.standardOutput = os
                it.errorOutput = os
            }
            exitValue = p.exitValue
        } catch (ExecException ignored) {
            exitValue = 1
        }
        logger.info("`${cmd.join(" ")}` output: ${os.toString().trim()}")
        if (exitValue != 0) {
            throw new GradleException(
                    "Failed to run `${cmd.join(" ")}`. " +
                            "You probably don't have '${cmd[0]}' executable in PATH: " +
                            System.getenv("PATH"))
        }
    }
}
