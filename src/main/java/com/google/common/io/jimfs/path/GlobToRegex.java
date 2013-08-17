/*
 * Copyright 2012 Google Inc. All Rights Reserved.
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

package com.google.common.io.jimfs.path;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.CharMatcher;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.regex.PatternSyntaxException;

/**
 * Translates globs to regex patterns.
 *
 * @author Colin Decker
 */
final class GlobToRegex {

  /**
   * Converts the given glob to a regular expression pattern. The given separators determine what
   * characters the resulting expression breaks on for glob expressions such as * which should not
   * cross directory boundaries.
   *
   * <p>Basic conversions (assuming / as only separator):
   *
   * <pre>{@code
   * ?        = [^/]
   * *        = [^/]*
   * **       = .*
   * [a-z]    = [[^/]&&[a-z]]
   * [!a-z]   = [[^/]&&[^a-z]]
   * {a,b,c}  = (a|b|c)
   * }</pre>
   */
  public static String toRegex(String glob, String separators) {
    return new GlobToRegex(glob, separators).convert();
  }

  private static final CharMatcher REGEX_RESERVED = CharMatcher.anyOf("^$.?+*\\[]{}()");

  private final String glob;
  private final String separators;
  private final CharMatcher separatorMatcher;

  private final StringBuilder builder = new StringBuilder();
  private final Deque<State> states = new ArrayDeque<>();
  private int index;

  private GlobToRegex(String glob, String separators) {
    this.glob = checkNotNull(glob);
    this.separators = separators;
    this.separatorMatcher = CharMatcher.anyOf(separators);
  }

  /**
   * Converts the glob to a regex one character at a time. A state stack (states) is maintained,
   * with the state at the top of the stack being the current state at any given time. The current
   * state is always used to process the next character. When a state processes a character, it may
   * pop the current state or push a new state as the current state. The resulting regex is written
   * to {@code builder}.
   */
  private String convert() {
    enterState(NORMAL);
    for (index = 0; index < glob.length(); index++) {
      currentState().process(this, glob.charAt(index));
    }
    currentState().finish(this);
    return builder.toString();
  }

  private void enterState(State state) {
    states.push(state);
  }

  private void enterPreviousState() {
    states.pop();
  }

  private void changeState(State state) {
    enterPreviousState();
    enterState(state);
  }

  private State currentState() {
    return states.peek();
  }

  private PatternSyntaxException syntaxError(String desc) {
    throw new PatternSyntaxException(desc, glob, index);
  }

  private void append(char c) {
    if (separatorMatcher.matches(c)) {
      appendSeparator();
    } else {
      appendNormal(c);
    }
  }

  private void appendNormal(char c) {
    if (REGEX_RESERVED.matches(c)) {
      builder.append('\\');
    }
    builder.append(c);
  }

  private void appendSeparator() {
    if (separators.length() == 1) {
      appendNormal(separators.charAt(0));
    } else {
      builder.append('[').append(separators).append("]");
    }
  }

  private void appendNonSeparator() {
    builder.append("[^").append(separators).append(']');
  }

  private void appendQuestionMark() {
    appendNonSeparator();
  }

  private void appendStar() {
    appendNonSeparator();
    builder.append('*');
  }

  private void appendStarStar() {
    builder.append(".*");
  }

  private void appendBracketStart() {
    builder.append('[');
    appendNonSeparator();
    builder.append("&&[");
  }

  private void appendBracketEnd() {
    builder.append("]]");
  }

  private void appendInBracket(char c) {
    // escape \ in regex character class
    if (c == '\\') {
      builder.append('\\');
    }

    builder.append(c);
  }

  private void appendCurlyBraceStart() {
    builder.append('(');
  }

  private void appendSubpatternSeparator() {
    builder.append('|');
  }

  private void appendCurlyBraceEnd() {
    builder.append(')');
  }

  /**
   * Converter state.
   */
  private abstract static class State {
    /**
     * Process the next character with the current state, returning the state to transition to.
     */
    abstract void process(GlobToRegex converter, char c);

    /**
     * Called after all characters have been read.
     */
    void finish(GlobToRegex converter) {
    }
  }

  /**
   * Normal state.
   */
  private static final State NORMAL = new State() {
    @Override
    void process(GlobToRegex converter, char c) {
      switch (c) {
        case '?':
          converter.appendQuestionMark();
          return;
        case '[':
          converter.appendBracketStart();
          converter.enterState(BRACKET_FIRST_CHAR);
          return;
        case '{':
          converter.appendCurlyBraceStart();
          converter.enterState(CURLY_BRACE);
          return;
        case '*':
          converter.enterState(STAR);
          return;
        case '\\':
          converter.enterState(ESCAPE);
          return;
      }

      converter.append(c);
    }
  };

  /**
   * State following the reading of a single \.
   */
  private static final State ESCAPE = new State() {
    @Override
    void process(GlobToRegex converter, char c) {
      converter.append(c);
      converter.enterPreviousState();
    }

    @Override
    void finish(GlobToRegex converter) {
      throw converter.syntaxError("Hanging escape (\\) at end of pattern");
    }
  };

  /**
   * State following the reading of a single *.
   */
  private static final State STAR = new State() {
    @Override
    void process(GlobToRegex converter, char c) {
      if (c == '*') {
        converter.appendStarStar();
        converter.enterPreviousState();
      } else {
        converter.appendStar();
        converter.enterPreviousState();
        converter.currentState().process(converter, c);
      }
    }

    @Override
    void finish(GlobToRegex converter) {
      converter.appendStar();
    }
  };

  /**
   * State immediately following the reading of a [.
   */
  private static final State BRACKET_FIRST_CHAR = new State() {
    @Override
    void process(GlobToRegex converter, char c) {
      if (c == ']') {
        throw converter.syntaxError("Empty []");
      }
      if (c == '!') {
        converter.builder.append('^');
      } else if (c == '-') {
        converter.builder.append(c);
      } else {
        converter.appendInBracket(c);
      }
      converter.changeState(BRACKET);
    }

    @Override
    void finish(GlobToRegex converter) {
      throw converter.syntaxError("Unclosed [");
    }
  };

  /**
   * State inside [brackets], but not at the first character inside the brackets.
   */
  private static final State BRACKET = new State() {
    @Override
    void process(GlobToRegex converter, char c) {
      if (c == ']') {
        converter.appendBracketEnd();
        converter.enterPreviousState();
      } else {
        converter.appendInBracket(c);
      }
    }

    @Override
    void finish(GlobToRegex converter) {
      throw converter.syntaxError("Unclosed [");
    }
  };

  /**
   * State inside {curly braces}.
   */
  private static final State CURLY_BRACE = new State() {
    @Override
    void process(GlobToRegex converter, char c) {
      switch (c) {
        case '?':
          converter.appendQuestionMark();
          return;
        case '[':
          converter.appendBracketStart();
          converter.enterState(BRACKET_FIRST_CHAR);
          return;
        case '{':
          throw converter.syntaxError("{ not allowed in subpattern group");
        case '*':
          converter.enterState(STAR);
          return;
        case '\\':
          converter.enterState(ESCAPE);
          return;
        case '}':
          converter.appendCurlyBraceEnd();
          converter.enterPreviousState();
          return;
        case ',':
          converter.appendSubpatternSeparator();
          return;
      }

      converter.append(c);
    }

    @Override
    void finish(GlobToRegex converter) {
      throw converter.syntaxError("Unclosed {");
    }
  };
}
