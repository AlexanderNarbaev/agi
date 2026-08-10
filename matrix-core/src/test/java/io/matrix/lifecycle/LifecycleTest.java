package io.matrix.lifecycle;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LifecycleTest {

    @Test
    void cauldronCreateAndValidate() {
        var cauldron = new CauldronProtocol();
        var e = cauldron.create("e1", "fnl", Map.of(), CauldronProtocol.Budget.unlimited(), 0.5);
        assertEquals(CauldronProtocol.Status.QUARANTINED, e.status());

        var result = cauldron.validate("e1", 0.6);
        assertTrue(result.promoted());
        assertEquals(CauldronProtocol.Status.PROMOTED, cauldron.status("e1"));
    }

    @Test
    void cauldronRollbackOnLowPhi() {
        var cauldron = new CauldronProtocol();
        cauldron.create("e1", "fnl", Map.of(), CauldronProtocol.Budget.unlimited(), 0.8);
        var result = cauldron.validate("e1", 0.5);
        assertFalse(result.promoted());
        assertEquals(CauldronProtocol.Status.ROLLED_BACK, cauldron.status("e1"));
        assertEquals(1, cauldron.totalRollbacks());
    }

    @Test
    void cauldronEventLog() {
        var cauldron = new CauldronProtocol();
        cauldron.create("e1", "fnl", Map.of(), CauldronProtocol.Budget.unlimited(), 0.5);
        cauldron.validate("e1", 0.6);
        var events = cauldron.events();
        assertEquals(2, events.size());
        assertEquals("created", events.get(0).action());
        assertEquals("promoted", events.get(1).action());
    }

    @Test
    void taskCellLifecycle() {
        var cell = new TaskCell("compute 2+2", Map.of(), 5000);
        assertEquals(TaskCell.State.CREATED, cell.state());

        cell.execute((task, ctx) -> "4");
        assertEquals(TaskCell.State.COMPLETED, cell.state());
        assertEquals("4", cell.result());

        cell.destroy();
        assertEquals(TaskCell.State.DESTROYED, cell.state());
    }

    @Test
    void taskCellFailure() {
        var cell = new TaskCell("fail", Map.of(), 5000);
        cell.execute((task, ctx) -> { throw new RuntimeException("boom"); });
        assertEquals(TaskCell.State.FAILED, cell.state());
        assertEquals("boom", cell.error());
    }

    @Test
    void taskCellTimeout() throws InterruptedException {
        var cell = new TaskCell("slow", Map.of(), 1); // 1ms timeout
        Thread.sleep(5); // ensure timeout
        assertTrue(cell.isTimeout());
    }

    @Test
    void taskCellUniqueIds() {
        var ids = new java.util.HashSet<String>();
        for (int i = 0; i < 100; i++) {
            ids.add(new TaskCell("t", Map.of(), 1000).id());
        }
        assertEquals(100, ids.size());
    }
}
