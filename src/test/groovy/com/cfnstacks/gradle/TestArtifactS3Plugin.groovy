package com.cfnstacks.gradle

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Test

import static org.junit.Assert.assertNotNull

class TestArtifactS3Plugin {

    private static final String PROJECT_NAME = "test-project"

    @Test
    void testTasksPresent() throws IOException {
        def project = getProjectWithPluginApplied()
        assertNotNull(project.tasks.build)
        assertNotNull(project.tasks.clean)
        assertNotNull(project.tasks.publish)
    }

    private static Project getProjectWithPluginApplied() {
        Project project = ProjectBuilder.builder().withName(PROJECT_NAME).build()
        project.pluginManager.apply ArtifactS3Plugin
        return project
    }
}