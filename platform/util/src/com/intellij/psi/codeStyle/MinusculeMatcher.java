/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.codeStyle;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.FList;
import com.intellij.util.io.IOUtil;
import com.intellij.util.text.Matcher;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.BitSet;
import java.util.Iterator;

/**
* @author peter
*/
public class MinusculeMatcher implements Matcher {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.codeStyle.MinusculeMatcher");
  /**
   * Lowercase humps don't work for parts separated by these characters
   * Need either an explicit uppercase letter or the same separator character in prefix
   */
  private static final String HARD_SEPARATORS = " ()";
  private final ThreadLocal<MatchingState> myMatchingState = new ThreadLocal<MatchingState>() {
    @Override
    protected MatchingState initialValue() {
      return new MatchingState();
    }
  };

  private final char[] myPattern;
  private final NameUtil.MatchingCaseSensitivity myOptions;
  private final boolean myHasHumps;
  private final boolean myHasSeparators;
  private final boolean myHasDots;
  private final boolean[] isLowerCase;
  private final boolean[] isUpperCase;
  private final boolean[] isWordSeparator;
  private final char[] toUpperCase;
  private final char[] toLowerCase;
  private final boolean myHasWildCards;

  public MinusculeMatcher(@NotNull String pattern, @NotNull NameUtil.MatchingCaseSensitivity options) {
    myOptions = options;
    myPattern = StringUtil.trimEnd(pattern, "* ").toCharArray();
    isLowerCase = new boolean[myPattern.length];
    isUpperCase = new boolean[myPattern.length];
    isWordSeparator = new boolean[myPattern.length];
    toUpperCase = new char[myPattern.length];
    toLowerCase = new char[myPattern.length];
    for (int k = 0; k < myPattern.length; k++) {
      char c = myPattern[k];
      isLowerCase[k] = Character.isLowerCase(c);
      isUpperCase[k] = Character.isUpperCase(c);
      isWordSeparator[k] = isWordSeparator(c);
      toUpperCase[k] = StringUtil.toUpperCase(c);
      toLowerCase[k] = StringUtil.toLowerCase(c);
    }
    int i = 0;
    while (isWildcard(i)) i++;
    myHasHumps = hasFlag(i + 1, isUpperCase) && hasFlag(i, isLowerCase);
    myHasSeparators = hasFlag(i, isWordSeparator);
    myHasDots = hasDots(i);
    myHasWildCards = hasWildCards();
  }

  private static boolean isWordSeparator(char c) {
    return Character.isWhitespace(c) || c == '_' || c == '-' || c == ':' || c == '+';
  }

  private static boolean isWordStart(String text, int i) {
    char c = text.charAt(i);
    if (Character.isUpperCase(c)) {
      if (i > 0 && Character.isUpperCase(text.charAt(i - 1))) {
        // check that we're not in the middle of an all-caps word
        return i + 1 < text.length() && Character.isLowerCase(text.charAt(i + 1));
      }
      return true;
    }
    if (Character.isDigit(c)) {
      return true;
    }
    if (!Character.isLetter(c)) {
      return false;
    }
    return i == 0 || !Character.isLetterOrDigit(text.charAt(i - 1));
  }

  private boolean hasWildCards() {
    for (int i = 0; i < myPattern.length; i++) {
      if (isWildcard(i)) {
        return true;
      }
    }
    return false;
  }

  private boolean hasFlag(int start, boolean[] flags) {
    for (int i = start; i < myPattern.length; i++) {
      if (flags[i]) {
        return true;
      }
    }
    return false;
  }

  private boolean hasDots(int start) {
    for (int i = start; i < myPattern.length; i++) {
      if (myPattern[i] == '.') {
        return true;
      }
    }
    return false;
  }

  private static FList<TextRange> prependRange(@NotNull FList<TextRange> ranges, int from, int length) {
    TextRange head = ranges.getHead();
    if (head != null && head.getStartOffset() == from + length) {
      return ranges.getTail().prepend(new TextRange(from, head.getEndOffset()));
    }
    return ranges.prepend(TextRange.from(from, length));
  }

