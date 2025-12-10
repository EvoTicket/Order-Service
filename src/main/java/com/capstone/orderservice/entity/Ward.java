package com.capstone.orderservice.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "wards", schema = "inventory_service")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ward {

    @Id
    private Integer code;

    private String name;

    @JsonProperty("division_type")
    private String divisionType;

    private String codename;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "province_code", referencedColumnName = "code")
    @JsonProperty("province_code")
    private Province province;
}