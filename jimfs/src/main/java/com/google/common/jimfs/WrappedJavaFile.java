package com.google.common.jimfs;

import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Wrapper for the {@link java.io.File} class.
 *
 * @author Morgen Peschke
 */
public class WrappedJavaFile extends java.io.File {
    private final JimfsPath parent;

    WrappedJavaFile(JimfsPath parent) {
      super(parent.toString());
      this.parent = parent;
    }

    private UnsupportedOperationException doesNotSupport(String methodName) {
        return new UnsupportedOperationException(methodName + " is not supported for Files created from Jimfs Paths");
    }

    /* -- Path-component accessors -- */

    @Override
    public String getName() {
        Path name = parent.getFileName();
        return name == null ? "" : name.toString();
    }

    @Override
    public String getParent() {
        File parentDir = getParentFile();
        return parentDir == null ? null : parentDir.toString();
    }

    @Override
    public File getParentFile() {
        Path parentDir = parent.getParent();
        return parentDir == null ? null : parentDir.toFile();
    }

    @Override
    public String getPath() {
        return parent.toString();
    }

    /* -- Path operations -- */

    @Override
    public boolean isAbsolute() {
        return parent.isAbsolute();
    }

    @Override
    public String getAbsolutePath() {
        return getAbsoluteFile().getPath();
    }

    @Override
    public File getAbsoluteFile() {
        return parent.toAbsolutePath().toFile();
    }

    @Override
    public String getCanonicalPath() throws IOException {
        return getCanonicalFile().toString();
    }

    @Override
    public File getCanonicalFile() throws IOException {
        return parent.toRealPath().toFile();
    }

    @Override
    public URL toURL() throws MalformedURLException {
        return parent.toUri().toURL();
    }

    @Override
    public URI toURI() {
        return parent.toUri();
    }

    /* -- Attribute accessors -- */

    @Override
    public boolean canRead() {
        return Files.isReadable(parent);
    }

    @Override
    public boolean canWrite() {
        return Files.isWritable(parent);
    }

    @Override
    public boolean exists() {
        return Files.exists(parent);
    }

    @Override
    public boolean isDirectory() {
        return Files.isDirectory(parent);
    }

    @Override
    public boolean isFile() {
        return Files.isRegularFile(parent);
    }

    @Override
    public boolean isHidden() {
        try {
            return Files.isHidden(parent);
        } catch (IOException ex) {
            return false;
        }
    }

    @Override
    public long lastModified() {
        try {
            return Files.getLastModifiedTime(parent).toMillis();
        } catch (IOException ex) {
            return 0L;
        }
    }

    @Override
    public long length() {
        try {
            return Files.size(parent);
        } catch (IOException ex) {
            return 0L;
        }
    }

    /* -- File operations -- */

    @Override
    public boolean createNewFile() throws IOException {
        if (Files.exists(parent)) {
            return false;
        }
        else {
            Files.createFile(parent);
            return true;
        }
    }

    @Override
    public boolean delete() {
        try {
            return Files.deleteIfExists(parent);
        } catch (IOException ex) {
            return false;
        }
    }

    @Override
    public void deleteOnExit() {
        throw doesNotSupport("deleteOnExit");
    }

    @Override
    public String[] list() {
        return list(null);
    }

