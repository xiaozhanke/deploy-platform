package com.xiaozhanke.deploy.controller;

import com.xiaozhanke.deploy.aspect.Auditable;
import com.xiaozhanke.deploy.enums.AuditOperationTypeEnum;
import com.xiaozhanke.deploy.model.request.HostParams;
import com.xiaozhanke.deploy.model.request.HostQueryParams;
import com.xiaozhanke.deploy.model.response.PageResult;
import com.xiaozhanke.deploy.model.validation.ValidationGroups;
import com.xiaozhanke.deploy.model.vo.HostRecordVo;
import com.xiaozhanke.deploy.service.HostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.groups.Default;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * 主机信息接口
 *
 * @author xiaozhanke
 */
@Tag(name = "hosts", description = "主机信息接口")
@RestController
@RequestMapping("/hosts")
public class HostController {

    private final HostService hostService;

    public HostController(HostService hostService) {
        this.hostService = hostService;
    }

    /**
     * 添加主机
     *
     * @param params 主机信息参数
     * @return 保存后的主机信息
     */
    @Operation(summary = "添加主机", description = "添加主机信息")
    @Auditable(operationType = AuditOperationTypeEnum.HOST_CREATE, target = "#params.address")
    @PostMapping
    public ResponseEntity<HostRecordVo> addHost(@Validated({Default.class, ValidationGroups.Create.class}) @RequestBody HostParams params) {
        HostRecordVo createdRecord = hostService.addHost(params);
        URI location =
                ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(createdRecord.getId()).toUri();
        return ResponseEntity.created(location).body(createdRecord);
    }

    /**
     * 更新主机
     *
     * @param id     主机 Id
     * @param params 主机信息参数
     * @return 更新后的主机信息
     */
    @Operation(summary = "更新主机", description = "更新主机信息")
    @Auditable(operationType = AuditOperationTypeEnum.HOST_UPDATE, target = "#id")
    @PutMapping("/{id}")
    public HostRecordVo updateHost(@Parameter(description = "主机 Id", required = true) @PathVariable String id,
                                   // 仅 Default 组：刻意不校验 Create 组的 password / privateKeyPassword 必填，
                                   // 允许编辑时留空沿用原凭据，合并与有效性兜底在 HostService#updateHost
                                   @Validated(Default.class) @RequestBody HostParams params) {
        return hostService.updateHost(id, params);
    }

    /**
     * 删除主机
     *
     * @param id 主机 Id
     */
    @Operation(summary = "删除主机", description = "删除主机信息")
    @Auditable(operationType = AuditOperationTypeEnum.HOST_DELETE, target = "#id")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteHost(@Parameter(description = "主机 Id", required = true) @PathVariable String id) {
        hostService.deleteHost(id);
    }

    /**
     * 获取主机
     *
     * @param id 主机 Id
     * @return 主机信息
     */
    @Operation(summary = "获取主机", description = "获取主机信息")
    @GetMapping("/{id}")
    public HostRecordVo getHost(@Parameter(description = "主机 Id", required = true) @PathVariable String id) {
        return hostService.queryHost(id);
    }

    /**
     * 查询主机所有列表
     *
     * @return 主机列表
     */
    @Operation(summary = "查询主机列表", description = "查询主机所有列表")
    @GetMapping("/list")
    public List<HostRecordVo> queryList() {
        return hostService.queryList();
    }

    /**
     * 分页查询主机列表
     *
     * @param params   查询参数
     * @param pageable 分页参数
     * @return 分页结果
     */
    @Operation(summary = "分页查询主机列表", description = "分页查询主机列表")
    @GetMapping("/page")
    public PageResult<HostRecordVo> queryPage(HostQueryParams params,
                                              @Parameter(description = "分页参数", example = "{\"page\": 0, \"size\": " +
                                                      "20, \"sort\": \"updateTime,desc\"}")
                                              @PageableDefault(size = 20, sort = "updateTime", direction =
                                                      Sort.Direction.DESC) Pageable pageable) {
        return hostService.queryPage(params, pageable);
    }

    /**
     * 测试主机连接
     *
     * @param params 主机信息参数
     * @return 连接测试结果
     */
    @Operation(summary = "测试主机连接", description = "用请求体携带的连接信息测试连接，供新增等尚未保存的场景使用")
    @PostMapping("/test-connection")
    public boolean testConnection(@Parameter(description = "主机信息") @Validated({Default.class,
            ValidationGroups.Create.class}) @RequestBody HostParams params) {
        return hostService.testConnection(params);
    }

    /**
     * 测试已保存主机的连接
     *
     * @param id 主机 Id
     * @return 连接测试结果
     */
    @Operation(summary = "测试已保存主机连接", description = "按主机 Id 用后端存储的凭据测试连接，无需前端回传密码")
    @PostMapping("/{id}/test-connection")
    public boolean testConnectionById(@Parameter(description = "主机 Id", required = true) @PathVariable String id) {
        return hostService.testConnectionById(id);
    }
}
