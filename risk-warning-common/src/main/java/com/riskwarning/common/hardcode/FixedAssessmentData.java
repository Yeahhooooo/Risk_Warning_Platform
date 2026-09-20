package com.riskwarning.common.hardcode;

import com.riskwarning.common.enums.AssessmentStatusEnum;
import com.riskwarning.common.enums.indicator.IndicatorRiskStatus;
import com.riskwarning.common.enums.risk.RiskLevelEnum;
import com.riskwarning.common.enums.risk.RiskStatusEnum;
import com.riskwarning.common.po.indicator.IndicatorResult;
import com.riskwarning.common.po.indicator.IndicatorResultDetail;
import com.riskwarning.common.po.report.Assessment;
import com.riskwarning.common.po.risk.RelatedBehavior;
import com.riskwarning.common.po.risk.RelatedIndicator;
import com.riskwarning.common.po.risk.RelatedRegulation;
import com.riskwarning.common.po.risk.Risk;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 写死的评估结果数据集。
 *
 * <p>数据来源：《JUDGE-A4DF00BA94F3 合规判定报告》与《课题三任务三对山东XX国际贸易公司的判定结果的风险指标追溯对应文档》。
 * 覆盖 6 大风险维度下的 17 条风险事项，其中“企业关联方风险 - 货物来源追溯”属于风险指标外的潜在风险，
 * 按任务三口径剔除，最终对应 16 条三级指标。</p>
 *
 * <p>得分口径与 report 模块一致：百分制，高分代表低风险；
 * 分值边界沿用 {@link RiskLevelEnum#getByScoreRatio(double)}——40 分及以上为低风险、20 分及以上且低于 40 分为中风险、低于 20 分为高风险，
 * 低于满分 50% 视为触发风险。</p>
 *
 * <p>该数据为演示/答辩环境固定返回，不写入数据库，也不覆盖任何真实评估记录。</p>
 */
public final class FixedAssessmentData {

    /** 写死评估记录的固定主键，避免与真实评估记录（以及单元测试中使用的 id）冲突。 */
    public static final Long ASSESSMENT_ID = 900001L;

    /** 写死评估所属项目。 */
    public static final Long PROJECT_ID = 900001L;

    /** 被评估企业名称。 */
    public static final String ENTERPRISE_NAME = "山东某某国际贸易公司";

    /** 统一社会信用代码（取自判定报告）。 */
    public static final String CREDIT_CODE = "91370102DLM9PF11QC";

    /** 判定报告任务号。 */
    public static final String JUDGE_TASK_NO = "JUDGE-A4DF00BA94F3";

    /** 评估时间（判定报告生成时间 2026-09-19T19:46:40+00:00）。 */
    public static final LocalDateTime ASSESSMENT_DATE = LocalDateTime.of(2026, 9, 19, 19, 46, 40);

    /** 触发风险的得分比例边界：低于该比例（即低于 50 分）判定为触发风险。 */
    public static final double RISK_TRIGGERED_RATIO = 0.5;

    // 各维度对外展示名称（与 ReportRiskDimensions 保持一致）
    private static final String DIM_ENTERPRISE_RELATED = "企业关联方风险";
    private static final String DIM_PRODUCT = "产品法律风险";
    private static final String DIM_LABOR = "劳动法律风险";
    private static final String DIM_CREDIT = "企业信用风险";
    private static final String DIM_INTERNATIONAL = "国际化经营风险";
    private static final String DIM_SUPPLY_CHAIN = "供应链风险";

    private static final String PRODUCT_LAW = "中华人民共和国产品质量法";
    private static final String LABOR_LAW = "中华人民共和国劳动合同法";

    private static final List<Item> ITEMS = buildItems();
    private static final List<IndicatorResult> INDICATOR_RESULTS = buildIndicatorResults();
    private static final List<Risk> RISKS = buildRisks();

    /**
     * 评估详情快照，内容为 {@code AssessmentGeneralDetails(general, indicatorResult)} 的 JSON，
     * 与 report 的 general / indicatorResult 两个接口输出严格一致。
     *
     * <p>org 的接口直接返回本类构造的 Assessment，前端会从 {@code details} 字段解析评估详情，
     * 因此这里把详情一并带上，保证 org 与 report 返回的是同一份结果。</p>
     *
     * <p>快照由 report 模块的 {@code GenerateFixedDetailsSnapshotTest} 生成，
     * 并由 {@code FixedAssessmentResultTest} 校验与实时组装结果的一致性。</p>
     */
    private static final String DETAILS_JSON = loadDetails();

    private static final String RECOMMENDATIONS =
            "存在风险。企业关联方风险：合作伙伴存在违法违规行为。产品法律风险：缺乏产品标识管理制度与特殊产品警示标识。"
                    + "劳动法律风险：未按规定支付加班工资，工时制度不合规。企业信用风险：应付账款逾期，票据信用记录不良。"
                    + "国际化经营风险：未办理两用物项出口许可证，进出口许可证过期。供应链风险：缺乏有效的制裁名单筛查机制，供应商准入管理不完善。";

    private FixedAssessmentData() {
    }

    /** 从 classpath 读取评估详情快照；资源缺失时返回 null（org 接口退化为不带 details 的评估记录）。 */
    private static String loadDetails() {
        try (InputStream in = FixedAssessmentData.class
                .getResourceAsStream("/hardcode/fixed-assessment-details.json")) {
            if (in == null) {
                return null;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * 写死的评估主记录。details 携带与 report 的 general / indicatorResult 完全一致的详情快照，
     * 因此 org 接口返回的评估结果与 report 接口返回的是同一份内容。
     */
    public static Assessment assessment() {
        return Assessment.builder()
                .id(ASSESSMENT_ID)
                .projectId(PROJECT_ID)
                .assessmentDate(ASSESSMENT_DATE)
                .overallScore(overallScore())
                .overallRiskLevel(overallRiskLevel())
                .status(AssessmentStatusEnum.ASSESSED)
                .details(DETAILS_JSON)
                .recommendations(RECOMMENDATIONS)
                .createdAt(ASSESSMENT_DATE)
                .build();
    }

    /** 判断给定评估ID是否为写死的演示评估记录。 */
    public static boolean matches(Long assessmentId) {
        return ASSESSMENT_ID.equals(assessmentId);
    }

    /** 写死的 16 条三级指标计算结果（每次返回副本，避免调用方修改缓存）。 */
    public static List<IndicatorResult> indicatorResults() {
        return new ArrayList<>(INDICATOR_RESULTS);
    }

    /** 写死的 16 条风险记录（每次返回副本，避免调用方修改缓存）。 */
    public static List<Risk> risks() {
        return new ArrayList<>(RISKS);
    }

    /** 总体百分制得分：各指标得分按满分权重加权平均，保留两位小数。 */
    public static Double overallScore() {
        double earned = 0;
        double maximum = 0;
        for (IndicatorResult row : INDICATOR_RESULTS) {
            Double score = row.getCalculatedScore();
            Double max = row.getMaxPossibleScore();
            if (score == null || max == null || !Double.isFinite(score) || !Double.isFinite(max) || max <= 0) {
                continue;
            }
            earned += score;
            maximum += max;
        }
        if (maximum <= 0) {
            return null;
        }
        return BigDecimal.valueOf(earned / maximum * 100).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    /** 总体风险等级：沿用按风险数量计算的既有规则。 */
    public static RiskLevelEnum overallRiskLevel() {
        int low = 0;
        int medium = 0;
        int high = 0;
        for (Item item : ITEMS) {
            switch (item.riskLevel()) {
                case HIGH_RISK:
                    high++;
                    break;
                case MEDIUM_RISK:
                    medium++;
                    break;
                default:
                    low++;
                    break;
            }
        }
        return RiskLevelEnum.getByRiskCount(low, medium, high);
    }

    private static String indicatorId(int index) {
        return "indicator-" + (index + 1);
    }

    private static String behaviorId(int index) {
        return "behavior-" + (index + 1);
    }

    private static String riskId(int index) {
        return "risk-" + (index + 1);
    }

    // ------------------------------------------------------------------
    // 数据集构建
    // ------------------------------------------------------------------

    private static List<IndicatorResult> buildIndicatorResults() {
        List<IndicatorResult> rows = new ArrayList<>();
        for (int i = 0; i < ITEMS.size(); i++) {
            Item item = ITEMS.get(i);
            rows.add(IndicatorResult.builder()
                    .id((long) (i + 1))
                    .projectId(PROJECT_ID)
                    .assessmentId(ASSESSMENT_ID)
                    .indicatorEsId(indicatorId(i))
                    .indicatorName(item.indicatorName)
                    .indicatorLevel(3)
                    .dimension(item.dimension)
                    .type("behavior")
                    .calculatedScore(item.score)
                    .maxPossibleScore(100.0)
                    .usedCalculationRuleType("STATIC_THRESHOLD")
                    .calculationDetails(IndicatorResultDetail.builder()
                            .relatedIndicators(Collections.singletonList(evidence(i, item)))
                            .build())
                    .riskTriggered(item.riskTriggered())
                    .riskStatus(IndicatorRiskStatus.EVALUATED)
                    .calculatedAt(ASSESSMENT_DATE)
                    .createdAt(ASSESSMENT_DATE)
                    .build());
        }
        return Collections.unmodifiableList(rows);
    }

    private static List<Risk> buildRisks() {
        List<Risk> rows = new ArrayList<>();
        for (int i = 0; i < ITEMS.size(); i++) {
            Item item = ITEMS.get(i);
            RiskLevelEnum level = item.riskLevel();
            rows.add(Risk.builder()
                    .id(riskId(i))
                    .projectId(PROJECT_ID)
                    .assessmentId(ASSESSMENT_ID)
                    .name(item.riskName)
                    .dimension(item.dimension)
                    .description(item.judgement)
                    .riskLevel(level)
                    .probability(probability(level))
                    .impact(impact(level))
                    .detectability(detectability(level))
                    .status(RiskStatusEnum.TO_BE_DISPOSED)
                    .processingStatus("成功")
                    .responsibleParty(responsibleParty(item.dimension))
                    .affectedObjects(affectedObjects(item.dimension))
                    .impactScope(item.impactScope)
                    .countermeasures(item.countermeasures)
                    .relatedIndicators(Collections.singletonList(evidence(i, item)))
                    .createAt(ASSESSMENT_DATE)
                    .build());
        }
        return Collections.unmodifiableList(rows);
    }

    /** 指标 -> 行为证据（score 以 0-1 合规比例存储，与既有 Risk 数据口径一致）。 */
    private static RelatedIndicator evidence(int index, Item item) {
        List<RelatedRegulation> regulations = item.regulations == null
                ? new ArrayList<RelatedRegulation>()
                : item.regulations;
        return RelatedIndicator.builder()
                .indicatorId(indicatorId(index))
                .indicatorName(item.indicatorName)
                .score(item.score / 100.0)
                .maxScore(100.0)
                .relatedBehaviors(Collections.singletonList(RelatedBehavior.builder()
                        .behaviorId(behaviorId(index))
                        .projectId(PROJECT_ID)
                        .description(item.behavior)
                        .relatedRegulations(regulations)
                        .build()))
                .isPrimaryTrigger(true)
                .build();
    }

    private static double probability(RiskLevelEnum level) {
        return level == RiskLevelEnum.HIGH_RISK ? 0.8 : 0.6;
    }

    private static double impact(RiskLevelEnum level) {
        return level == RiskLevelEnum.HIGH_RISK ? 0.8 : 0.6;
    }

    private static double detectability(RiskLevelEnum level) {
        return level == RiskLevelEnum.HIGH_RISK ? 0.6 : 0.5;
    }

    private static String responsibleParty(String dimension) {
        if (DIM_ENTERPRISE_RELATED.equals(dimension)) {
            return "市场部";
        }
        if (DIM_PRODUCT.equals(dimension)) {
            return "质量部";
        }
        if (DIM_LABOR.equals(dimension)) {
            return "人力资源部";
        }
        if (DIM_CREDIT.equals(dimension)) {
            return "财务部";
        }
        if (DIM_INTERNATIONAL.equals(dimension)) {
            return "进出口部";
        }
        return "采购部";
    }

    private static String[] affectedObjects(String dimension) {
        if (DIM_ENTERPRISE_RELATED.equals(dimension)) {
            return new String[]{"合作方管理"};
        }
        if (DIM_PRODUCT.equals(dimension)) {
            return new String[]{"产品标识", "包装标识"};
        }
        if (DIM_LABOR.equals(dimension)) {
            return new String[]{"劳动合同", "工资表"};
        }
        if (DIM_CREDIT.equals(dimension)) {
            return new String[]{"应付账款", "商业承兑汇票"};
        }
        if (DIM_INTERNATIONAL.equals(dimension)) {
            return new String[]{"进出口许可证", "报关单"};
        }
        return new String[]{"供应商准入", "采购订单"};
    }

    // ------------------------------------------------------------------
    // 数据明细
    // ------------------------------------------------------------------

    private static List<Item> buildItems() {
        List<Item> items = new ArrayList<>();

        // 1. 企业关联方风险（中风险）：任务三口径下唯一保留的关联方风险事项
        items.add(new Item(DIM_ENTERPRISE_RELATED, "企业合作伙伴是否存在违法违规行为", 30.0,
                "合作伙伴违反合作约定",
                "材料显示，渠道商 WWW国际贸易有限公司违反双方《无线通信模组转口经销协议》关于最终用户限制和进口国标识/认证配合义务的约定，相关情况已被行业媒体公开报道，属于企业合作伙伴存在违法违规行为。",
                "2025 年 9 月至 10 月，本公司发现授权渠道商 WWW国际贸易有限公司违反双方《无线通信模组转口经销协议》关于最终用户限制和进口国标识/认证配合义务的约定。",
                "可能引发合作方管理失控与转口贸易合规风险，影响企业商誉。",
                "立即核查渠道商违法违规记录，暂停或终止与违规渠道商的合作，重新签订合规条款并留存最终用户与进口国标识资料。",
                null));

        // 2. 产品法律风险（高风险）：路径中取最末三级指标“特殊产品警示标识”
        items.add(new Item(DIM_PRODUCT, "特殊产品警示标识", 15.0,
                "产品标识管理制度缺失",
                "企业未发布任何关于产品标识管理的制度文件，制度清单中也无相关条目，仓储作业指导书未规定中文名称、生产者、警示语、执行标准等内容，表明企业缺乏必要的产品标识管理制度，特殊产品警示标识要求无法落实。",
                "公司未发布过《产品标识管理办法》《包装标识作业指导书》或同等文件。制度清单（综合部 2025 年版）中无「产品标识」条目。仓储作业指导书只规定「按客户箱单发货」，不规定中文名称、生产者、警示语、执行标准如何印刷。",
                "可能因产品标识不符合强制性要求而被责令改正并承担行政处罚。",
                "依据《产品质量法》第二十七条制定《产品标识管理办法》，明确中文名称、生产者、警示语与执行标准的印刷要求，并纳入仓储作业指导书。",
                regulation("reg-product-27", PRODUCT_LAW, "第二十七条", "产品或者其包装上的标识必须真实，并按规定标明中文名称、生产厂厂名和厂址，对易造成损害或危及人身财产安全的产品应有警示标志或中文警示说明。")));

        // 3. 劳动法律风险（高风险）- 加班工资
        items.add(new Item(DIM_LABOR, "加班费支付合规率", 12.0,
                "未按规定支付加班工资",
                "材料显示，劳动合同第 3.3 条将延长工作时间报酬计入月工资包干，未另行计发加班工资。这违反了《中华人民共和国劳动合同法》关于加班工资的规定，存在高风险。",
                "第 3.3 条将延长工作时间报酬计入月工资包干。",
                "可能被劳动监察责令补发加班工资并加付赔偿金。",
                "修订劳动合同薪酬条款，取消“包干”表述，将加班工资单列并按 150%/200%/300% 计发，工资表中增设加班费科目。",
                regulation("reg-labor-17", LABOR_LAW, "第十七条", "劳动合同应当具备工作时间和休息休假、劳动报酬等必备条款。")));

        // 4. 劳动法律风险（高风险）- 加班工资（工会要求未修订）
        items.add(new Item(DIM_LABOR, "加班费支付合规率", 12.0,
                "加班工资条款长期未修订",
                "材料显示，工会书面要求补充工时和加班待遇条款，但截至 2026-01-10 仍未修订。赵某某所在仓储班组 2025 年 11 月连续三个周日上班，工资表无加班费科目。这表明企业未按规定支付加班工资，存在高风险。",
                "本文本为公司统一模板。工会书面要求补充工时和加班待遇条款，至 2026-01-10 未修订。赵某某所在仓储班组 2025 年 11 月连续三个周日上班，工资表无加班费科目。",
                "可能引发集体劳动争议，并被要求补发休息日加班工资。",
                "按工会意见限期修订劳动合同模板，补齐加班费科目，并倒查 2025 年 11 月休息日加班工资。",
                regulation("reg-labor-17", LABOR_LAW, "第十七条", "劳动合同应当具备工作时间和休息休假、劳动报酬等必备条款。")));

        // 5. 劳动法律风险（高风险）- 工时制度
        items.add(new Item(DIM_LABOR, "工时制度合规率", 18.0,
                "工时制度约定缺失",
                "材料显示，劳动合同全文未设立「工作时间与休息休假」专章，未约定每日工作小时数、每周工作天数、标准工时或综合计算工时，未约定工作日加班费 150%、休息日 200%、法定节假日 300% 及补休规则。",
                "本合同全文未设立「工作时间与休息休假」专章，未约定每日工作小时数、每周工作天数、标准工时或综合计算工时，未约定工作日加班费 150%、休息日 200%、法定节假日 300% 及补休规则。",
                "工时制度不合规，加班工资计算缺少依据，存在被认定违法的风险。",
                "在劳动合同中增设「工作时间与休息休假」专章，明确工时制度、加班计算方式与补休规则。",
                regulation("reg-labor-17", LABOR_LAW, "第十七条", "劳动合同应当具备工作时间和休息休假、劳动报酬等必备条款。")));

        // 6. 企业信用风险（中风险）- 应付账款逾期
        items.add(new Item(DIM_CREDIT, "应付账款逾期情况", 28.0,
                "应付账款逾期或拖欠付款",
                "材料显示，公司对主要供应商 YYY供应链的货款至本报告期末仍未付清，且两张商业承兑汇票到期拒付后仍未兑付。这表明公司存在严重的应付账款逾期问题，且已引发供应商的法律行动。",
                "对主要供应商 YYY供应链的货款至本报告期末仍未付清。两张本公司承兑的商业承兑汇票在 2025 年 12 月到期拒付后，至本报告期末尚未兑付。",
                "可能导致供应中断、诉讼追偿与融资受限。",
                "制定应付账款清偿计划并落实资金来源，优先清偿逾期款项，同步评估供应连续性风险。",
                null));

        // 7. 企业信用风险（中风险）- 逾期债务法律争议
        items.add(new Item(DIM_CREDIT, "应付账款逾期情况", 28.0,
                "逾期债务引发法律争议风险",
                "材料显示，YYY供应链已向公司发出律师函，主张货款逾期，并存在诉讼及供应中断风险。这表明公司因逾期债务已引发法律争议。",
                "YYY供应链于 2026 年 1 月 6 日向本公司发出律师函，主张合同 XXX-CG-2025-011 项下货款（当时金额 860 万元，本期末账面 920 万元）逾期。截至本报告披露日，尚未立案，但存在诉讼及供应中断风险。",
                "可能进入诉讼程序，并因供应中断影响正常经营。",
                "主动与债权人协商还款方案，评估诉讼与供应中断影响，建立债务争议与律师函预警台账。",
                null));

        // 8. 企业信用风险（中风险）- 票据信用
        items.add(new Item(DIM_CREDIT, "票据信用记录", 32.0,
                "商业汇票拒付",
                "材料显示，公司承兑的两张商业承兑汇票到期拒付后仍未兑付，持票人已向公司追索。这表明公司存在商业汇票拒付的问题。",
                "上述两张拒付商票已计入应付票据 250.00 万元。持票人已向本公司追索。",
                "票据信用记录不良，可能影响后续票据签发与银行授信。",
                "尽快兑付已拒付商票，规范票据签发与承兑审批流程，定期核查票据信用记录。",
                null));

        // 9. 企业信用风险（中风险）- 逾期付款费用
        items.add(new Item(DIM_CREDIT, "应付账款逾期情况", 28.0,
                "逾期付款利息及罚息增加",
                "材料显示，公司的财务费用中包含逾期付款利息及罚息，且金额较大，表明公司因逾期付款产生了较高的额外费用。",
                "财务费用（含逾期付款利息及罚息）186.00 万元。",
                "资金成本上升，侵蚀经营利润。",
                "压缩逾期规模以降低罚息支出，将逾期成本纳入资金预算与部门考核。",
                null));

        // 10. 国际化经营风险（中风险）- 两用物项出口许可证缺失
        items.add(new Item(DIM_INTERNATIONAL, "进出口许可证合规性", 25.0,
                "两用物项出口许可证缺失",
                "企业未办理两用物项出口许可证，涉及射频模块和含加密功能通信模块的出口。",
                "两用物项出口许可证 无 - 射频模块、含加密功能通信模块 没有办过",
                "可能被认定为违规出口，面临行政处罚与刑事风险。",
                "办理两用物项出口许可证，建立出口管制物项台账与许可前置审批机制。",
                null));

        // 11. 国际化经营风险（中风险）- 自动进口许可证过期未续
        items.add(new Item(DIM_INTERNATIONAL, "进出口许可证合规性", 25.0,
                "自动进口许可证过期未续",
                "企业的自动进口许可证已过期，但仍继续进口部分机电产品。",
                "自动进口许可证 ZXXXX-2024-18 2025-06-30 已到期 部分机电产品进口 过期未续",
                "进口业务存在违规风险，可能被暂停相关业务资格。",
                "立即办理自动进口许可证续期，设置证照到期提醒并在业务系统中增加有效期校验。",
                null));

        // 12. 国际化经营风险（中风险）- 电子模块出口许可证缺失
        items.add(new Item(DIM_INTERNATIONAL, "进出口许可证合规性", 25.0,
                "电子模块出口许可证缺失",
                "企业出口的 4 票电子模块被指出可能涉及两用物项，但企业未办理相应的出口许可证，且至今未申请。",
                "4 票出口申报品名为「电子模块」，归类讨论中被指出可能涉及两用物项，要求提供出口许可证。本公司回复「贸易公司无该项许可，货物为客户指定采购」。4 票已放行后被要求后续整改，至本台账日仍未申请许可证。",
                "已被要求整改，继续出口可能升级为行政处罚。",
                "对已放行批次补办许可证并完成整改，出口前强制开展物项归类判定与许可核验。",
                null));

        // 13. 国际化经营风险（中风险）- 最终用户/最终用途合规性
        items.add(new Item(DIM_INTERNATIONAL, "最终用户/最终用途合规性", 22.0,
                "无线通信模组未办理两用物项出口许可证",
                "企业出口的无线通信模组和含加密功能模块均未办理两用物项出口许可证，且未能提供最终用户证明。",
                "E-2025-0912 无线通信模组 经 WWW贸易转口美国 无两用物项证；无最终用户 E-2025-0920 同上 同上 无 E-2025-1005 含加密功能模块 荷兰 无 E-2025-1022 含加密功能模块 德国 无",
                "转口至管制国家或地区可能触发出口管制与制裁风险。",
                "补办两用物项出口许可证，建立最终用户与最终用途审查记录并留存证明材料。",
                null));

        // 14. 国际化经营风险（中风险）- 过期后继续进口
        items.add(new Item(DIM_INTERNATIONAL, "进出口许可证合规性", 25.0,
                "自动进口许可证过期后继续进口",
                "企业在自动进口许可证过期后的第 8 天仍继续进口机电部件。",
                "I-2025-0708 机电部件进口 国内 自动进口许可已过期后的第 8 天到货",
                "证照失效期间开展业务，合规管理流程存在漏洞。",
                "对违规批次的进口业务补充申报，完善证照有效期校验与业务阻断流程。",
                null));

        // 15. 供应链风险（中风险）- 制裁筛查机制缺失
        items.add(new Item(DIM_SUPPLY_CHAIN, "多清单筛查机制", 30.0,
                "制裁筛查机制缺失",
                "材料显示公司没有有效的制裁名单筛查机制，没有《制裁名单筛查作业指导书》，也没有订阅相关查询工具。合作方建档中缺少「是否命中美国制裁名单」字段，表明公司在制裁筛查方面存在严重缺失。",
                "公司没有《制裁名单筛查作业指导书》，没有订阅 OFAC SDN、美国商务部实体清单（Entity List）、BIS 未核实清单（UVL）或欧盟限制名单的查询工具。合作方建档只有营业执照，没有「是否命中美国制裁名单」字段。",
                "可能与受制裁主体发生交易，触发二级制裁与交易冻结风险。",
                "制定《制裁名单筛查作业指导书》，订阅 OFAC/BIS/欧盟清单查询工具，并在合作方建档中增加制裁命中字段。",
                null));

        // 16. 供应链风险（中风险）- 清单筛查覆盖率
        items.add(new Item(DIM_SUPPLY_CHAIN, "清单筛查覆盖率", 26.0,
                "与受制裁舆情合作方继续交易",
                "材料显示合作方出现相关舆情后，企业仍继续下单，且订单审批单中的「制裁筛查」栏为空白；企业尚未完成必要筛查即继续交易，存在供应链合规风险。",
                "ZZZ电子舆情日期：2025-10-11。其后采购订单：PO-2025-1018、PO-2025-1106、PO-2025-1202，合计约 210 万元，与 05 号对该司应付逾期为同一供方。三笔订单审批单「制裁筛查」栏为空白。采购经理批注：「老供应商，先交货。」",
                "在未完成制裁筛查的情况下继续交易，合规风险敞口扩大。",
                "暂停与舆情合作方的交易直至完成筛查，落实订单审批单制裁筛查栏必填与二次复核。",
                null));

        return Collections.unmodifiableList(items);
    }

    private static List<RelatedRegulation> regulation(String id, String name, String violationType,
                                                      String complianceRequirement) {
        return Arrays.asList(RelatedRegulation.builder()
                .regulationId(id)
                .regulationName(name)
                .violationType(violationType)
                .complianceRequirement(complianceRequirement)
                .build());
    }

    /** 单条“风险事项 -> 三级指标”映射。 */
    private static final class Item {

        private final String dimension;
        private final String indicatorName;
        private final double score;
        private final String riskName;
        private final String judgement;
        private final String behavior;
        private final String impactScope;
        private final String countermeasures;
        private final List<RelatedRegulation> regulations;

        private Item(String dimension, String indicatorName, double score, String riskName, String judgement,
                     String behavior, String impactScope, String countermeasures,
                     List<RelatedRegulation> regulations) {
            this.dimension = dimension;
            this.indicatorName = indicatorName;
            this.score = score;
            this.riskName = riskName;
            this.judgement = judgement;
            this.behavior = behavior;
            this.impactScope = impactScope;
            this.countermeasures = countermeasures;
            this.regulations = regulations == null
                    ? new ArrayList<RelatedRegulation>()
                    : Collections.unmodifiableList(regulations);
        }

        /** 风险等级由得分按既有阈值推导，保证分数与等级始终自洽。 */
        private RiskLevelEnum riskLevel() {
            return RiskLevelEnum.getByScoreRatio(score / 100.0);
        }

        /** 低于满分 50% 视为触发风险。 */
        private boolean riskTriggered() {
            return score < 100.0 * RISK_TRIGGERED_RATIO;
        }
    }
}
