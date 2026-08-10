package dev.foggy.packet;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe relationship index for plugin-owned renderer and vehicle entities. */
final class RelatedEntityRegistry {
    private final ConcurrentHashMap<Integer, Set<Integer>> byPrimary = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Integer> primaryByRelated = new ConcurrentHashMap<>();

    /** Associates an additional network entity with the authoritative player entity. */
    void register(int primaryEntityId, int relatedEntityId) {
        if (primaryEntityId == relatedEntityId) {
            return;
        }
        Integer previous = primaryByRelated.put(relatedEntityId, primaryEntityId);
        if (previous != null && previous != primaryEntityId) {
            removeFromPrimary(previous, relatedEntityId);
        }
        byPrimary.computeIfAbsent(primaryEntityId, ignored -> ConcurrentHashMap.newKeySet())
                .add(relatedEntityId);
    }

    /** Removes one renderer relationship after the owning plugin destroys it. */
    void unregister(int primaryEntityId, int relatedEntityId) {
        primaryByRelated.remove(relatedEntityId, primaryEntityId);
        removeFromPrimary(primaryEntityId, relatedEntityId);
    }

    /** Removes and returns every renderer belonging to one obsolete primary entity id. */
    Set<Integer> removePrimary(int primaryEntityId) {
        Set<Integer> removed = byPrimary.remove(primaryEntityId);
        if (removed == null || removed.isEmpty()) {
            return Collections.emptySet();
        }
        Set<Integer> snapshot = new java.util.HashSet<Integer>(removed);
        for (int relatedEntityId : snapshot) {
            primaryByRelated.remove(relatedEntityId, primaryEntityId);
        }
        return snapshot;
    }

    /** Returns a stable snapshot of renderer ids associated with one player id. */
    Set<Integer> related(int primaryEntityId) {
        Set<Integer> ids = byPrimary.get(primaryEntityId);
        return ids == null || ids.isEmpty()
                ? Collections.<Integer>emptySet() : Collections.unmodifiableSet(
                        new java.util.HashSet<Integer>(ids));
    }

    /** Returns the authoritative player id for a renderer id, or the supplied id when unrelated. */
    int primaryOrSelf(int entityId) {
        Integer primary = primaryByRelated.get(entityId);
        return primary == null ? entityId : primary;
    }

    /** Clears every relationship during full plugin shutdown. */
    void clear() {
        byPrimary.clear();
        primaryByRelated.clear();
    }

    private void removeFromPrimary(int primaryEntityId, int relatedEntityId) {
        Set<Integer> ids = byPrimary.get(primaryEntityId);
        if (ids == null) {
            return;
        }
        ids.remove(relatedEntityId);
        if (ids.isEmpty()) {
            byPrimary.remove(primaryEntityId, ids);
        }
    }
}
