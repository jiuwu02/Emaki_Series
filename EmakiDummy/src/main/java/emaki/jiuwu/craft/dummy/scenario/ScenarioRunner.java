package emaki.jiuwu.craft.dummy.scenario;

import emaki.jiuwu.craft.dummy.log.DummyLogger;
import emaki.jiuwu.craft.dummy.log.LogLevel;
import emaki.jiuwu.craft.dummy.session.DummyManager;
import emaki.jiuwu.craft.dummy.session.DummySession;

public final class ScenarioRunner {

    private static final int DEFAULT_WAIT_TIMEOUT_SECONDS = 60;
    private static final int WALK_TIMEOUT_EXTRA_SECONDS = 30;

    private final ScenarioDefinition definition;
    private final DummyManager manager;
    private final DummyLogger logger;
    private final ExpressionContext context;
    private final ScenarioRuntime runtime;
    private final long startedAt;
    private final long overallDeadlineAt;
    private final int overallTimeoutSeconds;

    private int stepIndex;
    private boolean done;
    private boolean stopRequested;
    private int passedCount;
    private int failedCount;
    private String result = "PASS";
    private boolean waiting;
    private String waitKind = "";
    private String currentAction = "";
    private boolean stepContinueOnFail;
    private long stepStartedAt;
    private long stepDeadlineAt;
    private double eventSnapshot;
    private String waitParamOne;
    private String waitParamTwo;
    private String lastExpectDetail = "";

    public ScenarioRunner(ScenarioDefinition definition, DummyManager manager, DummyLogger logger,
            ExpressionContext context, ScenarioRuntime runtime) {
        this.definition = definition;
        this.manager = manager;
        this.logger = logger;
        this.context = context;
        this.runtime = runtime;
        this.startedAt = runtime.currentTimeMillis();
        this.overallTimeoutSeconds = definition.timeoutSeconds() > 0
                ? definition.timeoutSeconds()
                : 300;
        this.overallDeadlineAt = startedAt + overallTimeoutSeconds * 1000L;
    }

    public synchronized void tick() {
        if (done) {
            return;
        }
        long now = runtime.currentTimeMillis();
        if (stopRequested) {
            logScenario("stop_requested at step=" + stepIndex);
            finish("FAIL");
            return;
        }
        if (now > overallDeadlineAt) {
            failedCount++;
            logAssert(currentAction, "FAIL",
                    "reason=scenario_timeout timeout=" + overallTimeoutSeconds + "s");
            finish("FAIL");
            return;
        }
        if (waiting && !advanceWait(now)) {
            return;
        }
        while (!done && !waiting && stepIndex < definition.steps().size()) {
            executeCurrentStep(now);
        }
        if (!done && !waiting && stepIndex >= definition.steps().size()) {
            finish(failedCount > 0 ? "FAIL" : "PASS");
        }
    }

    public synchronized void requestStop() {
        stopRequested = true;
    }

    public synchronized boolean isDone() {
        return done;
    }

    public String id() {
        return definition.id();
    }

    public String result() {
        return result;
    }

    public int passedCount() {
        return passedCount;
    }

    public int failedCount() {
        return failedCount;
    }

    public long elapsedMs() {
        return runtime.currentTimeMillis() - startedAt;
    }

    private void executeCurrentStep(long now) {
        ScenarioStep step = definition.steps().get(stepIndex);
        currentAction = step.action();
        stepContinueOnFail = step.continueOnFail();
        logScenario("step=" + stepIndex + "/" + definition.steps().size()
                + " action=" + step.action() + " " + step.summary());
        switch (step.action()) {
            case "join_all" -> executeJoinAll(step, now);
            case "leave_all" -> {
                for (String player : definition.players()) {
                    manager.leave(player);
                }
                passedCount++;
                stepIndex++;
            }
            case "cmd" -> dispatchAction(step,
                    manager.runCommand(step.param("player"), step.param("command")),
                    "cmd=" + step.param("command"));
            case "say" -> dispatchAction(step,
                    manager.say(step.param("player"), step.param("text")),
                    "text=" + step.param("text"));
            case "look" -> executeLook(step);
            case "interact" -> executeInteract(step);
            case "attack" -> executeAttack(step);
            case "click" -> executeClick(step);
            case "walk" -> executeWalk(step, now);
            case "sleep" -> {
                int seconds = secondsOf(step.param("seconds"), 1);
                openWait("sleep", now, seconds);
                logScenario("step=" + stepIndex + " waiting sleep seconds=" + seconds);
            }
            case "wait_event" -> executeEventWait(step, now);
            case "wait_log" -> executeLogWait(step, now);
            case "expect" -> executeExpectWait(step, now);
            case "assert" -> evaluateAssert(step);
            default -> failStep(step, "unsupported_action", step.action());
        }
    }

    private void executeLook(ScenarioStep step) {
        Double yaw = step.numberParam("yaw");
        Double pitch = step.numberParam("pitch");
        if (yaw == null || pitch == null) {
            failStep(step, "invalid_param", "yaw/pitch");
            return;
        }
        dispatchAction(step, manager.look(step.param("player"), yaw.floatValue(),
                pitch.floatValue()), "yaw=" + yaw + " pitch=" + pitch);
    }

