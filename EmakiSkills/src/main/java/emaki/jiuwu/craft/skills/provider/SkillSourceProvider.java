package emaki.jiuwu.craft.skills.provider;

import java.util.Collection;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.skills.model.UnlockedSkillEntry;

public interface SkillSourceProvider {

    String id();

    int priority();

    Collection<UnlockedSkillEntry> collect(Player player);
}
