package org.bukkit.craftbukkit.command;

import java.util.HashMap;

import org.bukkit.Server;
import org.bukkit.command.SimpleCommandMap;

public class CraftCommandMap extends SimpleCommandMap {

    public CraftCommandMap(Server server) {
        super(server, new HashMap<>());
    }
}
