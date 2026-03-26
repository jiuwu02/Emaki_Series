package emaki.jiuwu.craft.corelib.operation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OperationLineParserTest {

    private final OperationLineParser parser = new OperationLineParser();

    @Test
    void parsesMultipleControlsAndArguments() throws Exception {
        ParsedOperationLine parsed = parser.parse(
            1,
            "@if=\"%forge_quality% == 传说\" @chance=25% @delay=20t @ignore_failure send_message text=\"<gold>%player_name%</gold>\""
        );

        assertEquals("send_message", parsed.operationId());
        assertEquals("%forge_quality% == 传说", parsed.control().condition());
        assertEquals("25%", parsed.control().chance());
        assertEquals("20t", parsed.control().delay());
        assertTrue(parsed.control().ignoreFailure());
        assertEquals("<gold>%player_name%</gold>", parsed.arguments().get("text"));
    }

    @Test
    void rejectsDuplicateControls() {
        OperationSyntaxException exception = assertThrows(
            OperationSyntaxException.class,
            () -> parser.parse(1, "@chance=10% @chance=20% send_message text=test")
        );

        assertTrue(exception.getMessage().contains("Duplicate control"));
    }

    @Test
    void skipsCommentLines() throws Exception {
        assertNull(parser.parse(2, "   # comment"));
    }
}
