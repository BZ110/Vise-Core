package org.albi.core.modules.CustomKit;

import org.albi.core.Core;
import org.albi.core.modules.CustomKit.managers.CustomKitCommandManager;
import org.albi.core.modules.Module;

public class CustomKitManager extends Module {

    public CustomKitManager(Core plugin) {
        super(plugin);
    }

    @Override
    public void init() {
        getPlugin().getLogger().info("[CustomKit] Initializing");

        // We probably only need to execute commands.
        // Only load Command Manager
        getPlugin().getLogger().info("[CustomKit] Loading Command Manager");
        new CustomKitCommandManager(getPlugin());
    }
}