    private void executeWalk(ScenarioStep step, long now) {
        Double x = step.numberParam("x");
        Double y = step.numberParam("y");
        Double z = step.numberParam("z");
        if (x == null || y == null || z == null) {
            failStep(step, "invalid_param", "x/y/z");
            return;
        }
        boolean started = manager.startWalk(step.param("player"), x, y, z);
        if (!started) {
            failStep(step, "action_failed", "walk");
            return;
        }
        waitParamOne = step.param("player");
        openWait("walk", now,
                step.timeoutSeconds(DEFAULT_WAIT_TIMEOUT_SECONDS + WALK_TIMEOUT_EXTRA_SECONDS));
        logScenario("step=" + stepIndex + " waiting walk player=" + waitParamOne
                + " timeout=" + step.timeoutSeconds(
                        DEFAULT_WAIT_TIMEOUT_SECONDS + WALK_TIMEOUT_EXTRA_SECONDS) + "s");
    }

    private void executeInteract(ScenarioStep step) {
        Double x = step.numberParam("x");
        Double y = step.numberParam("y");
        Double z = step.numberParam("z");
        boolean anyCoord = x != null || y != null || z != null;
        boolean allCoords = x != null && y != null && z != null;
        if (anyCoord && !allCoords) {
            failStep(step, "invalid_param", "x/y/z must be given together");
            return;
        }
        DummyManager.InteractOutcome outcome = manager.interact(step.param("player"),
                allCoords ? x : null, allCoords ? y : null, allCoords ? z : null);
        if (outcome.code() == 0) {
            passedCount++;
            stepIndex++;
            return;
        }
        failStep(step, outcome.code() == -2 ? "no_pose" : "action_failed", "interact");
    }

    private void executeAttack(ScenarioStep step) {
        int entityId = manager.attack(step.param("player"));
        if (entityId == -1) {
            failStep(step, "no_target", "attack");
            return;
        }
        if (entityId == -2) {
            failStep(step, "action_failed", "attack");
            return;
        }
        passedCount++;
        stepIndex++;
    }

    private void executeClick(ScenarioStep step) {
        Integer slot = step.numberParam("slot") == null
                ? null
                : step.numberParam("slot").intValue();
        if (slot == null) {
            failStep(step, "invalid_param", "slot");
            return;
        }
        int outcome = manager.click(step.param("player"), slot);
        if (outcome == 0) {
            passedCount++;
            stepIndex++;
            return;
        }
        failStep(step, outcome == -2 ? "no_window" : "action_failed", "click slot=" + slot);
    }

    private void executeEventWait(ScenarioStep step, long now) {
        String type = step.param("type");
        runtime.ensureWatching(type);
        eventSnapshot = 0;
        waitParamOne = type;
        waitParamTwo = step.param("where");
        int timeout = step.timeoutSeconds(DEFAULT_WAIT_TIMEOUT_SECONDS);
        openWait("event", now, timeout);
        logScenario("step=" + stepIndex + " waiting event type=" + type
                + " timeout=" + timeout + "s"
                + (waitParamTwo == null ? "" : " where=" + waitParamTwo));
    }

    private void executeLogWait(ScenarioStep step, long now) {
        waitParamOne = step.param("file");
        waitParamTwo = step.param("contains");
        int timeout = step.timeoutSeconds(DEFAULT_WAIT_TIMEOUT_SECONDS);
        openWait("log", now, timeout);
        logScenario("step=" + stepIndex + " waiting log file=" + waitParamOne
                + " timeout=" + timeout + "s contains=" + waitParamTwo);
    }

    private void executeExpectWait(ScenarioStep step, long now) {
        waitParamOne = step.param("expr");
        lastExpectDetail = "";
        int timeout = step.timeoutSeconds(DEFAULT_WAIT_TIMEOUT_SECONDS);
        openWait("expect", now, timeout);
        logScenario("step=" + stepIndex + " waiting expect expr=" + waitParamOne
                + " timeout=" + timeout + "s");
    }

    private void executeJoinAll(ScenarioStep step, long now) {
        for (String player : definition.players()) {
            if (manager.find(player) != null) {
                continue;
            }
            DummyManager.JoinResult result = manager.join(player);
            if (result.outcome() == DummyManager.JoinOutcome.LIMIT
                    || result.outcome() == DummyManager.JoinOutcome.INVALID_NAME
                    || result.outcome() == DummyManager.JoinOutcome.FAILED) {
                failStep(step, "join_failed",
                        player + " outcome=" + result.outcome() + " detail=" + result.detail());
                return;
            }
        }
        beginJoinWait(step, now);
    }

    private void beginJoinWait(ScenarioStep step, long now) {
        int timeout = step.timeoutSeconds(DEFAULT_WAIT_TIMEOUT_SECONDS);
        openWait("online", now, timeout);
        logScenario("step=" + stepIndex + " waiting online players="
                + String.join(",", definition.players()) + " timeout=" + timeout + "s");
    }

