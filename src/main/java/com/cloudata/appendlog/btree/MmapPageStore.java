package com.cloudata.appendlog.btree;

import java.nio.MappedByteBuffer;

public class MmapPageStore extends PageStore {

    final MappedByteBuffer buffer;

    private static final int PAGE_SIZE = 4096;

    public MmapPageStore(MappedByteBuffer buffer) {
        this.buffer = buffer;
    }

    @Override
    public Page fetchPage(int pageNumber) {
        int offset = pageNumber * PAGE_SIZE;

        PageHeader header = new PageHeader(buffer, offset);

        switch (header.getPageType()) {
        case BranchPage.PAGE_TYPE:
            return new BranchPage(header.getPageSlice());

        case LeafPage.PAGE_TYPE:
            return new LeafPage(header.getPageSlice());

        default:
            throw new IllegalStateException();
        }
    }

}
