/*
 * Copyright (c) 2008 - 2013 10gen, Inc. <http://10gen.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package org.mongodb.gradle.clirr

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.logging.LogLevel
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.ReportingBasePlugin
import org.gradle.api.reporting.ReportingExtension
import javax.xml.transform.TransformerFactory
import javax.xml.transform.stream.StreamResult
import javax.xml.transform.stream.StreamSource

class ClirrPlugin implements Plugin<Project> {

    Configuration clirrConfiguration
    Configuration baseConfiguration
    ClirrPluginExtension extension

    @Override
    void apply(Project project) {
        createConfigurations(project)
        createExtension(project)
        addClirrTask(project)
    }

    void createConfigurations(Project project) {
        clirrConfiguration = project.configurations.create('clirr').with {
            visible = false
            transitive = true
            description = "The Clirr libraries to be used for this project."
            return (Configuration) delegate
        }

        baseConfiguration = project.configurations.create('base').with {
            visible = false
            transitive = false
            return (Configuration) delegate
        }
    }

    void createExtension(Project project) {
        project.apply(plugin: ReportingBasePlugin)

        extension = project.extensions.create('clirr', ClirrPluginExtension).with {
            reportsDir = new File("${project.reporting.baseDir.absolutePath}/clirr")
            baseline = "$project.group:$project.name:(,$project.version)".toString()
            formats = ['plain']
            failOnBinWarning = false
            failOnBinError = true
            failOnSrcWarning = false
            failOnSrcError = true
            return (ClirrPluginExtension) delegate
        }
    }

    void addClirrTask(Project project) {
        project.apply(plugin: JavaBasePlugin)

        def jarTask = project.tasks.getByName("jar")

        def clirrTask = project.task('clirr', dependsOn: jarTask) {
            doFirst {
                project.dependencies {
                    clirr project.parent.files('lib/clirr-core-0.7-SNAPSHOT.jar')
                    clirr 'asm:asm-all:2.2'
                    base "${extension.baseline}"
                }

                ant.taskdef(
                        resource: 'clirrtask.properties',
                        classpath: clirrConfiguration.asPath
                )

                extension.reportsDir.mkdirs()

                File baseJar = baseConfiguration.resolve().find { it.name.endsWith('.jar') }

                ant.clirr(
                        failOnBinWarning: extension.failOnBinWarning,
                        failOnBinError: extension.failOnBinError,
                        failOnSrcWarning: extension.failOnSrcWarning,
                        failOnSrcError: extension.failOnSrcError
                ) {
                    origfiles(file: baseJar.getPath())
                    newfiles(dir: jarTask.destinationDir, includes: jarTask.archiveName)
                    newClassPath {
                        pathElement(path: project.configurations.compile.asPath)
                    }

                    extension.formats.each { format ->
                        if (format != 'html'){
                            formatter(type: format, outfile: "$extension.reportsDir/report.${format == 'xml' ? 'xml' : 'txt'}")
                        } else {
                            formatter(type: 'xml', outfile: "$extension.reportsDir/report.xml")
                        }
                    }
                }
            }
            doLast {
                if (extension.formats.contains('html')){
                    def factory = TransformerFactory.newInstance()
                    def transformer = factory.newTransformer(new StreamSource(this.getClass().getResourceAsStream( '/clirr-html-template.xslt')))
                    transformer.transform(new StreamSource(new FileReader("$extension.reportsDir/report.xml")), new StreamResult(new FileOutputStream("$extension.reportsDir/report.html")))
                }
            }
        }

        project.tasks.getByName("check").dependsOn(clirrTask)
    }

}