package com.cloudata.appendlog.btree;

import java.nio.ByteBuffer;

public interface EntryListener {

    public boolean found(ByteBuffer key, ByteBuffer value);
}
