package io.github.kirchrath.sai.tools;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Kleiner rekursiver Abstiegsparser fuer arithmetische Ausdruecke.
 * Unterstuetzt: + - * / % ^, Klammern, unaeres Minus/Plus, Dezimalzahlen (Punkt oder Komma).
 * Bewusst ohne Script-Engine, damit keine Code-Ausfuehrung moeglich ist.
 */
public final class Calculator {

    private static final MathContext MC = MathContext.DECIMAL64;
    private static final int MAX_LENGTH = 1000;
    private static final int MAX_EXPONENT = 9999;

    private final String input;
    private int pos;

    private Calculator(String input) {
        this.input = input;
    }

    public static BigDecimal evaluate(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("Leerer Ausdruck");
        }
        if (expression.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Ausdruck zu lang");
        }
        Calculator calculator = new Calculator(expression);
        BigDecimal result = calculator.parseExpression();
        calculator.skipWhitespace();
        if (calculator.pos < calculator.input.length()) {
            throw calculator.error("Unerwartetes Zeichen '" + calculator.input.charAt(calculator.pos) + "'");
        }
        return result.stripTrailingZeros();
    }

    private BigDecimal parseExpression() {
        BigDecimal value = parseTerm();
        while (true) {
            if (consume('+')) {
                value = value.add(parseTerm(), MC);
            } else if (consume('-')) {
                value = value.subtract(parseTerm(), MC);
            } else {
                return value;
            }
        }
    }

    private BigDecimal parseTerm() {
        BigDecimal value = parseFactor();
        while (true) {
            if (consume('*')) {
                value = value.multiply(parseFactor(), MC);
            } else if (consume('/')) {
                value = value.divide(nonZero(parseFactor()), MC);
            } else if (consume('%')) {
                value = value.remainder(nonZero(parseFactor()), MC);
            } else {
                return value;
            }
        }
    }

    /** Potenz ist rechtsassoziativ und bindet staerker als unaeres Minus: -2^2 = -4. */
    private BigDecimal parseFactor() {
        if (consume('-')) {
            return parseFactor().negate();
        }
        if (consume('+')) {
            return parseFactor();
        }
        BigDecimal base = parsePrimary();
        if (consume('^')) {
            return power(base, parseFactor());
        }
        return base;
    }

    private BigDecimal parsePrimary() {
        if (consume('(')) {
            BigDecimal value = parseExpression();
            if (!consume(')')) {
                throw error("')' erwartet");
            }
            return value;
        }
        return parseNumber();
    }

    private BigDecimal parseNumber() {
        skipWhitespace();
        int start = pos;
        while (pos < input.length()
                && (Character.isDigit(input.charAt(pos)) || input.charAt(pos) == '.' || input.charAt(pos) == ',')) {
            pos++;
        }
        if (start == pos) {
            throw error(pos < input.length() ? "Zahl erwartet bei '" + input.charAt(pos) + "'" : "Zahl erwartet");
        }
        try {
            return new BigDecimal(input.substring(start, pos).replace(',', '.'));
        } catch (NumberFormatException e) {
            throw error("Ungueltige Zahl '" + input.substring(start, pos) + "'");
        }
    }

    private static BigDecimal power(BigDecimal base, BigDecimal exponent) {
        BigDecimal integral = exponent.setScale(0, RoundingMode.DOWN);
        if (exponent.compareTo(integral) == 0 && integral.abs().compareTo(BigDecimal.valueOf(MAX_EXPONENT)) <= 0) {
            int n = integral.intValue();
            if (n < 0) {
                return BigDecimal.ONE.divide(nonZero(base).pow(-n, MC), MC);
            }
            return base.pow(n, MC);
        }
        double result = Math.pow(base.doubleValue(), exponent.doubleValue());
        if (Double.isNaN(result) || Double.isInfinite(result)) {
            throw new IllegalArgumentException("Potenz nicht definiert");
        }
        return new BigDecimal(result, MC);
    }

    private static BigDecimal nonZero(BigDecimal value) {
        if (value.signum() == 0) {
            throw new IllegalArgumentException("Division durch null");
        }
        return value;
    }

    private boolean consume(char expected) {
        skipWhitespace();
        if (pos < input.length() && input.charAt(pos) == expected) {
            pos++;
            return true;
        }
        return false;
    }

    private void skipWhitespace() {
        while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) {
            pos++;
        }
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException(message + " (Position " + (pos + 1) + ")");
    }
}
