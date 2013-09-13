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

package com.xebialabs.deployit.engine.xml;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;

import nl.javadude.scannit.Scannit;

import org.jboss.resteasy.util.Types;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.Collections2;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.io.xml.XmlFriendlyReplacer;
import com.thoughtworks.xstream.io.xml.XppDriver;

import com.xebialabs.deployit.plugin.api.reflect.Descriptor;
import com.xebialabs.deployit.plugin.api.reflect.DescriptorRegistry;
import com.xebialabs.deployit.plugin.api.udm.ConfigurationItem;
import com.xebialabs.xltype.serialization.xstream.ConfigurationItemCollectionConverter;
import com.xebialabs.xltype.serialization.xstream.ConfigurationItemMarshallingStrategy;
import com.xebialabs.xltype.serialization.xstream.XStreamProvider;

/**
 * HOTFIX to work with the xstream version in jenkins.
 *
 */
@Provider
@Produces({"application/*+xml", "text/*+xml"})
@Consumes({"application/*+xml", "text/*+xml"})
public class XStreamReaderWriter implements MessageBodyReader<Object>, MessageBodyWriter<Object> {
    private static final XStream xStream = new XStream(new XppDriver(new XmlFriendlyReplacer("_-", "_")));
    private Collection<Converter> converters;

    public XStreamReaderWriter() {
        logger.debug("Created XStreamReaderWriter");
        init();
    }

    protected void init() {
        converters = allConverters();
        for (Converter converter : converters) {
            xStream.registerConverter(converter);
            XStreamProvider annotation = converter.getClass().getAnnotation(XStreamProvider.class);
            xStream.aliasType(annotation.tagName(), annotation.readable());
        }
    }

    /**
     * This is separate, as remote we need to have fetched the descriptors first, before we have the types to initialize
     * XStream with.
     */
    public static void registerConfigurationItemAliases() {
        xStream.registerConverter(new ConfigurationItemCollectionConverter(xStream.getMapper()));
        xStream.setMarshallingStrategy(new ConfigurationItemMarshallingStrategy(XStream.XPATH_RELATIVE_REFERENCES));
        for (Descriptor descriptor : DescriptorRegistry.getDescriptors()) {
            xStream.aliasType(descriptor.getType().toString(), ConfigurationItem.class);
        }
    }

    private Collection<Converter> allConverters() {
        Set<Class<?>> classes = Scannit.getInstance().getTypesAnnotatedWith(XStreamProvider.class);
        return Collections2.transform(classes, new Function<Class<?>, Converter>() {
            public Converter apply(Class<?> input) {
                Preconditions.checkArgument(Converter.class.isAssignableFrom(input));
                return constructConverter(input);
            }
        });
    }

    /**
     * Default implementation is to call the parameterless constructor of the Converter. But can be overridden to look
     * for the correct converter in for instance a Spring context.
     *
     * @param clazz The converter class
     * @return The instantiated converter.
     */
    protected Converter constructConverter(Class<?> clazz) {
        try {
            return (Converter) clazz.newInstance();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public boolean isReadable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return isForRegisteredConverter(type, genericType);
    }

    private boolean isForRegisteredConverter(Class<?> type, Type genericType) {
        if (Collection.class.isAssignableFrom(type) && genericType != null) {
            logger.trace("Is a collection of: {}", genericType);
            Class baseType = Types.getCollectionBaseType(type, genericType);
            return baseType != null && canBeConverted(baseType);
        }
        return canBeConverted(type);
    }

    private boolean canBeConverted(Class type) {
        for (Converter converter : converters) {
            if (converter.canConvert(type)) {
                return true;
            }
        }
        // Default types
        boolean canConvert = Arrays.asList(String.class, boolean.class, Boolean.class, int.class, Integer.class).contains(type);
        return canConvert;
    }

    public Object readFrom(Class<Object> type, Type genericType, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, String> httpHeaders, InputStream entityStream) throws IOException, WebApplicationException {
        logger.trace("Reading {}", genericType);
        return read(entityStream, type);
    }

    private Object read(InputStream entityStream, Class<Object> type) {
        return xStream.fromXML(entityStream);
    }

    private static final Logger logger = LoggerFactory.getLogger(XStreamReaderWriter.class);

    public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        logger.trace("Checking writeable: {} - {}", type, genericType);
        return isForRegisteredConverter(type, genericType);
    }

    public long getSize(Object o, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return -1;
    }

    public void writeTo(Object o, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream) throws IOException, WebApplicationException {
        logger.trace("Writing {}", genericType);
        xStream.toXML(o, entityStream);
    }

    public static XStream getConfiguredXStream() {
        return xStream;
    }
}
