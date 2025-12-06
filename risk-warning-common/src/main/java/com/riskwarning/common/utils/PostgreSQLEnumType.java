package com.riskwarning.common.utils;

import org.hibernate.type.EnumType;
import org.hibernate.engine.spi.SharedSessionContractImplementor;

import java.lang.reflect.Method;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

public class PostgreSQLEnumType extends EnumType {

    @Override
    public int[] sqlTypes() {
        return new int[]{Types.OTHER};
    }



    @Override
    public void nullSafeSet(PreparedStatement st, Object value, int index, SharedSessionContractImplementor session) throws SQLException {
        if (value == null) {
            st.setNull(index, Types.OTHER);
        } else {
            // 把枚举的字符串值以 Types.OTHER 发送，Postgres 会识别为 enum 类型
            st.setObject(index, value.toString(), Types.OTHER);
        }
    }


    @Override
    public Object nullSafeGet(ResultSet rs, String[] names, SharedSessionContractImplementor session, Object owner) throws SQLException {
        String dbValue = rs.getString(names[0]);
        if (dbValue == null) return null;

        Class returned = returnedClass();
        if (!returned.isEnum()) {
            throw new SQLException("PostgreSQLEnumType used with non-enum class: " + returned);
        }
        Class<? extends Enum> enumClass = returned.asSubclass(Enum.class);

        try {
            Method m = enumClass.getMethod("fromDbValue", String.class);
            Object res = m.invoke(null, dbValue);
            if (res != null) return res;
        } catch (NoSuchMethodException ignored) {
            // 没有 fromDbValue，继续后续匹配
        } catch (Exception ex) {
            throw new SQLException("Error invoking fromDbValue on " + enumClass.getName(), ex);
        }

        for (Object constant : enumClass.getEnumConstants()) {
            Enum e = (Enum) constant;
            if (e.toString().equals(dbValue) || e.name().equalsIgnoreCase(dbValue)) {
                return e;
            }
        }

        try {
            return Enum.valueOf(enumClass, dbValue);
        } catch (IllegalArgumentException ex) {
            throw new SQLException("Unknown enum value '" + dbValue + "' for enum " + enumClass.getName(), ex);
        }
    }

}
