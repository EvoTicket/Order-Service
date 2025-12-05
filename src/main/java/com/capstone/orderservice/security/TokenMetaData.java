package com.capstone.orderservice.security;

public record TokenMetaData(Long userId, boolean isOrganization, Long organizationId) {
}
