package com.monitoring.poc.configs.dto;

import com.monitoring.poc.configs.DiffEngine;

public class DiffLineDto {

    private String type;
    private Integer oldLineNumber;
    private Integer newLineNumber;
    private String text;

    public DiffLineDto() {
    }

    public DiffLineDto(DiffEngine.DiffLine line) {
        this.type = line.type().name();
        this.oldLineNumber = line.oldLineNumber();
        this.newLineNumber = line.newLineNumber();
        this.text = line.text();
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Integer getOldLineNumber() {
        return oldLineNumber;
    }

    public void setOldLineNumber(Integer oldLineNumber) {
        this.oldLineNumber = oldLineNumber;
    }

    public Integer getNewLineNumber() {
        return newLineNumber;
    }

    public void setNewLineNumber(Integer newLineNumber) {
        this.newLineNumber = newLineNumber;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
