package com.cloudata.appendlog;

import java.nio.ByteBuffer;

import javax.annotation.Nonnull;

import org.robotninjas.barge.LogAware;
import org.robotninjas.barge.RaftException;
import org.robotninjas.barge.RaftService;
import org.robotninjas.barge.StateMachine;
import org.robotninjas.barge.log.GetEntriesResult;
import org.robotninjas.barge.log.RaftLog;

import com.cloudata.appendlog.LogProto.LogEntry;
import com.google.protobuf.ByteString;

public class Database implements StateMachine, LogAware {

    // private final Map<byte[], byte[]> db = Maps.newConcurrentMap();
    private RaftService raft;
    private RaftLog log;

    public void init(RaftService raft) {
        this.raft = raft;
    }

    // public byte[] get(byte[] key) {
    // return db.get(key);
    // }

    public GetEntriesResult getEntriesFrom(long begin, int max) {
        return log.getEntriesFrom(begin, max);
    }

    public long commitIndex() {
        return log.commitIndex();
    }

    public boolean appendToLog(byte[] value) throws InterruptedException, RaftException {
        LogEntry entry = LogEntry.newBuilder()
        /* .setKey(ByteString.copyFrom(key)) */
        .setValue(ByteString.copyFrom(value)).build();
        return raft.commit(entry.toByteArray());
    }

    @Override
    public void applyOperation(long position, @Nonnull ByteBuffer op) {
        // try {
        // LogEntry entry = LogEntry.parseFrom(ByteString.copyFrom(op));
        // // ByteString key = entry.getKey();
        // ByteString value = entry.getValue();
        // // db.put(key.toByteArray(), value.toByteArray());
        // } catch (InvalidProtocolBufferException e) {
        // throw new IllegalArgumentException("Error deserializing log entry", e);
        // }
    }

    @Override
    public void setLog(RaftLog log) {
        this.log = log;
    }
}
