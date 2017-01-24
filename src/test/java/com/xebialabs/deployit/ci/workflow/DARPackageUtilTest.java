package com.xebialabs.deployit.ci.workflow;

import hudson.EnvVars;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class DARPackageUtilTest {

    @Test
    public void shouldCollectFileNamesFromManifest() {
        String manifestFileContent ="<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
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

        DARPackageUtil darPackageUtil = new DARPackageUtil("", "", "", new EnvVars());
        List<String> filteredFiles = darPackageUtil.filterFiles(manifestFileContent);
        assertEquals(1, filteredFiles.size());
        assertEquals(filteredFiles.get(0), "/libs/rest-o-rant-api.war");
    }

}