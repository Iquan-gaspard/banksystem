package com.banksystem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.banksystem.common.exception.ResourceNotFoundException;
import com.banksystem.model.Actor;
import com.banksystem.model.LikeAmount;
import com.banksystem.model.LikeInput;
import com.banksystem.model.LikeItem;
import com.banksystem.model.User;
import com.banksystem.repository.LikeRepository;

@ExtendWith(MockitoExtension.class)
class LikeServiceTest {

    private static final String USER_ID = "A1236456789";
    private static final User USER = new User(USER_ID, "王o明", "test@email.com", "1111999666");
    private static final Actor ACTOR = new Actor(USER_ID, "127.0.0.1");
    private static final LikeInput INPUT =
            new LikeInput("全球債券基金A", new BigDecimal("1000"), new BigDecimal("0.01"), 3, "1111999666");

    @Mock
    private LikeRepository likeRepository;

    @Mock
    private UserService userService;

    private LikeService likeService;

    @BeforeEach
    void setUp() {
        likeService = new LikeService(likeRepository, userService, new FeeCalculator());
    }

    @Test
    void createCalculatesAmountsAndSaves() {
        when(userService.getUser(USER_ID)).thenReturn(USER);
        when(likeRepository.create(eq(USER_ID), eq(INPUT), any(), eq(ACTOR))).thenReturn(10L);
        when(likeRepository.findByUserIdAndSn(USER_ID, 10L)).thenReturn(Optional.of(sampleItem(10L)));

        LikeItem result = likeService.create(USER_ID, INPUT, ACTOR);

        ArgumentCaptor<LikeAmount> amount = ArgumentCaptor.forClass(LikeAmount.class);
        verify(likeRepository).create(eq(USER_ID), eq(INPUT), amount.capture(), eq(ACTOR));
        assertThat(amount.getValue().totalFee()).isEqualByComparingTo("30");
        assertThat(amount.getValue().totalAmount()).isEqualByComparingTo("3030");
        assertThat(result.sn()).isEqualTo(10L);
    }

    @Test
    void createFailsWhenUserNotFound() {
        when(userService.getUser(USER_ID)).thenThrow(new ResourceNotFoundException("找不到使用者"));

        assertThatThrownBy(() -> likeService.create(USER_ID, INPUT, ACTOR))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(likeRepository, never()).create(anyString(), any(), any(), any());
    }

    @Test
    void updateFailsWhenLikeNotFoundOrNotOwned() {
        when(likeRepository.update(eq(USER_ID), eq(99L), eq(INPUT), any(), any())).thenReturn(false);

        assertThatThrownBy(() -> likeService.update(USER_ID, 99L, INPUT, ACTOR))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteFailsWhenLikeNotFoundOrNotOwned() {
        when(likeRepository.delete(anyString(), anyLong(), any())).thenReturn(false);

        assertThatThrownBy(() -> likeService.delete(USER_ID, 99L, ACTOR))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getLikesFailsWhenUserNotFound() {
        when(userService.getUser("NOPE")).thenThrow(new ResourceNotFoundException("找不到使用者"));

        assertThatThrownBy(() -> likeService.getLikes("NOPE"))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(likeRepository, never()).findByUserId(anyString());
    }

    private static LikeItem sampleItem(long sn) {
        return new LikeItem(sn, 1L, INPUT.productName(), INPUT.price(), INPUT.feeRate(), 3,
                INPUT.account(), new BigDecimal("30"), new BigDecimal("3030"), USER.email());
    }
}
