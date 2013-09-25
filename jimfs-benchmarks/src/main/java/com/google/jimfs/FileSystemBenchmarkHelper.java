/*
 * Copyright 2013 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.jimfs;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * @author Colin Decker
 */
final class FileSystemBenchmarkHelper {

  private FileSystem fs;
  private Path tempDir;

  public FileSystemBenchmarkHelper(FileSystemImpl impl) throws IOException {
    fs = impl.getFileSystem();
    tempDir = impl.createTempDir(fs);
  }

  public FileSystem getFileSystem() {
    return fs;
  }

  public Path getTempDir() {
    return tempDir;
  }

  public final void tearDown() throws IOException {
    if (tempDir != null) {
      deleteRecursively(tempDir);
    }

    if (fs != null) {
      try {
        fs.close();
      } catch (Exception ignore) {
      }
    }
  }

  /**
   * Deletes the given directory and all files in its subtree.
   */
  protected static void deleteRecursively(Path dir) throws IOException {
    Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        Files.delete(file);
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
        Files.delete(dir);
        return FileVisitResult.CONTINUE;
      }
    });
  }

  /*static void createRandomDirectoryTree(Path dir, int averageDepth) throws IOException {
    int totalDepth = 0;
    int filesInTree = 1;

    while (totalDepth / (double) filesInTree < averageDepth) {
      System.out.print("\r" + totalDepth + " / " + filesInTree + "  =  "
          + (totalDepth / (double) filesInTree) + "  [" + averageDepth + "]");
      Path parent;
      Path file = dir;
      int depth = 1;
      ImmutableSet<Path> filesInDir;
      do {
        parent = file;
        file = null;
        filesInDir = listFiles(parent);
        if (!filesInDir.isEmpty() && random.nextInt(10000) > 15) {
          file = filesInDir.asList().get(random.nextInt(filesInDir.size()));
          depth++;
        }
      } while (file != null);

      if (filesInDir.size() < (averageDepth - depth) / 2 + 2) {
        String name = pickName(filesInDir.size());
        Files.createDirectory(parent.resolve(name));
        filesInTree++;
        totalDepth += depth;
      }
    }
    System.out.println("\r" + totalDepth + " / " + filesInTree + "  =  "
        + (totalDepth / (double) filesInTree) + "  [" + averageDepth + "]");
  }

  static Path getRandomPath(Path dir, int depth) throws IOException {
    Set<Path> deadEnds = new HashSet<>();
    Path currentPath = dir;
    int currentDepth = 0;
    while (currentDepth < depth) {
      ImmutableSet<Path> filesInDir = listFiles(dir);
      if (!deadEnds.isEmpty() && !filesInDir.isEmpty() && deadEnds.containsAll(filesInDir)) {
        deadEnds.add(currentPath);
        currentPath = currentPath.getParent();
        currentDepth--;
      } else {
        Path file = filesInDir.isEmpty()
            ? null
            : filesInDir.asList().get(random.nextInt(filesInDir.size()));
        if (file == null) {
          if (currentDepth > 0) {
            deadEnds.add(currentPath);
            currentPath = currentPath.getParent();
            currentDepth--;
          } else {
            throw new IllegalStateException("no files in tree");
          }
        } else {
          currentPath = file;
          currentDepth++;
        }
      }
    }

    return currentPath;
  }

  private static final Random random = new Random();

  private static Path pickRandomFile(Path dir) throws IOException {
    ImmutableList<Path> files = listFiles(dir).asList();
    if (files.isEmpty()) {
      return null;
    }
    int index = random.nextInt(files.size());
    return files.get(index);
  }

  private static ImmutableSet<Path> listFiles(Path dir) throws IOException {
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
      return ImmutableSet.copyOf(stream);
    }
  }

  private static String pickName(int numberOfFiles) {
    char letter = (char) ('a' + (numberOfFiles % 26));
    int number = numberOfFiles / 26;
    if (number == 0) {
      return "" + letter;
    }
    return letter + "" + number;
  }*/
}
