package com.google.common.jimfs;

public class FileCopyAll implements FileCopyOptions {
    @Override
    public void copy(File file, File copy) {
        file.copyAttributes(copy);
    }
}
