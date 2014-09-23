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

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import com.xebialabs.deployit.ci.server.DeployitDescriptorRegistry;
import com.xebialabs.deployit.ci.util.JenkinsDeploymentListener;
import com.xebialabs.deployit.plugin.api.udm.Application;
import com.xebialabs.deployit.plugin.api.udm.ConfigurationItem;
import com.xebialabs.deployit.plugin.api.udm.Deployable;
import com.xebialabs.deployit.plugin.api.udm.DeploymentPackage;
import com.xebialabs.deployit.plugin.api.udm.base.BaseConfigurationItem;

import hudson.DescriptorExtensionList;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Describable;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import static com.google.common.base.Strings.nullToEmpty;
import static com.google.common.collect.Maps.newHashMap;
import static com.xebialabs.deployit.ci.util.Strings2.stripEnclosingQuotes;

public class JenkinsPackageOptions implements Describable<JenkinsPackageOptions> {

    private final List<DeployableView> deployables;

    @DataBoundConstructor
    public JenkinsPackageOptions(List<DeployableView> deployables) {
        this.deployables = deployables;
    }

    @Override
    public Descriptor<JenkinsPackageOptions> getDescriptor() {
        return Jenkins.getInstance().getDescriptorOrDie(getClass());
    }

    public List<DeployableView> getDeployables() {
        return deployables;
    }

    public DeploymentPackage toDeploymentPackage(String applicationName, String version, List<? extends NameValuePair> packageProperties, DeployitDescriptorRegistry registry, FilePath workspace, EnvVars envVars, JenkinsDeploymentListener listener) {
        Application application = registry.newInstance(Application.class, applicationName);
        DeploymentPackage deploymentPackage = registry.newInstance(DeploymentPackage.class, version);
        deploymentPackage.setApplication(application);
        Map<String,ConfigurationItem> deployablesByFqn = newHashMap();
        if (null == deployables) {
            String msg = String.format("No deployables defined for deployment package. Application: '%s'.", applicationName);
            throw new DeployitPluginException(msg);
        }
        List<DeployableView> sortedDeployables = sortDeployables(deployables);
        for (DeployableView deployableView : sortedDeployables) {
            ConfigurationItem deployable = deployableView.toConfigurationItem(registry, workspace, envVars, listener);
            if (deployableView instanceof EmbeddedView) {
                linkEmbeddedToParent(deployablesByFqn, deployable, (EmbeddedView)deployableView, registry, listener);
            } else {
                deploymentPackage.addDeployable((Deployable) deployable);
            }
            deployablesByFqn.put(deployableView.getFullyQualifiedName(), deployable);
        }
        deploymentPackage.setProperty("deployables", deploymentPackage.getDeployables());

        if (packageProperties != null) {
            for (NameValuePair pair : packageProperties) {
                String value = stripEnclosingQuotes(nullToEmpty(pair.propertyValue));
                value = envVars.expand(value);
                registry.setProperty(deploymentPackage, pair.propertyName, value);
            }
        }
        return deploymentPackage;
    }

    private List<DeployableView> sortDeployables(List<DeployableView> deployables) {
        List<DeployableView> result = Lists.newArrayList(Iterables.filter(deployables, Predicates.not(Predicates.instanceOf(EmbeddedView.class))));
        List<EmbeddedView> embeddeds = /* double cast: dirty but quick */
                (List<EmbeddedView>)(Object)Lists.newArrayList(Iterables.filter(deployables, Predicates.instanceOf(EmbeddedView.class)));
        // sort the embeddeds on the number of /'s in the parent name
        Collections.sort(embeddeds, new Comparator<EmbeddedView>() {
            @Override
            public int compare(EmbeddedView o1, EmbeddedView o2) {
                return o1.getParentName().split("/").length - o2.getParentName().split("/").length;
            }
        });
        result.addAll(embeddeds);
        return result;
    }

    private void linkEmbeddedToParent(Map<String, ConfigurationItem> deployablesByFqn, ConfigurationItem deployable, final EmbeddedView embeddedView,DeployitDescriptorRegistry registry, JenkinsDeploymentListener listener) {
        ConfigurationItem parent = deployablesByFqn.get(embeddedView.getParentName());
        if (parent == null) {
            listener.error("Failed to find parent [" + embeddedView.getParentName() + "] that embeds [" + deployable + "]");
            throw new DeployitPluginException("Failed to find parent that embeds " + deployable);
        }
        registry.addEmbedded(parent, deployable);
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
