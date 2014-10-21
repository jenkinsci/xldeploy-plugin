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
import org.kohsuke.stapler.DataBoundConstructor;

import com.xebialabs.deployit.ci.util.JenkinsDeploymentListener;

import hudson.DescriptorExtensionList;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Describable;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;

public class JenkinsImportOptions implements Describable<JenkinsImportOptions> {

    public final ImportLocation mode;

    @DataBoundConstructor
    public JenkinsImportOptions(ImportLocation mode) {
        this.mode = mode;
    }

    public Descriptor<JenkinsImportOptions> getDescriptor() {
        return Jenkins.getInstance().getDescriptorOrDie(getClass());
    }

    public String getDarFileLocation(FilePath workspace, JenkinsDeploymentListener deploymentListener, EnvVars envVars) {
        return mode.getDarFileLocation(workspace, deploymentListener, envVars);
    }

    public void setGeneratedDarLocation(String generatedDarLocation) {
        mode.setGeneratedLocation(generatedDarLocation);
    }

    public ImportLocation getMode() {
        return mode;
    }

    @Extension
    public static final class DescriptorImpl extends Descriptor<JenkinsImportOptions> {

        @Override
        public String getDisplayName() {
            return "JenkinsImportOptions";
        }

        public DescriptorExtensionList<ImportLocation, Descriptor<ImportLocation>> getLocationDescriptors() {
            return Jenkins.getInstance().<ImportLocation, Descriptor<ImportLocation>>getDescriptorList(ImportLocation.class);
        }
    }
}
