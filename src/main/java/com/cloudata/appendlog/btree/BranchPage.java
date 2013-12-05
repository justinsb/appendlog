package com.cloudata.appendlog.btree;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class BranchPage extends Page {
    public static final byte PAGE_TYPE = 'B';

    private static final int INDEX_ENTRY_SIZE = 6;

    Mutable mutable;

    public BranchPage(ByteBuffer buffer) {
        super(buffer);
    }

    // Entry find(Context context, ByteBuffer find, int position, int remaining) {
    // int minIndex;
    // int maxIndex;
    // int comparison;
    // int mid;
    //
    // ByteBuffer entryKey = buffer.duplicate();
    //
    // while (minIndex != maxIndex) {
    // mid = (minIndex + maxIndex) / 2; // We're not going to overflow with sensible page sizes
    //
    // setToKey(mid, entryKey);
    // comparison = find.compareTo(entryKey);
    //
    // if (comparison > 0) {
    // minIndex = mid + 1;
    // } else {
    // maxIndex = mid;
    // }
    // }
    //
    // assert minIndex == maxIndex;
    //
    // if (minIndex == 0) {
    // assert mid == 0;
    // if (comparison > 0) {
    // return null;
    // }
    // }
    //
    // int pageNumber = getPage(minIndex);
    //
    // TriePage page = (TriePage) context.getPage(pageNumber);
    // return page.find(context, find, position, remaining);
    // }

    static class Mutable {
        final List<Entry> entries;
        int totalKeySize;

        private int lbound(ByteBuffer find) {
            int findLength = find.remaining();

            int minIndex = 0;
            int maxIndex = getEntryCount();
            int comparison;
            int mid;

            while (minIndex < maxIndex) {
                mid = (minIndex + maxIndex) / 2; // We're not going to overflow with sensible page sizes

                ByteBuffer midKey = getKey(mid);
                comparison = ByteBuffers.compare(find, midKey, findLength);

                if (comparison < 0) {
                    maxIndex = mid - 1;
                } else {
                    minIndex = mid;
                }
            }

            assert minIndex == maxIndex;

            return minIndex;
        }

        // void insert(ByteBuffer key, ByteBuffer value) {
        // int pos = lbound(key);
        //
        // // TODO: Uniqueness checks?
        //
        // getChild(pos);
        //
        // entries.add(pos - 1, new Entry(key, value));
        //
        // totalKeySize += key.remaining();
        // totalValueSize += value.remaining();
        // }

        Mutable(BranchPage page) {
            int n = page.getEntryCount();

            this.entries = new ArrayList<Entry>(n);

            int pos = 0;

            ByteBuffer buffer = page.buffer;

            int keyStart = buffer.getShort(pos);

            for (int i = 0; i < n; i++) {
                int keyEnd = page.buffer.getShort(pos + INDEX_ENTRY_SIZE);
                int pageNumber = page.buffer.getShort(pos + INDEX_ENTRY_SIZE + 2);

                ByteBuffer key = buffer.duplicate();
                key.position(keyStart);
                key.limit(keyEnd);
                totalKeySize += key.remaining();

                Entry entry = new Entry(key, pageNumber);
                entries.add(entry);

                keyStart = keyEnd;
            }
        }

        public void write(ByteBuffer buffer) {
            buffer.put(PAGE_TYPE);
            buffer.put((byte) 0);

            short n = (short) entries.size();
            buffer.putShort(n);

            short keyStart = (short) ((INDEX_ENTRY_SIZE * n) + 2);

            for (int i = 0; i < n; i++) {
                Entry entry = entries.get(i);

                buffer.putShort(keyStart);
                buffer.putInt(entry.pageNumber);

                keyStart += entry.key.remaining();
            }

            // Write a dummy tail entry so we know the total sizes
            buffer.putShort(keyStart);

            for (int i = 0; i < n; i++) {
                Entry entry = entries.get(i);
                buffer.put(entry.key.duplicate());
            }
        }

        public ByteBuffer getKey(int i) {
            return entries.get(i).key;
        }

        public int getEntryCount() {
            return entries.size();
        }

        public boolean walk(Transaction txn, ByteBuffer from, EntryListener listener) {
            int n = getEntryCount();
            int pos = lbound(from);
            while (pos < n) {
                Entry entry = entries.get(pos);

                int pageNumber = entry.pageNumber;

                Page childPage = txn.getPage(pageNumber);

                boolean keepGoing = childPage.walk(txn, from, listener);
                if (!keepGoing) {
                    return false;
                }

                pos++;
            }

            return true;
        }

        public void updateLbound(Transaction txn, int pageNumber) {
            int n = getEntryCount();
            for (int i = 0; i < n; i++) {
                Entry entry = entries.get(i);
                if (entry.pageNumber != pageNumber) {
                    continue;
                }

                Page childPage = txn.getPage(pageNumber);

                ByteBuffer lbound = childPage.getKeyLbound();
                entries.set(i, new Entry(lbound, pageNumber));
                return;
            }

            throw new IllegalStateException();
        }

        public ByteBuffer getKeyLbound() {
            return getKey(0);
        }
    }

    static class Entry {
        final ByteBuffer key;
        final int pageNumber;

        public Entry(ByteBuffer key, int pageNumber) {
            this.key = key;
            this.pageNumber = pageNumber;
        }
    }

    private int lbound(ByteBuffer find) {
        assert mutable == null;

        int findLength = find.remaining();

        int minIndex = 0;
        int maxIndex = getEntryCount();
        int comparison;
        int mid;

        while (minIndex < maxIndex) {
            mid = (minIndex + maxIndex) / 2; // We're not going to overflow with sensible page sizes

            ByteBuffer midKey = getKey(mid);
            comparison = ByteBuffers.compare(find, midKey, findLength);

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
            int pageNumber = getPageNumber(pos);

            Page page = txn.getPage(pageNumber);
            boolean keepGoing = page.walk(txn, from, listener);
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

    private int getPageNumber(int i) {
        assert mutable == null;

        int offset = (i * INDEX_ENTRY_SIZE) + 2;
        int page = buffer.getInt(offset);
        return page;
    }

    Mutable getMutable() {
        if (mutable == null) {
            mutable = new Mutable(this);
        }
        return mutable;
    }

    @Override
    public void insert(Transaction txn, ByteBuffer key, ByteBuffer value) {
        int pos = lbound(key);
        int pageNumber = getPageNumber(pos);

        Page childPage = txn.getPage(pageNumber);

        ByteBuffer oldLbound = childPage.getKeyLbound();

        childPage.insert(txn, key, value);

        ByteBuffer newLbound = childPage.getKeyLbound();

        if (!oldLbound.equals(newLbound)) {
            getMutable().updateLbound(txn, pageNumber);
        }
    }

    @Override
    public ByteBuffer getKeyLbound() {
        if (mutable != null) {
            return mutable.getKeyLbound();
        }

        return getKey(0);
    }

    // private Page getChild(DbContext context, ByteBuffer key) {
    // int pos = lbound(key);
    // int pageNumber = getPageNumber(pos);
    //
    // Page page = context.getPage(pageNumber);
    // return page;
    // }
}
