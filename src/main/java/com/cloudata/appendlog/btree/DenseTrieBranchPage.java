//package com.cloudata.appendlog.btree;
//
//import java.nio.ByteBuffer;
//
//public class DenseTrieBranchPage extends TriePage {
//    Entry find(Context context, ByteBuffer find) {
//        byte b = find.get();
//
//        int pageNumber = getPage(b);
//        if (pageNumber == 0) {
//            return null;
//        }
//
//        TriePage page = (TriePage) context.getPage(pageNumber);
//        return page.find(context, find);
//    }
// }
