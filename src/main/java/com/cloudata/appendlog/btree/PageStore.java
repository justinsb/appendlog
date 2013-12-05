package com.cloudata.appendlog.btree;

public abstract class PageStore {

    public abstract Page fetchPage(int pageNumber);

}
