package com.familyledger.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.familyledger.entity.AutoTransaction;
import com.familyledger.mapper.AutoTransactionMapper;
import com.familyledger.service.impl.AutoTransactionServiceImpl;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 自动交易只读服务（t_5d7706ee）：
 * listActive 只返回 is_active=true 的周期性交易，按 next_run_date 升序。
 * 供财务状态聚合器展示「近期固定扣款」概览；绝不写 auto_transaction。
 */
class AutoTransactionServiceImplTest {

    private final AutoTransactionMapper mapper = mock(AutoTransactionMapper.class);

    @Test
    void listActiveReturnsOnlyActiveOrderedByNextRunDate() {
        AutoTransaction inactive = new AutoTransaction();
        inactive.setId(1L);
        inactive.setIsActive(false);
        inactive.setNextRunDate(LocalDate.of(2027, 2, 25));
        AutoTransaction late = new AutoTransaction();
        late.setId(2L);
        late.setIsActive(true);
        late.setNextRunDate(LocalDate.of(2026, 9, 10));
        AutoTransaction soon = new AutoTransaction();
        soon.setId(3L);
        soon.setIsActive(true);
        soon.setNextRunDate(LocalDate.of(2026, 8, 29));

        when(mapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(late, soon));
        AutoTransactionServiceImpl service = new AutoTransactionServiceImpl(mapper);

        List<AutoTransaction> active = service.listActive();

        assertThat(active).extracting(AutoTransaction::getId).containsExactly(2L, 3L);
        // 排序由 SQL orderByAsc(nextRunDate) 保证，服务层只做只读透传
        assertThat(active).allSatisfy(t -> assertThat(t.getIsActive()).isTrue());
    }
}
