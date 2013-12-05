package com.cloudata.appendlog.btree;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class LeafPage extends Page {
    public static final byte PAGE_TYPE = 'L';

    private static final int INDEX_ENTRY_SIZE = 4;

    Mutable mutable;

    public LeafPage(ByteBuffer buffer) {
        super(buffer);
    }

    static class Mutable {
        final List<Entry> entries;
        int totalKeySize;
        int totalValueSize;

        int lbound(ByteBuffer find) {
            int minIndex = 0;
            int maxIndex = getEntryCount();
            int comparison;
            int mid;

            while (minIndex != maxIndex) {
                mid = (minIndex + maxIndex) / 2; // We're not going to overflow with sensible page sizes

                ByteBuffer midKey = getKey(mid);
                comparison = ByteBuffers.compare(find, midKey);

                if (comparison < 0) {
                    maxIndex = mid - 1;
                } else {
                    minIndex = mid;
                }
            }

            assert minIndex == maxIndex;

            return minIndex;
        }

        void insert(ByteBuffer key, ByteBuffer value) {
            int position = lbound(key);

            // TODO: Should we enforce uniqueness?

            entries.add(position, new Entry(key, value));

            totalKeySize += key.remaining();
            totalValueSize += value.remaining();
        }

        Mutable(LeafPage page) {
            int n = page.getEntryCount();

            this.entries = new ArrayList<Entry>(n);

            int pos = 0;

            ByteBuffer buffer = page.buffer;

            int keyStart = buffer.getShort(pos);
            int valueStart = buffer.getShort(pos + 2);

            for (int i = 0; i < n; i++) {
                int keyEnd = page.buffer.getShort(pos + INDEX_ENTRY_SIZE);
                int valueEnd = page.buffer.getShort(pos + INDEX_ENTRY_SIZE + 2);

                ByteBuffer key = buffer.duplicate();
                key.position(keyStart);
                key.limit(keyEnd);
                totalKeySize += key.remaining();

                ByteBuffer value = buffer.duplicate();
                value.position(valueStart);
                value.limit(valueEnd);
                totalValueSize += value.remaining();

                Entry entry = new Entry(key, value);
                entries.add(entry);

                keyStart = keyEnd;
                valueStart = valueEnd;
            }
        }

        public void write(ByteBuffer buffer) {
            assert buffer.position() == 0;

            buffer.put(PAGE_TYPE);
            buffer.put((byte) 0);

            short n = (short) entries.size();
            buffer.putShort(n);

            short keyStart = (short) ((INDEX_ENTRY_SIZE * (n + 1)));
            short valueStart = (short) (keyStart + totalKeySize);

            for (int i = 0; i < n; i++) {
                buffer.putShort(keyStart);
                buffer.putShort(valueStart);

                Entry entry = entries.get(i);
                keyStart += entry.key.remaining();
                valueStart += entry.value.remaining();
            }

            // Write a dummy tail entry so we know the total sizes
            buffer.putShort(keyStart);
            buffer.putShort(valueStart);

            for (int i = 0; i < n; i++) {
                Entry entry = entries.get(i);
                buffer.put(entry.key.duplicate());
            }

            for (int i = 0; i < n; i++) {
                Entry entry = entries.get(i);
                buffer.put(entry.value.duplicate());
            }

            assert buffer.position() == ((INDEX_ENTRY_SIZE * (n + 1)) + totalKeySize + totalValueSize);
        }

        public ByteBuffer getKey(int i) {
            return entries.get(i).key;
        }

        public ByteBuffer getValue(int i) {
            return entries.get(i).value;
        }

        public int getEntryCount() {
            return entries.size();
        }

        public boolean walk(Transaction txn, ByteBuffer from, EntryListener listener) {
            int n = getEntryCount();
            int pos = lbound(from);
            while (pos < n) {
                ByteBuffer key = getKey(pos);
                ByteBuffer value = getValue(pos);

                boolean keepGoing = listener.found(key, value);
                if (!keepGoing) {
                    return false;
                }

                pos++;
            }

            return true;
        }

        public ByteBuffer getKeyLbound() {
            return getKey(0);
        }
    }

    static class Entry {
        ByteBuffer key;
        ByteBuffer value;

        public Entry(ByteBuffer key, ByteBuffer value) {
            this.key = key;
            this.value = value;
        }
    }

    private int lbound(ByteBuffer find) {
        assert mutable == null;

        int minIndex = 0;
        int maxIndex = getEntryCount();
        int comparison;
        int mid;

        while (minIndex < maxIndex) {
            mid = (minIndex + maxIndex) / 2; // We're not going to overflow with sensible page sizes

            ByteBuffer midKey = getKey(mid);
            comparison = ByteBuffers.compare(find, midKey);

            if (comparison < 0) {
                maxIndex = mid - 1;
            } else {
                minIndex = mid;
            }
        }

        assert minIndex == maxIndex;

        return minIndex;
    }

    @Override
    public boolean walk(Transaction txn, ByteBuffer from, EntryListener listener) {
        if (mutable != null) {
            return mutable.walk(txn, from, listener);
        }

        int n = getEntryCount();
        int pos = lbound(from);
        while (pos < n) {
            ByteBuffer key = getKey(pos);
            ByteBuffer value = getValue(pos);

            boolean keepGoing = listener.found(key, value);
            if (!keepGoing) {
                return false;
            }

            pos++;
        }

        return true;
    }

    private int getEntryCount() {
        assert mutable == null;
        return buffer.getShort(2);
    }

    private ByteBuffer getKey(int i) {
        assert mutable == null;

        ByteBuffer ret = buffer.duplicate();
        int offset = (i * INDEX_ENTRY_SIZE);
        int start = ret.getShort(offset);
        int end = ret.getShort(offset + INDEX_ENTRY_SIZE);

        ret.position(start);
        ret.limit(end);

        return ret;
    }

    private ByteBuffer getValue(int i) {
        assert mutable == null;

        ByteBuffer ret = buffer.duplicate();
        int offset = (i * INDEX_ENTRY_SIZE) + 2;
        int start = ret.getShort(offset);
        int end = ret.getShort(offset + INDEX_ENTRY_SIZE);

        ret.position(start);
        ret.limit(end);

        return ret;
    }

    Mutable getMutable() {
        if (mutable == null) {
            mutable = new Mutable(this);
        }
        return mutable;
    }

    @Override
    public void insert(Transaction txn, ByteBuffer key, ByteBuffer value) {
        // int remaining = buffer.remaining();
        // int keySize = key.remaining();
        // int valueSize = value.remaining();
        //
        // // TODO: Value overflow
        // int sizeNeeded = 2 + keySize + 2 + valueSize;
        // if (sizeNeeded > remaining) {
        // throw new IllegalArgumentException();
        // }

        getMutable().insert(key, value);
    }

    @Override
    public ByteBuffer getKeyLbound() {
        if (mutable != null) {
            return mutable.getKeyLbound();
        }
        return getKey(0);
    }
}
