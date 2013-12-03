package com.cloudata.appendlog;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;

import org.robotninjas.barge.RaftException;
import org.robotninjas.barge.RaftService;
import org.robotninjas.barge.StateMachine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cloudata.appendlog.LogProto.LogEntry;
import com.cloudata.appendlog.log.LogFileSet;
import com.cloudata.appendlog.log.LogMessageIterable;
import com.google.common.base.Throwables;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

public class Database implements StateMachine {
    private static final Logger log = LoggerFactory.getLogger(Database.class);

    // private final Map<byte[], byte[]> db = Maps.newConcurrentMap();
    private RaftService raft;
    private File baseDir;
    final LoadingCache<Long, LogFileSet> logCache;

    public Database() {
        LogCacheLoader loader = new LogCacheLoader();
        this.logCache = CacheBuilder.newBuilder().recordStats().build(loader);
    }

    public void init(RaftService raft, File stateDir) {
        this.raft = raft;
        this.baseDir = stateDir;
    }

    // public byte[] get(byte[] key) {
    // return db.get(key);
    // }

    public LogMessageIterable readLog(long logId, long begin, int max) {
        LogFileSet logFileSet = getLogFileSet(logId);
        return logFileSet.readLog(logId, begin, max);
    }

    // public long commitIndex() {
    // return log.commitIndex();
    // }

    public boolean appendToLog(long logId, byte[] value) throws InterruptedException, RaftException {
        ByteBuffer buffer = ByteBuffer.wrap(value);

        // LogFileSet logFileSet = getLogFileSet(logId);
        // long offset = logFileSet.prepareWrite(buffer);

        LogEntry entry = LogEntry.newBuilder().setLogId(logId)
        /* .setOffset(offset) */
        .setValue(ByteString.copyFrom(buffer)).build();

        log.debug("Proposing operation {}", entry);

        return raft.commit(entry.toByteArray());
    }

    @Override
    public void applyOperation(@Nonnull ByteBuffer op) {
        // TODO: We need to prevent repetition during replay
        // (we need idempotency)
        try {
            LogEntry entry = LogEntry.parseFrom(ByteString.copyFrom(op));
            log.debug("Committing operation {}", entry);

            long logId = entry.getLogId();
            // long offset = entry.getOffset();
            ByteString value = entry.getValue();

            LogFileSet logFileSet = getLogFileSet(logId);
            logFileSet.append(value.asReadOnlyByteBuffer());
            // db.put(key.toByteArray(), value.toByteArray());
        } catch (InvalidProtocolBufferException e) {
            log.error("Error deserializing operation", e);
            throw new IllegalArgumentException("Error deserializing log entry", e);
        } catch (Throwable e) {
            log.error("Error writing log entry", e);
            throw new IllegalArgumentException("Error writing log entry", e);
        }
    }

    private LogFileSet getLogFileSet(long logId) {
        try {
            return logCache.get(logId);
        } catch (ExecutionException e) {
            throw Throwables.propagate(e);
        }
    }

    // @Override
    // public void setLog(RaftLog log) {
    // this.log = log;
    // }

    @Immutable
    final class LogCacheLoader extends CacheLoader<Long, LogFileSet> {

        @Override
        public LogFileSet load(@Nonnull Long logId) throws Exception {
            checkNotNull(logId);
            try {
                log.debug("Loading {}", logId);
                File logDir = new File(baseDir, Long.toHexString(logId));

                logDir.mkdirs();

                return new LogFileSet(logDir);
            } catch (Exception e) {
                log.warn("Error building log", e);
                throw e;
            }
        }
    }
}
