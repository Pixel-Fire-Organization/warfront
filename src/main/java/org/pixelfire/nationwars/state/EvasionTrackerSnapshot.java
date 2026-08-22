package org.pixelfire.nationwars.state;

import net.minecraft.nbt.CompoundTag;

/**
 * Round-trips an {@link EvasionTracker} (keyed by {@link EvasionKey}) through NBT for persistence.
 */
public final class EvasionTrackerSnapshot
{
    private EvasionTrackerSnapshot()
    {
    }

    public static CompoundTag write(final EvasionTracker tracker)
    {
        final CompoundTag tag = new CompoundTag();
        tag.putUUID("warId", tracker.warId());
        tag.putUUID("nationId", tracker.nationId());
        tag.putLong("evasionAccruedMs", tracker.evasionAccruedMs());
        tag.putLong("qualifyingReadyMs", tracker.qualifyingReadyMs());
        tag.putInt("lastWarnedThresholdPercent", tracker.lastWarnedThresholdPercent());
        return tag;
    }

    public static EvasionTracker read(final CompoundTag tag)
    {
        return new EvasionTracker(
                tag.getUUID("warId"),
                tag.getUUID("nationId"),
                tag.getLong("evasionAccruedMs"),
                tag.getLong("qualifyingReadyMs"),
                tag.getInt("lastWarnedThresholdPercent"));
    }
}
