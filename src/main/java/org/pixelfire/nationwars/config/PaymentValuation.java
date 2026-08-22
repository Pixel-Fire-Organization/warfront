package org.pixelfire.nationwars.config;

import java.util.List;
import java.util.OptionalLong;
import java.util.function.Predicate;

/**
 * What a payment slot insertion is worth, given the parsed {@code payments.values} list. Exact item ids
 * are checked before tags, matching the order the config comment documents. Kept pure (tag matching is
 * injected as a predicate) so it's testable without booting Forge's item/tag registries.
 */
public final class PaymentValuation
{
    private PaymentValuation()
    {
    }

    public static OptionalLong valueOf(final String itemId, final Predicate<String> matchesTag, final List<PaymentEntry> entries)
    {
        for (final PaymentEntry entry : entries)
        {
            if (!entry.tag() && entry.itemOrTag().equals(itemId))
            {
                return OptionalLong.of(entry.value());
            }
        }
        for (final PaymentEntry entry : entries)
        {
            if (entry.tag() && matchesTag.test(entry.itemOrTag()))
            {
                return OptionalLong.of(entry.value());
            }
        }
        return OptionalLong.empty();
    }

    /**
     * The compressed block form of a priced ingot/gem (e.g. {@code minecraft:iron_ingot} to
     * {@code minecraft:iron_block}) is worth {@code blockMultiplier} times its single-item value,
     * derived from the {@code _block}/{@code _ingot} naming convention every vanilla default follows —
     * a custom ore not following it can still be priced directly as its own config entry.
     */
    public static OptionalLong blockFormValueOf(final String blockItemId, final Predicate<String> matchesTag,
            final List<PaymentEntry> entries, final int blockMultiplier)
    {
        if (!blockItemId.endsWith("_block"))
        {
            return OptionalLong.empty();
        }
        final String withoutBlockSuffix = blockItemId.substring(0, blockItemId.length() - "_block".length());
        final OptionalLong asIngot = valueOf(withoutBlockSuffix + "_ingot", matchesTag, entries);
        if (asIngot.isPresent())
        {
            return OptionalLong.of(asIngot.getAsLong() * blockMultiplier);
        }
        final OptionalLong asBareItem = valueOf(withoutBlockSuffix, matchesTag, entries);
        return asBareItem.isPresent() ? OptionalLong.of(asBareItem.getAsLong() * blockMultiplier) : OptionalLong.empty();
    }
}
