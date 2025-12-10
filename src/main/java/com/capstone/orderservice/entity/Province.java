package com.capstone.orderservice.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;


@Entity
@Table(name = "provinces", schema = "inventory_service")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Province {

    @Id
    private Integer code;

    private String name;

    @JsonProperty("division_type")
    private String divisionType;

    private String codename;

    @JsonProperty("phone_code")
    private Integer phoneCode;
}
