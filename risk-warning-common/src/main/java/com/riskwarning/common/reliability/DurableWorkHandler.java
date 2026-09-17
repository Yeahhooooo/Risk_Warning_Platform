package com.riskwarning.common.reliability;

public interface DurableWorkHandler {
    String kind();
    void execute(String payload) throws Exception;
    default int maxAttempts() { return 3; }
    default void onExhausted(String payload) throws Exception { }
}
