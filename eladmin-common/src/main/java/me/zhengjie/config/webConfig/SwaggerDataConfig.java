package me.zhengjie.config.webConfig;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * 将Pageable转换展示在swagger中
 */
@Configuration
public class SwaggerDataConfig {

    static {
        // 将 Pageable 参数在文档中展示为 page、size、sort
        SpringDocUtils.getConfig().replaceParameterObjectWithClass(Pageable.class, Page.class);
    }

    @Data
    @Schema
    public static class Page {
        @Schema(description = "页码 (0..N)")
        private Integer page;

        @Schema(description = "每页显示的数目")
        private Integer size;

        @Schema(description = "以下列格式排序标准：property[,asc | desc]。 默认排序顺序为升序。 支持多种排序条件：如：id,asc")
        private List<String> sort;
    }
}
