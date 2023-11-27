package com.google.common.jimfs;

import java.util.HashMap;

public class CopyFactory {
    private static final HashMap<AttributeCopyOption, FileCopyOptions> copyMap = new HashMap<>();

    static {
        copyMap.put(AttributeCopyOption.ALL, new FileCopyAll());
        copyMap.put(AttributeCopyOption.BASIC, new FileCopyBasic());
    }

    public static FileCopyOptions getCopyOption(AttributeCopyOption attributeCopyOption) {
        return copyMap.get(attributeCopyOption);
    }
}
