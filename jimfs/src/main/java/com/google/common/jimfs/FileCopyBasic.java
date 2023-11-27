package com.google.common.jimfs;

public class FileCopyBasic implements FileCopyOptions {
    @Override
    public void copy(File file, File copy) {
        file.copyBasicAttributes(copy);
    }
}
