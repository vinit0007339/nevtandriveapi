package com.nevtan.drive.dto;

import java.util.List;

public record PresentationSlidePreviewResponse(
        Long fileId,
        String fileName,
        int slideCount,
        List<SlideImage> slides
) {
    public record SlideImage(
            int slideNumber,
            int width,
            int height,
            String imageDataUrl
    ) {
    }
}