    @Override
    public String[] list(FilenameFilter filter) {
        try {
            final FilenameFilter nameFilter = filter;
            final List<String> children = new ArrayList<>();
            Files.walkFileTree(parent, EnumSet.noneOf(FileVisitOption.class), 1, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path child, BasicFileAttributes attrs) throws IOException {
                    boolean isNotTheParent = !Files.isSameFile(child, parent);
                    boolean nameIsAcceptable = nameFilter == null || nameFilter.accept(WrappedJavaFile.this, child.getFileName().toString());
                    if (isNotTheParent && nameIsAcceptable) {
                        children.add(child.toString());
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            return children.toArray(new String[children.size()]);
        } catch (IOException ex) {
            return new String[]{};
        }
    }

    @Override
    public java.io.File[] listFiles() {
        return listFiles((FileFilter) null);
    }

    @Override
    public File[] listFiles(FileFilter fileFilter) {
        try {
            final FileFilter filter = fileFilter;
            final List<File> children = new ArrayList<>();
            Files.walkFileTree(parent, EnumSet.noneOf(FileVisitOption.class), 1, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path childPath, BasicFileAttributes attrs) throws IOException {
                    File childFile = childPath.toFile();
                    boolean isNotTheParent = !Files.isSameFile(childPath, parent);
                    boolean fileIsAcceptable = filter == null || filter.accept(childFile);
                    if (isNotTheParent && fileIsAcceptable) {
                        children.add(childFile);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            return children.toArray(new File[children.size()]);
        } catch (IOException ex) {
            return new File[]{};
        }
    }

    @Override
    public File[] listFiles(FilenameFilter fileNameFilter) {
        if (fileNameFilter == null) {
            return listFiles();
        }
        final FilenameFilter filter = fileNameFilter;
        return listFiles(new FileFilter() {
            @Override
            public boolean accept(File child) {
                return filter.accept(child.getParentFile(), child.getName());
            }
        });
    }

    @Override
    public boolean mkdir() {
        try {
            Path created = Files.createDirectory(parent);
            return Files.exists(created) && Files.isDirectory(created);
        } catch (IOException ex) {
            return false;
        }
    }

    @Override
    public boolean mkdirs() {
        try {
            Path created = Files.createDirectories(parent);
            return Files.exists(created) && Files.isDirectory(created);
        } catch (IOException ex) {
            return false;
        }
    }

    @Override
    public boolean renameTo(File destFile) {
        try {
            Path dest = destFile.toPath();
            Path target = Files.move(parent, dest, StandardCopyOption.REPLACE_EXISTING);
            return target.equals(dest);
        } catch (IOException ex) {
            return false;
        }
    }

    @Override
    public boolean setLastModified(long time) {
        throw doesNotSupport("setLastModified");
    }

    @Override
    public boolean setReadOnly() {
        throw doesNotSupport("setReadOnly");
    }

    @Override
    public boolean setWritable(boolean writable, boolean ownerOnly) {
        throw doesNotSupport("setWritable");
    }

    @Override
    public boolean setWritable(boolean writable) {
        throw doesNotSupport("setWritable");
    }

    @Override
    public boolean setReadable(boolean readable, boolean ownerOnly) {
        throw doesNotSupport("setReadable");
    }

    @Override
    public boolean setReadable(boolean readable) {
        throw doesNotSupport("setReadable");
    }

    @Override
    public boolean setExecutable(boolean executable, boolean ownerOnly) {
        throw doesNotSupport("setExecutable");
    }

    @Override
    public boolean setExecutable(boolean executable) {
        throw doesNotSupport("setExecutable");
    }

    @Override
    public boolean canExecute() {
        return Files.isExecutable(parent);
    }

    /* -- Disk usage -- */

    @Override
    public long getTotalSpace() {
        try {
            return Files.getFileStore(parent).getTotalSpace();
        } catch (IOException ex) {
            return 0L;
        }
    }

    @Override
    public long getFreeSpace() {
        try {
            return Files.getFileStore(parent).getUnallocatedSpace();
        } catch (IOException ex) {
            return 0L;
        }
    }

    @Override
    public long getUsableSpace() {
        try {
            return Files.getFileStore(parent).getUsableSpace();
        } catch (IOException ex) {
            return 0L;
        }
    }

    /* -- Basic infrastructure -- */

    @Override
    public int compareTo(File pathname) {
        return parent.compareTo(pathname.toPath());
    }

    @Override
    public int hashCode() {
        return parent.hashCode() + 1;
    }

    // -- Integration with java.nio.file --

    @Override
    public Path toPath() {
        return parent;
    }
}
