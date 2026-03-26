package emaki.jiuwu.craft.corelib.action;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ActionValidationTest {

    @Test
    void allowsPlaceholderValuesForTypedArguments() {
        Action operation = new Action() {
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
            public List<ActionParameter> parameters() {
                return List.of(ActionParameter.required("amount", ActionParameterType.DOUBLE, "Amount"));
            }

            @Override
            public ActionResult execute(ActionContext context, Map<String, String> arguments) {
                return ActionResult.ok();
            }
        };

        ActionResult validation = operation.validate(Map.of("amount", "%template_amount%"));
        assertTrue(validation.success());
    }
}
