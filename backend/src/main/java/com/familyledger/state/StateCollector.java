package com.familyledger.state;

public interface StateCollector {
    String domain();

    StateDomainResponse collect();
}
