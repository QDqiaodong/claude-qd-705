package com.brewery.craft.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Data;

/** 模块三：配方。 */
@Data
@Entity
@Table(name = "recipe")
public class Recipe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 配方编码 */
    @Column(name = "code", nullable = false, length = 24)
    private String code;

    /** 配方名 */
    @Column(name = "name", nullable = false, length = 64)
    private String name;

    /** 风格：IPA/STOUT/PILSNER/WHEAT/SOUR/PALE_ALE */
    @Column(name = "style", nullable = false, length = 16)
    private String style;

    /** 目标原浓 OG */
    @Column(name = "target_og", precision = 5, scale = 3)
    private BigDecimal targetOg;

    /** 目标酒精度 %vol */
    @Column(name = "target_abv", precision = 4, scale = 2)
    private BigDecimal targetAbv;

    /** 目标苦度 IBU */
    @Column(name = "target_ibu", precision = 5, scale = 1)
    private BigDecimal targetIbu;

    /** 目标色度 EBC */
    @Column(name = "target_ebc", precision = 5, scale = 1)
    private BigDecimal targetEbc;

    @Column(name = "process_note", length = 512)
    private String processNote;

    /**
     * 【主数据访问路线：@OneToMany + @MapKeyColumn 的 Map 集合映射】
     * key = 投放阶段（MASH_IN / BOIL_60 / DRYHOP_1 …），落在 recipe_item.stage 列；
     * value = 该阶段的原料用量明细实体。
     */
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinTable(
            name = "recipe_item_slot",
            joinColumns = @JoinColumn(name = "recipe_id"),
            inverseJoinColumns = @JoinColumn(name = "item_id", unique = true))
    @MapKeyColumn(name = "stage")
    private Map<String, RecipeItem> items = new LinkedHashMap<>();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
