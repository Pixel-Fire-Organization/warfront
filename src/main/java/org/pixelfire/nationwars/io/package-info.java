/**
 * The I/O layer: audit log and persistence writes, each owned by a single dedicated writer thread so
 * disk I/O never blocks the main thread or a worker.
 */
package org.pixelfire.nationwars.io;
