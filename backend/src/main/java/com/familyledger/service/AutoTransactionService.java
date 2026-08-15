package com.familyledger.service;

import com.familyledger.entity.AutoTransaction;

import java.util.List;

/**
 * 自动交易只读服务（t_5d7706ee）。
 * 仅用于家庭状态中枢展示「近期固定扣款」概览，只读 auto_transaction，绝不写入。
 */
public interface AutoTransactionService {

    /** 返回所有生效中（is_active=true）的周期性交易，按 next_run_date 升序。 */
    List<AutoTransaction> listActive();
}
