package com.riskwarning.common.enums;

public enum FileTypeEnum {

    PDF("pdf"),

    WORD("docx");

    private final String suffix;

    FileTypeEnum(String suffix) {
        this.suffix = suffix;
    }

    public String getSuffix() {
        return suffix;
    }

    public static boolean contains(String suffix) {
        for (FileTypeEnum fileTypeEnum : FileTypeEnum.values()) {
            if (fileTypeEnum.suffix.equalsIgnoreCase(suffix)) {
                return true;
            }
        }
        return false;
    }
}
