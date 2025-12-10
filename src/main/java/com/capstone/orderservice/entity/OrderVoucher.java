package com.capstone.orderservice.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "orders_vouchers",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"order_id", "voucher_id"})
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderVoucher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "voucher_id", nullable = false)
    private Voucher voucher;
}
