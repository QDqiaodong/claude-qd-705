package com.brewery.craft.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.brewery.craft.common.BizException;
import com.brewery.craft.domain.Ingredient;
import com.brewery.craft.domain.Recipe;
import com.brewery.craft.domain.RecipeItem;
import com.brewery.craft.dto.RecipeDtos;
import com.brewery.craft.dto.Views;
import com.brewery.craft.repository.IngredientRepository;
import com.brewery.craft.repository.RecipeRepository;

/**
 * 模块三：配方与原料用量。
 *
 * 用量明细走 {@code Recipe#getItems()} 这张 {@code @OneToMany + @MapKeyColumn} 的 Map，
 * 这里集中实现 Map 的增（put）删（remove）改（拿到 entity 改字段）查（按 key 取）。
 */
@Service
@Transactional(readOnly = true)
public class RecipeService {

    private final RecipeRepository recipeRepository;
    private final IngredientRepository ingredientRepository;

    public RecipeService(RecipeRepository recipeRepository, IngredientRepository ingredientRepository) {
        this.recipeRepository = recipeRepository;
        this.ingredientRepository = ingredientRepository;
    }

    public List<Views.RecipeView> list() {
        List<Views.RecipeView> list = new ArrayList<>();
        for (Recipe r : recipeRepository.findAllByOrderByCodeAsc()) {
            list.add(Views.RecipeView.of(r));
        }
        return list;
    }

    public Views.RecipeView get(Long id) {
        return Views.RecipeView.of(recipeRepository.findById(id)
                .orElseThrow(() -> new BizException("配方不存在")));
    }

    @Transactional
    public Views.RecipeView create(RecipeDtos.RecipeSaveReq req) {
        if (recipeRepository.existsByCode(req.getCode())) {
            throw new BizException("配方编码 " + req.getCode() + " 已存在");
        }
        if (Views.styleName(req.getStyle()).equals(req.getStyle())) {
            throw new BizException("啤酒风格取值非法：" + req.getStyle());
        }
        Recipe r = new Recipe();
        r.setCode(req.getCode());
        r.setName(req.getName());
        r.setStyle(req.getStyle());
        r.setTargetOg(req.getTargetOg());
        r.setTargetAbv(req.getTargetAbv());
        r.setTargetIbu(req.getTargetIbu());
        r.setTargetEbc(req.getTargetEbc());
        r.setProcessNote(req.getProcessNote());
        r.setUpdatedAt(LocalDateTime.now());
        return Views.RecipeView.of(recipeRepository.save(r));
    }

    /** 【Map 查询】整张 Map：stage → 用量明细 */
    public List<Views.RecipeItemView> items(Long id) {
        return get(id).getItems();
    }

    /** 【Map 查询】按 key 查某一条 */
    public Views.RecipeItemView item(Long id, String stage) {
        Recipe r = recipeRepository.findById(id).orElseThrow(() -> new BizException("配方不存在"));
        RecipeItem item = r.getItems().get(stage);
        if (item == null) {
            throw new BizException("阶段 " + Views.stageName(stage) + " 还没有投放记录");
        }
        return Views.RecipeItemView.of(stage, item);
    }

    /**
     * 【Map 新增 / 修改】key 已存在 → 改用量；不存在 → put 一条新的。
     */
    @Transactional
    public Views.RecipeItemView putItem(Long id, String stage, RecipeDtos.ItemSaveReq req) {
        Recipe r = recipeRepository.findById(id).orElseThrow(() -> new BizException("配方不存在"));
        if (req.getQuantity() == null || req.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("用量必须大于 0");
        }
        Ingredient ing = ingredientRepository.findByCode(req.getIngredientCode())
                .orElseThrow(() -> new BizException("原料 " + req.getIngredientCode() + " 不在库存档案中"));

        RecipeItem item = r.getItems().get(stage);
        boolean isNew = item == null;
        if (isNew) {
            item = new RecipeItem();
        }
        item.setIngredientCode(ing.getCode());
        item.setIngredientName(ing.getName());
        item.setIngredientType(ing.getType());
        item.setQuantity(req.getQuantity());
        item.setUnit(req.getUnit() == null || req.getUnit().isBlank() ? ing.getUnit() : req.getUnit());
        item.setNote(req.getNote());
        if (isNew) {
            r.getItems().put(stage, item);
        }
        r.setUpdatedAt(LocalDateTime.now());
        recipeRepository.save(r);
        return Views.RecipeItemView.of(stage, item);
    }

    /** 【Map 删除】按 key 移除一条用量明细（orphanRemoval 会把行删掉） */
    @Transactional
    public Views.RecipeView removeItem(Long id, String stage) {
        Recipe r = recipeRepository.findById(id).orElseThrow(() -> new BizException("配方不存在"));
        RecipeItem removed = r.getItems().remove(stage);
        if (removed == null) {
            throw new BizException("阶段 " + Views.stageName(stage) + " 没有投放记录，无需删除");
        }
        r.setUpdatedAt(LocalDateTime.now());
        recipeRepository.save(r);
        return Views.RecipeView.of(r);
    }

    /** 环形图用的原料构成（按 Map 里的用量汇总） */
    public List<Views.CompositionSlice> composition(Long id) {
        return get(id).getComposition();
    }

    /** 所有可用投放阶段（UI 下拉用） */
    public List<Map<String, String>> stages() {
        List<Map<String, String>> list = new ArrayList<>();
        for (String key : Views.STAGE_ORDER) {
            list.add(Map.of("key", key, "name", Views.stageName(key)));
        }
        return list;
    }
}
