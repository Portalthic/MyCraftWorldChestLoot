package com.mycraft.worldchestloot;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

final class ConditionEvaluator {
    interface PlaceholderResolver {
        String resolve(String placeholder);
    }

    static final class Result {
        private final boolean passed;
        private final String failureMessage;

        private Result(boolean passed, String failureMessage) {
            this.passed = passed;
            this.failureMessage = failureMessage;
        }

        boolean isPassed() { return passed; }
        String getFailureMessage() { return failureMessage; }
    }

    private ConditionEvaluator() { }

    static Result evaluate(List<String> conditions, PlaceholderResolver resolver) {
        if (conditions == null || conditions.isEmpty()) return new Result(true, null);
        for (String configured : conditions) {
            ParsedCondition condition = splitMessage(configured == null ? "" : configured);
            boolean passed;
            try {
                passed = new Parser(tokenize(condition.expression), resolver).parse();
            } catch (IllegalArgumentException ex) {
                passed = false;
            }
            if (!passed) return new Result(false, condition.message);
        }
        return new Result(true, null);
    }

    private static ParsedCondition splitMessage(String configured) {
        boolean quoted = false;
        char quote = 0;
        boolean placeholder = false;
        for (int i = 0; i <= configured.length() - 9; i++) {
            char current = configured.charAt(i);
            if (quoted) {
                if (current == '\\') { i++; continue; }
                if (current == quote) quoted = false;
                continue;
            }
            if (current == '\'' || current == '"') {
                quoted = true;
                quote = current;
                continue;
            }
            if (current == '%') {
                placeholder = !placeholder;
                continue;
            }
            if (!placeholder && configured.regionMatches(i, "--message", 0, 9)
                    && (i == 0 || Character.isWhitespace(configured.charAt(i - 1)))
                    && (i + 9 == configured.length() || Character.isWhitespace(configured.charAt(i + 9)))) {
                String expression = configured.substring(0, i).trim();
                String message = configured.substring(i + 9).trim();
                return new ParsedCondition(expression, message.isEmpty() ? null : message);
            }
        }
        return new ParsedCondition(configured.trim(), null);
    }

    private static List<Token> tokenize(String expression) {
        List<Token> tokens = new ArrayList<>();
        int index = 0;
        while (index < expression.length()) {
            char current = expression.charAt(index);
            if (Character.isWhitespace(current)) { index++; continue; }
            if (current == '(' || current == ')') {
                tokens.add(new Token(current == '(' ? Type.LEFT_PAREN : Type.RIGHT_PAREN,
                        String.valueOf(current), false));
                index++;
                continue;
            }
            String operator = operatorAt(expression, index);
            if (operator != null) {
                tokens.add(new Token(Type.OPERATOR, operator, false));
                index += operator.length();
                continue;
            }
            if (current == '\'' || current == '"') {
                char quote = current;
                StringBuilder value = new StringBuilder();
                index++;
                boolean closed = false;
                while (index < expression.length()) {
                    current = expression.charAt(index++);
                    if (current == '\\' && index < expression.length()) {
                        value.append(expression.charAt(index++));
                    } else if (current == quote) {
                        closed = true;
                        break;
                    } else {
                        value.append(current);
                    }
                }
                if (!closed) throw new IllegalArgumentException("Unclosed quoted string");
                tokens.add(new Token(Type.VALUE, value.toString(), false));
                continue;
            }
            if (current == '%') {
                int end = expression.indexOf('%', index + 1);
                if (end < 0) throw new IllegalArgumentException("Unclosed placeholder");
                tokens.add(new Token(Type.VALUE, expression.substring(index, end + 1), true));
                index = end + 1;
                continue;
            }
            int start = index;
            while (index < expression.length()) {
                current = expression.charAt(index);
                if (Character.isWhitespace(current) || current == '(' || current == ')'
                        || current == '\'' || current == '"' || current == '%'
                        || operatorAt(expression, index) != null) break;
                index++;
            }
            if (start == index) throw new IllegalArgumentException("Unexpected character");
            tokens.add(new Token(Type.VALUE, expression.substring(start, index), false));
        }
        return tokens;
    }

    private static String operatorAt(String value, int index) {
        String[] operators = {">=", "<=", "==", "!=", "&&", "||", ">", "<"};
        for (String operator : operators) {
            if (value.startsWith(operator, index)) return operator;
        }
        return null;
    }

