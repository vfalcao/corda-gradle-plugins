package net.corda.plugins

import org.gradle.testkit.runner.GradleRunner
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

import static org.assertj.core.api.Assertions.*
import static org.gradle.testkit.runner.TaskOutcome.*

class QuasarPluginTest {
    private static final String TEST_GRADLE_USER_HOME = System.getProperty("test.gradle.user.home", ".")
    private static final String QUASAR_VERSION = QuasarPlugin.defaultVersion

    @Rule
    public final TemporaryFolder testProjectDir = new TemporaryFolder()

    @Before
    void setup() {
        Utilities.installResource(testProjectDir, "settings.gradle")
    }

    @Test
    void checkDefaultVersionIsUsed() {
        def output = runGradleFor """
plugins {
    id 'java'
    id 'net.corda.plugins.quasar-utils' apply false
}

description 'Show quasar-core added to configurations'
    
repositories {
    mavenCentral()
}

apply plugin: 'net.corda.plugins.quasar-utils'

jar {
    enabled = false
}

def configs = configurations.matching { it.name in ['quasar', 'cordaRuntime', 'compileOnly', 'compileClasspath'] }
configs.collectEntries { [(it.name):it] }.forEach { name, files ->
    files.forEach { file ->
        println "\$name: \${file.name}"
    }
}
"""
        assertThat(output).containsOnlyOnce(
            "quasar: quasar-core-$QUASAR_VERSION-jdk8.jar".toString(),
            "cordaRuntime: quasar-core-$QUASAR_VERSION-jdk8.jar".toString(),
            "compileOnly: quasar-core-$QUASAR_VERSION-jdk8.jar".toString(),
            "compileClasspath: quasar-core-$QUASAR_VERSION-jdk8.jar".toString()
        )
    }

    @Test
    void checkOverriddenVersionIsUsed() {
        def quasarVersion = "0.7.9"
        assertThat(quasarVersion).isNotEqualTo(QUASAR_VERSION)

        def output = runGradleFor """
buildscript {
    ext {
        quasar_group = 'co.paralleluniverse'
        quasar_version = '$quasarVersion'
    }
}

plugins {
    id 'java'
    id 'net.corda.plugins.quasar-utils' apply false
}

description 'Show quasar-core added to configurations'
    
repositories {
    mavenCentral()
}

apply plugin: 'net.corda.plugins.quasar-utils'

jar {
    enabled = false
}

def configs = configurations.matching { it.name in ['quasar', 'cordaRuntime', 'compileOnly', 'compileClasspath'] }
configs.collectEntries { [(it.name):it] }.forEach { name, files ->
    files.forEach { file ->
        println "\$name: \${file.name}"
    }
}
"""
        assertThat(output).containsOnlyOnce(
            "quasar: quasar-core-$quasarVersion-jdk8.jar".toString(),
            "cordaRuntime: quasar-core-$quasarVersion-jdk8.jar".toString(),
            "compileOnly: quasar-core-$quasarVersion-jdk8.jar".toString(),
            "compileClasspath: quasar-core-$quasarVersion-jdk8.jar".toString()
        )
    }

    @Test
    void checkForTransitiveDependencies() {
        def output = runGradleFor """
plugins {
    id 'java'
    id 'net.corda.plugins.quasar-utils' apply false
}

description 'Show quasar-core added to configurations'
    
repositories {
    mavenCentral()
}

apply plugin: 'net.corda.plugins.quasar-utils'

jar {
    enabled = false
}

def configs = configurations.matching { it.name in ['quasar', 'cordaRuntime', 'compileClasspath', 'compileOnly', 'runtimeClasspath'] }
configs.collectEntries { [(it.name):it] }.forEach { name, files ->
    files.forEach { file ->
        println "\$name: \${file.name}"
    }
}
"""
        assertThat(output.findAll { it.startsWith("quasar:") }).hasSize(1)
        assertThat(output.findAll { it.startsWith("cordaRuntime:") }).hasSize(1)
        assertThat(output.findAll { it.startsWith("compileOnly:") }).hasSize(1)
        assertThat(output.findAll { it.startsWith("compileClasspath:") }).hasSize(1)
        assertThat(output.findAll { it.startsWith("runtimeClasspath:") }.size()).isGreaterThan(1)
    }

    @Test
    void checkJVMArgsAddedForTests() {
        def output = runGradleFor """
plugins {
    id 'java'
    id 'net.corda.plugins.quasar-utils' apply false
}

description 'Show quasar-core added to test JVM arguments'

repositories {
    mavenCentral()
}

apply plugin: 'net.corda.plugins.quasar-utils'

jar {
    enabled = false
}

test {
    allJvmArgs.forEach {
        println "TEST-JVM: \${it}"
    }
}
"""
        assertThat(output).anyMatch {
            it.startsWith("TEST-JVM: -javaagent:") && it.endsWith("quasar-core-$QUASAR_VERSION-jdk8.jar")
        }.anyMatch {
            it == "TEST-JVM: -Dco.paralleluniverse.fibers.verifyInstrumentation"
        }
    }

    private List<String> runGradleFor(String script) {
        def buildFile = testProjectDir.newFile("build.gradle")
        buildFile.text = script
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.getRoot())
            .withArguments("--info", "build", "-g", TEST_GRADLE_USER_HOME)
            .withPluginClasspath()
            .build()
        println result.output

        def build = result.task(":build")
        assertThat(build).isNotNull()
        assertThat(build.outcome).isEqualTo(UP_TO_DATE)

        return result.output.readLines()
    }
}