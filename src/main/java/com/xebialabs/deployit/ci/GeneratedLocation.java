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

import java.io.File;
import java.io.IOException;
import org.kohsuke.stapler.DataBoundConstructor;
import com.google.common.io.Files;

import com.xebialabs.deployit.ci.util.JenkinsDeploymentListener;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;

import static com.google.common.base.Preconditions.checkNotNull;

public class GeneratedLocation extends ImportLocation {

    private File localTempDir;
    private FilePath localTempDar;

    @DataBoundConstructor
    public GeneratedLocation() {
    }

    /**
     * For local workspace just returns the path;
     * For remote workspace - copies dar file into local temporary location first,
     * then returns temporary path. FilePath.cleanup() method should be used to delete all temporary files.
     */
    @Override
    public String getDarFileLocation(FilePath workspace, JenkinsDeploymentListener deploymentListener, EnvVars envVars) {
        checkNotNull(generatedLocation, "The package has not been generated");

        if (!workspace.isRemote()) {
            return generatedLocation;
        }

        FilePath remoteDar = new FilePath(workspace.getChannel(), generatedLocation);
        localTempDir = Files.createTempDir();
        localTempDar = new FilePath(new File(localTempDir, remoteDar.getName()));
        try {
            remoteDar.copyTo(localTempDar);
        } catch (Exception e) {
            String msg = String.format("Unable to copy remote dar '%s' to local temp directory '%s'.", remoteDar, localTempDar);
            throw new DeployitPluginException(msg, e);
        }

        return localTempDar.getRemote();
    }

    @Extension
    public static final class DescriptorImpl extends ImportLocationDescriptor {
        @Override
        public String getDisplayName() {
            return " Generated";
        }
    }

    @Override
    public void cleanup() {
        try {
            if (localTempDar != null && localTempDar.exists())
                localTempDar.delete();
            if (localTempDir != null && localTempDir.exists())
                localTempDir.delete();
        } catch (IOException e) {
             //ignore
        } catch (InterruptedException e) {
            //ignore
        }
    }
}
