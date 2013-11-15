/**
 * Copyright (c) 2013, XebiaLabs B.V., All rights reserved.
 *
 *
 * The Deployit plugin for Jenkins is licensed under the terms of the GPLv2
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
import java.util.Map;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import com.xebialabs.deployit.ci.util.DeployitTypes;
import com.xebialabs.deployit.ci.util.JenkinsDeploymentListener;
import com.xebialabs.deployit.plugin.api.udm.Application;
import com.xebialabs.deployit.plugin.api.udm.ConfigurationItem;
import com.xebialabs.deployit.plugin.api.udm.Deployable;
import com.xebialabs.deployit.plugin.api.udm.DeploymentPackage;

import hudson.DescriptorExtensionList;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Describable;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

import static com.google.common.collect.Maps.newHashMap;

public class JenkinsPackageOptions implements Describable<JenkinsPackageOptions> {

    private final List<DeployableView> deployables;

    @DataBoundConstructor
    public JenkinsPackageOptions(List<DeployableView> deployables) {
        this.deployables = deployables;
    }

    public Descriptor<JenkinsPackageOptions> getDescriptor() {
        return Jenkins.getInstance().getDescriptorOrDie(getClass());
    }

    public List<DeployableView> getDeployables() {
        return deployables;
    }

    public DeploymentPackage toDeploymentPackage(String applicationName, String version, DeployitTypes deployitTypes, FilePath workspace, EnvVars envVars, JenkinsDeploymentListener listener) {
        Application application = deployitTypes.newInstance(Application.class, applicationName);
        DeploymentPackage deploymentPackage = deployitTypes.newInstance(DeploymentPackage.class, version);
        deploymentPackage.setApplication(application);
        Map<String,ConfigurationItem> deployablesByFqn = newHashMap();
        for (DeployableView deployableView : deployables) {
            ConfigurationItem deployable = deployableView.toConfigurationItem(deployitTypes, workspace, envVars, listener);
            if (deployableView instanceof  EmbeddedView) {
                linkEmbeddedToParent(deployablesByFqn, deployable, deployableView, deployitTypes, listener);
            } else {
                deploymentPackage.addDeployable((Deployable) deployable);
            }
            deployablesByFqn.put(deployableView.getFullyQualifiedName(), deployable);
        }
        deploymentPackage.setProperty("deployables",deploymentPackage.getDeployables());
        return deploymentPackage;
    }


    private void linkEmbeddedToParent(Map<String, ConfigurationItem> deployablesByFqn, ConfigurationItem deployable, final DeployableView deployableView,DeployitTypes deployitTypes, JenkinsDeploymentListener listener) {
        final EmbeddedView embeddedView = (EmbeddedView)deployableView;
        ConfigurationItem parent = deployablesByFqn.get(embeddedView.getParentName());
        if (parent == null) {
            listener.error("Failed to find parent [" + embeddedView.getParentName() + "] that embeds [" + deployable + "]");
            throw new RuntimeException("Failed to find parent that embeds " + deployable);
        }
        deployitTypes.addEmbedded(parent, deployable);
    }



    @Extension
    public static final class DescriptorImpl extends Descriptor<JenkinsPackageOptions> {
        @Override
        public String getDisplayName() {
            return "JenkinsPackageOptions";
        }

        /**
         * Returns all the registered {@link DeployableView} descriptors.
         */
        public DescriptorExtensionList<DeployableView, DeployableViewDescriptor> deployables() {
            return Jenkins.getInstance().<DeployableView, DeployableViewDescriptor>getDescriptorList(DeployableView.class);
        }

        @Override
        public JenkinsPackageOptions newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return super.newInstance(req, formData);
        }
    }
}
