package com.fiscaladmin.gam.framework.status;

import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Captures the {@code INITIAL_STATUS_MAP} as a text snapshot.
 * <p>
 * The initial-status semantics — which statuses an entity can be created
 * with when it has no current status — is a critical part of the lifecycle
 * contract. This test makes any change visible as a snapshot diff.
 *
 * @see TransitionMapSnapshotTest for the full transition map
 */
public class InitialStatusMapSnapshotTest {

    private static final String SNAPSHOT_PATH = "initial-status-map-snapshot.txt";

    @Test
    public void initialStatusMap_matchesSnapshot() throws IOException {
        String actual = renderInitialStatusMap();
        String expected = loadSnapshot(SNAPSHOT_PATH);
        assertNotNull(
            "Snapshot file " + SNAPSHOT_PATH + " not found on classpath.",
            expected);
        assertEquals(
            "Initial-status map drifted from baseline.",
            expected.trim(), actual.trim());
    }

    private static String renderInitialStatusMap() {
        StringBuilder sb = new StringBuilder();
        Map<EntityType, Set<Status>> init = StatusManager.getInitialStatusMap();
        List<EntityType> entities = new ArrayList<>(init.keySet());
        entities.sort(Comparator.comparing(Enum::name));
        for (EntityType e : entities) {
            sb.append(e.name()).append(" -> [");
            List<Status> sorted = new ArrayList<>(init.get(e));
            sorted.sort(Comparator.comparing(Enum::name));
            for (int i = 0; i < sorted.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(sorted.get(i).name());
            }
            sb.append("]\n");
        }
        return sb.toString();
    }

    private static String loadSnapshot(String resourceName) throws IOException {
        InputStream in = InitialStatusMapSnapshotTest.class.getResourceAsStream("/" + resourceName);
        if (in == null) return null;
        byte[] bytes = in.readAllBytes();
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
