package com.erik.authservice.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public class GetUsersByIdsRequest {
    @NotBlank
    private List<Long> userIds;
}
