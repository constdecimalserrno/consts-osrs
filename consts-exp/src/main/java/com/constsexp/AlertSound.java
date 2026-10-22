package com.constsexp;

import net.runelite.api.SoundEffectID;

/**
 * What every alert sounds like: the system beep, or one of the game's own sound effects. Custom plays any sound
 * effect by its id, set in the Sound section.
 */
public enum AlertSound
{
    SYSTEM_BEEP("System beep", -1),
    TOWN_CRIER_BELL("Town crier bell", SoundEffectID.TOWN_CRIER_BELL_DING),
    GRAND_EXCHANGE_OFFER("Grand Exchange offer", SoundEffectID.GE_ADD_OFFER_DINGALING),
    COINS("Coins", SoundEffectID.GE_COIN_TINKLE),
    PRAYER_DRAINED("Prayer drained", SoundEffectID.PRAYER_DEPLETE_TWINKLE),
    TELEPORT("Teleport", SoundEffectID.TELEPORT_VWOOP),
    TREE_FALLING("Tree falling", SoundEffectID.TREE_FALLING),
    BOOP("Boop", SoundEffectID.UI_BOOP),
    ANVIL("Anvil", SoundEffectID.SMITH_ANVIL_TINK),
    CUSTOM("Custom sound ID", -1);

    private final String label;
    private final int soundEffectId;

    AlertSound(String label, int soundEffectId)
    {
        this.label = label;
        this.soundEffectId = soundEffectId;
    }

    /** The game sound effect to play, or -1 for the system beep. */
    int soundEffectId(ConstsExpConfig config)
    {
        return this == CUSTOM ? Math.max(-1, config.customSoundId()) : soundEffectId;
    }

    @Override
    public String toString()
    {
        return label;
    }
}
