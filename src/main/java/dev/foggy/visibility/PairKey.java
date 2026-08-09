package dev.foggy.visibility;

import java.util.UUID;

/**
 * Directed visibility relation: viewer sees target.
 *
 * @param viewerId receiving player's UUID
 * @param targetId target player's UUID
 */
public record PairKey(UUID viewerId, UUID targetId) {
}
