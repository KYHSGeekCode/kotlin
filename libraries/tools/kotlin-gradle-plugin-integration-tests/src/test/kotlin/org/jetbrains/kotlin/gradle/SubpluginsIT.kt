/*
 * Copyright 2010-2018 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle

import org.gradle.api.logging.LogLevel
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.gradle.testbase.*
import org.jetbrains.kotlin.gradle.util.checkBytecodeContains
import org.jetbrains.kotlin.test.util.JUnit4Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import kotlin.io.path.readText

@DisplayName("Other plugins tests")
@OtherGradlePluginTests
class SubpuginsIT : KGPBaseTest() {

    @DisplayName("Subplugin example works as expected")
    @GradleTest
    fun testGradleSubplugin(gradleVersion: GradleVersion) {
        project("kotlinGradleSubplugin", gradleVersion) {
            build("compileKotlin", "build") {
                assertTasksExecuted(":compileKotlin")
                assertOutputContains("ExampleSubplugin loaded")
                assertOutputContains("ExampleLegacySubplugin loaded")
                assertOutputContains("Project component registration: exampleValue")
                assertOutputContains("Project component registration: exampleLegacyValue")
            }

            build("compileKotlin", "build") {
                assertTasksUpToDate(":compileKotlin")
                assertOutputContains("ExampleSubplugin loaded")
                assertOutputContains("ExampleLegacySubplugin loaded")
                assertOutputDoesNotContain("Project component registration: exampleValue")
                assertOutputDoesNotContain("Project component registration: exampleLegacyValue")
            }
        }
    }

    @DisplayName("Allopen plugin opens classes and methods")
    @GradleTest
    fun testAllOpenPlugin(gradleVersion: GradleVersion) {
        project("allOpenSimple", gradleVersion) {
            build("assemble") {
                val classesDir = kotlinClassesDir()
                val openClass = classesDir.resolve("test/OpenClass.class")
                val closedClass = classesDir.resolve("test/ClosedClass.class")
                assertFileExists(openClass)
                assertFileExists(closedClass)

                checkBytecodeContains(
                    openClass.toFile(),
                    "public class test/OpenClass {",
                    "public method()V"
                )

                checkBytecodeContains(
                    closedClass.toFile(),
                    "public final class test/ClosedClass {",
                    "public final method()V"
                )
            }
        }
    }

    @DisplayName("Kotlin Spring plugin opens classes and methods")
    @GradleTest
    fun testKotlinSpringPlugin(gradleVersion: GradleVersion) {
        project("allOpenSpring", gradleVersion) {
            build("assemble") {

                val classesDir = kotlinClassesDir()
                val openClass = classesDir.resolve("test/OpenClass.class")
                val closedClass = classesDir.resolve("test/ClosedClass.class")

                assertFileExists(openClass)
                assertFileExists(closedClass)

                checkBytecodeContains(
                    openClass.toFile(),
                    "public class test/OpenClass {",
                    "public method()V"
                )

                checkBytecodeContains(
                    closedClass.toFile(),
                    "public final class test/ClosedClass {",
                    "public final method()V"
                )
            }
        }
    }

    @DisplayName("Jpa plugin generates no-arg constructor")
    @GradleTest
    fun testKotlinJpaPlugin(gradleVersion: GradleVersion) {
        project("noArgJpa", gradleVersion) {
            build("assemble") {
                val classesDir = kotlinClassesDir()

                fun checkClass(name: String) {
                    val testClass = classesDir.resolve("test/$name.class")
                    assertFileExists(testClass)
                    checkBytecodeContains(testClass.toFile(), "public <init>()V")
                }

                checkClass("Test")
                checkClass("Test2")
            }
        }
    }

    @DisplayName("NoArg: Don't invoke initializers by default")
    @GradleTest
    fun testNoArgKt18668(gradleVersion: GradleVersion) {
        project("noArgKt18668", gradleVersion) {
            build("assemble")
        }
    }

    @DisplayName("sam-with-receiver works")
    @GradleTest
    fun testSamWithReceiverSimple(gradleVersion: GradleVersion) {
        project("samWithReceiverSimple", gradleVersion) {
            build("assemble")
        }
    }

    @DisplayName("Allopen plugin works when classpath dependency is not declared in current or root project ")
    @GradleTest
    fun testAllOpenFromNestedBuildscript(gradleVersion: GradleVersion) {
        project("allOpenFromNestedBuildscript", gradleVersion) {
            build("build") {
                val nestedSubproject = subProject("a/b")
                assertFileExists(nestedSubproject.kotlinClassesDir().resolve("MyClass.class"))
                assertFileExists(nestedSubproject.kotlinClassesDir("test").resolve("MyTestClass.class"))
            }
        }
    }

    @DisplayName("Allopen applied from script works")
    @GradleTest
    fun testAllopenFromScript(gradleVersion: GradleVersion) {
        project("allOpenFromScript", gradleVersion) {
            build("build") {
                assertFileExists(kotlinClassesDir().resolve("MyClass.class"))
                assertFileExists(kotlinClassesDir(sourceSet = "test").resolve("MyTestClass.class"))
            }
        }
    }

    @DisplayName("KT-39809: kapt subplugin legacy loading does not fail the build")
    @GradleTest
    fun testKotlinVersionDowngradeInSupbrojectKt39809(gradleVersion: GradleVersion) {
        project("kapt2/android-dagger", gradleVersion) {
            subProject("app").buildGradle.modify {
                """
                buildscript {
                	repositories {
                		mavenCentral()
                	}
                	dependencies {
                		classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${TestVersions.Kotlin.STABLE_RELEASE}")
                	}
                }

                $it
                """.trimIndent()
            }

            build(
                ":app:compileDebugKotlin",
                buildOptions = defaultBuildOptions.copy(
                    androidVersion = TestVersions.AGP.AGP_42
                )
            )
        }
    }

    @DisplayName("KT-39809: subplugins legacy loading does not fail the build")
    @GradleTest
    fun testKotlinVersionDowngradeWithNewerSubpluginsKt39809(gradleVersion: GradleVersion) {
        project("multiprojectWithDependency", gradleVersion) {

            val projectA = subProject("projA")
            val subprojectBuildGradle = projectA.buildGradle
            val originalScript = subprojectBuildGradle.readText()

            listOf("allopen", "noarg", "sam-with-receiver", "serialization").forEach { plugin ->
                subprojectBuildGradle.modify {
                    """
                    buildscript {
                        repositories {
                            mavenLocal()
                            mavenCentral()
                        }
                        dependencies {
                            classpath("org.jetbrains.kotlin:kotlin-$plugin:${defaultBuildOptions.kotlinVersion}")
                        }
                    }

                    apply plugin: "org.jetbrains.kotlin.plugin.${plugin.replace("-", ".")}"

                    $originalScript
                    """.trimIndent()
                }

                buildAndFail(
                    ":projA:compileKotlin",
                    buildOptions = defaultBuildOptions.copy(kotlinVersion = "1.3.72")
                ) {
                    assertOutputContains(
                        "This version of the kotlin-$plugin Gradle plugin is built for a newer Kotlin version. " +
                                "Please use an older version of kotlin-$plugin or upgrade the Kotlin Gradle plugin version to make them match."
                    )
                }
            }
        }
    }

    @DisplayName("Lombok plugin is working")
    @GradleTest
    fun testLombokPlugin(gradleVersion: GradleVersion) {
        project("lombokProject", gradleVersion) {
            build("build")
        }
    }

    @DisplayName("KT-47921: serialization plugin passed first to the compiler")
    @GradleTest
    fun testSerializationPluginOrderedFirst(gradleVersion: GradleVersion) {
        project("allOpenSimple", gradleVersion) {
            // Ensure that there are also allopen, noarg, and serialization plugins applied:
            buildGradle.modify {
                """
                |plugins {
                |    id "org.jetbrains.kotlin.plugin.noarg"
                |    id "org.jetbrains.kotlin.plugin.serialization"
                |${it.substringAfter("plugins {")}
                """.trimMargin()
            }

            build(
                "compileKotlin",
                buildOptions = defaultBuildOptions.copy(logLevel = LogLevel.DEBUG)
            ) {
                val xPlugin = output
                    .split(" ")
                    .single { it.startsWith("-Xplugin") }
                    .substringAfter("-Xplugin")
                    .split(",")
                assertTrue(xPlugin.first().contains("serialization")) {
                    "Expected serialization plugin to go first; actual order: $xPlugin"
                }
            }
        }
    }
}
