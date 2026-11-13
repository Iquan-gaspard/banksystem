package com.banksystem.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.banksystem.common.exception.ResourceNotFoundException;
import com.banksystem.model.Actor;
import com.banksystem.model.LikeAmount;
import com.banksystem.model.LikeInput;
import com.banksystem.model.LikeItem;
import com.banksystem.repository.LikeRepository;

/**
 * 業務層：喜好清單。
 *
 * 新增、修改、刪除都會同時異動 product、like_list、audit_log 三張表，
 * 以 @Transactional 包成一個交易：任何一步失敗就整筆 rollback，避免資料不一致。
 */
@Service
public class LikeService {

    private static final String LIKE_NOT_FOUND = "找不到喜好商品資料";

    private final LikeRepository likeRepository;
    private final UserService userService;
    private final FeeCalculator feeCalculator;

    public LikeService(LikeRepository likeRepository, UserService userService, FeeCalculator feeCalculator) {
        this.likeRepository = likeRepository;
        this.userService = userService;
        this.feeCalculator = feeCalculator;
    }

    public List<LikeItem> getLikes(String userId) {
        userService.getUser(userId);
        return likeRepository.findByUserId(userId);
    }

    public LikeItem getLike(String userId, long sn) {
        return likeRepository.findByUserIdAndSn(userId, sn)
                .orElseThrow(() -> new ResourceNotFoundException(LIKE_NOT_FOUND));
    }

    @Transactional(rollbackFor = Exception.class)
    public LikeItem create(String userId, LikeInput input, Actor actor) {
        userService.getUser(userId);
        LikeAmount amount = calculate(input);
        long sn = likeRepository.create(userId, input, amount, actor);
        return getLike(userId, sn);
    }

    @Transactional(rollbackFor = Exception.class)
    public LikeItem update(String userId, long sn, LikeInput input, Actor actor) {
        LikeAmount amount = calculate(input);
        if (!likeRepository.update(userId, sn, input, amount, actor)) {
            throw new ResourceNotFoundException(LIKE_NOT_FOUND);
        }
        return getLike(userId, sn);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(String userId, long sn, Actor actor) {
        if (!likeRepository.delete(userId, sn, actor)) {
            throw new ResourceNotFoundException(LIKE_NOT_FOUND);
        }
    }

    private LikeAmount calculate(LikeInput input) {
        return feeCalculator.calculate(input.price(), input.purchaseQuantity(), input.feeRate());
    }
}
