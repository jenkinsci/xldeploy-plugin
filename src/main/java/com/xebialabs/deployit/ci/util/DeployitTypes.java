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

package com.xebialabs.deployit.ci.util;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.xebialabs.deployit.client.Descriptors;
import com.xebialabs.deployit.plugin.api.reflect.Descriptor;
import com.xebialabs.deployit.plugin.api.reflect.PropertyDescriptor;

import java.util.Collection;
import java.util.Map;

public class DeployitTypes {
    private final Map<String, Descriptor> descriptors;

    public DeployitTypes(Descriptors descriptorList) {
        Builder<String, Descriptor> typesToDescriptors = ImmutableMap.builder();
        for (Descriptor descriptor : descriptorList.getDescriptors()) {
            typesToDescriptors.put(descriptor.getType().toString(), descriptor);
        }
        descriptors = typesToDescriptors.build();
    }

    public Descriptor getDescriptor(String type) {
        return descriptors.get(type);
    }

    public Collection<PropertyDescriptor> getPropertyDescriptors(String type) {
        return getDescriptor(type).getPropertyDescriptors();
    }
}