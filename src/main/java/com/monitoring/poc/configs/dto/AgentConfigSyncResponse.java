package com.monitoring.poc.configs.dto;

import java.util.List;

public class AgentConfigSyncResponse {

    private List<AgentConfigSyncItemDto> dueFiles;

    public AgentConfigSyncResponse() {
    }

    public AgentConfigSyncResponse(List<AgentConfigSyncItemDto> dueFiles) {
        this.dueFiles = dueFiles;
    }

    public List<AgentConfigSyncItemDto> getDueFiles() {
        return dueFiles;
    }

    public void setDueFiles(List<AgentConfigSyncItemDto> dueFiles) {
        this.dueFiles = dueFiles;
    }
}
