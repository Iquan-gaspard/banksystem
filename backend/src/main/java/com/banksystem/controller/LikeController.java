package com.banksystem.controller;

import java.net.URI;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.banksystem.common.validation.ValidationPatterns;
import com.banksystem.dto.request.LikeRequest;
import com.banksystem.dto.response.LikeResponse;
import com.banksystem.model.Actor;
import com.banksystem.model.LikeItem;
import com.banksystem.service.LikeService;

/** 展示層：喜好金融商品 API（新增、查詢、修改、刪除）。 */
@RestController
@RequestMapping("/api/users/{userId}/likes")
public class LikeController {

    private static final String USER_ID_MESSAGE = "使用者 ID 格式錯誤";
    private static final String SN_MESSAGE = "序號必須為正整數";

    private final LikeService likeService;

    public LikeController(LikeService likeService) {
        this.likeService = likeService;
    }

    /** 查詢喜好清單。 */
    @GetMapping
    public List<LikeResponse> getLikes(
            @PathVariable @Pattern(regexp = ValidationPatterns.USER_ID, message = USER_ID_MESSAGE) String userId) {
        return likeService.getLikes(userId).stream().map(LikeResponse::from).toList();
    }

    /** 查詢單筆喜好。 */
    @GetMapping("/{sn}")
    public LikeResponse getLike(
            @PathVariable @Pattern(regexp = ValidationPatterns.USER_ID, message = USER_ID_MESSAGE) String userId,
            @PathVariable @Positive(message = SN_MESSAGE) long sn) {
        return LikeResponse.from(likeService.getLike(userId, sn));
    }

    /** 新增喜好：201 Created + Location header。 */
    @PostMapping
    public ResponseEntity<LikeResponse> create(
            @PathVariable @Pattern(regexp = ValidationPatterns.USER_ID, message = USER_ID_MESSAGE) String userId,
            @Valid @RequestBody LikeRequest request,
            @AuthenticationPrincipal UserDetails principal,
            HttpServletRequest httpRequest) {
        LikeItem created = likeService.create(userId, request.toInput(), actor(principal, httpRequest));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{sn}")
                .buildAndExpand(created.sn())
                .toUri();
        return ResponseEntity.created(location).body(LikeResponse.from(created));
    }

    /** 修改喜好（整筆取代）。 */
    @PutMapping("/{sn}")
    public LikeResponse update(
            @PathVariable @Pattern(regexp = ValidationPatterns.USER_ID, message = USER_ID_MESSAGE) String userId,
            @PathVariable @Positive(message = SN_MESSAGE) long sn,
            @Valid @RequestBody LikeRequest request,
            @AuthenticationPrincipal UserDetails principal,
            HttpServletRequest httpRequest) {
        return LikeResponse.from(likeService.update(userId, sn, request.toInput(), actor(principal, httpRequest)));
    }

    /** 刪除喜好：204 No Content。 */
    @DeleteMapping("/{sn}")
    public ResponseEntity<Void> delete(
            @PathVariable @Pattern(regexp = ValidationPatterns.USER_ID, message = USER_ID_MESSAGE) String userId,
            @PathVariable @Positive(message = SN_MESSAGE) long sn,
            @AuthenticationPrincipal UserDetails principal,
            HttpServletRequest httpRequest) {
        likeService.delete(userId, sn, actor(principal, httpRequest));
        return ResponseEntity.noContent().build();
    }

    /** 操作紀錄用：執行者為登入者，IP 為來源位址（經 Nginx 轉送後由 Tomcat 解析 X-Forwarded-For）。 */
    private static Actor actor(UserDetails principal, HttpServletRequest request) {
        return new Actor(principal.getUsername(), request.getRemoteAddr());
    }
}
