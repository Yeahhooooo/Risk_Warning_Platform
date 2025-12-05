package com.riskwarning.common.utils;

import org.hibernate.type.EnumType;
import org.hibernate.engine.spi.SharedSessionContractImplementor;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;

public class PostgreSQLEnumType extends EnumType {

    @Override
    public void nullSafeSet(PreparedStatement st, Object value, int index, SharedSessionContractImplementor session) throws SQLException {
        if (value == null) {
            st.setNull(index, Types.OTHER);
        } else {
            // 把枚举的字符串值以 Types.OTHER 发送，Postgres 会识别为 enum 类型
            st.setObject(index, value.toString(), Types.OTHER);
        }
    }
}