    private enum Type { VALUE, OPERATOR, LEFT_PAREN, RIGHT_PAREN }

    private static final class Token {
        final Type type;
        final String value;
        final boolean placeholder;

        Token(Type type, String value, boolean placeholder) {
            this.type = type;
            this.value = value;
            this.placeholder = placeholder;
        }
    }

    private static final class ParsedCondition {
        final String expression;
        final String message;

        ParsedCondition(String expression, String message) {
            this.expression = expression;
            this.message = message;
        }
    }

    private static final class Parser {
        private final List<Token> tokens;
        private final PlaceholderResolver resolver;
        private int position;

        Parser(List<Token> tokens, PlaceholderResolver resolver) {
            this.tokens = tokens;
            this.resolver = resolver;
        }

        boolean parse() {
            if (tokens.isEmpty()) throw new IllegalArgumentException("Empty condition");
            boolean result = parseOr(true);
            if (position != tokens.size()) throw new IllegalArgumentException("Unexpected token");
            return result;
        }

        private boolean parseOr(boolean evaluate) {
            boolean result = parseAnd(evaluate);
            while (matchOperator("||")) {
                boolean right = parseAnd(evaluate && !result);
                if (evaluate) result = result || right;
            }
            return result;
        }

        private boolean parseAnd(boolean evaluate) {
            boolean result = parsePrimary(evaluate);
            while (matchOperator("&&")) {
                boolean right = parsePrimary(evaluate && result);
                if (evaluate) result = result && right;
            }
            return result;
        }

        private boolean parsePrimary(boolean evaluate) {
            if (match(Type.LEFT_PAREN)) {
                boolean result = parseOr(evaluate);
                require(Type.RIGHT_PAREN);
                return result;
            }
            String left = operand(evaluate);
            if (position < tokens.size() && tokens.get(position).type == Type.OPERATOR
                    && isComparison(tokens.get(position).value)) {
                String operator = tokens.get(position++).value;
                String right = operand(evaluate);
                return evaluate && compare(left, operator, right);
            }
            if ("true".equalsIgnoreCase(left)) return true;
            if ("false".equalsIgnoreCase(left)) return false;
            throw new IllegalArgumentException("Expected comparison");
        }

        private String operand(boolean evaluate) {
            if (position >= tokens.size() || tokens.get(position).type != Type.VALUE) {
                throw new IllegalArgumentException("Expected value");
            }
            Token token = tokens.get(position++);
            if (!token.placeholder || !evaluate) return token.value;
            if (resolver == null) throw new IllegalArgumentException("PlaceholderAPI is unavailable");
            String resolved = resolver.resolve(token.value);
            if (resolved == null || resolved.equals(token.value)) {
                throw new IllegalArgumentException("Unresolved placeholder");
            }
            return resolved;
        }

        private boolean match(Type type) {
            if (position < tokens.size() && tokens.get(position).type == type) {
                position++;
                return true;
            }
            return false;
        }

        private boolean matchOperator(String operator) {
            if (position < tokens.size() && tokens.get(position).type == Type.OPERATOR
                    && operator.equals(tokens.get(position).value)) {
                position++;
                return true;
            }
            return false;
        }

        private void require(Type type) {
            if (!match(type)) throw new IllegalArgumentException("Missing closing parenthesis");
        }

        private static boolean isComparison(String operator) {
            return ">=".equals(operator) || "<=".equals(operator) || ">".equals(operator)
                    || "<".equals(operator) || "==".equals(operator) || "!=".equals(operator);
        }

        private static boolean compare(String left, String operator, String right) {
            BigDecimal leftNumber = number(left);
            BigDecimal rightNumber = number(right);
            if (leftNumber != null && rightNumber != null) {
                int comparison = leftNumber.compareTo(rightNumber);
                if (">=".equals(operator)) return comparison >= 0;
                if ("<=".equals(operator)) return comparison <= 0;
                if (">".equals(operator)) return comparison > 0;
                if ("<".equals(operator)) return comparison < 0;
                if ("==".equals(operator)) return comparison == 0;
                return comparison != 0;
            }
            if ("==".equals(operator)) return left.equalsIgnoreCase(right);
            if ("!=".equals(operator)) return !left.equalsIgnoreCase(right);
            throw new IllegalArgumentException("Text only supports == and !=");
        }

        private static BigDecimal number(String value) {
            try { return new BigDecimal(value.trim()); }
            catch (NumberFormatException ex) { return null; }
        }
    }
}
