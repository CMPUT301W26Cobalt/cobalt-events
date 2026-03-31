package com.example.cobaltevents.db;

import static org.junit.Assert.*;

import com.example.cobaltevents.model.LotteryErrorCodes;
import com.example.cobaltevents.model.WaitingList;

import org.junit.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Unit tests for the private static helper methods in {@link WaitingListDB} via reflection.
 *
 * Methods under test:
 *  - {@code entryDocId(WaitingList)}                                 – doc-ID extraction with fallback
 *  - {@code uniqueEntryDocIds(List<WaitingList>)}                    – ordered deduplication
 *  - {@code pickReplacementAssigneesWithCapacity(List, int, int, int)} – lottery selection math
 */
public class WaitingListDBTest {

    // ── Reflection helpers ────────────────────────────────────────────────────

    private static Method entryDocIdMethod() throws Exception {
        Method m = WaitingListDB.class.getDeclaredMethod("entryDocId", WaitingList.class);
        m.setAccessible(true);
        return m;
    }

    private static Method uniqueEntryDocIdsMethod() throws Exception {
        Method m = WaitingListDB.class.getDeclaredMethod("uniqueEntryDocIds", List.class);
        m.setAccessible(true);
        return m;
    }

    private static Method pickMethod() throws Exception {
        Method m = WaitingListDB.class.getDeclaredMethod(
                "pickReplacementAssigneesWithCapacity",
                List.class, int.class, int.class, int.class);
        m.setAccessible(true);
        return m;
    }

    /** Invoke pickReplacementAssigneesWithCapacity, unwrapping the InvocationTargetException. */
    @SuppressWarnings("unchecked")
    private static List<WaitingList> pick(
            List<WaitingList> candidates, int requested, int spotsAvailable, int capacity)
            throws Exception {
        try {
            return (List<WaitingList>) pickMethod().invoke(null, candidates, requested, spotsAvailable, capacity);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            throw e;
        }
    }

    private static WaitingList entry(String id, String deviceId, String status) {
        WaitingList w = new WaitingList();
        w.setId(id);
        w.setDeviceId(deviceId);
        w.setStatus(status);
        return w;
    }

    // ── entryDocId ────────────────────────────────────────────────────────────

    @Test
    public void entryDocId_null_returnsNull() throws Exception {
        assertNull(entryDocIdMethod().invoke(null, (Object) null));
    }

    @Test
    public void entryDocId_withId_returnsId() throws Exception {
        WaitingList w = entry("doc-001", "dev-abc", WaitingList.STATUS_PENDING);
        assertEquals("doc-001", entryDocIdMethod().invoke(null, w));
    }

    @Test
    public void entryDocId_emptyId_fallsBackToDeviceId() throws Exception {
        WaitingList w = entry("", "dev-abc", WaitingList.STATUS_PENDING);
        assertEquals("dev-abc", entryDocIdMethod().invoke(null, w));
    }

    @Test
    public void entryDocId_nullId_fallsBackToDeviceId() throws Exception {
        WaitingList w = entry(null, "dev-xyz", WaitingList.STATUS_PENDING);
        assertEquals("dev-xyz", entryDocIdMethod().invoke(null, w));
    }

    @Test
    public void entryDocId_bothNullOrEmpty_returnsNull() throws Exception {
        WaitingList w = entry(null, null, WaitingList.STATUS_PENDING);
        assertNull(entryDocIdMethod().invoke(null, w));
    }

    @Test
    public void entryDocId_emptyBoth_returnsNull() throws Exception {
        WaitingList w = entry("", "", WaitingList.STATUS_PENDING);
        assertNull(entryDocIdMethod().invoke(null, w));
    }

