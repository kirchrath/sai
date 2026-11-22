package io.github.kirchrath.sai.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class CalculatorTest {

    @ParameterizedTest
    @CsvSource(delimiter = ';', value = {
            "1 + 2 * 3; 7",
            "(1 + 2) * 3; 9",
            "-2^2; -4",
            "2^3^2; 512",
            "10 / 4; 2.5",
            "10 % 3; 1",
            "1,5 + 1.5; 3",
            "2^-2; 0.25",
            "--3; 3",
            "0.1 + 0.2; 0.3",
            "+5; 5",
            "2 ^ 0.5; 1.414213562373095",
            "4 / 2; 2",
            "5 - 2 - 1; 2",
            "2 - -3; 5"
    })
    void evaluates(String expression, String expected) {
        assertThat(Calculator.evaluate(expression).toPlainString()).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "1 +", "(1 + 2", "1 / 0", "1 % 0", "0 ^ -1", "2 ** 3", "abc", "1..2",
            "System.exit(0)", "1 2", "(-8) ^ 0.5", "10 ^ 400.5", "2 ^ 10000"})
    void rejectsInvalidExpressions(String expression) {
        assertThatThrownBy(() -> Calculator.evaluate(expression)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullAndOverlongExpressions() {
        assertThatThrownBy(() -> Calculator.evaluate(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Calculator.evaluate("1+".repeat(500) + "1"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Ausdruck zu lang");
    }

    @Test
    void reportsPositionOfErrors() {
        assertThatThrownBy(() -> Calculator.evaluate("1 + x"))
                .hasMessage("Zahl erwartet bei 'x' (Position 5)");
        assertThatThrownBy(() -> Calculator.evaluate("1 +")).hasMessage("Zahl erwartet (Position 4)");
    }

    @Test
    void negativeIntegerExponent() {
        assertThat(Calculator.evaluate("10 ^ -3").toPlainString()).isEqualTo("0.001");
    }

    @Test
    void toolReturnsResult() {
        assertThat(new CalculatorTools().calculate("6 * 7")).isEqualTo("42");
    }

    @Test
    void toolReportsArithmeticOverflow() {
        assertThat(new CalculatorTools().calculate("((10 ^ 9999) ^ 9999) ^ 9999")).startsWith("Fehler:");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1 / 0", "1 +"})
    void toolReturnsErrorText(String expression) {
        assertThat(new CalculatorTools().calculate(expression)).startsWith("Fehler:");
    }
}
