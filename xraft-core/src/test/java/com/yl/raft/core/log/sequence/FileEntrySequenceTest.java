package com.yl.raft.core.log.sequence;

import com.yl.raft.core.log.entry.Entry;
import com.yl.raft.core.log.entry.NoOpEntry;
import com.yl.raft.core.support.ByteArraySeekableFile;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

/**
 * FileEntrySequenceTest
 */
public class FileEntrySequenceTest {

    private EntryIndexFile entryIndexFile;
    private EntriesFile entriesFile;

    @Before
    public void setUp() throws IOException {
        entryIndexFile = new EntryIndexFile(new ByteArraySeekableFile());
        entriesFile = new EntriesFile(new ByteArraySeekableFile());
    }

    @Test
    public void testInitialize() throws IOException {
        entryIndexFile.appendEntryIndex(1, 0L, 1, 1);
        entryIndexFile.appendEntryIndex(2, 20L, 1, 1);
        FileEntrySequence sequence = new FileEntrySequence(entriesFile, entryIndexFile, 1);
        Assert.assertEquals(3, sequence.getNextLogIndex());
        Assert.assertEquals(1, sequence.getFirstLogIndex());
        Assert.assertEquals(2, sequence.getLastLogIndex());
        Assert.assertEquals(2, sequence.getCommitIndex());
    }

    @Test
    public void testAppendEntry() {
        FileEntrySequence sequence = new FileEntrySequence(entriesFile, entryIndexFile, 1);
        Assert.assertEquals(1, sequence.getNextLogIndex());
        sequence.append(new NoOpEntry(1, 1));
        Assert.assertEquals(2, sequence.getNextLogIndex());
        Assert.assertEquals(1, sequence.getLastEntry().getIndex());
    }

    private void appendEntryToFIle(Entry entry) throws IOException {
        long offset = entriesFile.appendEntry(entry);
        entryIndexFile.appendEntryIndex(entry.getIndex(), offset, entry.getKind(), entry.getTerm());
    }

    @Test
    public void testGetEntry() throws IOException {
        appendEntryToFIle(new NoOpEntry(1, 1));
        FileEntrySequence sequence = new FileEntrySequence(entriesFile, entryIndexFile, 1);
        sequence.append(new NoOpEntry(2, 1));
        Assert.assertNull(sequence.getEntry(0));
        Assert.assertEquals(1, sequence.getEntry(1).getIndex());
        Assert.assertEquals(2, sequence.getEntry(2).getIndex());
        Assert.assertNull(sequence.getEntry(3));
    }

    @Test
    public void testSubList2() throws IOException {
        appendEntryToFIle(new NoOpEntry(1, 1));
        appendEntryToFIle(new NoOpEntry(2, 2));
        FileEntrySequence sequence = new FileEntrySequence(entriesFile, entryIndexFile, 1);
        sequence.append(new NoOpEntry(sequence.getNextLogIndex(), 3));
        sequence.append(new NoOpEntry(sequence.getNextLogIndex(), 4));
        List<Entry> entries = sequence.subList(2);
        Assert.assertEquals(3, entries.size());
        Assert.assertEquals(2, entries.get(0).getIndex());
        Assert.assertEquals(4, entries.get(2).getIndex());
    }

    @Test
    public void testRemoveAfterEntriesInFile() throws IOException {
        appendEntryToFIle(new NoOpEntry(1, 1));
        appendEntryToFIle(new NoOpEntry(2, 1));
        FileEntrySequence sequence = new FileEntrySequence(entriesFile, entryIndexFile, 1);
        sequence.append(new NoOpEntry(sequence.getNextLogIndex(), 2));
        Assert.assertEquals(1, sequence.getFirstLogIndex());
        Assert.assertEquals(3, sequence.getLastLogIndex());
        sequence.removeAfter(1);
        Assert.assertEquals(1, sequence.getFirstLogIndex());
        Assert.assertEquals(1, sequence.getLastLogIndex());
    }
}
