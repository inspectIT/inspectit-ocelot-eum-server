package rocks.inspectit.ocelot.eum.server.arithmetic;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThat;

class ArithmeticExpressionTest {

    @Nested
    class Eval {

        @Test
        void evaluateMinus() {
            ArithmeticExpression expression = new ArithmeticExpression("1565601241723 - 1565601241693");

            double result = expression.eval();

            assertThat(result).isEqualTo(30);
        }

        @Test
        void evaluatePlus() {
            ArithmeticExpression expression = new ArithmeticExpression("10 + 20");

            double result = expression.eval();

            assertThat(result).isEqualTo(30);
        }

        @Test
        void evaluateParentheses() {
            ArithmeticExpression expression = new ArithmeticExpression("(1+1)*5");

            double result = expression.eval();

            assertThat(result).isEqualTo(10);
        }

        @Test
        void invalidExpression() {
            ArithmeticExpression expression = new ArithmeticExpression("(1+*5");

            assertThatExceptionOfType(RuntimeException.class)
                    .isThrownBy(expression::eval)
                    .withMessage("Could not solve expression '(1+*5'. Unexpected character at position 3: *");
        }
    }
}
