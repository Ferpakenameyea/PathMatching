package com.daxia.mapmaching;

public class IDSupplier {
    private long id = 1;

    public synchronized long getNew() {
        return id++;
    }
}
