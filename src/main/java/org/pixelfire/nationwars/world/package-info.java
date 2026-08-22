/**
 * The world layer: everything that must run on the main server thread because it touches block/entity
 * state, packet dispatch, or the Open Parties and Claims API. Code outside this package should treat
 * the world as inaccessible and ask the world layer to act on its behalf instead.
 */
package org.pixelfire.nationwars.world;
