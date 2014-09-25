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

package com.xebialabs.xltype.serialization.xstream;

import hudson.PluginFirstClassLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;

import org.jboss.resteasy.util.Types;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.reflection.ReflectionConverter;
import com.thoughtworks.xstream.core.MapBackedDataHolder;
import com.thoughtworks.xstream.io.xml.XmlFriendlyNameCoder;
import com.thoughtworks.xstream.io.xml.XppDriver;

import com.xebialabs.deployit.plugin.api.reflect.Descriptor;
import com.xebialabs.deployit.plugin.api.reflect.DescriptorRegistry;
import com.xebialabs.deployit.plugin.api.udm.ConfigurationItem;

import nl.javadude.scannit.Scannit;

/**
 * This provider shadows {@link XStreamReaderWriter} and should regularly be synced with upstream changes.
 * The major difference is configuration of XStream classloader.
 */
@Provider
@Produces({"application/*+xml", "text/*+xml"})
@Consumes({"application/*+xml", "text/*+xml"})
public class XStreamReaderWriter implements MessageBodyReader<Object>, MessageBodyWriter<Object> {
    public static final XppDriver HIERARCHICAL_STREAM_DRIVER = new XppDriver(new XmlFriendlyNameCoder("_-", "_"));
    private static final XStream xStream = new XStreamWithoutReflectionConverter(HIERARCHICAL_STREAM_DRIVER);
    private static final AtomicReference<List<Converter>> CONVERTERS = new AtomicReference<List<Converter>>(Lists.<Converter>newArrayList());

    public XStreamReaderWriter() {
        logger.debug("Created XStreamReaderWriter");
        init();
    }

    protected void init() {
        Collection<Converter> converters = allConverters();
        for (Converter converter : converters) {
            registerConverter(converter);
        }
        final Thread currentThread = Thread.currentThread();
        final ClassLoader tccl = currentThread.getContextClassLoader();
        if (tccl instanceof PluginFirstClassLoader) {
            getConfiguredXStream().setClassLoader(tccl);
        } else {
            getConfiguredXStream().setClassLoader(this.getClass().getClassLoader());
        }
    }

    public static void registerConverter(Converter converter) {
        xStream.registerConverter(converter);
        XStreamProvider annotation = converter.getClass().getAnnotation(XStreamProvider.class);
        xStream.aliasType(annotation.tagName(), annotation.readable());
        CONVERTERS.get().add(converter);
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
            @Override
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

    @Override
    public boolean isReadable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return isForRegisteredConverter(type, genericType);
    }

    protected boolean isForRegisteredConverter(Class<?> type, Type genericType) {
        if (Collection.class.isAssignableFrom(type) && genericType != null) {
            logger.trace("Is a collection of: {}", genericType);
            Class baseType = Types.getCollectionBaseType(type, genericType);
            return baseType != null && canBeConverted(baseType);
        } else if (Map.class.isAssignableFrom(type) && genericType != null) {
            logger.trace("Is a collection of: {}", genericType);
            Class keyClass = Types.getMapKeyType(genericType);
            Class valueClass = Types.getMapValueType(genericType);
            if (Collection.class.isAssignableFrom(valueClass)) {
                Type valueType = ((ParameterizedType) genericType).getActualTypeArguments()[1];
                Class valueBaseClass = Types.getCollectionBaseType(valueClass, valueType);
                return valueBaseClass != null && keyClass != null && canBeConverted(valueBaseClass) && canBeConverted(keyClass);
            }
            return keyClass != null && canBeConverted(keyClass) && valueClass != null && canBeConverted(valueClass);
        }
        return canBeConverted(type);
    }

    private boolean canBeConverted(Class type) {
        for (Converter converter : CONVERTERS.get()) {
            if (converter.canConvert(type)) {
                return true;
            }
        }
        // Default types
        boolean canConvert = Arrays.asList(String.class, boolean.class, Boolean.class, int.class, Integer.class).contains(type);
        return canConvert;
    }

    @Override
    public Object readFrom(Class<Object> type, Type genericType, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, String> httpHeaders, InputStream entityStream) throws IOException, WebApplicationException {
        logger.trace("Reading {}", genericType);
        return read(entityStream, type, httpHeaders.getFirst("BOOTER_CONFIG"));
    }

    private Object read(InputStream entityStream, Class<Object> type, String booterConfigKey) {
        MapBackedDataHolder dataHolder = new MapBackedDataHolder();
        dataHolder.put("BOOTER_CONFIG", booterConfigKey);
        return xStream.unmarshal(HIERARCHICAL_STREAM_DRIVER.createReader(entityStream), null, dataHolder);
    }

    private static final Logger logger = LoggerFactory.getLogger(XStreamReaderWriter.class);

    @Override
    public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        logger.trace("Checking writeable: {} - {}", type, genericType);
        return isForRegisteredConverter(type, genericType);
    }

    @Override
    public long getSize(Object o, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return -1;
    }

    @Override
    public void writeTo(Object o, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream) throws IOException, WebApplicationException {
        logger.trace("Writing {}", genericType);
        xStream.toXML(o, new OutputStreamWriter(entityStream, "UTF-8"));
    }

    public static XStream getConfiguredXStream() {
        return xStream;
    }


    static class XStreamWithoutReflectionConverter extends XStream {

        public XStreamWithoutReflectionConverter(final XppDriver hierarchicalStreamDriver) {
            super(hierarchicalStreamDriver);
        }

        @Override
        public void registerConverter(Converter converter, int priority) {
            if(!(converter instanceof ReflectionConverter)){
                super.registerConverter(converter, priority);
            }
        }
    }

}

