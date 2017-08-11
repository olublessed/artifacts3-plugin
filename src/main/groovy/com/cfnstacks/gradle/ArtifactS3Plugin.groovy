package com.cfnstacks.gradle

import com.amazonaws.auth.profile.ProfileCredentialsProvider
import jp.classmethod.aws.gradle.cloudformation.AmazonCloudFormationPlugin
import net.researchgate.release.ReleasePlugin
import org.apache.tools.ant.filters.ReplaceTokens
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
        project.plugins.apply(AmazonCloudFormationPlugin)
        project.plugins.apply(ReleasePlugin)
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

        Task buildTask = project.task(TASK_BUILD_NAME, type: Jar, dependsOn: copyAndFilter, overwrite: true) {
            group = GROUP_NAME
            description = "Build CloudFormation template artifacts"
            from 'build/cloudformation'
            archiveName = "build/${project.name}-${project.version}.cfn.jar"
        }

        project.task(TASK_CLEAN_NAME, type: Delete, overwrite: true) {
            group = GROUP_NAME
            description = 'Deletes the build directory'
            delete 'build'
        }

        def config = project.extensions.artifacts3

        // Alias all the CloudFormation tasks we want to enable

        project.task('createStack', dependsOn: [buildTask, 'awsCfnMigrateStack']) {
            group = GROUP_NAME
            description = 'Creates the stack and returns immediately'
        }

        project.task('updateStack', dependsOn: [buildTask, 'awsCfnMigrateStack']) {
            group = GROUP_NAME
            description = 'Updates the stack and returns immediately'
        }

        project.task('deleteStack', dependsOn: ['awsCfnDeleteStack']) {
            group = GROUP_NAME
            description = 'Deletes the stack and returns immediately'
        }

        project.task('createStackAndWait', dependsOn: [buildTask, 'awsCfnMigrateStackAndWaitCompleted']) {
            group = GROUP_NAME
            description = 'Creates the stack and returns when completed'
        }

        project.task('updateStackAndWait', dependsOn: [buildTask, 'awsCfnMigrateStackAndWaitCompleted']) {
            group = GROUP_NAME
            description = 'Updates the stack and returns when completed'
        }

        project.task('deleteStackAndWait', dependsOn: [buildTask, 'awsCfnDeleteStackAndWaitCompleted']) {
            group = GROUP_NAME
            description = 'Deletes the stack and returns when completed'
        }

        project.task('createChangeSet', dependsOn: [buildTask, 'awsCfnCreateChangeSet']) {
            group = GROUP_NAME
            description = 'Creates a change set'
        }

        project.task('executeChangeSet', dependsOn: ['awsCfnExecuteChangeSet']) {
            group = GROUP_NAME
            description = 'Executes and then removes a change set'
        }

        // Configuration

        project.publishing {
            publications {
                CloudFormationArtifact(MavenPublication) {
                    artifact buildTask
                    setGroupId config.groupSetting
                }
            }
            repositories {
                maven {
                    url "s3://${config.repoSetting}/${project.version.endsWith('-SNAPSHOT') ? 'snapshot' : 'release'}/"
                    credentials(AwsCredentials) {
                        if(System.getenv('AWS_ACCESS_KEY_ID') != null &&  System.getenv('AWS_SECRET_ACCESS_KEY')) {
                            accessKey System.getenv('AWS_ACCESS_KEY_ID')
                            secretKey System.getenv('AWS_SECRET_ACCESS_KEY')
                        } else {
                            if (config.profileNameSetting) {
                                def creds = new ProfileCredentialsProvider(config.profileNameSetting).getCredentials();
                                accessKey creds.getAWSAccessKeyId()
                                secretKey creds.getAWSSecretKey()
                            }
                        }
                    }
                }
            }
        }
        project.release { tagTemplate = 'v${version}' }

        Task publishTask = project.tasks.getByName('publish')
        project.tasks.getByName('afterReleaseBuild').dependsOn(publishTask)
        publishTask.dependsOn(buildTask)

        project.afterEvaluate { project.extensions.getByType(ArtifactS3PluginExtension).settings(project) }
    }
}