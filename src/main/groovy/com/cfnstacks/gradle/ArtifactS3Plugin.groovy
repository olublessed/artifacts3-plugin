package com.cfnstacks.gradle

import com.amazonaws.auth.profile.ProfileCredentialsProvider
import jp.classmethod.aws.gradle.cloudformation.AmazonCloudFormationPlugin
import net.researchgate.release.ReleasePlugin
import org.asciidoctor.gradle.AsciidoctorPlugin
import org.apache.tools.ant.filters.ReplaceTokens
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.credentials.AwsCredentials
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.bundling.Jar
import org.kordamp.gradle.livereload.LiveReloadPlugin

class ArtifactS3Plugin implements Plugin<Project> {

    static final String GROUP_NAME = 'ArtifactS3'
    static final String PLUGIN_NAME = 'artifacts3'

    static final String TASK_BUILD_NAME = 'pluginBuild'
    static final String TASK_CLEAN_NAME = 'pluginClean'
    static final String TASK_COPY_NAME = 'copyAndFilter'
    static final String TASK_DOCS = 'docs'
    static final String TASK_PROJECT_VERSION = 'projectVersion'
    static final String TASK_CREATE_STACK = 'createStack'
    static final String TASK_UPDATE_STACK = 'updateStack'
    static final String TASK_DELETE_STACK = 'deleteStack'
    static final String TASK_CREATE_STACK_AND_WAIT = 'createStackAndWait'
    static final String TASK_UPDATE_STACK_AND_WAIT = 'updateStackAndWait'
    static final String TASK_DELETE_STACK_AND_WAIT = 'deleteStackAndWait'
    static final String TASK_CREATE_CHANGE_SET = 'createChangeSet'
    static final String TASK_EXECUTE_CHANGE_SET = 'executeChangeSet'

    @Override
    void apply(Project project) {

        project.plugins.apply(AmazonCloudFormationPlugin)
        project.plugins.apply(AsciidoctorPlugin)
        project.plugins.apply(BasePlugin)
        project.plugins.apply(LiveReloadPlugin)
        project.plugins.apply(MavenPublishPlugin)
        project.plugins.apply(ReleasePlugin)

        project.extensions.create(PLUGIN_NAME, ArtifactS3PluginExtension)

        // Internal tasks

        Task copyAndFilter = project.task(TASK_COPY_NAME, type: Sync) {
            from 'src/main/cloudformation'
            into 'build/cloudformation'

            // When templates need to refer to other templates within the same artifact they
            // can use the @templatePath@ token which will compute the path
            String path = "${(project.version.contains('SNAPSHOT')) ? 'snapshot' : 'release'}/${params.group.replace('.', '/')}/${project.version}"

            filter(ReplaceTokens, tokens: [
                    artifactId: project.name,
                    version: project.version,
                    templatePath: path
            ])
        }

        project.tasks.build.dependsOn(copyAndFilter)
        project.tasks.build.mustRunAfter(copyAndFilter)

        Task buildTask = project.task(TASK_BUILD_NAME, type: Jar) {
            description = "Build CloudFormation template artifacts"
            from 'build/cloudformation'
            archiveName = "build/${project.name}-${project.version}.cfn.jar"
        }
        buildTask.mustRunAfter(project.tasks.build)

        project.task(TASK_CLEAN_NAME, type: Delete, dependsOn: [project.tasks.clean]) {
            description = 'Deletes the build directory'
            delete 'build'
        }

        // Externally facing tasks

        project.task(TASK_PROJECT_VERSION) {
            group = GROUP_NAME
            description = 'Prints the version of the project'
            doLast { println project.version }
        }

        project.task(TASK_DOCS, dependsOn: ['asciidoctor']) {
            group = GROUP_NAME
            description = 'Run AsciiDoctor to generate documentation'
        }

        def config = project.extensions.artifacts3

        // Alias all the CloudFormation tasks we want to enable

        Task createStackTask = project.task(TASK_CREATE_STACK, dependsOn: [copyAndFilter, 'awsCfnMigrateStack']) {
            group = GROUP_NAME
            description = 'Creates the stack and returns immediately'
        }
        createStackTask.mustRunAfter(copyAndFilter)

        Task updateStackTask = project.task(TASK_UPDATE_STACK, dependsOn: [copyAndFilter, 'awsCfnMigrateStack']) {
            group = GROUP_NAME
            description = 'Updates the stack and returns immediately'
        }
        updateStackTask.mustRunAfter(copyAndFilter)

        project.task(TASK_DELETE_STACK, dependsOn: ['awsCfnDeleteStack']) {
            group = GROUP_NAME
            description = 'Deletes the stack and returns immediately'
        }

        Task createStackAndWaitTask = project.task(TASK_CREATE_STACK_AND_WAIT, dependsOn: [copyAndFilter, 'awsCfnMigrateStackAndWaitCompleted']) {
            group = GROUP_NAME
            description = 'Creates the stack and returns when completed'
        }
        createStackAndWaitTask.mustRunAfter(copyAndFilter)

        Task updateStackAndWait = project.task(TASK_UPDATE_STACK_AND_WAIT, dependsOn: [copyAndFilter, 'awsCfnMigrateStackAndWaitCompleted']) {
            group = GROUP_NAME
            description = 'Updates the stack and returns when completed'
        }
        updateStackAndWait.mustRunAfter(copyAndFilter)

        project.task(TASK_DELETE_STACK_AND_WAIT, dependsOn: ['awsCfnDeleteStackAndWaitCompleted']) {
            group = GROUP_NAME
            description = 'Deletes the stack and returns when completed'
        }

        Task createChangeSetTask = project.task(TASK_CREATE_CHANGE_SET, dependsOn: [copyAndFilter, 'awsCfnCreateChangeSet']) {
            group = GROUP_NAME
            description = 'Creates a change set'
        }
        createChangeSetTask.mustRunAfter(copyAndFilter)

        project.task(TASK_EXECUTE_CHANGE_SET, dependsOn: ['awsCfnExecuteChangeSet']) {
            group = GROUP_NAME
            description = 'Executes and then removes a change set'
        }

        // Configuration

        project.asciidoctorj {
            version = '1.5.6'
        }
        project.liveReload {
            docRoot 'build/asciidoc/html5'
        }
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