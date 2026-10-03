package net.mysterria.stuff.features.dungeons;

import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Stable audit identity for party objects that expose no id of their own. Parties are tracked by
 * object identity (weakly referenced, so a disbanded party is not pinned) in a bounded map.
 * Pure Java: never touches Bukkit, so it is safe from the async party-chat event.
 */
final class PartyTracker {

    static final String SOURCE_EXPOSED = "exposed";
    static final String SOURCE_CREATED = "leader_created";
    static final String SOURCE_FIRST_SEEN = "leader_first_seen";
    static final String SOURCE_ANONYMOUS = "anonymous_first_seen";

    private final BoundedMap<IdentityKey, PartyState> parties;

    PartyTracker(int capacity) {
        this.parties = new BoundedMap<>(capacity);
    }

    static UUID derive(UUID leader, long createdMillis) {
        String seed = "mythicdungeons-party:" + leader + ":" + createdMillis;
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }

    /** Id for a party first seen without a known leader (e.g. async chat): identity hash + time. */
    static UUID deriveAnonymous(Object party, long firstSeenMillis) {
        String seed = "mythicdungeons-party-anon:" + System.identityHashCode(party) + ":" + firstSeenMillis;
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }

    PartyState lookup(Object party) {
        return party == null ? null : parties.get(new IdentityKey(party));
    }

    PartyState track(Object party, UUID id, String source) {
        if (party == null || id == null) return null;
        return parties.computeIfAbsent(new IdentityKey(party), key -> new PartyState(id, source));
    }

    int size() {
        return parties.size();
    }

    static final class PartyState {
        private final UUID id;
        private final String source;
        private volatile int memberCount = -1;

        PartyState(UUID id, String source) {
            this.id = id;
            this.source = source;
        }

        UUID id() {
            return id;
        }

        String source() {
            return source;
        }

        /** Last member count observed on the main thread; -1 when never observed. */
        int memberCount() {
            return memberCount;
        }

        void memberCount(int count) {
            this.memberCount = count;
        }
    }

    private static final class IdentityKey {
        private final WeakReference<Object> ref;
        private final int hash;

        IdentityKey(Object referent) {
            this.ref = new WeakReference<>(referent);
            this.hash = System.identityHashCode(referent);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof IdentityKey key) || key.hash != hash) return false;
            Object mine = ref.get();
            return mine != null && mine == key.ref.get();
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }
}
