package emaki.jiuwu.craft.corelib.action.v2.compile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Pipeline grammar behaviour.
 *
 * <p>Temporary asset introduced for the Action v2 migration (decision D5); removed at stage 6.</p>
 */
class PipelineParserTest {

    private final PipelineParser parser = new PipelineParser();

    private List<ActionAst> parse(String line) {
        PipelineParser.Result result = parser.parse(line);
        assertTrue(result.successful(), () -> "parse failed: " + result.diagnostic());
        return result.nodes();
    }

    private CompileDiagnostic reject(String line) {
        PipelineParser.Result result = parser.parse(line);
        assertFalse(result.successful(), () -> "expected rejection but parsed: " + result.nodes());
        return result.diagnostic();
    }

    @Nested
    @DisplayName("stages")
    class Stages {

        @Test
        @DisplayName("a single stage with named arguments")
        void singleStage() {
            List<ActionAst> nodes = parse("heal amount=20");
            assertEquals(1, nodes.size());
            ActionAst.Stage stage = assertInstanceOf(ActionAst.Stage.class, nodes.get(0));
            assertEquals("heal", stage.id());
            assertEquals("20", stage.arguments().get("amount"));
        }

        @Test
        @DisplayName("stages chained by pipes keep written order")
        void chainedStages() {
            List<ActionAst> nodes = parse("nearby radius=8 | where %target.health%<50 | damage amount=10");
            assertEquals(3, nodes.size());
            assertEquals("nearby", ((ActionAst.Stage) nodes.get(0)).id());
            assertEquals("where", ((ActionAst.Stage) nodes.get(1)).id());
            assertEquals("damage", ((ActionAst.Stage) nodes.get(2)).id());
        }

        @Test
        @DisplayName("a bare value becomes a positional argument")
        void positionalArgument() {
            ActionAst.Stage stage = (ActionAst.Stage) parse("where %target.health%<50").get(0);
            assertEquals(List.of("%target.health%<50"), stage.positional());
            assertTrue(stage.arguments().isEmpty());
        }

        @Test
        @DisplayName("stage names normalise to lower case")
        void stageNameIsLowerCased() {
            assertEquals("send_message", ((ActionAst.Stage) parse("SEND_MESSAGE text=hi").get(0)).id());
        }

        @Test
        @DisplayName("a duplicate argument is rejected")
        void duplicateArgumentRejected() {
            assertEquals("action.v2.parse.duplicate_argument", reject("heal amount=1 amount=2").reasonKey());
        }

        @Test
        @DisplayName("a stage name that is really an argument is rejected")
        void stageNameCannotBeArgument() {
            assertEquals("action.v2.parse.stage_name_is_argument", reject("amount=20").reasonKey());
        }

        @Test
        @DisplayName("a trailing pipe is rejected")
        void trailingPipeRejected() {
            assertEquals("action.v2.parse.trailing_pipe", reject("self |").reasonKey());
        }

        @Test
        @DisplayName("an empty stage between two pipes is rejected")
        void emptyStageRejected() {
            assertEquals("action.v2.parse.empty_stage", reject("self | | heal").reasonKey());
        }

        @Test
        @DisplayName("blank input and comments parse to nothing, not an error")
        void blankIsNotAnError() {
            assertTrue(parser.parse("").blank());
            assertTrue(parser.parse("   ").blank());
            assertTrue(parser.parse("# a comment").blank());
            assertTrue(parser.parse(null).blank());
        }
    }

    @Nested
    @DisplayName("branches")
    class Branches {

        @Test
        @DisplayName("if with a then body")
        void ifThen() {
            ActionAst.Branch branch = assertInstanceOf(ActionAst.Branch.class,
                    parse("if %caster.health%<20 [ send_message text=danger ]").get(0));
            assertEquals("%caster.health%<20", branch.condition());
            assertEquals(1, branch.thenBranch().size());
            assertFalse(branch.hasElse());
        }

        @Test
        @DisplayName("if with else")
        void ifElse() {
            ActionAst.Branch branch = (ActionAst.Branch)
                    parse("if %a%>1 [ heal amount=10 ] else [ damage amount=5 ]").get(0);
            assertEquals(1, branch.thenBranch().size());
            assertEquals(1, branch.elseBranch().size());
            assertEquals("damage", ((ActionAst.Stage) branch.elseBranch().get(0)).id());
        }

