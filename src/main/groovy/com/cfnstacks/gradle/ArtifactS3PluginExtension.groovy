package com.cfnstacks.gradle

import org.gradle.api.Project

class ArtifactS3PluginExtension {

    String group = ""

    String profileName = ""

    String repo = ""

    String groupSetting = ""

    String profileNameSetting = ""

    String repoSetting = ""

    // Look in a variety of locations to determine a setting
    // ---
    // $HOME/.gradle/gradle.properties
    // build.gradle
    // environment variables
    //
    def settings(Project project) {

        groupSetting = getProp([
                System.properties['artifacts3.group'],
                project.artifacts3.group,
                System.getenv('ARTIFACTS3_GROUP')
        ])

        profileNameSetting = getProp([
                System.properties['artifacts3.profileName'],
                project.artifacts3.profileName,
                System.getenv('ARTIFACTS3_PROFILENAME')])

        repoSetting = getProp([
                System.properties['artifacts3.repo'],
                project.artifacts3.repo,
                System.getenv('ARTIFACTS3_REPO')
        ])
    }

    static String getProp(valueArray) {
        def r = ''
        valueArray.each({ if(it) { r = it }})
        return r
    }
}