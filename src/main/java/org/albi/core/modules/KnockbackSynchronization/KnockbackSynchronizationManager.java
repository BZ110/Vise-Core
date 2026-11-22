package org.albi.core.modules.KnockbackSynchronization;

import org.albi.core.Core;
import org.albi.core.modules.Module;

public final class KnockbackSynchronizationManager extends Module {

    private KnockbackSynchronizationConfig config;
    private KnockbackSynchronizationService service;

    public KnockbackSynchronizationManager(Core plugin) {
        super(plugin);
    }

    @Override
    public void init() {
        getPlugin().getLogger().info("[KnockbackSynchronization] Initializing");

        this.config = new KnockbackSynchronizationConfig(getPlugin());
        this.service = new KnockbackSynchronizationService(getPlugin(), config);
        this.service.start();
    }

    public KnockbackSynchronizationConfig getConfig() {
        return config;
    }

    public KnockbackSynchronizationService getService() {
        return service;
    }
}
