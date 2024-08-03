package com.depromeet.stonebed.global.common.constants;

import java.util.Arrays;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum SwaggerUrlConstants {
    SWAGGER_RESOURCES_URL("/swagger-resources/**"),
    SWAGGER_UI_URL("/swagger-ui/**"),
    SWAGGER_API_DOCS_URL("/v3/api-docs/**"),
    ;

    private final String value;

    public static String[] getSwaggerUrls() {
        return Arrays.stream(SwaggerUrlConstants.values())
                .map(SwaggerUrlConstants::getValue)
                .toArray(String[]::new);
    }
}
