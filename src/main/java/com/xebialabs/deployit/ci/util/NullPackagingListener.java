package com.xebialabs.deployit.ci.util;

import java.io.Serializable;

import com.xebialabs.deployit.engine.packager.content.PackagingListener;

public class NullPackagingListener implements PackagingListener, Serializable {

    public void println(final String message) {}

}
