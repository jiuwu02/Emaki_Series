package emaki.jiuwu.craft.corelib.action.v2.compile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Lexer behaviour that the pipeline grammar depends on.
 *
 * <p>Temporary asset introduced for the Action v2 migration (decision D5); removed at stage 6.</p>
 */
class PipelineLexerTest {

    private final PipelineLexer lexer = new PipelineLexer();

    private List<PipelineToken> tokens(String line) {
        PipelineLexer.Result result = lexer.lex(line);
        assertTrue(result.successful(), () -> "lex failed: " + result.diagnostic());
        return result.tokens();
    }

    @Test
    @DisplayName("pipe separates stages with or without surrounding whitespace")
    void pipeSeparatesEitherWay() {
        assertEquals(List.of("self", "|", "heal"),
                tokens("self | heal").stream().map(PipelineToken::text).toList());
        assertEquals(List.of("self", "|", "heal"),
                tokens("self|heal").stream().map(PipelineToken::text).toList());
    }

    @Test
    @DisplayName("a pipe inside a quoted value stays part of the value")
    void quotedPipeIsNotASeparator() {
        List<PipelineToken> result = tokens("send_message text=\"a | b\"");
        assertEquals(2, result.size());
        assertEquals("send_message", result.get(0).text());
        assertEquals("text=a | b", result.get(1).text());
        assertTrue(result.get(1).quoted());
    }

    @Test
    @DisplayName("brackets lex as their own tokens")
    void bracketsAreSeparateTokens() {
        List<PipelineToken> result = tokens("if %a%>1 [ heal ]");
        assertEquals(PipelineToken.Kind.BRACKET_OPEN, result.get(2).kind());
        assertEquals(PipelineToken.Kind.BRACKET_CLOSE, result.get(4).kind());
    }

    @Test
    @DisplayName("brackets lex without whitespace too")
    void bracketsWithoutWhitespace() {
        List<PipelineToken> result = tokens("if %a%>1 [heal]");
        assertEquals(5, result.size());
        assertEquals(PipelineToken.Kind.BRACKET_OPEN, result.get(2).kind());
        assertEquals("heal", result.get(3).text());
        assertEquals(PipelineToken.Kind.BRACKET_CLOSE, result.get(4).kind());
    }

    @Test
    @DisplayName("columns are one-based and point at the token start")
    void columnsAreTracked() {
        List<PipelineToken> result = tokens("self | heal amount=20");
        assertEquals(1, result.get(0).column());
        assertEquals(6, result.get(1).column());
        assertEquals(8, result.get(2).column());
        assertEquals(13, result.get(3).column());
    }

    @Test
    @DisplayName("MiniMessage tags and comparison operators survive unquoted")
    void operatorsAndTagsSurvive() {
        assertEquals("where", tokens("where %target.health%<50").get(0).text());
        assertEquals("%target.health%<50", tokens("where %target.health%<50").get(1).text());
        assertEquals("text=<gold>hit <red>hard",
                tokens("send_message text=\"<gold>hit <red>hard\"").get(1).text());
    }

    @Test
    @DisplayName("escaped quotes stay inside the value")
    void escapedQuotesArePreserved() {
        List<PipelineToken> result = tokens("send_message text=\"say \\\"hi\\\" now\"");
        assertEquals("text=say \"hi\" now", result.get(1).text());
    }

    @Test
    @DisplayName("an unclosed quote is reported with the opening column")
    void unclosedQuoteIsReported() {
        PipelineLexer.Result result = lexer.lex("send_message text=\"unterminated");
        assertFalse(result.successful());
        assertEquals("action.v2.lex.unclosed_quote", result.diagnostic().reasonKey());
        assertEquals(19, result.diagnostic().column());
    }

    @Test
    @DisplayName("an empty quoted value still produces a token")
    void emptyQuotedValueIsAToken() {
        List<PipelineToken> result = tokens("send_message text=\"\"");
        assertEquals(2, result.size());
        assertEquals("text=", result.get(1).text());
    }

    @Test
    @DisplayName("|| is the logical-or operator, not a stage separator")
    void doublePipeIsAnOperator() {
        List<PipelineToken> result = tokens("where %a%>1 || %b%<2");
        assertEquals(4, result.size());
        assertEquals("||", result.get(2).text());
        assertEquals(PipelineToken.Kind.WORD, result.get(2).kind());
    }

    @Test
    @DisplayName("comparison operators do not split a key from a value")
    void comparisonOperatorsAreNotKeyValue() {
        assertFalse(tokens("where %c%==3").get(1).isKeyValue());
        assertFalse(tokens("where %c%!=3").get(1).isKeyValue());
        assertFalse(tokens("where %c%>=3").get(1).isKeyValue());
        assertFalse(tokens("where %c%<=3").get(1).isKeyValue());
    }

    @Test
    @DisplayName("a real key=value pair does split")
    void keyValueSplits() {
        PipelineToken token = tokens("heal amount=20").get(1);
        assertTrue(token.isKeyValue());
        assertEquals("amount", token.key());
        assertEquals("20", token.value());
    }

    @Test
    @DisplayName("only the first equals sign splits the pair")
    void onlyFirstEqualsSplits() {
        PipelineToken token = tokens("run_command_as_console command=\"say a=b\"").get(1);
        assertEquals("command", token.key());
        assertEquals("say a=b", token.value());
    }

    @Test
    @DisplayName("a leading equals sign does not split")
    void leadingEqualsDoesNotSplit() {
        assertFalse(tokens("where =5").get(1).isKeyValue());
    }
}
