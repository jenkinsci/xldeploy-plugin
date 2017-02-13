/**
 * Copyright (c) 2014, XebiaLabs B.V., All rights reserved.
 * <p>
 * <p>
 * The XL Release plugin for Jenkins is licensed under the terms of the GPLv2
 * <http://www.gnu.org/licenses/old-licenses/gpl-2.0.html>, like most XebiaLabs Libraries.
 * There are special exceptions to the terms and conditions of the GPLv2 as it is applied to
 * this software, see the FLOSS License Exception
 * <https://github.com/jenkinsci/xlrelease-plugin/blob/master/LICENSE>.
 * <p>
 * This program is free software; you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation; version 2
 * of the License.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with this
 * program; if not, write to the Free Software Foundation, Inc., 51 Franklin St, Fifth
 * Floor, Boston, MA 02110-1301  USA
 */

package com.xebialabs.deployit.ci;

import hudson.model.Result;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class XLDeployJenkinsFileITest {

    private static final String ADMIN_CREDENTIAL = "admin_credential";

    private static final String ENV_XLD_HOST = "xlDeployIntegration.host";
    private static final String ENV_XLD_USERNAME = "xlDeployIntegration.username";
    private static final String ENV_XLD_PASSWORD = "xlDeployIntegration.password";

    private static final String DEFAULT_HOST = "http://localhost:4516";
    private static final String DEFAULT_USERNAME = "admin";
    private static final String DEFAULT_PASSWORD = "admin";

    private static String host;
    private static String username;
    private static String password;

    private static final Logger logger = LoggerFactory.getLogger(XLDeployJenkinsFileITest.class);
    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    @Before
    public void before() {
        host = System.getProperty(ENV_XLD_HOST) != null ? System.getProperty(ENV_XLD_HOST) : DEFAULT_HOST;
        username = System.getProperty(ENV_XLD_USERNAME) != null ? System.getProperty(ENV_XLD_USERNAME) : DEFAULT_USERNAME;
        password = System.getProperty(ENV_XLD_PASSWORD) != null ? System.getProperty(ENV_XLD_PASSWORD) : DEFAULT_PASSWORD;
    }

    @Test
    @LocalData
    public void shouldStartReleaseWithJenkinsFile() throws Exception {
        WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "rest-o-rant-api");
        job.setDefinition(new CpsFlowDefinition(getJenkinsFileScript("Jenkinsfile"), true));
        jenkins.assertBuildStatus(Result.SUCCESS, job.scheduleBuild2(0).get());
    }
    
    @Test
    @LocalData
    public void shouldStartReleaseWithJenkinsFileOverridingDeployCredentials() throws Exception {
        WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "rest-o-rant-api-override-deploy-creds");
        job.setDefinition(new CpsFlowDefinition(getJenkinsFileScript("Jenkinsfile-deployOverriding"), true));
        jenkins.assertBuildStatus(Result.SUCCESS, job.scheduleBuild2(0).get());
    }


    private String getJenkinsFileScript(String fileName) throws IOException {
        String jenkinsFile = "";

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            Files.copy(Paths.get(getClass().getClassLoader().getResource(fileName).getFile()), outputStream);
            jenkinsFile = new String(outputStream.toByteArray());
        }
        return jenkinsFile;
    }

}
