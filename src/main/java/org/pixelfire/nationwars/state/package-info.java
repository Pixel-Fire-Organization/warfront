/**
 * The state layer: in-memory game state (cities, checkpoints, wars, coalitions, nation records) held
 * as immutable records in {@code NationRegistry}. Readable from any thread without locking; mutation
 * produces a new instance and cross-record consistency is guarded by striped locks keyed by id.
 */
package org.pixelfire.nationwars.state;