  public int matchingDegree(@NotNull String name) {
    FList<TextRange> iterable = matchingFragments(name);
    if (iterable == null) return Integer.MIN_VALUE;
    if (iterable.isEmpty()) return 0;

    final TextRange first = iterable.getHead();
    boolean startMatch = first.getStartOffset() == 0;

    int matchingCase = 0;
    int p = -1;

    int integral = 0; // -sum of matching-char-count * hump-index over all matched humps; favors longer fragments matching earlier words
    int humpIndex = 1;
    int nextHumpStart = 0;
    for (TextRange range : iterable) {
      for (int i = range.getStartOffset(); i < range.getEndOffset(); i++) {
        boolean isHumpStart = false;
        while (nextHumpStart <= i) {
          if (nextHumpStart == i) {
            isHumpStart = true;
          }
          nextHumpStart = NameUtil.nextWord(name, nextHumpStart);
          if (first != range) {
            humpIndex++;
          }
        }
        integral -= humpIndex;

        char c = name.charAt(i);
        p = StringUtil.indexOf(myPattern, c, p + 1, myPattern.length, false);
        if (p < 0) {
          break;
        }

        if (c == myPattern[p]) {
          if (isUpperCase[p]) matchingCase += 50; // strongly prefer user's uppercase matching uppercase: they made an effort to press Shift
          else if (i == 0 && startMatch) matchingCase += 15; // the very first letter case distinguishes classes in Java etc
          else if (isHumpStart) matchingCase += 1; // if a lowercase matches lowercase hump start, that also means something 
        } else if (isHumpStart) {
          // disfavor hump starts where pattern letter case doesn't match name case
          matchingCase -= 20;
        }
      }
    }

    int startIndex = first.getStartOffset();
    boolean afterSeparator = StringUtil.indexOfAny(name, HARD_SEPARATORS, 0, startIndex) >= 0;
    boolean wordStart = startIndex == 0 || isWordStart(name, startIndex) && !isWordStart(name, startIndex - 1);
    boolean finalMatch = iterable.get(iterable.size() - 1).getEndOffset() == name.length();

    return (wordStart ? 1000 : 0) + 
           integral * 10 + 
           matchingCase * (startMatch ? 10 : 1) + // in start matches, case is more important; in middle matches - fragment length (integral)
           (afterSeparator ? 0 : 2) + 
           (finalMatch ? 1 : 0);
  }