        @Test
        @DisplayName("a branch body may hold a full pipeline")
        void branchBodyIsAPipeline() {
            ActionAst.Branch branch = (ActionAst.Branch)
                    parse("if %skill.level%>=3 [ nearby radius=5 | damage amount=8 ]").get(0);
            assertEquals(2, branch.thenBranch().size());
        }

        @Test
        @DisplayName("branches nest three deep")
        void threeLevelNesting() {
            ActionAst.Branch outer = (ActionAst.Branch) parse(
                    "if %a%>1 [ if %b%>2 [ if %c%>3 [ heal amount=1 ] else [ heal amount=2 ] ] ] "
                            + "else [ damage amount=9 ]").get(0);
            ActionAst.Branch middle = assertInstanceOf(ActionAst.Branch.class, outer.thenBranch().get(0));
            ActionAst.Branch inner = assertInstanceOf(ActionAst.Branch.class, middle.thenBranch().get(0));
            assertEquals("1", ((ActionAst.Stage) inner.thenBranch().get(0)).arguments().get("amount"));
            assertEquals("2", ((ActionAst.Stage) inner.elseBranch().get(0)).arguments().get("amount"));
            assertEquals("9", ((ActionAst.Stage) outer.elseBranch().get(0)).arguments().get("amount"));
        }

        @Test
        @DisplayName("a branch may sit mid-pipeline")
        void branchMidPipeline() {
            List<ActionAst> nodes = parse(
                    "nearby radius=8 | if %target.health%<50 [ damage amount=20 ] else [ damage amount=5 ]");
            assertEquals(2, nodes.size());
            assertInstanceOf(ActionAst.Stage.class, nodes.get(0));
            assertInstanceOf(ActionAst.Branch.class, nodes.get(1));
        }

        @Test
        @DisplayName("a multi-token condition keeps its spacing")
        void multiTokenCondition() {
            ActionAst.Branch branch = (ActionAst.Branch)
                    parse("if %a% > 1 [ heal amount=1 ]").get(0);
            assertEquals("%a% > 1", branch.condition());
        }

        @Test
        @DisplayName("a missing closing bracket names the opening column")
        void missingCloseBracketReportsOpeningColumn() {
            CompileDiagnostic diagnostic = reject("if %a%>1 [ damage amount=5");
            assertEquals("action.v2.parse.unclosed_bracket", diagnostic.reasonKey());
            assertEquals(10, diagnostic.detail().get("open_column"));
        }

        @Test
        @DisplayName("a stray closing bracket is rejected")
        void strayCloseBracketRejected() {
            assertEquals("action.v2.parse.unmatched_bracket_close", reject("heal amount=5 ]").reasonKey());
        }

        @Test
        @DisplayName("an empty branch body is rejected")
        void emptyBranchBodyRejected() {
            assertEquals("action.v2.parse.empty_branch_body", reject("if %a%>1 [ ]").reasonKey());
        }

        @Test
        @DisplayName("if without a condition is rejected")
        void missingConditionRejected() {
            assertEquals("action.v2.parse.branch_missing_condition", reject("if [ heal ]").reasonKey());
        }

        @Test
        @DisplayName("if without a body is rejected")
        void missingBodyRejected() {
            assertEquals("action.v2.parse.branch_missing_body", reject("if %a%>1").reasonKey());
        }

        @Test
        @DisplayName("a bracket where a stage is expected is rejected")
        void unexpectedOpenBracketRejected() {
            assertEquals("action.v2.parse.unexpected_bracket_open", reject("[ heal ]").reasonKey());
        }
    }

    @Nested
    @DisplayName("sequence calls")
    class SequenceCalls {

        @Test
        @DisplayName("run takes a positional name then named parameters")
        void runWithParameters() {
            ActionAst.SequenceCall call = assertInstanceOf(ActionAst.SequenceCall.class,
                    parse("run burn_target damage=10 burn_ticks=60").get(0));
            assertEquals("burn_target", call.sequence());
            assertEquals("10", call.parameters().get("damage"));
            assertEquals("60", call.parameters().get("burn_ticks"));
        }

