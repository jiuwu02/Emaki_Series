package emaki.jiuwu.craft.corelib.action.builtin.v2;

import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionTarget;

/**
 * Maps a stage's declared domain to the target object CoreLib checks at registration time.
 *
 * <p>Requirement R2 says every stage declares its domain explicitly, so each builtin stage passes a
 * {@link CoreActionExecutionDomain} constant with a Javadoc reason. This class turns that constant
 * into a {@link CoreActionExecutionTarget}.</p>
 *
 * <p>A region-domain stage returns the domain marker without coordinates on purpose. The interpreter
 * derives the actual region owner from the subject it is about to act on, and filling coordinates here
 * would mean reading {@code Entity#getLocation()} while still on the calling thread — exactly the
 * off-thread read Folia forbids.</p>
 */
final class BuiltinDomains {

    private BuiltinDomains() {
    }

    static CoreActionExecutionTarget target(CoreActionExecutionDomain domain) {
        return switch (domain == null ? CoreActionExecutionDomain.UNDECLARED : domain) {
            case SERVER_GLOBAL -> CoreActionExecutionTarget.global();
            case CONTEXT_ENTITY -> CoreActionExecutionTarget.contextEntity();
            case LOCATION_REGION -> CoreActionExecutionTarget.location(null, 0D, 0D, 0D);
            case ASYNC_COMPUTE -> CoreActionExecutionTarget.asyncCompute();
            case UNDECLARED -> CoreActionExecutionTarget.undeclared();
        };
    }
}
