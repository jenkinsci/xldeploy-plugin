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

package com.xebialabs.deployit.booter.remote;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.bind.DatatypeConverter;

import com.google.common.base.Splitter;

import com.xebialabs.deployit.plugin.api.reflect.PropertyDescriptor;
import com.xebialabs.deployit.plugin.api.reflect.PropertyKind;
import com.xebialabs.deployit.plugin.api.reflect.Type;
import com.xebialabs.deployit.plugin.api.udm.ConfigurationItem;
import com.xebialabs.deployit.plugin.api.udm.Property;
import com.xebialabs.deployit.plugin.api.udm.base.BaseConfigurationItem;

import static com.google.common.collect.Sets.newHashSet;

/**
 * Shadow class required until DEPL-4899 is fixed.
 * TODO: Remove it once we upgrade to the remote booter that has support for hidden fields.
 */
public class RemotePropertyDescriptor implements PropertyDescriptor, Serializable {
    static final Field syntheticField = initSyntheticField();
    public static final Splitter SPLITTER = Splitter.on(",").trimResults().omitEmptyStrings();

    private static Field initSyntheticField() {
        try {
            Field f = BaseConfigurationItem.class.getDeclaredField(BaseConfigurationItem.SYNTHETIC_PROPERTIES_FIELD);
            f.setAccessible(true);
            return f;
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException("Could not find the Synthetic property field.");
        }
    }

    private String name;
    private String label;
    private PropertyKind kind;
    private List<String> enumValues = new ArrayList<String>();
    private Type referencedType;
    private String description;
    private String category;
    private String defaultValue;
    private boolean password;
    private boolean required;
    private boolean containment = false;
    private boolean isTransient;
    private Property.Size size;
    private boolean inspectionProperty;
    private boolean requiredInspection;
    private String fqn;
    private String candidateValuesFilter;
    private boolean hidden = false;

    @Override
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public boolean isAsContainment() {
        return containment;
    }

    public void setAsContainment() {
        containment = true;
    }

    @Override
    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    @Override
    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    @Override
    public boolean isPassword() {
        return password;
    }

    public void setPassword() {
        password = true;
    }

    @Override
    public boolean isRequired() {
        return required;
    }

    public void setRequired() {
        required = true;
    }

    @Override
    public Property.Size getSize() {
        return size;
    }

    public void setSize(Property.Size size) {
        this.size = size;
    }

    @Override
    public PropertyKind getKind() {
        return kind;
    }

    public void setKind(PropertyKind kind) {
        this.kind = kind;
    }

    @Override
    public List<String> getEnumValues() {
        return enumValues;
    }

    public void setEnumValues(List<String> enumValues) {
        this.enumValues = enumValues;
    }

    @Override
    public Type getReferencedType() {
        return referencedType;
    }

    public void setReferencedType(Type referencedType) {
        this.referencedType = referencedType;
    }

    @Override
    public Object getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    @Override
    public boolean isTransient() {
        return isTransient;
    }

    public void setTransient() {
        isTransient = true;
    }

    @Override
    public String getCandidateValuesFilter() {
        return candidateValuesFilter;
    }

    public void setCandidateValuesFilter(String candidateValuesFilter) {
        this.candidateValuesFilter = candidateValuesFilter;
    }

    @Override
    public Object get(ConfigurationItem item) {
        return item.getSyntheticProperties().get(name);
    }

    @Override
    public void set(ConfigurationItem item, Object value) {
        // TODO! Conversion
        try {
            Map<String, Object> map = (Map<String, Object>) syntheticField.get(item);
            map.put(name, convertValue(value));
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Could not access Synthetic properties...");
        }
    }

    private Object convertValue(final Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof String) {
            if (kind.equals(PropertyKind.BOOLEAN)) {
                return Boolean.valueOf((String) value);
            }
            if (kind.equals(PropertyKind.INTEGER)) {
                return Integer.valueOf((String) value);
            }
            if (kind.equals(PropertyKind.DATE)) {
                return DatatypeConverter.parseDateTime((String) value).getTime();
            }
            if (kind.equals(PropertyKind.LIST_OF_STRING)) {
                return SPLITTER.splitToList((String) value);
            }
            if (kind.equals(PropertyKind.SET_OF_STRING)) {
                return newHashSet(SPLITTER.splitToList((String) value));
            }
            if (kind.equals(PropertyKind.MAP_STRING_STRING)) {
                return SPLITTER.withKeyValueSeparator(":").split((String) value);
            }
        }

        return value;
    }

    @Override
    public boolean areEqual(ConfigurationItem item, ConfigurationItem other) {
        // TODO implement
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public String getFqn() {
        return fqn;
    }

    public void setFqn(String fqn) {
        this.fqn = fqn;
    }

    @Override
    public boolean isRequiredForInspection() {
        return requiredInspection;
    }

    public void setRequiredInspection() {
        this.requiredInspection = true;
    }

    @Override
    public boolean isHidden() {
        return hidden;
    }

    public void setHidden() {
        this.hidden = true;
    }

    @Override
    public boolean isInspectionProperty() {
        return inspectionProperty;
    }

    public void setInspectionProperty() {
        this.inspectionProperty = true;
    }

    @Override
    public Set<String> getAliases() {
        return newHashSet();
    }
}
