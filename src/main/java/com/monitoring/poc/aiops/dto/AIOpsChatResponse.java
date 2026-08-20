package com.monitoring.poc.aiops.dto;

public class AIOpsChatResponse {

    private String reply;

    public AIOpsChatResponse() {
    }

    public AIOpsChatResponse(String reply) {
        this.reply = reply;
    }

    public String getReply() {
        return reply;
    }

    public void setReply(String reply) {
        this.reply = reply;
    }
}
