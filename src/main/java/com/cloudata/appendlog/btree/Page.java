package com.cloudata.appendlog.btree;

import java.nio.ByteBuffer;

public abstract class Page {
    protected final ByteBuffer buffer;

    protected Page(ByteBuffer buffer) {
        this.buffer = buffer;
    }

    public abstract boolean walk(Transaction txn, ByteBuffer from, EntryListener listener);

    public abstract void insert(Transaction txn, ByteBuffer key, ByteBuffer value);

    public abstract ByteBuffer getKeyLbound();
}
