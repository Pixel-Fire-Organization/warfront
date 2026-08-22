package org.pixelfire.nationwars.io.audit;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonWriter;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Converts one {@link AuditEntry} to and from a single JSONL line. {@code before}/{@code after} are
 * stored as their SNBT text (the same format {@code CompoundTag#toString()} produces and
 * {@code /data get} accepts) rather than as nested JSON, so this doesn't need its own NBT-to-JSON
 * mapping.
 */
public final class AuditEntryJson
{
    private AuditEntryJson()
    {
    }

    public static String toJsonLine(final AuditEntry entry) throws IOException
    {
        final StringWriter out = new StringWriter();
        try (JsonWriter writer = new JsonWriter(out))
        {
            writer.beginObject();
            writer.name("entryId").value(entry.entryId());
            writer.name("timestamp").value(entry.timestamp());
            writeUuid(writer, "actorUuid", entry.actorUuid());
            writer.name("actorName").value(entry.actorName());
            writeUuid(writer, "actorNationId", entry.actorNationId());
            writer.name("actorRole").value(entry.actorRole().name());
            writer.name("source").value(entry.source().name());
            writer.name("actionType").value(entry.actionType().toString());
            writer.name("targets").beginArray();
            for (final UUID target : entry.targets())
            {
                writer.value(target.toString());
            }
            writer.endArray();
            writer.name("before").value(entry.before().toString());
            writer.name("after").value(entry.after().toString());
            writer.name("reversible").value(entry.reversible());
            writeNullableString(writer, "revertOf", entry.revertOf());
            writeNullableString(writer, "revertedBy", entry.revertedBy());
            writer.endObject();
        }
        return out.toString();
    }

    public static AuditEntry fromJsonLine(final String line) throws IOException
    {
        // Broad catch is deliberate: malformed input can throw Gson's JsonParseException,
        // IllegalStateException/IllegalArgumentException from a bad field, or TagParser's checked
        // CommandSyntaxException — a caller reading a whole file just wants "this line is bad."
        try
        {
            final JsonObject obj = JsonParser.parseString(line).getAsJsonObject();
            return new AuditEntry(
                    obj.get("entryId").getAsString(),
                    obj.get("timestamp").getAsLong(),
                    readUuid(obj, "actorUuid"),
                    obj.get("actorName").getAsString(),
                    readUuid(obj, "actorNationId"),
                    ActorRole.valueOf(obj.get("actorRole").getAsString()),
                    AuditSource.valueOf(obj.get("source").getAsString()),
                    parseResourceLocation(obj.get("actionType").getAsString()),
                    readTargets(obj.getAsJsonArray("targets")),
                    TagParser.parseTag(obj.get("before").getAsString()),
                    TagParser.parseTag(obj.get("after").getAsString()),
                    obj.get("reversible").getAsBoolean(),
                    readNullableString(obj, "revertOf"),
                    readNullableString(obj, "revertedBy"));
        }
        catch (final CommandSyntaxException | RuntimeException e)
        {
            throw new IOException("malformed audit entry line: " + line, e);
        }
    }

    private static ResourceLocation parseResourceLocation(final String raw) throws IOException
    {
        final ResourceLocation parsed = ResourceLocation.tryParse(raw);
        if (parsed == null)
        {
            throw new IOException("malformed actionType id in audit entry: " + raw);
        }
        return parsed;
    }

    private static List<UUID> readTargets(final JsonArray array)
    {
        final List<UUID> targets = new ArrayList<>(array.size());
        for (final JsonElement element : array)
        {
            targets.add(UUID.fromString(element.getAsString()));
        }
        return targets;
    }

    private static void writeUuid(final JsonWriter writer, final String name, final UUID value) throws IOException
    {
        writer.name(name);
        if (value == null)
        {
            writer.nullValue();
        }
        else
        {
            writer.value(value.toString());
        }
    }

    private static UUID readUuid(final JsonObject obj, final String name)
    {
        final JsonElement element = obj.get(name);
        return element == null || element.isJsonNull() ? null : UUID.fromString(element.getAsString());
    }

    private static void writeNullableString(final JsonWriter writer, final String name, final String value) throws IOException
    {
        writer.name(name);
        if (value == null)
        {
            writer.nullValue();
        }
        else
        {
            writer.value(value);
        }
    }

    private static String readNullableString(final JsonObject obj, final String name)
    {
        final JsonElement element = obj.get(name);
        return element == null || element.isJsonNull() ? null : element.getAsString();
    }
}
