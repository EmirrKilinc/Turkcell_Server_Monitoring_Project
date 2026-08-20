package com.monitoring.poc.metrics.dto;

import com.monitoring.poc.entity.MetricGroup;
import com.monitoring.poc.entity.MetricGroupItem;

import java.util.List;

public class MetricGroupDetailResponse {

    private MetricGroupResponse group;
    private List<MetricGroupItemResponse> items;

    public MetricGroupDetailResponse() {
    }

    public MetricGroupDetailResponse(MetricGroup group, List<MetricGroupItem> items) {
        this.group = new MetricGroupResponse(group);
        this.items = items.stream().map(MetricGroupItemResponse::new).toList();
    }

    public MetricGroupResponse getGroup() {
        return group;
    }

    public void setGroup(MetricGroupResponse group) {
        this.group = group;
    }

    public List<MetricGroupItemResponse> getItems() {
        return items;
    }

    public void setItems(List<MetricGroupItemResponse> items) {
        this.items = items;
    }
}
