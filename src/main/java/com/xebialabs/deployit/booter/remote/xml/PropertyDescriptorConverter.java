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

package com.xebialabs.deployit.booter.remote.xml;

import java.util.List;

import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;

import com.xebialabs.deployit.booter.remote.DeployitCommunicator;
import com.xebialabs.deployit.booter.remote.RemoteBooter;
import com.xebialabs.deployit.booter.remote.RemotePropertyDescriptor;
import com.xebialabs.deployit.plugin.api.reflect.PropertyDescriptor;
import com.xebialabs.deployit.plugin.api.reflect.PropertyKind;
import com.xebialabs.deployit.plugin.api.udm.Property;
import com.xebialabs.xltype.serialization.xstream.XStreamProvider;

import static com.xebialabs.xltype.serialization.xstream.Converters.readList;

/**
 * Shadow class required until DEPL-4899 is fixed.
 * TODO: Remove it once we upgrade to the remote booter that has support for hidden fields.
 */
@XStreamProvider(tagName = "property-descriptor", readable = PropertyDescriptor.class)
public class PropertyDescriptorConverter implements Converter {
    @Override
    public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
        throw new IllegalStateException("Cannot serialize PropertyDescriptor from remote-booter");
    }

    @Override
    public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
        String booterConfigKey = (String) context.get("BOOTER_CONFIG");
        DeployitCommunicator communicator = RemoteBooter.getCommunicator(booterConfigKey);

        RemotePropertyDescriptor pd = new RemotePropertyDescriptor();
        setAttributes(pd, reader);
        while (reader.hasMoreChildren()) {
            reader.moveDown();
            if ("referencedType".equals(reader.getNodeName())) {
                pd.setReferencedType(communicator.getType(reader.getValue()));
            } else if ("enumValues".equals(reader.getNodeName())) {
                reader.moveDown();
                List<String> strings = readList(pd, String.class, reader, context);
                reader.moveUp();
                pd.setEnumValues(strings);
            }
            reader.moveUp();
        }
        return pd;
    }

    private void setAttributes(RemotePropertyDescriptor pd, HierarchicalStreamReader reader) {
        pd.setName(reader.getAttribute("name"));
        pd.setFqn(reader.getAttribute("fqn"));
        pd.setLabel(reader.getAttribute("label"));
        pd.setKind(PropertyKind.valueOf(reader.getAttribute("kind")));
        pd.setDescription(reader.getAttribute("description"));
        pd.setCategory(reader.getAttribute("category"));
        pd.setCandidateValuesFilter(reader.getAttribute("candidateValuesFilter"));

        setDefaultValue(pd, reader);

        if ("true".equalsIgnoreCase(reader.getAttribute("hidden"))) {
            pd.setHidden();
        }
        if ("true".equalsIgnoreCase(reader.getAttribute("asContainment"))) {
            pd.setAsContainment();
        }
        if ("true".equalsIgnoreCase(reader.getAttribute("required"))) {
            pd.setRequired();
        }
        if ("true".equalsIgnoreCase(reader.getAttribute("inspection"))) {
            pd.setInspectionProperty();
        }
        if ("true".equalsIgnoreCase(reader.getAttribute("requiredInspection"))) {
            pd.setRequiredInspection();
        }
        if ("true".equalsIgnoreCase(reader.getAttribute("password"))) {
            pd.setPassword();
        }
        if ("true".equalsIgnoreCase(reader.getAttribute("transient"))) {
            pd.setTransient();
        }
        if (null != reader.getAttribute("size")) {
            pd.setSize(Property.Size.valueOf(reader.getAttribute("size")));
        }
    }

    private void setDefaultValue(RemotePropertyDescriptor pd, HierarchicalStreamReader reader) {
        String defaultValue = null;
        defaultValue = reader.getAttribute("default");
        switch (pd.getKind()) {
            case BOOLEAN:
                if (null == defaultValue) {
                    defaultValue = "false";
                }
                break;
            // INTEGER: not sure if 0 is sensible and expected default
        }
        if (defaultValue != null) {
            pd.setDefaultValue(defaultValue);
        }
    }

    @Override
    public boolean canConvert(Class type) {
        return PropertyDescriptor.class.equals(type);
    }
}
