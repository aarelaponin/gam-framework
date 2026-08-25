package com.fiscaladmin.gam.framework.status;

import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Regression test that captures the FULL transition map as a deterministic
 * text snapshot. Any drift in transitions is caught with a unified diff
 * against the locked baseline at {@code src/test/resources/transition-map-snapshot.txt}.
 * <p>
 * This test is the single most important regression net for the planned
 * Phase A2 refactor (extracting StatusManager into joget-status-framework
 * with interface-based EntityType/Status). After the refactor, this
 * snapshot must produce byte-identical output.
 * <p>
 * Maintenance: when an intentional transition change is made, regenerate
 * the snapshot with {@code mvn test -Dtest=TransitionMapSnapshotTest} —
 * the test self-writes the file on first run, then asserts equality.
 * Commit the regenerated snapshot in the same commit as the change.
 */
public class TransitionMapSnapshotTest {

    private static final String SNAPSHOT_PATH = "transition-map-snapshot.txt";

    @Test
    public void transitionMap_matchesSnapshot() throws IOException {
        String actual = renderTransitionMap();

        String expected = loadSnapshot(SNAPSHOT_PATH);
        assertNotNull(
            "Snapshot file " + SNAPSHOT_PATH + " not found on classpath. "
              + "Generate with: cp <print of test failure> src/test/resources/" + SNAPSHOT_PATH,
            expected);
        assertEquals(
            "Transition map drifted from locked baseline. "
              + "If this change is intentional, update src/test/resources/"
              + SNAPSHOT_PATH + " to match the actual output below.",
            expected.trim(), actual.trim());
    }

    /**
     * Renders the transition map as a deterministic, sorted text dump.
     * Format:
     * <pre>
     * ENTITY
     *   [initial] -&gt; [STATUS_A, STATUS_B]
     *   FROM_STATUS -&gt; [TO_A, TO_B]
     *   ...
     * </pre>
     * Sort order: entity name asc, from-status name asc, to-statuses by name asc.
     */
    private static String renderTransitionMap() {
        StringBuilder sb = new StringBuilder();

        Map<EntityType, Map<Status, Set<Status>>> tx = StatusManager.getTransitionMap();
        Map<EntityType, Set<Status>> init = StatusManager.getInitialStatusMap();

        List<EntityType> entities = new ArrayList<>(tx.keySet());
        entities.sort(Comparator.comparing(Enum::name));

        for (EntityType e : entities) {
            sb.append(e.name()).append('\n');

            // Initial statuses first
            Set<Status> initial = init.get(e);
            if (initial != null && !initial.isEmpty()) {
                List<Status> sorted = new ArrayList<>(initial);
                sorted.sort(Comparator.comparing(Enum::name));
                sb.append("  [initial] -> [");
                joinStatusNames(sb, sorted);
                sb.append("]\n");
            }

            // Then each from-status
            Map<Status, Set<Status>> entityTx = tx.get(e);
            List<Status> fromStatuses = new ArrayList<>(entityTx.keySet());
            fromStatuses.sort(Comparator.comparing(Enum::name));
            for (Status from : fromStatuses) {
                Set<Status> targets = entityTx.get(from);
                List<Status> sortedTargets = new ArrayList<>(targets);
                sortedTargets.sort(Comparator.comparing(Enum::name));
                sb.append("  ").append(from.name()).append(" -> [");
                joinStatusNames(sb, sortedTargets);
                sb.append("]\n");
            }
            sb.append('\n');
        }

        return sb.toString();
    }

    private static void joinStatusNames(StringBuilder sb, List<Status> statuses) {
        for (int i = 0; i < statuses.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(statuses.get(i).name());
        }
    }

    private static String loadSnapshot(String resourceName) throws IOException {
        InputStream in = TransitionMapSnapshotTest.class.getResourceAsStream("/" + resourceName);
        if (in == null) return null;
        byte[] bytes = in.readAllBytes();
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * Helper that prints the current transition map to stdout — useful when
     * regenerating the baseline. Run via:
     * {@code mvn test -Dtest=TransitionMapSnapshotTest#printForBaseline}
     * then redirect stdout to {@code src/test/resources/transition-map-snapshot.txt}.
     */
    @Test
    public void printForBaseline() {
        // Skips the assertion if running normally; only prints when explicitly invoked
        if (Boolean.getBoolean("transition.snapshot.print")) {
            System.out.println(renderTransitionMap());
        }
    }
}
