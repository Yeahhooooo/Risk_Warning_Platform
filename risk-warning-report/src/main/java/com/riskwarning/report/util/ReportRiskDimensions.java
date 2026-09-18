package com.riskwarning.report.util;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.riskwarning.common.enums.RiskDimensionEnum;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;

/** Report-facing names; stored dimension descriptions and enum identifiers remain compatible. */
public final class ReportRiskDimensions {
    private static final Map<RiskDimensionEnum, String> NAMES = new EnumMap<>(RiskDimensionEnum.class);
    static {
        NAMES.put(RiskDimensionEnum.ENTERPRISE_RELATED_RISK, "企业关联方");
        NAMES.put(RiskDimensionEnum.PRODUCT_LEGITIMACY_RISK, "产品法律");
        NAMES.put(RiskDimensionEnum.LABOR_LEGITIMACY_RISK, "劳动法律");
        NAMES.put(RiskDimensionEnum.ENTERPRISE_CREDIT_RISK, "企业信用");
        NAMES.put(RiskDimensionEnum.ENTERPRISE_INTERNATIONAL_COOPERATION_RISK, "国际化经营");
        NAMES.put(RiskDimensionEnum.SUPPLY_CHAIN_RISK, "供应链");
    }

    private ReportRiskDimensions() {}

    public static RiskDimensionEnum parse(String value) {
        String name = value == null ? null : value.trim();
        for (Map.Entry<RiskDimensionEnum, String> entry : NAMES.entrySet()) {
            if (entry.getValue().equals(name) || (entry.getValue() + "风险").equals(name)
                    || entry.getKey().name().equals(name)) return entry.getKey();
        }
        return RiskDimensionEnum.fromValue(name);
    }

    public static String normalize(String value) {
        return value == null ? null : displayName(parse(value));
    }

    private static String displayName(RiskDimensionEnum dimension) {
        return NAMES.get(dimension) + "风险";
    }

    public static class ValueSerializer extends JsonSerializer<RiskDimensionEnum> {
        @Override
        public void serialize(RiskDimensionEnum value, JsonGenerator generator, SerializerProvider provider)
                throws IOException {
            generator.writeString(displayName(value));
        }
    }

    public static class KeySerializer extends JsonSerializer<RiskDimensionEnum> {
        @Override
        public void serialize(RiskDimensionEnum value, JsonGenerator generator, SerializerProvider provider)
                throws IOException {
            generator.writeFieldName(displayName(value));
        }
    }
}