    // ── uniqueEntryDocIds ─────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    public void uniqueEntryDocIds_null_returnsEmptyList() throws Exception {
        List<String> result = (List<String>) uniqueEntryDocIdsMethod().invoke(null, (Object) null);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void uniqueEntryDocIds_emptyList_returnsEmptyList() throws Exception {
        List<String> result = (List<String>) uniqueEntryDocIdsMethod()
                .invoke(null, new ArrayList<WaitingList>());
        assertTrue(result.isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void uniqueEntryDocIds_noDuplicates_preservesOrder() throws Exception {
        List<WaitingList> input = Arrays.asList(
                entry("a", null, WaitingList.STATUS_PENDING),
                entry("b", null, WaitingList.STATUS_PENDING),
                entry("c", null, WaitingList.STATUS_PENDING));
        List<String> result = (List<String>) uniqueEntryDocIdsMethod().invoke(null, input);
        assertEquals(Arrays.asList("a", "b", "c"), result);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void uniqueEntryDocIds_withDuplicates_deduplicates() throws Exception {
        List<WaitingList> input = Arrays.asList(
                entry("a", null, WaitingList.STATUS_PENDING),
                entry("a", null, WaitingList.STATUS_PENDING),
                entry("b", null, WaitingList.STATUS_PENDING));
        List<String> result = (List<String>) uniqueEntryDocIdsMethod().invoke(null, input);
        assertEquals(2, result.size());
        assertEquals("a", result.get(0));
        assertEquals("b", result.get(1));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void uniqueEntryDocIds_nullIdEntries_skipped() throws Exception {
        List<WaitingList> input = Arrays.asList(
                entry(null, null, WaitingList.STATUS_PENDING),   // no id at all
                entry("good", null, WaitingList.STATUS_PENDING));
        List<String> result = (List<String>) uniqueEntryDocIdsMethod().invoke(null, input);
        assertEquals(1, result.size());
        assertEquals("good", result.get(0));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void uniqueEntryDocIds_usesDeviceIdFallback() throws Exception {
        // Id is null, deviceId provides the doc id
        WaitingList w = entry(null, "dev-fallback", WaitingList.STATUS_PENDING);
        List<String> result = (List<String>) uniqueEntryDocIdsMethod()
                .invoke(null, Arrays.asList(w));
        assertEquals(1, result.size());
        assertEquals("dev-fallback", result.get(0));
    }

    // ── pickReplacementAssigneesWithCapacity ──────────────────────────────────

    @Test
    public void pick_zeroRequested_returnsEmptyList() throws Exception {
        List<WaitingList> candidates = Arrays.asList(
                entry("a", null, WaitingList.STATUS_PENDING));
        List<WaitingList> result = pick(candidates, 0, 5, 10);
        assertTrue(result.isEmpty());
    }

    @Test
    public void pick_emptyCandidates_returnsEmptyList() throws Exception {
        List<WaitingList> result = pick(new ArrayList<>(), 3, 5, 10);
        assertTrue(result.isEmpty());
    }

    @Test
    public void pick_noCapacity_returnsRequestedCount() throws Exception {
        // capacity <= 0 means unlimited — just shuffle and return requestedCount
        List<WaitingList> candidates = Arrays.asList(
                entry("a", null, WaitingList.STATUS_PENDING),
                entry("b", null, WaitingList.STATUS_PENDING),
                entry("c", null, WaitingList.STATUS_PENDING));
        List<WaitingList> result = pick(candidates, 2, 5, 0);
        assertEquals(2, result.size());
    }

    @Test
    public void pick_onlyPendingCandidates_selectsCorrectCount() throws Exception {
        List<WaitingList> candidates = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            candidates.add(entry("p" + i, null, WaitingList.STATUS_PENDING));
        }
        List<WaitingList> result = pick(candidates, 3, 3, 10);
        assertEquals(3, result.size());
    }

    @Test
    public void pick_onlySelectedCandidates_selectsCorrectCount() throws Exception {
        // Already-selected entries consume no capacity spots
        List<WaitingList> candidates = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            candidates.add(entry("s" + i, null, WaitingList.STATUS_SELECTED));
        }
        List<WaitingList> result = pick(candidates, 3, 0, 10);
        assertEquals(3, result.size());
    }

    @Test
    public void pick_mixedCandidates_totalEqualsRequested() throws Exception {
        List<WaitingList> candidates = Arrays.asList(
                entry("p1", null, WaitingList.STATUS_PENDING),
                entry("p2", null, WaitingList.STATUS_PENDING),
                entry("s1", null, WaitingList.STATUS_SELECTED),
                entry("s2", null, WaitingList.STATUS_SELECTED));
        List<WaitingList> result = pick(candidates, 3, 2, 10);
        assertEquals(3, result.size());
    }

    @Test
    public void pick_noCapacityAvailableButAllAlreadySelected_succeeds() throws Exception {
        // spotsAvailable=0 but we can still pick SELECTED entries (they consume no spot)
        List<WaitingList> candidates = Arrays.asList(
                entry("s1", null, WaitingList.STATUS_SELECTED),
                entry("s2", null, WaitingList.STATUS_SELECTED));
        List<WaitingList> result = pick(candidates, 2, 0, 10);
        assertEquals(2, result.size());
    }

    @Test
    public void pick_notEnoughCapacityForAllPending_throwsIllegalArgument() throws Exception {
        // 2 pending candidates, 0 spots, capacity set → need at least 1 new slot but 0 available
        List<WaitingList> candidates = Arrays.asList(
                entry("p1", null, WaitingList.STATUS_PENDING),
                entry("p2", null, WaitingList.STATUS_PENDING));
        try {
            pick(candidates, 2, 0, 5);
            fail("Expected IllegalArgumentException with NO_CAPACITY");
        } catch (IllegalArgumentException e) {
            assertEquals(LotteryErrorCodes.NO_CAPACITY, e.getMessage());
        }
    }

    @Test
    public void pick_notSelectedTreatedLikePending() throws Exception {
        // NOT_SELECTED candidates also consume capacity slots (same bucket as PENDING)
        List<WaitingList> candidates = Arrays.asList(
                entry("ns1", null, WaitingList.STATUS_NOT_SELECTED),
                entry("ns2", null, WaitingList.STATUS_NOT_SELECTED),
                entry("ns3", null, WaitingList.STATUS_NOT_SELECTED));
        List<WaitingList> result = pick(candidates, 2, 2, 10);
        assertEquals(2, result.size());
    }

    @Test
    public void pick_resultDoesNotContainDuplicates() throws Exception {
        // Run several times (random shuffle) and verify no entry appears twice
        List<WaitingList> candidates = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            candidates.add(entry("e" + i, null, WaitingList.STATUS_PENDING));
        }
        for (int trial = 0; trial < 20; trial++) {
            List<WaitingList> result = pick(candidates, 4, 4, 10);
            assertEquals(4, result.size());
            long distinct = result.stream().map(WaitingList::getId).distinct().count();
            assertEquals("Duplicates found in trial " + trial, 4, distinct);
        }
    }
}
