package com.xebialabs.deployit.ci.workflow;

import hudson.EnvVars;
import hudson.remoting.Callable;
import org.jenkinsci.remoting.RoleChecker;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class DARPackageUtil implements Callable<String, IOException> {

    private static final String SEPARATOR = "/";

    private final String artifactsPath;
    private final String manifestPath;
    private final String darPath;
    private final String workspace;
    private final EnvVars envVars;

    public DARPackageUtil(String artifactsPath, String manifestPath, String darPath, EnvVars envVars) {
        this.artifactsPath = artifactsPath;
        this.manifestPath = manifestPath;
        this.darPath = envVars.expand(darPath);
        this.workspace = envVars.get("WORKSPACE");
        this.envVars = envVars;
    }

    public String call() throws IOException {
        replaceEnvVarInManifest();
        String packagePath = outputFilePath();
        File darFile = new File(packagePath);
        darFile.getParentFile().mkdirs();

        try (FileOutputStream fileOutputStream = new FileOutputStream(darFile);
             ZipOutputStream zipOutputStream = new ZipOutputStream(fileOutputStream)) {
            // Add manifest file
            addEntryToZip("", this.workspace + File.separator + manifestPath, zipOutputStream, false, true);
            // Add artifacts directory
            addFolderToZip("", this.workspace + File.separator + artifactsPath, zipOutputStream);
        }
        return packagePath;
    }

    private void replaceEnvVarInManifest() throws IOException {
        String manifestContent;
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Files.copy(Paths.get(workspace, manifestPath), outputStream);
            manifestContent = new String(outputStream.toByteArray());
        }
        manifestContent = envVars.expand(manifestContent);
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(workspace, manifestPath), StandardOpenOption.TRUNCATE_EXISTING)) {
            writer.append(manifestContent);
        }
    }

    private void addFolderToZip(String path, String srcFolder, ZipOutputStream zip) throws IOException {
        File folder = new File(srcFolder);
        if (folder.list().length == 0) {
            addEntryToZip(path, srcFolder, zip, true, false);
        } else {
            for (String fileName : folder.list()) {
                if (path.equals("")) {
                    addEntryToZip(folder.getName(), srcFolder + File.separator + fileName, zip, false, false);
                } else {
                    addEntryToZip(path + SEPARATOR + folder.getName(), srcFolder + File.separator + fileName, zip, false, false);
                }
            }
        }
    }

    private void addEntryToZip(String path, String srcFile, ZipOutputStream zip, boolean isFolder, boolean isManifest) throws IOException {
        File entry = new File(srcFile);
        if (isFolder == true) {
            zip.putNextEntry(new ZipEntry(path + SEPARATOR + entry.getName() + SEPARATOR));
        } else {
            if (entry.isDirectory()) {
                addFolderToZip(path, srcFile, zip);
            } else {
                byte[] buf = new byte[8192];
                int len;
                ZipEntry zipEntry = isManifest ? new ZipEntry(entry.getName()) : new ZipEntry(path + SEPARATOR + entry.getName());
                zip.putNextEntry(zipEntry);
                try (FileInputStream in = new FileInputStream(srcFile)) {
                    while ((len = in.read(buf)) > 0) {
                        zip.write(buf, 0, len);
                    }
                }
            }
        }
    }

    private String outputFilePath() {
        return new StringBuilder(this.workspace).append(File.separator).append(this.darPath).toString();
    }

    @Override
    public void checkRoles(RoleChecker checker) throws SecurityException {

    }
}