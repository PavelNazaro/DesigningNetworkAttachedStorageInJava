package com.geekbrains.cloud.storage.client;

public class TableData {
    private String fileName;
    private String sizeFile;

    public TableData(String fileName, String sizeFile) {
        this.fileName = fileName;
        this.sizeFile = sizeFile;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getSizeFile() {
        return sizeFile;
    }

    public void setSizeFile(String sizeFile) {
        this.sizeFile = sizeFile;
    }
}
