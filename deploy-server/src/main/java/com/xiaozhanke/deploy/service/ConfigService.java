package com.xiaozhanke.deploy.service;

import com.xiaozhanke.deploy.exception.BusinessException;
import com.xiaozhanke.deploy.messaging.dto.ConfigChangeMessage;
import com.xiaozhanke.deploy.messaging.producer.ConfigChangeProducer;
import com.xiaozhanke.deploy.model.request.NginxConfigParams;
import com.xiaozhanke.deploy.util.AuthenticationHelper;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.view.freemarker.FreeMarkerConfigurer;

import java.io.IOException;
import java.io.StringWriter;
import java.time.LocalDateTime;

/**
 * 配置文件服务类
 *
 * @author xiaozhanke
 */
@Slf4j
@Service
public class ConfigService {

    private final FreeMarkerConfigurer freeMarkerConfigurer;
    private final ConfigChangeProducer configChangeProducer;
    private final AuthenticationHelper authenticationHelper;

    public ConfigService(FreeMarkerConfigurer freeMarkerConfigurer, ConfigChangeProducer configChangeProducer,
                         AuthenticationHelper authenticationHelper) {
        this.freeMarkerConfigurer = freeMarkerConfigurer;
        this.configChangeProducer = configChangeProducer;
        this.authenticationHelper = authenticationHelper;
    }

    /**
     * 生成 Nginx 配置文件,并通过 MQ 广播配置变更(BROADCASTING 模式)。
     *
     * <p>广播在 Service 层完成而非 Controller:保证所有触发入口(CLI/定时任务/内部 API)
     * 都能统一广播,避免遗漏。
     *
     * @param params 配置参数
     * @return 配置文件内容
     */
    public String addNginxConfig(NginxConfigParams params) {
        String templateName = "nginx.conf.ftl";
        log.info("开始生成 Nginx 配置, 模板=[{}]", templateName);
        try {
            Template template = freeMarkerConfigurer.getConfiguration().getTemplate(templateName);
            StringWriter writer = new StringWriter();
            template.process(params, writer);
            String content = writer.toString();
            // 仅记录度量信息（行数 / 字符数 / 模板名）以避免把内网域名、upstream 地址、SSL 配置等基础设施细节
            // 灌进日志体系；完整配置仍以接口返回值形式回给调用方落盘
            log.info("生成 Nginx 配置完成, 模板=[{}], 行数={}, 字符数={}",
                    templateName, countLines(content), content.length());
            // 广播配置变更,通知所有实例
            String operator = authenticationHelper.getCurrentUserName().orElse("anonymous");
            configChangeProducer.broadcast(new ConfigChangeMessage(
                    "nginx.conf", "[generated]", ConfigChangeMessage.ChangeType.UPDATED,
                    operator, LocalDateTime.now()));
            return content;
        } catch (IOException e) {
            String errorMessage = String.format("无法加载配置文件模板 [%s]，请检查文件是否存在且可读。", templateName);
            throw new BusinessException(errorMessage, e);
        } catch (TemplateException e) {
            String errorMessage = String.format("使用模板 [%s] 生成配置文件时失败，请检查模板语法和参数。", templateName);
            throw new BusinessException(errorMessage, e);
        }
    }

    private int countLines(String content) {
        if (content == null || content.isEmpty()) {
            return 0;
        }
        int count = 1;
        for (int i = 0; i < content.length(); i++) {
            if (content.charAt(i) == '\n') {
                count++;
            }
        }
        return count;
    }
}
