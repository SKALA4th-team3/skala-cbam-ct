package com.skala.cbam.parts.entity;

public enum PartUnit {
    KG,
    TON,
    EA;

    public static PartUnit from(String value) {
        for (PartUnit unit : values()) {
            if (unit.name().equalsIgnoreCase(value)) {
                return unit;
            }
        }
        return null;
    }
}
