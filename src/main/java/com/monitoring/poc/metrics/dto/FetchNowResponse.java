package com.monitoring.poc.metrics.dto;

public class FetchNowResponse {

    private String status;
    private Long fetchRequestId;
    private Object resultPayload;
    private String pollUrl;

    public FetchNowResponse() {
    }

    public FetchNowResponse(String status, Long fetchRequestId, Object resultPayload, String pollUrl) {
        this.status = status;
        this.fetchRequestId = fetchRequestId;
        this.resultPayload = resultPayload;
        this.pollUrl = pollUrl;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getFetchRequestId() {
        return fetchRequestId;
    }

    public void setFetchRequestId(Long fetchRequestId) {
        this.fetchRequestId = fetchRequestId;
    }

    public Object getResultPayload() {
        return resultPayload;
    }

    public void setResultPayload(Object resultPayload) {
        this.resultPayload = resultPayload;
    }

    public String getPollUrl() {
        return pollUrl;
    }

    public void setPollUrl(String pollUrl) {
        this.pollUrl = pollUrl;
    }
}
