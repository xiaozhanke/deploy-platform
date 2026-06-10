package com.xiaozhanke.deploy.model.request;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 服务器查询参数
 *
 * <p>仅承载列表 / 分页查询所需的过滤字段，与写入用的 {@link ServerParams} 解耦：后者带 host / port /
 * authType 等必填校验以及 password 等凭据字段，不适合作为 GET 查询条件出站。
 *
 * @author xiaozhanke
 */
@Schema(description = "服务器查询参数")
public record ServerQueryParams(
        @Schema(description = "服务器名称")
        String name,

        @Schema(description = "主机地址", example = "localhost")
        String host
) {
}
