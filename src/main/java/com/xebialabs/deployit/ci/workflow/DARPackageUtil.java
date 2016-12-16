package com.xebialabs.deployit.ci.workflow;

import hudson.EnvVars;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class DARPackageUtil {

    private static final String SEPARATOR = "/";

    private final String artifactDirPath;
    private final String manifestPath;
    private final String packageName;
    private final String packageVersion;
    private final String workspace;
    private final EnvVars envVars;

    public DARPackageUtil(String artifactDirPath, String manifestPath, String packageName, String packageVersion, EnvVars envVars) {
        this.artifactDirPath = artifactDirPath;
        this.manifestPath = manifestPath;
        this.packageName = envVars.expand(packageName);
        this.packageVersion = envVars.expand(packageVersion);
        this.workspace = envVars.get("WORKSPACE");
        this.envVars = envVars;
    }

    public String createPackage() throws IOException {
        String packagePath = outputFilePath();
        replaceEnvVarInManifest();
        try (FileOutputStream fileOutputStream = new FileOutputStream(packagePath);
             ZipOutputStream zipOutputStream = new ZipOutputStream(fileOutputStream)) {
            // Add manifest file
            addEntryToZip("", this.workspace + File.separator + manifestPath, zipOutputStream, false, true);
            // Add artifacts directory
            addFolderToZip("", this.workspace + File.separator + artifactDirPath, zipOutputStream);
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
                FileInputStream in = new FileInputStream(srcFile);
                ZipEntry zipEntry = isManifest ? new ZipEntry(entry.getName()) : new ZipEntry(path + SEPARATOR + entry.getName());
                zip.putNextEntry(zipEntry);
                while ((len = in.read(buf)) > 0) {
                    zip.write(buf, 0, len);
                }
            }
        }
    }

    private String outputFilePath() {
        return new StringBuilder(this.workspace).append(File.separator).append(this.packageName).append("-").append(this.packageVersion).append(".dar").toString();
    }

}