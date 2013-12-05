package com.cloudata.appendlog.btree;

import java.util.Map;
import java.util.Map.Entry;

import com.google.common.collect.Maps;

public class Transaction {

    final PageStore pageStore;
    final Map<Integer, TrackedPage> trackedPages = Maps.newHashMap();

    static class TrackedPage {
        final Page page;

        public TrackedPage(Page page) {
            super();
            this.page = page;
        }

    }

    public Transaction(PageStore pageStore) {
        this.pageStore = pageStore;
    }

    public Page getPage(int pageNumber) {
        TrackedPage trackedPage = trackedPages.get(pageNumber);
        if (trackedPage == null) {
            Page page = pageStore.fetchPage(pageNumber);
            trackedPage = new TrackedPage(page);
            trackedPages.put(pageNumber, trackedPage);
        }
        return trackedPage.page;
    }

    public void commit() {
        for (Entry<Integer, TrackedPage> entry : trackedPages.entrySet()) {
            TrackedPage page = entry.getValue();
            int pageNumber = entry.getKey();

            if (!page.isDirty()) {
                continue;
            }

            int newPageNumber = assignPageNumber(page);
            page.changePageNumber(newPageNumber);
        }
    }

}