        @Test
        @DisplayName("run with no parameters")
        void runWithoutParameters() {
            assertEquals("big_hit", ((ActionAst.SequenceCall) parse("run big_hit").get(0)).sequence());
        }

        @Test
        @DisplayName("run inside a branch body")
        void runInsideBranch() {
            ActionAst.Branch branch = (ActionAst.Branch)
                    parse("if %skill.level%>=5 [ run big_hit ] else [ run small_hit ]").get(0);
            assertEquals("big_hit", ((ActionAst.SequenceCall) branch.thenBranch().get(0)).sequence());
            assertEquals("small_hit", ((ActionAst.SequenceCall) branch.elseBranch().get(0)).sequence());
        }

        @Test
        @DisplayName("run without a sequence name is rejected")
        void runNeedsName() {
            assertEquals("action.v2.parse.run_missing_sequence", reject("run").reasonKey());
            assertEquals("action.v2.parse.run_missing_sequence", reject("run damage=10").reasonKey());
        }

        @Test
        @DisplayName("a bare value after the sequence name is rejected")
        void runRejectsPositionalParameters() {
            assertEquals("action.v2.parse.run_positional_parameter", reject("run foo bar").reasonKey());
        }
    }

    @Nested
    @DisplayName("real configuration shapes")
    class RealShapes {

        @Test
        @DisplayName("the fireball hit line from the syntax spec")
        void fireballHitLine() {
            List<ActionAst> nodes = parse("inherited | damage amount=%var.damage% type=spell "
                    + "element=%var.element% | if %skill.level%>=3 [ ignite duration=%var.ignite_ticks% ]");
            assertEquals(3, nodes.size());
            assertEquals("inherited", ((ActionAst.Stage) nodes.get(0)).id());
            ActionAst.Stage damage = (ActionAst.Stage) nodes.get(1);
            assertEquals("%var.damage%", damage.arguments().get("amount"));
            assertEquals("spell", damage.arguments().get("type"));
            assertInstanceOf(ActionAst.Branch.class, nodes.get(2));
        }

        @Test
        @DisplayName("a long-running task line keeps its quoted key")
        void startTaskLine() {
            ActionAst.Stage stage = (ActionAst.Stage) parse("self | start_task "
                    + "key=\"nutrition_wellfed:%caster.uuid%\" sequence=nutrition_well_fed "
                    + "interval=40t on_conflict=replace stop_when_offline=true").get(1);
            assertEquals("start_task", stage.id());
            assertEquals("nutrition_wellfed:%caster.uuid%", stage.arguments().get("key"));
            assertEquals("40t", stage.arguments().get("interval"));
            assertEquals("true", stage.arguments().get("stop_when_offline"));
        }

        @Test
        @DisplayName("a bracketed condition with logical operators keeps || as an operator, not a separator")
        void bracketedCondition() {
            List<ActionAst> nodes = parse("where (%a%>1 || %b%<2) && %c%==3");
            assertEquals(1, nodes.size());
            ActionAst.Stage stage = (ActionAst.Stage) nodes.get(0);
            assertEquals("where", stage.id());
            assertEquals(List.of("(%a%>1", "||", "%b%<2)", "&&", "%c%==3"), stage.positional());
        }

        @Test
        @DisplayName("|| inside a condition does not split the pipeline")
        void logicalOrDoesNotSplitPipeline() {
            List<ActionAst> nodes = parse("nearby radius=8 | where %a%>1 || %b%<2 | damage amount=5");
            assertEquals(3, nodes.size());
            assertEquals("nearby", ((ActionAst.Stage) nodes.get(0)).id());
            assertEquals("where", ((ActionAst.Stage) nodes.get(1)).id());
            assertEquals(List.of("%a%>1", "||", "%b%<2"), ((ActionAst.Stage) nodes.get(1)).positional());
            assertEquals("damage", ((ActionAst.Stage) nodes.get(2)).id());
        }

        @Test
        @DisplayName("a quoted value may itself contain an equals sign")
        void quotedValueMayContainEquals() {
            ActionAst.Stage stage = (ActionAst.Stage) parse("run_command_as_console command=\"say a=b\"").get(0);
            assertEquals("say a=b", stage.arguments().get("command"));
        }
    }
}
