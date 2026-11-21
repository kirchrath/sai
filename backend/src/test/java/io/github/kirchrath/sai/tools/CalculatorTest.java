package io.github.kirchrath.sai.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
            "0.1 + 0.2; 0.3"
    })
    void evaluates(String expression, String expected) {
        assertThat(Calculator.evaluate(expression).toPlainString()).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "1 +", "(1 + 2", "1 / 0", "2 ** 3", "abc", "1..2", "System.exit(0)"})
    void rejectsInvalidExpressions(String expression) {
        assertThatThrownBy(() -> Calculator.evaluate(expression)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1 / 0", "1 +"})
    void toolReturnsErrorText(String expression) {
        assertThat(new CalculatorTools().calculate(expression)).startsWith("Fehler:");
    }
}
