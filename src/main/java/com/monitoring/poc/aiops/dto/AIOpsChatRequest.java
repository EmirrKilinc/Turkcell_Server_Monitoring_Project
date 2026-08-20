package com.monitoring.poc.aiops.dto;

import jakarta.validation.constraints.NotBlank;

public class AIOpsChatRequest {

    @NotBlank(message = "Mesaj bos olamaz")
    private String message;

    /**
     * Sohbetin odaklanacagi tek bir {@link com.monitoring.poc.entity.MetricGroup}
     * id'si - null ise kullanici "Genel" secmis demektir ve tum izlenen
     * gruplar baglam olarak kullanilir (bkz. AIOpsService.buildSystemContext).
     */
    private Long scopeGroupId;

    public AIOpsChatRequest() {
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Long getScopeGroupId() {
        return scopeGroupId;
    }

    public void setScopeGroupId(Long scopeGroupId) {
        this.scopeGroupId = scopeGroupId;
    }
}
