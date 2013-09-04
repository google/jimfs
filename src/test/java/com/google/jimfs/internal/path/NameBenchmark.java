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

package com.google.jimfs.internal.path;

import com.google.caliper.Benchmark;
import com.google.caliper.Param;
import com.google.caliper.runner.CaliperMain;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

import com.ibm.icu.text.Normalizer2;

import java.text.Collator;
import java.util.Iterator;
import java.util.Locale;
import java.util.Random;

/**
 * @author Colin Decker
 */
public class NameBenchmark extends Benchmark {

  private static int CHAR_RANGE_START = '.';
  private static int CHAR_RANGE_END = 'z';
  private static int CHAR_RANGE_LENGTH = CHAR_RANGE_END - CHAR_RANGE_START;

  private static final Collator COLLATOR = Collator.getInstance(Locale.US);
  private static final Normalizer2 NORMALIZER = Normalizer2.getNFKCCasefoldInstance();

  static {
    COLLATOR.setStrength(Collator.SECONDARY);
  }

  @Param
  private NameImpl implementation;

  private Iterable<String> strings;
  private Iterable<Name> names;
  private Iterable<Name> names2;

  @Override
  protected void setUp() throws Exception {
    // consistent for each run
    Random random = new Random(928374893271892L);

    ImmutableList.Builder<String> builder = ImmutableList.builder();
    for (int i = 0; i < 200; i++) {
      int stringSize = random.nextInt(100) + 4;
      char[] chars = new char[stringSize];
      for (int j = 0; j < chars.length; j++) {
        int n = random.nextInt(CHAR_RANGE_LENGTH);
        char c = (char) (CHAR_RANGE_START + n);
        chars[j] = c;
      }
      builder.add(new String(chars));
    }

    ImmutableList<String> list = builder.build();
    strings = Iterables.cycle(list);

    names = createNames(list);
    names2 = createNames(list);
  }

  private Iterable<Name> createNames(ImmutableList<String> list) {
    ImmutableList.Builder<Name> nameBuilder = ImmutableList.builder();
    for (String string : list) {
      nameBuilder.add(implementation.create(string));
    }

    return Iterables.cycle(nameBuilder.build());
  }

  public int timeCreate(int reps) {
    Iterator<String> iterator = strings.iterator();
    int result = 0;
    for (int i = 0; i < reps; i++) {
      result ^= implementation.create(iterator.next()).toString().length();
    }
    return result;
  }

  public int timeEqualsAndHashCode(int reps) {
    Iterator<Name> iterator = names.iterator();
    Iterator<Name> iterator2 = names2.iterator();
    int result = 0;
    for (int i = 0; i < reps; i++) {
      Name name = iterator.next();
      Name name2 = iterator2.next();
      if (name.equals(name2)) { // always true
        result ^= name.hashCode();
      }
    }
    return result;
  }

  public static void main(String[] args) {
    CaliperMain.main(NameBenchmark.class, args);
  }

  private enum NameImpl {
    SIMPLE {
      @Override
      Name create(String string) {
        return Name.simple(string);
      }
    },

    COLLATING {
      @Override
      Name create(String string) {
        return Name.collating(string, COLLATOR);
      }
    },

    NORMALIZING {
      @Override
      Name create(String string) {
        return Name.normalizing(string, NORMALIZER);
      }
    },

    CASE_INSENSITIVE_ASCII {
      @Override
      Name create(String string) {
        return Name.caseInsensitiveAscii(string);
      }
    };

    abstract Name create(String string);
  }
}
