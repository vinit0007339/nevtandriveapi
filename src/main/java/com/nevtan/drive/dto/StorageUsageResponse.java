package com.nevtan.drive.dto;

public record StorageUsageResponse(
        long usedBytes,
        long quotaBytes,
        long availableBytes,
        double usedPercentage
) {
}
