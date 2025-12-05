package com.riskwarning.common.po.enterprise;

import lombok.*;
import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "t_enterprise", indexes = {
        @Index(name = "idx_enterprise_credit_code", columnList = "credit_code", unique = true)
})
public class Enterprise {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "credit_code", nullable = false, unique = true)
    private String creditCode;

    @Column(name = "type")
    private String type;

    @Column
    private String industry;

    @Column(name = "business_scope")
    private String businessScope;

    @Column(name = "registered_capital")
    private BigDecimal registeredCapital;

    @Column(name = "establishment_date")
    private LocalDate establishmentDate;

    @Column(name = "legal_representative")
    private String legalRepresentative;

    @Column(name = "registered_address")
    private String registeredAddress;

    @Column(name = "business_status")
    private String businessStatus;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}