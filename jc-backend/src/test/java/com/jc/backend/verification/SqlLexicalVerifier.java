package com.jc.backend.verification;

import static com.jc.backend.verification.StaticContractSupport.failContract;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class SqlLexicalVerifier {
    private static final Pattern DOLLAR_QUOTE = Pattern.compile("\\$(?:[A-Za-z_][A-Za-z0-9_]*)?\\$");

    private SqlLexicalVerifier() {
    }

    static void verify(Path path) throws IOException {
        String text = Files.readString(path, StandardCharsets.UTF_8);
        Deque<Integer> parentheses = new ArrayDeque<>();
        State state = State.NORMAL;
        String dollarTag = "";
        int index = 0;
        int line = 1;

        while (index < text.length()) {
            char current = text.charAt(index);
            char next = index + 1 < text.length() ? text.charAt(index + 1) : '\0';
            if (current == '\n') {
                line++;
            }

            switch (state) {
                case LINE_COMMENT -> {
                    if (current == '\n') {
                        state = State.NORMAL;
                    }
                    index++;
                    continue;
                }
                case BLOCK_COMMENT -> {
                    if (current == '*' && next == '/') {
                        state = State.NORMAL;
                        index += 2;
                    } else {
                        index++;
                    }
                    continue;
                }
                case SINGLE_QUOTE -> {
                    if (current == '\'') {
                        if (next == '\'') {
                            index += 2;
                        } else {
                            state = State.NORMAL;
                            index++;
                        }
                    } else {
                        index++;
                    }
                    continue;
                }
                case DOUBLE_QUOTE -> {
                    if (current == '"') {
                        if (next == '"') {
                            index += 2;
                        } else {
                            state = State.NORMAL;
                            index++;
                        }
                    } else {
                        index++;
                    }
                    continue;
                }
                case DOLLAR_QUOTE -> {
                    if (text.startsWith(dollarTag, index)) {
                        state = State.NORMAL;
                        index += dollarTag.length();
                    } else {
                        index++;
                    }
                    continue;
                }
                case NORMAL -> {
                    // Continue below.
                }
            }

            if (current == '-' && next == '-') {
                state = State.LINE_COMMENT;
                index += 2;
                continue;
            }
            if (current == '/' && next == '*') {
                state = State.BLOCK_COMMENT;
                index += 2;
                continue;
            }
            if (current == '\'') {
                state = State.SINGLE_QUOTE;
                index++;
                continue;
            }
            if (current == '"') {
                state = State.DOUBLE_QUOTE;
                index++;
                continue;
            }
            if (current == '$') {
                Matcher matcher = DOLLAR_QUOTE.matcher(text.substring(index));
                if (matcher.lookingAt()) {
                    dollarTag = matcher.group();
                    state = State.DOLLAR_QUOTE;
                    index += dollarTag.length();
                    continue;
                }
            }

            if (current == '(') {
                parentheses.push(line);
            } else if (current == ')') {
                if (parentheses.isEmpty()) {
                    failContract("unmatched ')' in " + RepositoryLayout.relative(path) + ":" + line);
                }
                parentheses.pop();
            }
            index++;
        }

        if (state != State.NORMAL && state != State.LINE_COMMENT) {
            failContract("unterminated " + state + " in " + RepositoryLayout.relative(path));
        }
        if (!parentheses.isEmpty()) {
            failContract("unclosed '(' in " + RepositoryLayout.relative(path) + ":" + parentheses.peek());
        }
        if (text.indexOf('\0') >= 0) {
            failContract("NUL byte in " + RepositoryLayout.relative(path));
        }
        if (text.contains("\r\n")) {
            failContract("CRLF found in " + RepositoryLayout.relative(path));
        }
    }

    private enum State {
        NORMAL,
        LINE_COMMENT,
        BLOCK_COMMENT,
        SINGLE_QUOTE,
        DOUBLE_QUOTE,
        DOLLAR_QUOTE
    }
}
