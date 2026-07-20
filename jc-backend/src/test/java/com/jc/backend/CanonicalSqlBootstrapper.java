package com.jc.backend;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class CanonicalSqlBootstrapper {
    private static final Pattern DOLLAR_QUOTE = Pattern.compile("\\$(?:[A-Za-z_][A-Za-z0-9_]*)?\\$");

    private CanonicalSqlBootstrapper() {
    }

    static void resetAndApply(
            String jdbcUrl,
            String username,
            String password,
            List<String> scripts) {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password)) {
            connection.setAutoCommit(true);
            execute(connection, "DROP SCHEMA IF EXISTS public CASCADE");
            execute(connection, "CREATE SCHEMA public");
            for (String script : scripts) {
                apply(connection, script);
            }
        } catch (SQLException | IOException exception) {
            throw new IllegalStateException("External canonical PostgreSQL bootstrap failed", exception);
        }
    }

    private static void apply(Connection connection, String scriptName) throws IOException, SQLException {
        String resource = "db/canonical/" + scriptName;
        try (InputStream input = CanonicalSqlBootstrapper.class.getClassLoader().getResourceAsStream(resource)) {
            if (input == null) {
                throw new IOException("Missing canonical SQL resource: " + resource);
            }
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            int statementNumber = 0;
            for (String statement : splitStatements(sql)) {
                statementNumber++;
                try {
                    execute(connection, statement);
                } catch (SQLException exception) {
                    throw new SQLException(
                            "Canonical SQL failed at " + scriptName + " statement " + statementNumber,
                            exception);
                }
            }
        }
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    static List<String> splitStatements(String sql) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        State state = State.NORMAL;
        String dollarTag = "";
        int index = 0;

        while (index < sql.length()) {
            char character = sql.charAt(index);
            char next = index + 1 < sql.length() ? sql.charAt(index + 1) : '\0';

            if (state == State.LINE_COMMENT) {
                current.append(character);
                if (character == '\n') {
                    state = State.NORMAL;
                }
                index++;
                continue;
            }
            if (state == State.BLOCK_COMMENT) {
                current.append(character);
                if (character == '*' && next == '/') {
                    current.append(next);
                    state = State.NORMAL;
                    index += 2;
                } else {
                    index++;
                }
                continue;
            }
            if (state == State.SINGLE_QUOTE) {
                current.append(character);
                if (character == '\'') {
                    if (next == '\'') {
                        current.append(next);
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
            if (state == State.DOUBLE_QUOTE) {
                current.append(character);
                if (character == '"') {
                    if (next == '"') {
                        current.append(next);
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
            if (state == State.DOLLAR_QUOTE) {
                if (sql.startsWith(dollarTag, index)) {
                    current.append(dollarTag);
                    state = State.NORMAL;
                    index += dollarTag.length();
                } else {
                    current.append(character);
                    index++;
                }
                continue;
            }

            if (character == '-' && next == '-') {
                current.append(character).append(next);
                state = State.LINE_COMMENT;
                index += 2;
                continue;
            }
            if (character == '/' && next == '*') {
                current.append(character).append(next);
                state = State.BLOCK_COMMENT;
                index += 2;
                continue;
            }
            if (character == '\'') {
                current.append(character);
                state = State.SINGLE_QUOTE;
                index++;
                continue;
            }
            if (character == '"') {
                current.append(character);
                state = State.DOUBLE_QUOTE;
                index++;
                continue;
            }
            if (character == '$') {
                Matcher matcher = DOLLAR_QUOTE.matcher(sql.substring(index));
                if (matcher.lookingAt()) {
                    dollarTag = matcher.group();
                    current.append(dollarTag);
                    state = State.DOLLAR_QUOTE;
                    index += dollarTag.length();
                    continue;
                }
            }
            if (character == ';') {
                String statement = current.toString().trim();
                if (!statement.isEmpty()) {
                    statements.add(statement);
                }
                current.setLength(0);
                index++;
                continue;
            }

            current.append(character);
            index++;
        }

        String trailing = current.toString().trim();
        if (!trailing.isEmpty()) {
            statements.add(trailing);
        }
        return List.copyOf(statements);
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
