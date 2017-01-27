package com.xebialabs.deployit.ci.workflow;

import hudson.EnvVars;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class DARPackageUtilTest {

    private DARPackageUtil darPackageUtil = new DARPackageUtil("", "", "", new EnvVars());

    @Test
    public void shouldCollectFileNamesFromManifest() {
        String manifestFileContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<udm.DeploymentPackage version=\"2.0\" application=\"rest-o-rant-api\">\n" +
                "    <application />\n" +
                "    <orchestrator />\n" +
                "    <deployables>\n" +
                "        <tomcat.War name=\"/rest-o-rant-api\" file=\"/libs/rest-o-rant-api.war\">\n" +
                "            <tags />\n" +
                "            <scanPlaceholders>false</scanPlaceholders>\n" +
                "        </tomcat.War>\n" +
                "    </deployables>\n" +
                "    <applicationDependencies />\n" +
                "    <dependencyResolution>LATEST</dependencyResolution>\n" +
                "    <undeployDependencies>false</undeployDependencies>\n" +
                "</udm.DeploymentPackage>\n";


        assertFileNames(manifestFileContent, 1, new String[]{"/libs/rest-o-rant-api.war"});
    }

    @Test
    public void shouldCollectAllFilesFromADeployitManifest() {
        String manifestXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<udm.DeploymentPackage version=\"2.0\" application=\"rest-o-rant-api\">\n" +
                "    <deployables>\n" +
                "        <tomcat.War name=\"/rest-o-rant-api\" file=\"/libs/rest-o-rant-api.war\">\n" +
                "        </tomcat.War>\n" +
                "        <file.Folder name=\"/test-folder\" file=\"/libs/test/folder\">\n" +
                "        </file.Folder>\n" +
                "    </deployables>\n" +
                "    <dependencyResolution>LATEST</dependencyResolution>\n" +
                "    <undeployDependencies>false</undeployDependencies>\n" +
                "</udm.DeploymentPackage>\n";
        assertFileNames(manifestXml, 2, new String[]{"/libs/rest-o-rant-api.war", "/libs/test/folder"});
    }

    private void assertFileNames(String manifestFileContent, int expectedLength, String[] fileNames) {
        List<String> filteredFiles = darPackageUtil.filterFiles(manifestFileContent);
        assertEquals(expectedLength, filteredFiles.size());
        int i = 0;
        for (String fileName : fileNames) {
            assertEquals(filteredFiles.get(i++), fileName);
        }
    }

}