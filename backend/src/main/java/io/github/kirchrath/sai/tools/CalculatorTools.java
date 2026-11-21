package io.github.kirchrath.sai.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class CalculatorTools {

    @Tool(name = "calculate",
            description = "Berechnet einen arithmetischen Ausdruck exakt. Erlaubt: Zahlen, + - * / % ^ und Klammern.")
    public String calculate(@ToolParam(description = "Ausdruck, z.B. (12.5 + 3) * 2^3") String expression) {
        try {
            return Calculator.evaluate(expression).toPlainString();
        } catch (IllegalArgumentException | ArithmeticException e) {
            return "Fehler: " + e.getMessage();
        }
    }
}
