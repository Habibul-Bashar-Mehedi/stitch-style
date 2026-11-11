package com.stitchstyle.app.dto.response;

import java.util.List;
import java.util.Map;

public record HomeResponse(
        Map<String, String> settings,
        List<BannerResponse> banners,
        List<NoticeResponse> notices,
        List<CategoryResponse> cards,
        List<ProductResponse> latestProducts
) {}
