package com.riskwarning.common.enums;

/*
* Kafka 主题枚举
*/

import lombok.Getter;

@Getter
public enum KafkaTopic {

    TEST_TOPIC("test", "测试主题"),

    BEHAVIOR_PROCESSING_TASKS("behavior_processing_tasks", "行为处理任务主题"),

    INDICATOR_CALCULATION_TASKS("indicator_calculation_tasks", "指标计算任务主题"),

    RISK_ASSESSMENT_TASKS("risk_assessment_tasks", "风险评估任务主题"),

    QUESTIONNAIRE_REMINDER_TASKS("questionnaire_reminder_tasks", "问卷提醒任务主题"),

    ASSESSMENT_COMPLETED_EVENTS("assessment_completed_events", "评估完成事件主题");

    private final String topicName;

    private final String description;

    KafkaTopic(String topicName, String description) {
        this.topicName = topicName;
        this.description = description;
    }
}
