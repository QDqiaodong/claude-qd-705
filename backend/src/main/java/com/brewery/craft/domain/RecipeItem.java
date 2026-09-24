package com.brewery.craft.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * 模块三：配方原料用量明细。
 *
 * 它同时是 {@link Recipe#getItems()} 这张 Map 的 value —— 投放阶段不写在本实体里，
 * 而是由 {@code @MapKeyColumn(name = "stage")} 单独维护（Map 的 key 即这一行所处的投放阶段）。
 */
@Data
@Entity
@Table(name = "recipe_item")
public class RecipeItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属配方：这张表的关系由 Recipe.items 单向维护（Map 的 key 落在 stage 列） */
    @Column(name = "ingredient_code", nullable = false, length = 24)
    private String ingredientCode;

    /** 原料名称（冗余，便于列表展示） */
    @Column(name = "ingredient_name", length = 64)
    private String ingredientName;

    /** 原料大类：MALT/HOP/YEAST/ADJUNCT/WATER */
    @Column(name = "ingredient_type", length = 16)
    private String ingredientType;

    /** 用量 */
    @Column(name = "quantity", nullable = false, precision = 10, scale = 3)
    private BigDecimal quantity;

    /** 单位：kg / g / pkg / L */
    @Column(name = "unit", length = 8)
    private String unit;

    /** 投放备注（如"60 分钟苦花"） */
    @Column(name = "note", length = 128)
    private String note;
}
