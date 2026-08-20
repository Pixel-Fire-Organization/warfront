/**
 * Client/server networking: packet definitions and the channel they travel on. Every packet handler
 * re-validates its payload against live server state rather than trusting the client.
 */
package org.pixelfire.nationwars.net;
