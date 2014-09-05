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

import java.util.List;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import com.xebialabs.deployit.ci.server.DeployitDescriptorRegistry;
import com.xebialabs.deployit.ci.server.DeployitServerFactory;

import hudson.Extension;
import hudson.RelativePath;
import hudson.model.AbstractProject;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.TaskListener;
import hudson.util.ComboBoxModel;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.xebialabs.deployit.ci.util.ListBoxModels.emptyModel;
import static com.xebialabs.deployit.ci.util.ListBoxModels.of;
import static hudson.util.FormValidation.ok;
import static hudson.util.FormValidation.warning;


public class JenkinsDeploymentOptions implements Describable<JenkinsDeploymentOptions> {

    public String environment = "Environments/";

    public boolean generateDeployedOnUpgrade;
    public boolean skipMode;
    public boolean testMode;
    public boolean rollbackOnError;

    public final VersionKind versionKind;
    public String version;

    @DataBoundConstructor
    public JenkinsDeploymentOptions(String environment, VersionKind versionKind, boolean generateDeployedOnUpgrade, boolean skipMode, boolean testMode, boolean rollbackOnError) {
        this.generateDeployedOnUpgrade = generateDeployedOnUpgrade;
        this.skipMode = skipMode;
        this.testMode = testMode;
        this.rollbackOnError = rollbackOnError;
        this.environment = environment;
        this.versionKind = versionKind;
    }

    public Descriptor<JenkinsDeploymentOptions> getDescriptor() {
        return Jenkins.getInstance().getDescriptorOrDie(getClass());
    }

    @Extension
    public static final class DescriptorImpl extends Descriptor<JenkinsDeploymentOptions> {
        @Override
        public String getDisplayName() {
            return "DeploymentOptions";
        }

        public ComboBoxModel doFillEnvironmentItems(@QueryParameter(value = "credential") @RelativePath(value = "..") String credential, @QueryParameter(value = "credential") String credential2) {
            credential = !isNullOrEmpty(credential) ? credential : credential2;
            DeployitNotifier.DeployitDescriptor deployitDescriptor = getDeployitDescriptor();
            return isNullOrEmpty(credential) ? new ComboBoxModel() : new ComboBoxModel(deployitDescriptor.environments(credential));
        }

        public FormValidation doCheckEnvironment(@QueryParameter(value = "credential") @RelativePath(value = "..") String credential, @QueryParameter(value = "credential") String credential2, @QueryParameter final String value, @AncestorInPath AbstractProject project) {
            if (isNullOrEmpty(value) || "Environments/".equals(value))
                return ok("Fill in the target environment ID, eg Environments/MyEnv");

            credential = !isNullOrEmpty(credential) ? credential : credential2;

            DeployitNotifier.DeployitDescriptor deployitDescriptor = getDeployitDescriptor();
            String resolvedValue = deployitDescriptor.expandValue(value, project);
            final String environment = DeployitServerFactory.getNameFromId(resolvedValue).trim();

            List<String> candidates = deployitDescriptor.getDeployitServer(credential).search(DeployitDescriptorRegistry.UDM_ENVIRONMENT, environment);

            if(candidates.isEmpty()) {
                return warning("Environment '%s' does not exist, please ensure it exists during deployment", environment);
            }

            return ok();
        }

        protected DeployitNotifier.DeployitDescriptor getDeployitDescriptor() {
            return (DeployitNotifier.DeployitDescriptor) Jenkins.getInstance().getDescriptorOrDie(DeployitNotifier.class);
        }
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }
}
