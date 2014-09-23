package com.xebialabs.deployit.ci.server;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import jenkins.model.Jenkins;

public class PluginFirstClassloaderInvocationHandler implements InvocationHandler {

    private static final Object[] NO_ARGS = {};
    private Object target;

    public PluginFirstClassloaderInvocationHandler(Object target) {
        this.target = target;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (args == null) {
            args = NO_ARGS;
        }

        //Classloader magic required to bootstrap resteasy.
        final Thread currentThread = Thread.currentThread();
        final ClassLoader origClassLoader = currentThread.getContextClassLoader();
        try {
            ClassLoader pluginClassLoader = Jenkins.getInstance().getPluginManager().getPlugin("deployit-plugin").classLoader;
            currentThread.setContextClassLoader(pluginClassLoader);
            return doInvoke(proxy, method, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        } finally {
            currentThread.setContextClassLoader(origClassLoader);
        }
    }

    protected Object doInvoke(Object proxy, Method method, Object[] args) throws Throwable {
        return method.invoke(target, args);
    }
}
