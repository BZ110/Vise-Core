package org.albi.core.modules;

import org.albi.core.Core;

public class Module {

    private final Core plugin;


    public Module(Core plugin) {
        this.plugin = plugin;
    }

    /**
     * Initialize the module, this must be overwritten.
     */
    public void init() {

    }

    public Core getPlugin() {
        return plugin;
    }
}
