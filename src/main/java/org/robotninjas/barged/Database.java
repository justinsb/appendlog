package org.robotninjas.barged;

import com.google.common.collect.Maps;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import org.robotninjas.barge.RaftException;
import org.robotninjas.barge.RaftService;
import org.robotninjas.barge.StateMachine;

import javax.annotation.Nonnull;
import java.nio.ByteBuffer;
import java.util.Map;

import static org.robotninjas.barged.KvProto.KvEntry;

public class Database implements StateMachine {

  private final Map<byte[], byte[]> db = Maps.newConcurrentMap();
  private RaftService raft;

  public void init(RaftService raft) {
    this.raft = raft;
  }

  public byte[] get(byte[] key) {
    return db.get(key);
  }

  public void put(byte[] key, byte[] value) throws InterruptedException, RaftException {
    KvEntry entry = KvEntry.newBuilder()
      .setKey(ByteString.copyFrom(key))
      .setValue(ByteString.copyFrom(value))
      .build();
    raft.commit(entry.toByteArray());
  }

  @Override
  public void applyOperation(@Nonnull ByteBuffer op) {
    try {
      KvEntry entry = KvEntry.parseFrom(ByteString.copyFrom(op));
      ByteString key = entry.getKey();
      ByteString value = entry.getValue();
      db.put(key.toByteArray(), value.toByteArray());
    } catch (InvalidProtocolBufferException e) {
      e.printStackTrace();
    }
  }
}
