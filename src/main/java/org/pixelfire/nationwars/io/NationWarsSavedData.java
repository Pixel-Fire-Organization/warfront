package org.pixelfire.nationwars.io;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.pixelfire.nationwars.state.City;
import org.pixelfire.nationwars.state.CitySnapshot;
import org.pixelfire.nationwars.state.Checkpoint;
import org.pixelfire.nationwars.state.CheckpointSnapshot;
import org.pixelfire.nationwars.state.EvasionKey;
import org.pixelfire.nationwars.state.EvasionTracker;
import org.pixelfire.nationwars.state.EvasionTrackerSnapshot;
import org.pixelfire.nationwars.state.NationRegistry;
import org.pixelfire.nationwars.state.NationState;
import org.pixelfire.nationwars.state.NationStateSnapshot;
import org.pixelfire.nationwars.state.PeaceSettlement;
import org.pixelfire.nationwars.state.PeaceSettlementSnapshot;
import org.pixelfire.nationwars.state.War;
import org.pixelfire.nationwars.state.WarSnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * The mod's persisted state, written to {@code world/data/nationwars.dat} via {@link PersistenceIo}
 * (not vanilla's {@code SavedData}/{@code DimensionDataStorage} mechanism, whose write path is
 * synchronous on the main thread): cities, checkpoints, wars, nation states, staged settlements and
 * evasion trackers, keyed the same way the live {@link NationRegistry} is. {@link #save} — building
 * the NBT tag from this instance's fields — runs on the main thread, since that's the only thread
 * allowed to touch anything derived from live state; the NBT encoding, gzip and actual file write all
 * happen after that, off the main thread, via {@link WriterThread}.
 *
 * <p>{@link #syncFromRegistry} snapshots the live registry into this instance; callers do that at
 * every force-save trigger the spec names (war phase transitions, occupations, settlements,
 * disbandments) — the moments losing unsaved progress would actually matter — via {@link
 * org.pixelfire.nationwars.NationWarsMod#forceSave()}.
 */
public final class NationWarsSavedData
{
    public static final int CURRENT_SCHEMA_VERSION = 2;

    private static final String KEY_SCHEMA_VERSION = "schemaVersion";

    private volatile List<City> cities = List.of();
    private volatile List<Checkpoint> checkpoints = List.of();
    private volatile List<War> wars = List.of();
    private volatile List<NationState> nationStates = List.of();
    private volatile List<PeaceSettlement> settlements = List.of();
    private volatile List<EvasionTracker> evasionTrackers = List.of();

    /**
     * Copies the live registry's collections into this instance and marks it dirty so vanilla's next
     * save cycle picks up the change. The registry is the live source of truth right up until this
     * call; this instance only ever holds a point-in-time copy for {@link #save} to serialize.
     */
    public void syncFromRegistry(final NationRegistry registry)
    {
        cities = List.copyOf(registry.cities().values());
        checkpoints = List.copyOf(registry.checkpoints().values());
        wars = List.copyOf(registry.wars().values());
        nationStates = List.copyOf(registry.nationStates().values());
        settlements = List.copyOf(registry.settlements().values());
        evasionTrackers = List.copyOf(registry.evasionTrackers().values());
    }

    /**
     * The inverse of {@link #syncFromRegistry}: populates a live registry from what was loaded,
     * resetting every checkpoint's capture progress to 0 first, per the spec — a restart's worth of
     * elapsed time makes any partial progress meaningless to preserve.
     */
    public void applyTo(final NationRegistry registry)
    {
        for (final City city : cities)
        {
            registry.cities().put(city.cityId(), city);
        }
        for (final Checkpoint checkpoint : checkpoints)
        {
            registry.checkpoints().put(checkpoint.checkpointId(), new Checkpoint(checkpoint.checkpointId(), checkpoint.cityId(),
                    checkpoint.dimension(), checkpoint.pos(), checkpoint.holderNationId(), 0f, null, checkpoint.status(),
                    checkpoint.claimedChunks(), checkpoint.lastEvaluatedTime(), checkpoint.placedBy(), checkpoint.placedAt()));
        }
        for (final War war : wars)
        {
            registry.wars().put(war.warId(), war);
        }
        for (final NationState nationState : nationStates)
        {
            registry.nationStates().put(nationState.nationId(), nationState);
        }
        for (final PeaceSettlement settlement : settlements)
        {
            registry.settlements().put(settlement.warId(), settlement);
        }
        for (final EvasionTracker tracker : evasionTrackers)
        {
            registry.evasionTrackers().put(new EvasionKey(tracker.warId(), tracker.nationId()), tracker);
        }
    }

    public CompoundTag save(final CompoundTag tag)
    {
        tag.putInt(KEY_SCHEMA_VERSION, CURRENT_SCHEMA_VERSION);
        tag.put("cities", writeList(cities, CitySnapshot::write));
        tag.put("checkpoints", writeList(checkpoints, CheckpointSnapshot::write));
        tag.put("wars", writeList(wars, WarSnapshot::write));
        tag.put("nationStates", writeList(nationStates, NationStateSnapshot::write));
        tag.put("settlements", writeList(settlements, PeaceSettlementSnapshot::write));
        tag.put("evasionTrackers", writeList(evasionTrackers, EvasionTrackerSnapshot::write));
        return tag;
    }

    public static NationWarsSavedData load(final CompoundTag rawTag)
    {
        final CompoundTag tag = migrate(rawTag);
        final NationWarsSavedData data = new NationWarsSavedData();
        data.cities = readList(tag.getList("cities", Tag.TAG_COMPOUND), CitySnapshot::read);
        data.checkpoints = readList(tag.getList("checkpoints", Tag.TAG_COMPOUND), CheckpointSnapshot::read);
        data.wars = readList(tag.getList("wars", Tag.TAG_COMPOUND), WarSnapshot::read);
        data.nationStates = readList(tag.getList("nationStates", Tag.TAG_COMPOUND), NationStateSnapshot::read);
        data.settlements = readList(tag.getList("settlements", Tag.TAG_COMPOUND), PeaceSettlementSnapshot::read);
        data.evasionTrackers = readList(tag.getList("evasionTrackers", Tag.TAG_COMPOUND), EvasionTrackerSnapshot::read);
        return data;
    }

    /**
     * Brings a compound written by an older schema version up to {@link #CURRENT_SCHEMA_VERSION}. v1
     * (the earlier stages' placeholder — a schema version and a dummy string, nothing else) has no
     * real game state to carry forward, so migrating it forward means only initializing every v2
     * collection as empty, not translating any actual data.
     */
    static CompoundTag migrate(final CompoundTag tag)
    {
        final int fromVersion = tag.contains(KEY_SCHEMA_VERSION) ? tag.getInt(KEY_SCHEMA_VERSION) : 0;
        if (fromVersion > CURRENT_SCHEMA_VERSION)
        {
            throw new IllegalStateException("nationwars save data schema version " + fromVersion
                    + " is newer than this build supports (" + CURRENT_SCHEMA_VERSION + "); refusing to load it and risk data loss");
        }
        if (fromVersion < 2)
        {
            return migrateV1ToV2(tag);
        }
        return tag;
    }

    private static CompoundTag migrateV1ToV2(final CompoundTag v1Tag)
    {
        final CompoundTag v2Tag = new CompoundTag();
        v2Tag.putInt(KEY_SCHEMA_VERSION, 2);
        for (final String key : List.of("cities", "checkpoints", "wars", "nationStates", "settlements", "evasionTrackers"))
        {
            v2Tag.put(key, new ListTag());
        }
        return v2Tag;
    }

    private static <T> ListTag writeList(final List<T> values, final java.util.function.Function<T, CompoundTag> writer)
    {
        final ListTag list = new ListTag();
        for (final T value : values)
        {
            list.add(writer.apply(value));
        }
        return list;
    }

    private static <T> List<T> readList(final ListTag list, final java.util.function.Function<CompoundTag, T> reader)
    {
        final List<T> values = new ArrayList<>(list.size());
        for (final Tag element : list)
        {
            values.add(reader.apply((CompoundTag) element));
        }
        return List.copyOf(values);
    }
}
