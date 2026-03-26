package emaki.jiuwu.craft.corelib.operation;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OperationValidationTest {

    @Test
    void allowsPlaceholderValuesForTypedArguments() {
        Operation operation = new Operation() {
            @Override
            public String id() {
                return "test";
            }

            @Override
            public String description() {
                return "test";
            }

            @Override
            public String category() {
                return "test";
            }

            @Override
            public List<OperationParameter> parameters() {
                return List.of(OperationParameter.required("amount", OperationParameterType.DOUBLE, "Amount"));
            }

            @Override
            public OperationResult execute(OperationContext context, Map<String, String> arguments) {
                return OperationResult.ok();
            }
        };

        OperationResult validation = operation.validate(Map.of("amount", "%template_amount%"));
        assertTrue(validation.success());
    }
}