    private void openWait(String kind, long now, int timeoutSeconds) {
        waiting = true;
        waitKind = kind;
        stepStartedAt = now;
        stepDeadlineAt = now + timeoutSeconds * 1000L;
    }

    private void dispatchAction(ScenarioStep step, boolean ok, String detail) {
        if (ok) {
            passedCount++;
            stepIndex++;
            return;
        }
        failStep(step, "action_failed", detail);
    }

    private void evaluateAssert(ScenarioStep step) {
        String expr = step.param("expr");
        try {
            ExpressionEngine.Value value = ExpressionEngine.evaluate(expr, context);
            boolean truthy = value.isTruthy();
            logAssert(expr, truthy ? "PASS" : "FAIL", "actual=" + value.describe());
            if (truthy) {
                passedCount++;
                stepIndex++;
                return;
            }
            failedCount++;
            if (stepContinueOnFail) {
                stepIndex++;
                return;
            }
            finish("FAIL");
        } catch (ExpressionException failure) {
            logAssert(expr, "FAIL", "error=" + failure.getMessage());
            failedCount++;
            if (stepContinueOnFail) {
                stepIndex++;
                return;
            }
            finish("FAIL");
        }
    }

    private boolean advanceWait(long now) {
        ScenarioStep step = definition.steps().get(stepIndex);
        if (conditionMet(now)) {
            waiting = false;
            logScenario("step=" + stepIndex + " condition_met wait=" + waitKind
                    + " elapsed=" + (now - stepStartedAt) + "ms");
            clearWait();
            passedCount++;
            stepIndex++;
            return true;
        }
        if (now < stepDeadlineAt) {
            return false;
        }
        String kind = waitKind;
        String detail = waitParamOne == null ? "" : waitParamOne;
        boolean continueOnFail = stepContinueOnFail;
        failedCount++;
        String expr = step.param("expr") != null ? step.param("expr") : "wait:" + kind;
        logAssert(expr, "FAIL", "reason=timeout wait=" + kind + " detail=" + detail
                + (lastExpectDetail.isEmpty() ? "" : " last=" + lastExpectDetail));
        clearWait();
        waiting = false;
        if (continueOnFail) {
            stepIndex++;
            return true;
        }
        finish("FAIL");
        return false;
    }

    private void clearWait() {
        waitKind = "";
        waitParamOne = null;
        waitParamTwo = null;
        lastExpectDetail = "";
    }

    private boolean conditionMet(long now) {
        switch (waitKind) {
            case "sleep" -> {
                return now >= stepDeadlineAt;
            }
            case "online" -> {
                for (String player : definition.players()) {
                    DummySession session = manager.find(player);
                    if (session == null || !session.isOnline()) {
                        return false;
                    }
                }
                return true;
            }
            case "walk" -> {
                DummySession session = manager.find(waitParamOne);
                if (session == null) {
                    return true;
                }
                return !session.walkActive();
            }
            case "event" -> {
                return runtime.eventMatched(waitParamOne, waitParamTwo, eventSnapshot);
            }
            case "log" -> {
                return runtime.logContains(waitParamOne, waitParamTwo);
            }
            case "expect" -> {
                try {
                    ExpressionEngine.Value value = ExpressionEngine.evaluate(waitParamOne, context);
                    lastExpectDetail = "actual=" + value.describe();
                    if (value.isTruthy()) {
                        logAssert(waitParamOne, "PASS", lastExpectDetail);
                        return true;
                    }
                    return false;
                } catch (ExpressionException failure) {
                    lastExpectDetail = "error=" + failure.getMessage();
                    return false;
                }
            }
            default -> {
                return false;
            }
        }
    }

    private void failStep(ScenarioStep step, String reason, String detail) {
        failedCount++;
        String expr = step.param("expr") != null ? step.param("expr") : step.action();
        logAssert(expr, "FAIL", "reason=" + reason + " detail=" + detail);
        if (stepContinueOnFail) {
            stepIndex++;
            return;
        }
        finish("FAIL");
    }

    private void finish(String finalResult) {
        if (done) {
            return;
        }
        done = true;
        result = finalResult;
        waiting = false;
        for (String player : definition.players()) {
            manager.leave(player);
        }
        logger.log(LogLevel.ASSERT, definition.id(), "scenario=" + definition.id()
                + " result=" + finalResult
                + " passed=" + passedCount
                + " failed=" + failedCount
                + " elapsed=" + elapsedMs() + "ms");
    }

    private void logScenario(String message) {
        logger.log(LogLevel.SCENARIO, definition.id(), message);
    }

    private void logAssert(String expr, String verdict, String detail) {
        logger.log(LogLevel.ASSERT, definition.id(), "expr=" + expr
                + " result=" + verdict + " " + detail);
    }

    private static int secondsOf(String raw, int fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Math.max(0, Integer.parseInt(raw.trim()));
        } catch (NumberFormatException failure) {
            return fallback;
        }
    }
}
