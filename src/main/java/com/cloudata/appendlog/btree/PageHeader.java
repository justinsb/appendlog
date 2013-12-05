package com.cloudata.appendlog.btree;

import java.nio.ByteBuffer;

public class PageHeader {

    final static int HEADER_SIZE = 8;

    private static final int OFFSET_TYPE = 0;
    private static final int OFFSET_LENGTH = 2;
    private static final int OFFSET_CRC = 4;

    final ByteBuffer buffer;
    final int offset;

    public PageHeader(ByteBuffer buffer, int offset) {
        this.buffer = buffer;
        this.offset = offset;
    }

    public byte getPageType() {
        byte pageType = buffer.get(offset + OFFSET_TYPE);
        return pageType;
    }

    public ByteBuffer getPageSlice() {
        // We have a 64MB page-size limit
        int length = buffer.getShort(offset + OFFSET_LENGTH) & 0xffff;
        length *= 1024;

        ByteBuffer slice = buffer.duplicate();
        slice.position(offset + HEADER_SIZE);
        slice.limit(offset + HEADER_SIZE + length);

        return slice.asReadOnlyBuffer();
    }

}
