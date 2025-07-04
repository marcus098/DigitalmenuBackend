package com.modules.categorymodule.requests;

import com.modules.common.model.IdWithOrder;
import com.modules.common.model.Request;

import java.util.List;

public class ChangeOrder implements Request {
    private List<IdWithOrder> list;

    public ChangeOrder(List<IdWithOrder> list) {
        this.list = list;
    }

    public ChangeOrder() {}

    public List<IdWithOrder> getList() {
        return list;
    }

    public void setList(List<IdWithOrder> list) {
        this.list = list;
    }

    @Override
    public String toString() {
        return "ChangeOrder{" +
                "list=" + list +
                '}';
    }

    @Override
    public boolean validate() {
        return !list.isEmpty();
    }
}
