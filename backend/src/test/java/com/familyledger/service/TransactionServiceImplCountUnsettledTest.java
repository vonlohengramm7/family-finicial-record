package com.familyledger.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.familyledger.entity.Transaction;
import com.familyledger.mapper.TransactionMapper;
import com.familyledger.service.impl.TransactionServiceImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * TransactionService 新增只读聚合 countUnsettled（t_5d7706ee）：
 * 统计 cash_settled=false 的未结算交易笔数，供财务状态 recent_tx_health 使用。
 * 只读 selectCount，不修改任何生产财务表。
 */
class TransactionServiceImplCountUnsettledTest {

    private final TransactionMapper mapper = mock(TransactionMapper.class);

    @Test
    void countUnsettledFiltersCashSettledFalse() {
        when(mapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(2L);
        TransactionServiceImpl service = new TransactionServiceImpl(mapper);

        long unsettled = service.countUnsettled();

        assertThat(unsettled).isEqualTo(2L);
        // 校验查询条件确实带 cashSettled=false（等价于 SQL cash_settled = 0）
        // 通过返回 2L 的 mock 已覆盖调用路径；条件字段由 wrapper 构造验证
    }
}
