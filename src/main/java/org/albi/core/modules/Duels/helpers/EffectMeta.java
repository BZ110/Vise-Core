package org.albi.core.modules.Duels.helpers;

import java.io.Serial;
import java.io.Serializable;

public class EffectMeta implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String effect;     // SPEED, STRENGTH, etc.
    private int amplifier;    // 0 = level 1
    private int duration;     // seconds
    private boolean ambient;  // particles / icon flag

    public EffectMeta() {}

    public EffectMeta(String effect, int amplifier, int duration, boolean ambient) {
        this.effect = effect;
        this.amplifier = amplifier;
        this.duration = duration;
        this.ambient = ambient;
    }

    public String getEffect() { return effect; }
    public int getAmplifier() { return amplifier; }
    public int getDuration() { return duration; }
    public boolean isAmbient() { return ambient; }

    public void setEffect(String effect) { this.effect = effect; }
    public void setAmplifier(int amplifier) { this.amplifier = amplifier; }
    public void setDuration(int duration) { this.duration = duration; }
    public void setAmbient(boolean ambient) { this.ambient = ambient; }
}