  public boolean isStartMatch(@NotNull String name) {
    Iterable<TextRange> fragments = matchingFragments(name);
    if (fragments != null) {
      Iterator<TextRange> iterator = fragments.iterator();
      if (!iterator.hasNext() || iterator.next().getStartOffset() == 0) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean matches(@NotNull String name) {
    // optimisation: name too short for this pattern
    if (!myHasWildCards && name.length() < myPattern.length) return false;

    return matchingFragments(name) != null;
  }

  @Nullable
  private FList<TextRange> calcMatchingFragments(@NotNull String name) {
    MatchingState state = myMatchingState.get();
    state.initializeState(name);
    try {
      return matchWildcards(name, 0, 0, state);
    }
    finally {
      state.releaseState();
    }
  }

  @Nullable
  public FList<TextRange> matchingFragments(@NotNull String name) {
    long start = System.currentTimeMillis();
    FList<TextRange> result = calcMatchingFragments(name);
    if (System.currentTimeMillis() - start > 1000 &&
        // if there's little free memory, it might have been the gc affecting the performance
        Runtime.getRuntime().freeMemory() > Runtime.getRuntime().totalMemory() * 3 / 10) {
      start = System.currentTimeMillis();
      calcMatchingFragments(name);
      if (System.currentTimeMillis() - start > 1000) {
        LOG.error("Too long name matching: name=" + name + "; prefix=" + new String(myPattern));
      }
    }
    return result;
  }

  /**
   * After a wildcard (* or space), search for the first non-wildcard pattern character in the name starting from nameIndex
   * and try to {@link #matchFragment(String, int, int, com.intellij.psi.codeStyle.MinusculeMatcher.MatchingState)} for it.
   */
  @Nullable
  private FList<TextRange> matchWildcards(@NotNull String name,
                                          int patternIndex,
                                          int nameIndex,
                                          MatchingState matchingState) {
    if (nameIndex < 0) {
      return null;
    }
    if (!isWildcard(patternIndex)) {
      if (patternIndex == myPattern.length) {
        return FList.emptyList();
      }
      return matchFragment(name, patternIndex, nameIndex, matchingState);
    }

    do {
      patternIndex++;
    } while (isWildcard(patternIndex));

    if (patternIndex == myPattern.length) {
      boolean space = isPatternChar(patternIndex - 1, ' ');
      // the trailing space should match if the pattern ends with the last word part, or only its first hump character
      if (space && nameIndex != name.length() && (patternIndex < 2 || !NameUtil.isWordStart(myPattern[patternIndex - 2]))) {
        int spaceIndex = name.indexOf(' ', nameIndex);
        if (spaceIndex >= 0) {
          return FList.<TextRange>emptyList().prepend(TextRange.from(spaceIndex, 1));
        }
        return null;
      }
      return FList.emptyList();
    }

    FList<TextRange> ranges = matchFragment(name, patternIndex, nameIndex, matchingState);
    if (ranges != null) {
      return ranges;
    }

    return matchSkippingWords(name, patternIndex, nameIndex, true, matchingState);
  }

  /**
   * Enumerates places in name that could be matched by the pattern at patternIndex position
   * and invokes {@link #matchFragment(String, int, int, com.intellij.psi.codeStyle.MinusculeMatcher.MatchingState)} at those candidate positions
   */
  @Nullable
  private FList<TextRange> matchSkippingWords(@NotNull String name,
                                              final int patternIndex,
                                              int nameIndex,
                                              boolean allowSpecialChars,
                                              MatchingState matchingState) {
    boolean star = isPatternChar(patternIndex - 1, '*');
    final char p = myPattern[patternIndex];
    while (true) {
      int nextOccurrence = star ?
                           indexOfIgnoreCase(name, nameIndex + 1, p, patternIndex, matchingState.isAsciiName) :
                           indexOfWordStart(name, patternIndex, nameIndex);
      if (nextOccurrence < 0) {
        return null;
      }
      // pattern humps are allowed to match in words separated by " ()", lowercase characters aren't
      if (!allowSpecialChars && !myHasSeparators && !myHasHumps && StringUtil.containsAnyChar(name, HARD_SEPARATORS, nameIndex, nextOccurrence)) {
        return null;
      }
      // if the user has typed a dot, don't skip other dots between humps
      // but one pattern dot may match several name dots
      if (!allowSpecialChars && myHasDots && !isPatternChar(patternIndex - 1, '.') && StringUtil.contains(name, nameIndex, nextOccurrence, '.')) {
        return null;
      }
      // uppercase should match either uppercase or a word start
      if (!isUpperCase[patternIndex] ||
          Character.isUpperCase(name.charAt(nextOccurrence)) ||
          isWordStart(name, nextOccurrence) ||
          // accept uppercase matching lowercase if the whole prefix is uppercase and case sensitivity allows that
          !myHasHumps && myOptions != NameUtil.MatchingCaseSensitivity.ALL) {
        FList<TextRange> ranges = matchFragment(name, patternIndex, nextOccurrence, matchingState);
        if (ranges != null) {
          return ranges;
        }
      }
      nameIndex = nextOccurrence;
    }
  }

  private boolean charEquals(char patternChar, int patternIndex, char c, boolean isIgnoreCase) {
    return patternChar == c ||
           isIgnoreCase && (toLowerCase[patternIndex] == c || toUpperCase[patternIndex] == c);
  }

  @Nullable
  private FList<TextRange> matchFragment(@NotNull String name,
                                         int patternIndex,
                                         int nameIndex,
                                         MatchingState matchingState) {
    if (matchingState.hasFailed(patternIndex, nameIndex)) {
      return null;
    }

    FList<TextRange> result = doMatchFragments(name, patternIndex, nameIndex, matchingState);
    if (result == null) {
      matchingState.registerFailure(patternIndex, nameIndex);
    }
    return result;
  }

  /**
   * Attempts to match an alphanumeric sequence of pattern (starting at patternIndex)
   * to some continuous substring of name, starting from nameIndex.
   */
  private FList<TextRange> doMatchFragments(String name,
                                            int patternIndex,
                                            int nameIndex,
                                            MatchingState matchingState) {
    if (!isFirstCharMatching(name, nameIndex, patternIndex)) {
      return null;
    }

    // middle matches have to be at least of length 3, to prevent too many irrelevant matches
    int minFragment = isPatternChar(patternIndex - 1, '*') && !isWildcard(patternIndex + 1) &&
                      Character.isLetterOrDigit(name.charAt(nameIndex)) && !isWordStart(name, nameIndex)
                      ? 3 : 1;
    int i = 1;
    boolean ignoreCase = myOptions != NameUtil.MatchingCaseSensitivity.ALL;
    while (nameIndex + i < name.length() &&
           patternIndex + i < myPattern.length &&
           charEquals(myPattern[patternIndex+i], patternIndex+i, name.charAt(nameIndex + i), ignoreCase)) {
      if (isUpperCase[patternIndex + i] && myHasHumps) {
        if (i < minFragment) {
          return null;
        }
        // when an uppercase pattern letter matches lowercase name letter, try to find an uppercase (better) match further in the name
        if (myPattern[patternIndex + i] != name.charAt(nameIndex + i)) {
          int nextWordStart = indexOfWordStart(name, patternIndex + i, nameIndex + i);
          FList<TextRange> ranges = matchWildcards(name, patternIndex + i, nextWordStart, matchingState);
          if (ranges != null) {
            return prependRange(ranges, nameIndex, i);
          }
          // at least three consecutive uppercase letters shouldn't match lowercase
          if (i > 1 && isUpperCase[patternIndex + i - 1] && isUpperCase[patternIndex + i - 2]) {
            // but if there's a lowercase after them, it can match (in case shift was released a bit later)
            if (nameIndex + i + 1 == name.length() ||
                patternIndex + i + 1 < myPattern.length && !isLowerCase[patternIndex + i + 1]) {
              return null;
            }
          }
        }
      }
      i++;
    }

    // we've found the longest fragment matching pattern and name

    if (patternIndex + i >= myPattern.length) {
      return FList.<TextRange>emptyList().prepend(TextRange.from(nameIndex, i));
    }

    // try to match the remainder of pattern with the remainder of name
    // it may not succeed with the longest matching fragment, then try shorter matches
    while (i >= minFragment || isWildcard(patternIndex + i)) {
      FList<TextRange> ranges = isWildcard(patternIndex + i) ?
                                matchWildcards(name, patternIndex + i, nameIndex + i, matchingState) :
                                matchSkippingWords(name, patternIndex + i, nameIndex + i, false, matchingState);
      if (ranges != null) {
        return prependRange(ranges, nameIndex, i);
      }
      i--;
    }
    return null;
  }

  private boolean isFirstCharMatching(@NotNull String name, int nameIndex, int patternIndex) {
    if (nameIndex >= name.length()) return false;

    boolean ignoreCase = myOptions != NameUtil.MatchingCaseSensitivity.ALL;
    char patternChar = myPattern[patternIndex];
    if (!charEquals(patternChar, patternIndex, name.charAt(nameIndex), ignoreCase)) return false;

    if (myOptions == NameUtil.MatchingCaseSensitivity.FIRST_LETTER &&
        (patternIndex == 0 || patternIndex == 1 && isWildcard(0)) && 
        Character.isUpperCase(patternChar) != Character.isUpperCase(name.charAt(0))) {
      return false;
    }
    return true;
  }

  private boolean isWildcard(int patternIndex) {
    if (patternIndex >= 0 && patternIndex < myPattern.length) {
      char pc = myPattern[patternIndex];
      return pc == ' ' || pc == '*';
    }
    return false;
  }
  private boolean isPatternChar(int patternIndex, char c) {
    return patternIndex >= 0 && patternIndex < myPattern.length && myPattern[patternIndex] == c;
  }

  private int indexOfWordStart(@NotNull String name, int patternIndex, int startFrom) {
    final char p = myPattern[patternIndex];
    if (startFrom >= name.length() ||
        myHasHumps && isLowerCase[patternIndex] && !(patternIndex > 0 && isWordSeparator[patternIndex - 1])) {
      return -1;
    }
    int nextWordStart = startFrom;
    while (true) {
      nextWordStart = NameUtil.nextWord(name, nextWordStart);
      if (nextWordStart >= name.length()) {
        return -1;
      }
      if (charEquals(p, patternIndex, name.charAt(nextWordStart), true)) {
        return nextWordStart;
      }
    }
  }

  private int indexOfIgnoreCase(String name, int fromIndex, char p, int patternIndex, boolean isAsciiName) {
    if (isAsciiName && IOUtil.isAscii(p)) {
      char pUpper = toUpperCase[patternIndex];
      char pLower = toLowerCase[patternIndex];
      for (int i = fromIndex; i < name.length(); i++) {
        char c = name.charAt(i);
        if (c == p || toUpperAscii(c) == pUpper || toLowerAscii(c) == pLower) {
          return i;
        }
      }
      return -1;
    }
    return StringUtil.indexOfIgnoreCase(name, p, fromIndex);
  }

  private static char toUpperAscii(char c) {
    if (c >= 'a' && c <= 'z') {
      return (char)(c + ('A' - 'a'));
    }
    return c;
  }
  private static char toLowerAscii(char c) {
    if (c >= 'A' && c <= 'Z') {
      return (char)(c - ('A' - 'a'));
    }
    return c;
  }
  @NonNls
  @Override
  public String toString() {
    return "MinusculeMatcher{myPattern=" + new String(myPattern) + ", myOptions=" + myOptions + '}';
  }

  private static class MatchingState {
    private boolean myBusy;
    private int myNameLength;
    private boolean isAsciiName;
    private final BitSet myTable = new BitSet();

    void initializeState(String name) {
      assert !myBusy;
      myBusy = true;
      myNameLength = name.length();
      isAsciiName = IOUtil.isAscii(name);
      myTable.clear();
    }

    void releaseState() {
      assert myBusy;
      myBusy = false;
    }

    void registerFailure(int patternIndex, int nameIndex) {
      myTable.set(patternIndex * myNameLength + nameIndex);
    }

    boolean hasFailed(int patternIndex, int nameIndex) {
      return myTable.get(patternIndex * myNameLength + nameIndex);
    }

  }
}
