/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin

import com.gradle.scan.plugin.BuildScanExtension
import org.gradle.api.Project
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.jetbrains.kotlin.gradle.logging.kotlinDebug
import org.jetbrains.kotlin.gradle.plugin.internal.state.TaskExecutionResults
import org.jetbrains.kotlin.gradle.plugin.internal.state.TaskLoggers
import org.jetbrains.kotlin.gradle.plugin.stat.CompileStatData
import org.jetbrains.kotlin.gradle.plugin.stat.ReportStatistics
import org.jetbrains.kotlin.gradle.plugin.statistics.KotlinBuildStatListener
import org.jetbrains.kotlin.gradle.plugin.statistics.ReportStatisticsToBuildScan
import org.jetbrains.kotlin.gradle.plugin.statistics.ReportStatisticsByHttp
import org.jetbrains.kotlin.gradle.report.BuildReportType
import org.jetbrains.kotlin.gradle.report.ReportingSettings
import org.jetbrains.kotlin.gradle.report.reportingSettings
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

internal abstract class KotlinGradleBuildServices : BuildService<KotlinGradleBuildServices.Parameters>, AutoCloseable {

    interface Parameters : BuildServiceParameters {
        var buildDir: File
        var rootDir: File
    }

    private val log = Logging.getLogger(this.javaClass)
    private val buildHandler: KotlinGradleFinishBuildHandler = KotlinGradleFinishBuildHandler()
    private val CLASS_NAME = KotlinGradleBuildServices::class.java.simpleName
    val INIT_MESSAGE = "Initialized $CLASS_NAME"
    val DISPOSE_MESSAGE = "Disposed $CLASS_NAME"

    init {
        log.kotlinDebug(INIT_MESSAGE)
        buildHandler.buildStart()
    }

    override fun close() {
        buildHandler.buildFinished(parameters.buildDir, parameters.rootDir)
        log.kotlinDebug(DISPOSE_MESSAGE)

        buildMetricReporterWorker?.shutdown()
        buildMetricReporterWorker = null

        TaskLoggers.clear()
        TaskExecutionResults.clear()
    }

    companion object {
        private const val timeoutInSeconds = 10L

        private var buildMetricReporterWorker: ExecutorService? = null

        fun registerIfAbsent(project: Project): Provider<KotlinGradleBuildServices> = project.gradle.sharedServices.registerIfAbsent(
            "kotlin-build-service-${KotlinGradleBuildServices::class.java.canonicalName}_${KotlinGradleBuildServices::class.java.classLoader.hashCode()}",
            KotlinGradleBuildServices::class.java
        ) { service ->
            val reporterWorker = Executors.newSingleThreadExecutor()
            service.parameters.rootDir = project.rootProject.rootDir
            service.parameters.buildDir = project.rootProject.buildDir

            val reportingSettings = reportingSettings(project.rootProject)
            addListeners(project, reportingSettings, reporterWorker)
            buildMetricReporterWorker = reporterWorker
        }

        fun addListeners(project: Project, reportingSettings: ReportingSettings, reporterWorker: ExecutorService) {
            val listeners = project.rootProject.objects.listProperty(ReportStatistics::class.java)
                .value(listOf<ReportStatistics>())

            project.rootProject.extensions.findByName("buildScan")
                ?.also {
                    if (reportingSettings.buildReportOutputs.contains(BuildReportType.BUILD_SCAN)) {
                        listeners.add(ReportStatisticsToBuildScan(it as BuildScanExtension))
                    }
                }

            if (listeners.get().isNotEmpty()) {
                val listenerRegistryHolder = BuildEventsListenerRegistryHolder.getInstance(project)
                val statListener = KotlinBuildStatListener(project.rootProject.name, reportingSettings.buildReportLabel, listeners.get())
                listenerRegistryHolder.listenerRegistry.onTaskCompletion(project.provider { statListener })
            }
        }

        private val multipleProjectsHolder = KotlinPluginInMultipleProjectsHolder(
            trackPluginVersionsSeparately = true
        )

        @Synchronized
        internal fun detectKotlinPluginLoadedInMultipleProjects(project: Project, kotlinPluginVersion: String) {
            val onRegister = {
                project.gradle.taskGraph.whenReady {
                    if (multipleProjectsHolder.isInMultipleProjects(project, kotlinPluginVersion)) {
                        val loadedInProjects = multipleProjectsHolder.getAffectedProjects(project, kotlinPluginVersion)!!
                        if (PropertiesProvider(project).ignorePluginLoadedInMultipleProjects != true) {
                            project.logger.warn("\n$MULTIPLE_KOTLIN_PLUGINS_LOADED_WARNING")
                            project.logger.warn(
                                MULTIPLE_KOTLIN_PLUGINS_SPECIFIC_PROJECTS_WARNING + loadedInProjects.joinToString(limit = 4) { "'$it'" }
                            )
                        }
                        project.logger.info(
                            "$MULTIPLE_KOTLIN_PLUGINS_SPECIFIC_PROJECTS_INFO: " +
                                    loadedInProjects.joinToString { "'$it'" }
                        )
                    }
                }
            }

            multipleProjectsHolder.addProject(
                project,
                kotlinPluginVersion,
                onRegister
            )
        }
    }
}


