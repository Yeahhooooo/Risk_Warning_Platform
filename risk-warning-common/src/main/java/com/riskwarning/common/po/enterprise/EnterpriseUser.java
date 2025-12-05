package com.riskwarning.common.po.enterprise;

import com.riskwarning.common.enums.EnterpriseRole;
import com.riskwarning.common.po.user.User;
import com.riskwarning.common.utils.PostgreSQLEnumType;
import lombok.*;
import javax.persistence.*;

import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "t_enterprise_user")
@TypeDef(name = "pgsql_enum", typeClass = PostgreSQLEnumType.class)
public class EnterpriseUser {

    @EmbeddedId
    private EnterpriseUserId id;

    @MapsId("enterpriseId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "enterprise_id", nullable = false)
    @Setter
    @Getter
    private Enterprise enterprise;

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @Setter
    @Getter
    private User user;


    @Type(type = "pgsql_enum")
    @Enumerated(EnumType.STRING)
    @Column(name = "role", columnDefinition = "enterprise_role")
    @Setter
    @Getter
    private EnterpriseRole role;
}