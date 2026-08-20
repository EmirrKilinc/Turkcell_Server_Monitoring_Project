package com.monitoring.poc.configs.dto;

import java.util.List;

public class ConfigFileDiffResponse {

    private Integer v1;
    private Integer v2;
    private List<DiffLineDto> lines;

    public ConfigFileDiffResponse() {
    }

    public ConfigFileDiffResponse(Integer v1, Integer v2, List<DiffLineDto> lines) {
        this.v1 = v1;
        this.v2 = v2;
        this.lines = lines;
    }

    public Integer getV1() {
        return v1;
    }

    public void setV1(Integer v1) {
        this.v1 = v1;
    }

    public Integer getV2() {
        return v2;
    }

    public void setV2(Integer v2) {
        this.v2 = v2;
    }

    public List<DiffLineDto> getLines() {
        return lines;
    }

    public void setLines(List<DiffLineDto> lines) {
        this.lines = lines;
    }
}
