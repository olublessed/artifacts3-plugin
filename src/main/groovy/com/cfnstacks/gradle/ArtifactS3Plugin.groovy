package com.cfnstacks.gradle

import com.amazonaws.auth.profile.ProfileCredentialsProvider
import org.apache.tools.ant.filters.ReplaceTokens
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.credentials.AwsCredentials
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.bundling.Jar

class ArtifactS3Plugin implements Plugin<Project> {

    static final String GROUP_NAME = 'ArtifactS3'
    static final String PLUGIN_NAME = 'artifacts3'

    static final String TASK_BUILD_NAME = 'build'
    static final String TASK_CLEAN_NAME = 'clean'
    static final String TASK_COPY_NAME = 'copyAndFilter'

    @Override
    void apply(Project project) {
        project.plugins.apply(MavenPublishPlugin)
        project.extensions.create(PLUGIN_NAME, ArtifactS3PluginExtension)

        // Internal tasks

        Task copyAndFilter = project.task(TASK_COPY_NAME, type: Sync) {
            from 'src/main/cloudformation'
            into 'build/cloudformation'

            filter(ReplaceTokens, tokens: [
                artifactId: project.name,
                version: project.version
            ])
        }

        // Externally facing tasks

        Task buildTask = project.task(TASK_BUILD_NAME, type: Jar) {
            group = GROUP_NAME
            description = "Build CloudFormation template artifacts"
            from 'build/cloudformation'
            archiveName = "build/${project.name}-${project.version}.cfn.jar"
        }
        buildTask.dependsOn(copyAndFilter)

        project.task(TASK_CLEAN_NAME, type: Delete) {
            group = GROUP_NAME
            description = 'Deletes the build directory'
            delete 'build'
        }

        project.afterEvaluate {

            if(! project.extensions.artifacts3.repo?.trim()) {
                throw new GradleException('Error: Required property missing. Set artifacts3.repo property in build.repo')
            }

            if(! project.extensions.artifacts3.group?.trim()) {
                throw new GradleException('Error: Required property missing. Set artifacts3.group property in build.repo')
            }

            project.publishing {
                publications {
                    CloudFormationArtifact(MavenPublication) {
                        artifact buildTask
                        setGroupId project.extensions.artifacts3.group

                    }
                }
                repositories {
                    maven {
                        url "s3://${project.extensions.artifacts3.repo}/${project.version.endsWith('-SNAPSHOT') ? 'snapshot' : 'release'}/"
                        credentials(AwsCredentials) {
                            if(System.getenv('AWS_ACCESS_KEY_ID') != null &&
                                    System.getenv('AWS_SECRET_ACCESS_KEY')) {
                                accessKey System.getenv('AWS_ACCESS_KEY_ID')
                                secretKey System.getenv('AWS_SECRET_ACCESS_KEY')
                            } else {
                                if (project.extensions.artifacts3.profileName?.trim()) {
                                    def creds = new ProfileCredentialsProvider(project.extensions.artifacts3.profileName).getCredentials();
                                    accessKey creds.getAWSAccessKeyId()
                                    secretKey creds.getAWSSecretKey()
                                } else {
                                    throw new GradleException('Error: No AWS credential environment variables or profile name found')
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}