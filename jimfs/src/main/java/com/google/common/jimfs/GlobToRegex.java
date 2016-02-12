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

package com.google.common.jimfs;

import static com.google.common.base.Preconditions.checkNotNull;

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

  private static final InternalCharMatcher REGEX_RESERVED =
      InternalCharMatcher.anyOf("^$.?+*\\[]{}()");

  private final String glob;
  private final String separators;
  private final InternalCharMatcher separatorMatcher;

  private final StringBuilder builder = new StringBuilder();
  private final Deque<State> states = new ArrayDeque<>();
  private int index;

  private GlobToRegex(String glob, String separators) {
    this.glob = checkNotNull(glob);
    this.separators = separators;
    this.separatorMatcher = InternalCharMatcher.anyOf(separators);
  }

  /**
   * Converts the glob to a regex one character at a time. A state stack (states) is maintained,
   * with the state at the top of the stack being the current state at any given time. The current
   * state is always used to process the next character. When a state processes a character, it may
   * pop the current state or push a new state as the current state. The resulting regex is written
   * to {@code builder}.
   */
  private String convert() {
    pushState(NORMAL);
    for (index = 0; index < glob.length(); index++) {
      currentState().process(this, glob.charAt(index));
    }
    currentState().finish(this);
    return builder.toString();
  }

  /**
   * Enters the given state. The current state becomes the previous state.
   */
  private void pushState(State state) {
    states.push(state);
  }

  /**
   * Returns to the previous state.
   */
  private void popState() {
    states.pop();
  }

  /**
   * Returns the current state.
   */
  private State currentState() {
    return states.peek();
  }

  /**
   * Throws a {@link PatternSyntaxException}.
   */
  private PatternSyntaxException syntaxError(String desc) {
    throw new PatternSyntaxException(desc, glob, index);
  }

  /**
   * Appends the given character as-is to the regex.
   */
  private void appendExact(char c) {
    builder.append(c);
  }

  /**
   * Appends the regex form of the given normal character or separator from the glob.
   */
  private void append(char c) {
    if (separatorMatcher.matches(c)) {
      appendSeparator();
    } else {
      appendNormal(c);
    }
  }

  /**
   * Appends the regex form of the given normal character from the glob.
   */
  private void appendNormal(char c) {
    if (REGEX_RESERVED.matches(c)) {
      builder.append('\\');
    }
    builder.append(c);
  }

  /**
   * Appends the regex form matching the separators for the path type.
   */
  private void appendSeparator() {
    if (separators.length() == 1) {
      appendNormal(separators.charAt(0));
    } else {
      builder.append('[');
      for (int i = 0; i < separators.length(); i++) {
        appendInBracket(separators.charAt(i));
      }
      builder.append("]");
    }
  }

  /**
   * Appends the regex form that matches anything except the separators for the path type.
   */
  private void appendNonSeparator() {
    builder.append("[^");
    for (int i = 0; i < separators.length(); i++) {
      appendInBracket(separators.charAt(i));
    }
    builder.append(']');
  }

  /**
   * Appends the regex form of the glob ? character.
   */
  private void appendQuestionMark() {
    appendNonSeparator();
  }

  /**
   * Appends the regex form of the glob * character.
   */
  private void appendStar() {
    appendNonSeparator();
    builder.append('*');
  }

  /**
   * Appends the regex form of the glob ** pattern.
   */
  private void appendStarStar() {
    builder.append(".*");
  }

  /**
   * Appends the regex form of the start of a glob [] section.
   */
  private void appendBracketStart() {
    builder.append('[');
    appendNonSeparator();
    builder.append("&&[");
  }

  /**
   * Appends the regex form of the end of a glob [] section.
   */
  private void appendBracketEnd() {
    builder.append("]]");
  }

  /**
   * Appends the regex form of the given character within a glob [] section.
   */
  private void appendInBracket(char c) {
    // escape \ in regex character class
    if (c == '\\') {
      builder.append('\\');
    }

    builder.append(c);
  }

  /**
   * Appends the regex form of the start of a glob {} section.
   */
  private void appendCurlyBraceStart() {
    builder.append('(');
  }

  /**
   * Appends the regex form of the separator (,) within a glob {} section.
   */
  private void appendSubpatternSeparator() {
    builder.append('|');
  }

  /**
   * Appends the regex form of the end of a glob {} section.
   */
  private void appendCurlyBraceEnd() {
    builder.append(')');
  }

  /**
   * Converter state.
   */
  private abstract static class State {
    /**
     * Process the next character with the current state, transitioning the converter to a new
     * state if necessary.
     */
    abstract void process(GlobToRegex converter, char c);

    /**
     * Called after all characters have been read.
     */
    void finish(GlobToRegex converter) {}
  }

  /**
   * Normal state.
   */
  private static final State NORMAL =
      new State() {
        @Override
        void process(GlobToRegex converter, char c) {
          switch (c) {
            case '?':
              converter.appendQuestionMark();
              return;
            case '[':
              converter.appendBracketStart();
              converter.pushState(BRACKET_FIRST_CHAR);
              return;
            case '{':
              converter.appendCurlyBraceStart();
              converter.pushState(CURLY_BRACE);
              return;
            case '*':
              converter.pushState(STAR);
              return;
            case '\\':
              converter.pushState(ESCAPE);
              return;
            default:
              converter.append(c);
          }
        }

        @Override
        public String toString() {
          return "NORMAL";
        }
      };

  /**
   * State following the reading of a single \.
   */
  private static final State ESCAPE =
      new State() {
        @Override
        void process(GlobToRegex converter, char c) {
          converter.append(c);
          converter.popState();
        }

        @Override
        void finish(GlobToRegex converter) {
          throw converter.syntaxError("Hanging escape (\\) at end of pattern");
        }

        @Override
        public String toString() {
          return "ESCAPE";
        }
      };

  /**
   * State following the reading of a single *.
   */
  private static final State STAR =
      new State() {
        @Override
        void process(GlobToRegex converter, char c) {
          if (c == '*') {
            converter.appendStarStar();
            converter.popState();
          } else {
            converter.appendStar();
            converter.popState();
            converter.currentState().process(converter, c);
          }
        }

        @Override
        void finish(GlobToRegex converter) {
          converter.appendStar();
        }

        @Override
        public String toString() {
          return "STAR";
        }
      };

  /**
   * State immediately following the reading of a [.
   */
  private static final State BRACKET_FIRST_CHAR =
      new State() {
        @Override
        void process(GlobToRegex converter, char c) {
          if (c == ']') {
            // A glob like "[]]" or "[]q]" is apparently fine in Unix (when used with ls for example)
            // but doesn't work for the default java.nio.file implementations. In the cases of "[]]" it
            // produces:
            // java.util.regex.PatternSyntaxException: Unclosed character class near index 13
            // ^[[^/]&&[]]\]$
            //              ^
            // The error here is slightly different, but trying to make this work would require some
            // kind of lookahead and break the simplicity of char-by-char conversion here. Also, if
            // someone wants to include a ']' inside a character class, they should escape it.
            throw converter.syntaxError("Empty []");
          }
          if (c == '!') {
            converter.appendExact('^');
          } else if (c == '-') {
            converter.appendExact(c);
          } else {
            converter.appendInBracket(c);
          }
          converter.popState();
          converter.pushState(BRACKET);
        }

        @Override
        void finish(GlobToRegex converter) {
          throw converter.syntaxError("Unclosed [");
        }

        @Override
        public String toString() {
          return "BRACKET_FIRST_CHAR";
        }
      };

  /**
   * State inside [brackets], but not at the first character inside the brackets.
   */
  private static final State BRACKET =
      new State() {
        @Override
        void process(GlobToRegex converter, char c) {
          if (c == ']') {
            converter.appendBracketEnd();
            converter.popState();
          } else {
            converter.appendInBracket(c);
          }
        }

        @Override
        void finish(GlobToRegex converter) {
          throw converter.syntaxError("Unclosed [");
        }

        @Override
        public String toString() {
          return "BRACKET";
        }
      };

  /**
   * State inside {curly braces}.
   */
  private static final State CURLY_BRACE =
      new State() {
        @Override
        void process(GlobToRegex converter, char c) {
          switch (c) {
            case '?':
              converter.appendQuestionMark();
              break;
            case '[':
              converter.appendBracketStart();
              converter.pushState(BRACKET_FIRST_CHAR);
              break;
            case '{':
              throw converter.syntaxError("{ not allowed in subpattern group");
            case '*':
              converter.pushState(STAR);
              break;
            case '\\':
              converter.pushState(ESCAPE);
              break;
            case '}':
              converter.appendCurlyBraceEnd();
              converter.popState();
              break;
            case ',':
              converter.appendSubpatternSeparator();
              break;
            default:
              converter.append(c);
          }
        }

        @Override
        void finish(GlobToRegex converter) {
          throw converter.syntaxError("Unclosed {");
        }

        @Override
        public String toString() {
          return "CURLY_BRACE";
        }
      };
}
