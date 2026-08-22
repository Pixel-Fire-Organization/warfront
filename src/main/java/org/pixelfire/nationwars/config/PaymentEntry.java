package org.pixelfire.nationwars.config;

/**
 * One priced entry from {@code payments.values}: either an item id or, when {@code itemOrTag}
 * starts with {@code #}, a tag — tags are matched after exact ids.
 */
public record PaymentEntry(String itemOrTag, boolean tag, long value)
{
}
