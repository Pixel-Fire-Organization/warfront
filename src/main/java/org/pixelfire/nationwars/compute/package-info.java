/**
 * The compute layer: work that can run on the shared worker pool because it is a pure function of a
 * state snapshot (claim set computation, war score aggregation, settlement validation, and similar).
 * A worker computes what should happen; only the main thread ever commits the result.
 */
package org.pixelfire.nationwars.compute;
