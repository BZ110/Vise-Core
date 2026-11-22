package org.albi.core.modules.AntiGhostArrow;

import org.albi.core.Core;
import org.albi.core.modules.Module;

public final class AntiGhostArrowManager extends Module {

    private AntiGhostArrowConfig config;
    private AntiGhostArrowService service;

    public AntiGhostArrowManager(Core plugin) {
        super(plugin);
    }

    @Override
    public void init() {
        getPlugin().getLogger().info("[AntiGhostArrow] Initializing");

        this.config = new AntiGhostArrowConfig(getPlugin());
        this.service = new AntiGhostArrowService(getPlugin(), config);
        this.service.start();
    }

    public AntiGhostArrowConfig getConfig() {
        return config;
    }

    public AntiGhostArrowService getService() {
        return service;
    }
}
