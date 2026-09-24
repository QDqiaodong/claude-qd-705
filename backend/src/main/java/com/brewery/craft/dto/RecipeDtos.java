package com.brewery.craft.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 模块三（配方与原料用量）入参。 */
public final class RecipeDtos {

    private RecipeDtos() {
    }

    @Data
    public static class RecipeSaveReq {

        @NotBlank(message = "配方编码不能为空")
        private String code;

        @NotBlank(message = "配方名不能为空")
        private String name;

        @NotBlank(message = "风格不能为空")
        private String style;

        private BigDecimal targetOg;

        private BigDecimal targetAbv;

        private BigDecimal targetIbu;

        private BigDecimal targetEbc;

        private String processNote;
    }

    /**
     * 新增或修改「某一个投放阶段」的用量。
     * Map 的 key 走路径变量 {stage}，body 只带这条明细本身。
     */
    @Data
    public static class ItemSaveReq {

        @NotBlank(message = "原料编码不能为空")
        private String ingredientCode;

        private String ingredientName;

        private String ingredientType;

        private BigDecimal quantity;

        private String unit;

        private String note;
    }
}
