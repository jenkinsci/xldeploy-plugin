/**
 * Copyright (c) 2014, XebiaLabs B.V., All rights reserved.
 *
 *
 * The XL Deploy plugin for Jenkins is licensed under the terms of the GPLv2
 * <http://www.gnu.org/licenses/old-licenses/gpl-2.0.html>, like most XebiaLabs Libraries.
 * There are special exceptions to the terms and conditions of the GPLv2 as it is applied to
 * this software, see the FLOSS License Exception
 * <https://github.com/jenkinsci/deployit-plugin/blob/master/LICENSE>.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation; version 2
 * of the License.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this
 * program; if not, write to the Free Software Foundation, Inc., 51 Franklin St, Fifth
 * Floor, Boston, MA 02110-1301  USA
 */

package com.xebialabs.deployit.ci;

import com.xebialabs.deployit.ci.util.JenkinsDeploymentListener;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.remoting.VirtualChannel;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.File;

import static java.util.Arrays.asList;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.when;

public class FileSystemLocationTest {

    @Mock
    private JenkinsDeploymentListener listener;

    @Mock
    private VirtualChannel channel;

    private FilePath remoteFilePath, localFilePath;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        remoteFilePath = new FilePath(channel, "./build/resources/test");
        localFilePath = new FilePath(new File("./build/resources/test"));
    }

    @Test
    public void shouldReturnPathWithoutChangesIfLocal() {
        FileSystemLocation fileSystemLocation = new FileSystemLocation("test.dar","./build/resources/test");
        assertThat(fileSystemLocation.getDarFileLocation(localFilePath, listener, new EnvVars()), is("build/resources/test/test.dar"));
    }

    @Test
    public void shouldReturnLocalTempFileWhenWorkspaceIsRemote() throws Throwable {
        // arrange
        when(channel.call(Mockito.<hudson.remoting.Callable<Object, Throwable>>any())).thenReturn(asList("test.dar"));
        FileSystemLocation fileSystemLocation = new FileSystemLocation("test.dar","./build/resources/test");

        // act
        String localDarLocation = fileSystemLocation.getDarFileLocation(remoteFilePath, listener, new EnvVars());

        // assert
        assertThat(localDarLocation, not("/build/resources/test/test.dar"));
    }

    @Test
    public void shouldResolveEnvVarInPath() throws Exception {
        EnvVars envVars = new EnvVars();
        envVars.put("NAME","test");
        FileSystemLocation fileSystemLocation = new FileSystemLocation("$NAME.dar","./build/resources/test");
        assertThat(fileSystemLocation.getDarFileLocation(localFilePath, listener, envVars), is("build/resources/test/test.dar"));

    }
}
