package com.riskwarning.common.po.enterprise;

import java.io.Serializable;
import java.util.Objects;
import javax.persistence.Column;
import javax.persistence.Embeddable;

@Embeddable
public class EnterpriseUserId implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "enterprise_id")
    private Long enterpriseId;

    @Column(name = "user_id")
    private Long userId;

    public EnterpriseUserId() {}

    public EnterpriseUserId(Long enterpriseId, Long userId) {
        this.enterpriseId = enterpriseId;
        this.userId = userId;
    }

    public Long getEnterpriseId() {
        return enterpriseId;
    }

    public void setEnterpriseId(Long enterpriseId) {
        this.enterpriseId = enterpriseId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EnterpriseUserId)) return false;
        EnterpriseUserId that = (EnterpriseUserId) o;
        return Objects.equals(getEnterpriseId(), that.getEnterpriseId()) &&
                Objects.equals(getUserId(), that.getUserId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getEnterpriseId(), getUserId());
    }
}
