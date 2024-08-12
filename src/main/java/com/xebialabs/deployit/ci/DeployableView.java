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
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

import com.xebialabs.deployit.ci.server.DeployitDescriptorRegistry;
import com.xebialabs.deployit.ci.util.JenkinsDeploymentListener;
import com.xebialabs.deployit.plugin.api.udm.ConfigurationItem;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.Describable;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.base.Strings.nullToEmpty;
import static com.google.common.collect.Sets.newHashSet;
import static com.xebialabs.deployit.ci.util.Strings2.commaSeparatedListToList;
import static com.xebialabs.deployit.ci.util.Strings2.stripEnclosingQuotes;

public abstract class DeployableView implements Describable<DeployableView> {

    public String type;
    public String name;
    public String tags;
    public List<NameValuePair> properties;

    protected DeployableView(String type, String name, String tags, List<NameValuePair> properties) {
        this.type = type;
        this.name = name;
        this.tags = tags;
        this.properties = properties;
    }

    public String getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public String getFullyQualifiedName() {
        return name;
    }

    public ConfigurationItem toConfigurationItem(DeployitDescriptorRegistry registry, FilePath workspace, EnvVars envVars, JenkinsDeploymentListener listener) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(getName()), "Name is required.");
        ConfigurationItem deployable = registry.newInstance(type, envVars.expand(getName()));
        if (!isNullOrEmpty(tags)) {
            String resolvedTags = envVars.expand(tags);
            deployable.setProperty("tags", newHashSet(commaSeparatedListToList(resolvedTags)));
        }

        if (properties != null) {
            for (NameValuePair pair : properties) {
                String value = stripEnclosingQuotes(nullToEmpty(pair.propertyValue));
                value = envVars.expand(value);
                registry.setProperty(deployable, pair.propertyName, value);
            }
        }

        return deployable;
    }

    public Descriptor<DeployableView> getDescriptor() {
        return Jenkins.getInstance().getDescriptorOrDie(getClass());
    }
}
