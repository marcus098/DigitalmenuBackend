package com.modules.common.model;

import java.util.List;

public class OptionInProductList {
    private List<OptionInProduct> optionInProductList;

    public OptionInProductList() {}

    public List<OptionInProduct> getOptionInProductList() {
        return optionInProductList;
    }

    public void setOptionInProductList(List<OptionInProduct> optionInProductList) {
        this.optionInProductList = optionInProductList;
    }

    @Override
    public String toString() {
        return "OptionInProductList{" +
                "optionInProductList=" + optionInProductList +
                '}';
    }
}