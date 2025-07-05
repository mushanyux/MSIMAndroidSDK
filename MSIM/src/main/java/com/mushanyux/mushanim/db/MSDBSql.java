package com.mushanyux.mushanim.db;

import java.util.List;


public class MSDBSql {
    public long index;
    public List<String> sqlList;

    public MSDBSql(long index, List<String> sqlList) {
        this.index = index;
        this.sqlList = sqlList;
    }
}
