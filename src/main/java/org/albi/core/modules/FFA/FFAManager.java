package org.albi.core.modules.FFA;

import org.albi.core.Core;
import org.albi.core.modules.Module;
import org.albi.core.modules.FFA.helpers.FFAStorage;
import org.albi.core.modules.FFA.managers.CommandManager;
import org.albi.core.modules.FFA.managers.EventManager;

public final class FFAManager extends Module {

    public static FFAStorage stor;

    public FFAManager(Core plugin) { super(plugin); }

    @Override
    public void init() {
        Object o = getPlugin().getData().get("ffa:storage");
        if (o instanceof FFAStorage s) stor = s; else stor = new FFAStorage();
        // register events & commands
        new EventManager(getPlugin(), this);
        new CommandManager(getPlugin(), this);
    }

    /** Persist storage back into Core.Data */
    public static void persist(Core plugin) {
        plugin.getData().set("ffa:storage", stor);
    }
}