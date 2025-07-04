package com.modules.common.model;

public class LongInteger {
    private long longValue;
    private int intValue;

    public LongInteger() {}

    public LongInteger(long longValue, int intValue) {
        this.longValue = longValue;
        this.intValue = intValue;
    }

    public long getLongValue() {
        return longValue;
    }

    public int getIntValue() {
        return intValue;
    }

    public void setIntValue(int intValue) {
        this.intValue = intValue;
    }

    public void setLongValue(long longValue) {
        this.longValue = longValue;
    }

    @Override
    public String toString() {
        return "LongInteger{" +
                "longValue=" + longValue +
                ", intValue=" + intValue +
                '}';
    }
}
