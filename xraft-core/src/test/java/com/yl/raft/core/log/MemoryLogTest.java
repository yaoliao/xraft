package com.yl.raft.core.log;

import com.yl.raft.core.log.entry.Entry;
import com.yl.raft.core.log.entry.NoOpEntry;
import com.yl.raft.core.node.NodeId;
import com.yl.raft.core.rpc.message.AppendEntriesRpc;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/**
 * MemoryLogTest
 */
public class MemoryLogTest {

    @Test
    public void testCreateAppendEntriesRpcStartFromOne() {
        MemoryLog log = new MemoryLog();
        log.appendEntry(1);
        log.appendEntry(1);
        AppendEntriesRpc rpc = log.createAppendEntriesRpc(1, new NodeId("A"), 1, Log.ALL_ENTRIES);
        Assert.assertEquals(1, rpc.getTerm());
        Assert.assertEquals(0, rpc.getPrevLogIndex());
        Assert.assertEquals(0, rpc.getPrevLogTerm());
        Assert.assertEquals(2, rpc.getEntries().size());
        Assert.assertEquals(1, rpc.getEntries().get(0).getIndex());
    }

    // (index, term)
    // follower: (1, 1), (2, 1)
    // leader  :         (2, 1), (3, 2)
    @Test
    public void testAppendEntriesFromLeaderSkip() {
        MemoryLog log = new MemoryLog();
        log.appendEntry(1);
        log.appendEntry(1);
        List<Entry> leaderEntries = Arrays.asList(
                new NoOpEntry(2, 1),
                new NoOpEntry(3, 2)
        );
        Assert.assertTrue(log.appendEntriesFromLeader(1, 1, leaderEntries).isSuccess());
        Assert.assertEquals(4, log.getNextIndex());
    }

    // (index, term)
    // follower: (1, 1), (2, 1)
    // leader  :         (2, 2), (3, 2)
    @Test
    public void testAppendEntriesFromLeaderConflict1() {
        MemoryLog log = new MemoryLog();
        log.appendEntry(1);
        log.appendEntry(1);
        List<Entry> leaderEntries = Arrays.asList(
                new NoOpEntry(2, 2),
                new NoOpEntry(3, 2)
        );
        Assert.assertTrue(log.appendEntriesFromLeader(1, 1, leaderEntries).isSuccess());
        Assert.assertEquals(2, log.entrySequence.getEntryMeta(2).getTerm());
        Assert.assertEquals(4, log.getNextIndex());
    }

}
