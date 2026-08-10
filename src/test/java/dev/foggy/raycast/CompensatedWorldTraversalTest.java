package dev.foggy.raycast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

class CompensatedWorldTraversalTest {
    @Test
    void diagonalBoundaryTieOrderMatchesMojangTraversal() throws ReflectiveOperationException {
        List<String> cells = new ArrayList<>();

        assertNull(CompensatedWorld.traverse(
                new Vector(0.5, 0.5, 0.5), new Vector(2.5, 2.5, 2.5),
                (x, y, z) -> {
                    cells.add(x + "," + y + "," + z);
                    return null;
                }));

        assertEquals(List.of(
                "0,0,0", "0,0,1", "0,1,1", "1,1,1",
                "1,1,2", "1,2,2", "2,2,2"), cells);
    }

    @Test
    void negativeCoordinatesUseFloorRatherThanIntegerTruncation() throws ReflectiveOperationException {
        List<String> cells = new ArrayList<>();

        CompensatedWorld.traverse(new Vector(-0.1, 1.0, -0.1), new Vector(-2.1, 1.0, -0.1),
                (x, y, z) -> {
                    cells.add(x + "," + y + "," + z);
                    return null;
                });

        // Mojang's Double.MAX_VALUE * frac(1.0) starts at zero for the stationary Y
        // axis, producing one harmless duplicate visit before X advances.
        assertEquals(List.of("-1,1,-1", "-1,1,-1", "-2,1,-1", "-3,1,-1"), cells);
    }

    @Test
    void zeroLengthSegmentDoesNotVisitACell() throws ReflectiveOperationException {
        List<String> cells = new ArrayList<>();
        Vector point = new Vector(1.0, 2.0, 3.0);

        assertNull(CompensatedWorld.traverse(point, point.clone(), (x, y, z) -> {
            cells.add("unexpected");
            return null;
        }));
        assertEquals(List.of(), cells);
    }
}
