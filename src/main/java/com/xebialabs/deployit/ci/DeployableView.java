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

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import com.google.common.base.Objects;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.common.collect.Iterables;

import com.xebialabs.deployit.ci.util.DeployitTypes;
import com.xebialabs.deployit.ci.util.JenkinsDeploymentListener;
import com.xebialabs.deployit.engine.packager.content.DarMember;
import com.xebialabs.deployit.plugin.api.reflect.PropertyDescriptor;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.Describable;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.base.Strings.nullToEmpty;
import static com.xebialabs.deployit.ci.util.Strings2.commaSeparatedListToList;
import static com.xebialabs.deployit.ci.util.Strings2.stripEnclosingQuotes;
import static com.xebialabs.deployit.ci.util.Strings2.uriQueryStringToMap;

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

    public DarMember newDarMember(DeployitTypes deployitTypes, FilePath workspace, EnvVars envVars, JenkinsDeploymentListener listener) {
        final DarMember deployable = new DarMember(name, type);
        if (!isNullOrEmpty(tags))
            deployable.getTags().addAll(commaSeparatedListToList(tags));
        deployable.getValues().putAll(getMapOfProperties(deployitTypes.getPropertyDescriptors(type), envVars));
        return deployable;
    }

    public Descriptor<DeployableView> getDescriptor() {
        return Jenkins.getInstance().getDescriptorOrDie(getClass());
    }

    private Map<String, String> getMapOfProperties(Collection<PropertyDescriptor> propertyDescriptors,
            EnvVars envVars) {
        if (properties == null)
            return Collections.emptyMap();

        ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
        for (NameValuePair pair : properties) {
            // strip '"' characters Jenkins is fond of adding
            String value = stripEnclosingQuotes(nullToEmpty(pair.propertyValue));
            addManifestEntries(builder, getPropertyDescriptor(propertyDescriptors, pair.propertyName),
                    pair.propertyName, envVars.expand(value));
        }
        return builder.build();
    }

    private static PropertyDescriptor getPropertyDescriptor(
            Collection<PropertyDescriptor> propertyDescriptors,
            final String propertyName) {
        return Iterables.find(propertyDescriptors, new Predicate<PropertyDescriptor>() {
                public boolean apply(PropertyDescriptor input) {
                    return Objects.equal(input.getName(), propertyName);
                }
            });
    }

    private static void addManifestEntries(Builder<String, String> entries,
                                           PropertyDescriptor propertyDescriptor, String propertyName, String value) {
        if (propertyDescriptor.isHidden()) {
            return;
        }

        if (value == null) {
            return;
        }

        switch (propertyDescriptor.getKind()) {
        case BOOLEAN:
        case INTEGER:
        case STRING:
        case ENUM:
            entries.put(propertyName, value);
            break;
        case CI:
            // assuming the CI is an item in the same package
            entries.put(propertyName, value);
            break;
        case SET_OF_STRING:
        case LIST_OF_STRING:
        case SET_OF_CI:
        case LIST_OF_CI:
            int entryCount = 1;
            for (String entry : commaSeparatedListToList(value)) {
                String attributeName = propertyName + "-EntryValue-" + entryCount++;
                entries.put(attributeName, entry);
            }
            break;
        case MAP_STRING_STRING:
            for (Entry<String, String> entry : uriQueryStringToMap(value).entrySet()) {
                String attributeName = propertyName + "-" + entry.getKey();
                entries.put(attributeName, entry.getValue());
            }
            break;
        }
    }
}
