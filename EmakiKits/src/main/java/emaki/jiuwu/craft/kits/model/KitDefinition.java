package emaki.jiuwu.craft.kits.model;

import emaki.jiuwu.craft.corelib.gui.GuiTemplate;

import java.util.List;

public record KitDefinition(String id,
                            KitType type,
                            boolean enabled,
                            String permission,
                            String displayName,
                            JoinRule join,
                            InviteRule invite,
                            CycleRule cycle,
                            CdkRule cdk,
                            ChoiceRule choice,
                            PurchaseRule purchase,
                            OpenRule open,
                             LimitRule limit,
                            RewardGroup fixedRewards,
                            RewardGroup randomRewards,
                            int maxClaimsTotal,
                            List<ConfigIssue> issues) {

    public record JoinRule(boolean onFirstJoin, boolean onEverySpecifiedJoin,
                           boolean requireInviteCode, String inviteCodeType,
                           long missedWindowSeconds, List<String> worlds) {
        public JoinRule {
            worlds = worlds == null ? List.of() : List.copyOf(worlds);
            inviteCodeType = inviteCodeType == null ? "uuid" : inviteCodeType;
            missedWindowSeconds = Math.max(0L, missedWindowSeconds);
        }
    }

    public record InviteRule(String rewardTarget, boolean requireNewPlayer,
                             boolean allowMutual, int maxBindsPerInvitee) {
        public InviteRule {
            rewardTarget = rewardTarget == null ? "INVITER" : rewardTarget;
        }
    }

    public record CycleRule(String timeZone, boolean allowMakeup) {
        public CycleRule {
            timeZone = timeZone == null ? "Asia/Shanghai" : timeZone;
        }
    }

    public record CdkRule(CdkMode mode, List<String> codes, int perPlayerLimit) {
        public enum CdkMode { GLOBAL_ONCE, PER_PLAYER_ONCE }
        public CdkRule {
            codes = codes == null ? List.of() : List.copyOf(codes);
            perPlayerLimit = Math.max(1, perPlayerLimit);
        }
    }

    public record ChoiceRule(int minSelect, int maxSelect, String guiTemplate,
                             List<ChoiceOption> options, GuiTemplate inlineGui) {
        public ChoiceRule {
            options = options == null ? List.of() : List.copyOf(options);
            guiTemplate = guiTemplate == null ? "" : guiTemplate;
        }
        public boolean valid() {
            return minSelect >= 0 && maxSelect >= minSelect && !options.isEmpty();
        }
        public boolean hasGui() {
            return inlineGui != null || !guiTemplate.isBlank();
        }
        public boolean usesInlineGui() {
            return inlineGui != null;
        }
    }

    public record PurchaseRule(boolean enabled,
                               double price,
                               String economyProvider,
                               String currencyId,
                               int maxPurchases,
                               boolean countPassToNested) {
        public PurchaseRule {
            economyProvider = economyProvider == null ? "auto" : economyProvider;
            currencyId = currencyId == null ? "" : currencyId;
        }
    }

    public record OpenRule(boolean enabled, int maxOpens, List<OpenMilestone> milestones) {
        public OpenRule {
            milestones = milestones == null ? List.of() : List.copyOf(milestones);
        }
    }

    public record LimitRule(boolean enabled, String mode, String start, String end,
                            String cron, long durationSeconds, String timeZone) {
        public LimitRule {
            mode = mode == null ? "always" : mode;
            start = start == null ? "" : start;
            end = end == null ? "" : end;
            cron = cron == null ? "" : cron;
            timeZone = timeZone == null ? "Asia/Shanghai" : timeZone;
            durationSeconds = Math.max(0L, durationSeconds);
        }
    }

    public record ConfigIssue(String path, String reasonKey) {}

    public KitDefinition {
        issues = issues == null ? List.of() : List.copyOf(issues);
        permission = permission == null ? "emakikits.claim" : permission;
        displayName = displayName == null ? id : displayName;
        limit = limit == null ? new LimitRule(false, "always", "", "", "", 0L, "Asia/Shanghai") : limit;
        choice = choice == null ? new ChoiceRule(0, 0, "", List.of(), null) : choice;
    }

    public boolean hasTimeLimit() {
        return limit != null && limit.enabled();
    }

    public boolean hasIssues() {
        return !issues.isEmpty();
    }

    public boolean isCyclic() {
        return type == KitType.DAILY || type == KitType.WEEKLY || type == KitType.MONTHLY;
    }

    public boolean hasPurchase() {
        return purchase != null && purchase.enabled();
    }

    public boolean hasOpenCount() {
        return open != null && open.enabled();
    }
}
