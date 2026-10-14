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
 * 覆盖 6 大风险维度下的 12 个知识库风险指标，每个风险点对应一条完整指标路径
 * （维度 &gt; 二级指标 &gt; 三级指标 &gt; 三级指标项），指标名取路径最后一级，风险点名称与三级指标同名。</p>
 *
 * <p>得分口径与 report 模块一致：百分制，高分代表低风险；
 * 分值边界沿用 {@link RiskLevelEnum#getByScoreRatio(double)}——40 分及以上为低风险、20 分及以上且低于 40 分为中风险、低于 20 分为高风险，
 * 低于满分 50% 视为触发风险。</p>
 *
 * <p>各指标得分是按多条行为加权后的结果，统一保留两位小数；写死数据不携带「相关法规」——
 * 判定报告只在产品标识与劳动用工两类风险点上给出法律依据，为保持 12 条口径一致，
 * {@code relatedRegulations} 一律输出空列表（真实评估写入的是规则库命中的法条）。</p>
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
            "存在风险。企业关联方风险：合作伙伴存在违法违规行为。产品法律风险：产品标识管理制度与特殊产品警示标识缺失。"
                    + "劳动法律风险：未按规定支付加班工资，工时制度约定缺失。企业信用风险：应付账款逾期，票据信用记录不良。"
                    + "国际化经营风险：出口管制物项与进出口许可证管理不合规，缺少最终用户/最终用途审查。"
                    + "供应链风险：缺乏多清单筛查机制，供应商准入环节的制裁清单筛查覆盖不足。";

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

    /** 写死的 12 条三级指标计算结果（每次返回副本，避免调用方修改缓存）。 */
    public static List<IndicatorResult> indicatorResults() {
        return new ArrayList<>(INDICATOR_RESULTS);
    }

    /** 写死的 12 条风险记录（每次返回副本，避免调用方修改缓存）。 */
    public static List<Risk> risks() {
        return new ArrayList<>(RISKS);
    }

    /**
     * 写死的 12 条「风险点名称 -&gt; 知识库完整指标路径」映射，顺序与 {@link #risks()} 一致。
     *
     * <p>风险点与三级指标同名，因此可以用风险点名称直接查到它对应的完整路径
     * （维度 &gt; 二级指标 &gt; 三级指标 &gt; 三级指标项）。</p>
     */
    public static java.util.Map<String, String> relatedIndicatorPaths() {
        java.util.Map<String, String> paths = new java.util.LinkedHashMap<>();
        for (Item item : ITEMS) {
            paths.put(item.riskName(), item.path);
        }
        return paths;
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
                    .indicatorName(item.indicatorName())
                    .relatedIndicator(item.path)
                    .indicatorLevel(3)
                    .dimension(item.dimension())
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
                    .name(item.riskName())
                    .dimension(item.dimension())
                    .relatedIndicator(item.path)
                    .description(item.judgement)
                    .riskLevel(level)
                    .probability(probability(level))
                    .impact(impact(level))
                    .detectability(detectability(level))
                    .status(RiskStatusEnum.TO_BE_DISPOSED)
                    .processingStatus("成功")
                    .responsibleParty(responsibleParty(item.dimension()))
                    .affectedObjects(affectedObjects(item.dimension()))
                    .impactScope(item.impactScope)
                    .countermeasures(item.countermeasures)
                    .relatedIndicators(Collections.singletonList(evidence(i, item)))
                    .createAt(ASSESSMENT_DATE)
                    .build());
        }
        return Collections.unmodifiableList(rows);
    }

    /**
     * 指标 -> 行为证据（score 以 0-1 合规比例存储，与既有 Risk 数据口径一致）。
     *
     * <p>写死数据集不提供「相关法规」：判定报告只在产品标识与劳动用工两类风险点上给出法律依据，
     * 为保持 12 条口径一致，这里统一给出空列表；真实评估写入的是规则库命中的法条。</p>
     */
    private static RelatedIndicator evidence(int index, Item item) {
        return RelatedIndicator.builder()
                .indicatorId(indicatorId(index))
                .indicatorName(item.indicatorName())
                .score(item.score / 100.0)
                .maxScore(100.0)
                .relatedBehaviors(behaviors(index, item))
                .isPrimaryTrigger(true)
                .build();
    }

    /**
     * 一个风险指标可以对应多条行为，每条行为单独作为一条证据返回。
     *
     * <p>行为 ID 按数据集顺序全局编号（behavior-1 …… behavior-N）。
     * 同一条事实允许同时挂在多个指标下（例如「未办理两用物项出口许可证」同时命中
     * 出口管制物项合规性与进出口许可证合规性），因此不同指标下出现相同描述是刻意的，
     * 不视为重复。</p>
     */
    private static List<RelatedBehavior> behaviors(int index, Item item) {
        int offset = 0;
        for (int i = 0; i < index; i++) {
            offset += ITEMS.get(i).behaviors.size();
        }
        List<RelatedBehavior> rows = new ArrayList<>();
        for (int i = 0; i < item.behaviors.size(); i++) {
            rows.add(RelatedBehavior.builder()
                    .behaviorId(behaviorId(offset + i))
                    .projectId(PROJECT_ID)
                    .description(item.behaviors.get(i))
                    .relatedRegulations(new ArrayList<>())
                    .build());
        }
        return rows;
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

        // 1. 企业关联方风险（中风险）
        items.add(new Item(
                "企业关联方风险 > 合作伙伴（合资方、战略合作方） > 合作方违规 > 企业合作伙伴是否存在违法违规行为",
                30.25,
                Arrays.asList(
                        "2025年9月至10月，授权渠道商 WWW国际贸易有限公司违反《无线通信模组转口经销协议》关于最终用户限制和进口国标识/认证配合义务的约定。"),
                "授权渠道商 WWW国际贸易有限公司违反《无线通信模组转口经销协议》关于最终用户限制和进口国标识/认证配合义务的约定，属于企业合作伙伴存在违法违规行为。",
                "可能引发合作方管理失控与转口贸易合规风险，影响企业商誉。",
                "立即核查渠道商违法违规记录，暂停或终止与违规渠道商的合作，重新签订合规条款并留存最终用户与进口国标识资料。"));

        // 2. 产品法律风险（高风险）
        items.add(new Item(
                "产品法律风险 > 产品设计开发合规 > 产品标识与警示 > 产品标识管理制度",
                16.42,
                Arrays.asList(
                        "公司未发布《产品标识管理办法》《包装标识作业指导书》或同等制度文件；2025年版制度清单中无“产品标识”条目；仓储作业指导书仅规定“按客户箱单发货”，未规定中文名称、生产者、警示语、执行标准等标识要求。"),
                "公司未发布产品标识管理制度文件，2025 年版制度清单中无相关条目，仓储作业指导书亦未规定中文名称、生产者、警示语、执行标准等标识要求，产品标识管理制度缺失。",
                "可能因产品标识不符合强制性要求而被责令改正并承担行政处罚。",
                "依据《产品质量法》第二十七条制定《产品标识管理办法》，明确中文名称、生产者、警示语与执行标准的印刷要求，并纳入仓储作业指导书。"));

        // 3. 产品法律风险（高风险）
        items.add(new Item(
                "产品法律风险 > 产品设计开发合规 > 产品标识与警示 > 特殊产品警示标识",
                15.73,
                Arrays.asList(
                        "公司未发布过《产品标识管理办法》《包装标识作业指导书》或同等文件。制度清单（综合部 2025 年版）中无「产品标识」条目。仓储作业指导书只规定「按客户箱单发货」，不规定中文名称、生产者、警示语、执行标准如何印刷。"),
                "现有制度与仓储作业要求均未明确警示语等特殊产品警示标识的设置与印刷要求，特殊产品警示标识管理缺失。",
                "对易造成损害或危及人身财产安全的产品缺少警示说明，存在被责令改正与行政处罚的风险。",
                "在《产品标识管理办法》中单列特殊产品警示标识条款，明确警示语内容、印刷位置与入库验收要求。"));

        // 4. 劳动法律风险（高风险）
        items.add(new Item(
                "劳动法律风险 > 薪酬福利管理 > 加班工资 > 加班费支付合规率",
                12.56,
                Arrays.asList(
                        "第 3.3 条将延长工作时间报酬计入月工资包干。",
                        "本文本为公司统一模板。工会书面要求补充工时和加班待遇条款，至 2026-01-10 未修订。赵某某所在仓储班组 2025 年 11 月连续三个周日上班，工资表无加班费科目，见 06 号。"),
                "劳动合同将延长工作时间报酬计入月工资包干、未单独计发加班工资，工会要求补充条款后仍未修订，且存在休息日加班而无加班费记录的情形，未按规定支付加班工资。",
                "可能被劳动监察责令补发加班工资并加付赔偿金，并存在集体劳动争议风险。",
                "修订劳动合同薪酬条款，取消“包干”表述，加班工资单列并按 150%/200%/300% 计发，工资表增设加班费科目并倒查 2025 年 11 月休息日加班工资。"));

        // 5. 劳动法律风险（高风险）
        items.add(new Item(
                "劳动法律风险 > 工时与休假管理 > 工时制度 > 工时制度合规率",
                18.34,
                Arrays.asList(
                        "本合同全文未设立「工作时间与休息休假」专章，未约定每日工作小时数、每周工作天数、标准工时或综合计算工时，未约定工作日加班费 150%、休息日 200%、法定节假日 300% 及补休规则。"),
                "劳动合同未设置工作时间与休息休假专章，未约定每日工作小时数、每周工作天数与适用工时制度，也未明确加班待遇及补休规则，工时制度约定缺失。",
                "工时制度不合规，加班工资计算缺少依据，存在被认定违法的风险。",
                "在劳动合同中增设“工作时间与休息休假”专章，明确工时制度、加班计算方式与补休规则。"));

        // 6. 企业信用风险（中风险）
        items.add(new Item(
                "企业信用风险 > 合同与交易信用 > 应付账款信用 > 应付账款逾期情况",
                28.45,
                Arrays.asList(
                        "对主要供应商 YYY供应链的货款至本报告期末仍未付清。两张本公司承兑的商业承兑汇票在 2025 年 12 月到期拒付后，至本报告期末尚未兑付。",
                        "YYY供应链于 2026 年 1 月 6 日向本公司发出律师函，主张合同 XXX-CG-2025-011 项下货款（当时金额 860 万元，本期末账面 920 万元）逾期。截至本报告披露日，尚未立案，但存在诉讼及供应中断风险。",
                        "财务费用（含逾期付款利息及罚息）186.00 万元。"),
                "对主要供应商 YYY供应链的货款截至报告期末仍未付清，已收到律师函；同时存在商业承兑汇票拒付与逾期付款利息、罚息，应付账款逾期问题突出。",
                "可能导致供应中断、诉讼追偿与融资受限，资金成本上升。",
                "制定应付账款清偿计划并落实资金来源，优先清偿逾期款项，同步评估供应连续性与诉讼风险。"));

        // 7. 企业信用风险（中风险）
        items.add(new Item(
                "企业信用风险 > 投融资信用合规 > 信贷信用合规 > 票据信用记录",
                32.17,
                Arrays.asList(
                        "上述两张拒付商票已计入应付票据 250.00 万元。持票人已向本公司追索。"),
                "公司承兑的两张商业承兑汇票到期拒付后仍未兑付，已计入应付票据 250.00 万元，持票人已向公司追索，票据信用记录不良。",
                "票据信用记录不良，可能影响后续票据签发与银行授信。",
                "尽快兑付已拒付商票，规范票据签发与承兑审批流程，定期核查票据信用记录。"));

        // 8. 国际化经营风险（中风险）
        items.add(new Item(
                "国际化经营风险 > 跨境贸易合规风险 > 贸易制裁与出口管制合规风险 > 出口管制物项合规性",
                24.58,
                Arrays.asList(
                        "两用物项出口许可证 无 - 射频模块、含加密功能通信模块 没有办过",
                        "4 票出口申报品名为「电子模块」，归类讨论中被指出可能涉及两用物项，要求提供出口许可证。本公司回复「贸易公司无该项许可，货物为客户指定采购」。4 票已放行后被要求后续整改，至本台账日仍未申请许可证。",
                        "E-2025-0912 无线通信模组 经 WWW贸易转口美国 无两用物项证；无最终用户 E-2025-0920 同上 同上 无 E-2025-1005 含加密功能模块 荷兰 无 E-2025-1022 含加密功能模块 德国 无"),
                "射频模块、含加密功能通信模块等出口均未办理两用物项出口许可证，被要求提供许可证的电子模块批次在放行后至台账日仍未补办，出口管制物项合规性不足。",
                "可能被认定为违规出口，面临行政处罚与刑事风险。",
                "补办两用物项出口许可证，建立出口管制物项台账与许可前置审批机制，出口前强制开展物项归类判定。"));

        // 9. 国际化经营风险（中风险）
        items.add(new Item(
                "国际化经营风险 > 跨境贸易合规风险 > 进出口监管合规风险 > 进出口许可证合规性",
                25.62,
                Arrays.asList(
                        "两用物项出口许可证 无 - 射频模块、含加密功能通信模块 没有办过",
                        "自动进口许可证 ZXXXX-2024-18 2025-06-30 已到期 部分机电产品进口 过期未续",
                        "4 票出口申报品名为「电子模块」，归类讨论中被指出可能涉及两用物项，要求提供出口许可证。本公司回复「贸易公司无该项许可，货物为客户指定采购」。4 票已放行后被要求后续整改，至本台账日仍未申请许可证。",
                        "E-2025-0912 无线通信模组 经 WWW贸易转口美国 无两用物项证；无最终用户 E-2025-0920 同上 同上 无 E-2025-1005 含加密功能模块 荷兰 无 E-2025-1022 含加密功能模块 德国 无",
                        "I-2025-0708 机电部件进口 国内 自动进口许可已过期后的第 8 天到货"),
                "射频模块、含加密功能通信模块出口未取得相应许可证，自动进口许可证到期后仍继续进口机电产品，部分批次在证照失效期间到货，进出口许可证管理存在多处违规。",
                "进口业务存在违规风险，可能被暂停相关业务资格并处以罚款。",
                "立即办理自动进口许可证续期、补办出口许可证，设置证照到期提醒并在业务系统中增加有效期校验与业务阻断。"));

        // 10. 国际化经营风险（中风险）
        items.add(new Item(
                "国际化经营风险 > 跨境贸易合规风险 > 贸易制裁与出口管制合规风险 > 最终用户/最终用途合规性",
                22.39,
                Arrays.asList(
                        "E-2025-0912 无线通信模组 经 WWW贸易转口美国 无两用物项证；无最终用户 E-2025-0920 同上 同上 无 E-2025-1005 含加密功能模块 荷兰 无 E-2025-1022 含加密功能模块 德国 无"),
                "无线通信模组经 WWW 贸易转口美国时缺少最终用户信息，同类交易亦未留存最终用户资料，且同时存在许可证信息缺失问题，最终用户与最终用途审查缺失。",
                "转口至管制国家或地区可能触发出口管制与制裁风险。",
                "建立最终用户与最终用途审查记录并留存证明材料，出口前完成最终用户尽调与用途核验。"));

        // 11. 供应链风险（中风险）
        items.add(new Item(
                "供应链风险 > 供应链制裁合规 > 制裁清单筛查 > 多清单筛查机制",
                30.14,
                Arrays.asList(
                        "没有形成有效管理。公司没有《制裁名单筛查作业指导书》，没有订阅 OFAC SDN、美国商务部实体清单（Entity List）、BIS 未核实清单（UVL）或欧盟限制名单的查询工具。合作方建档只有营业执照，没有「是否命中美国制裁名单」字段。"),
                "公司未建立制裁名单筛查作业指导书，未订阅 OFAC SDN、实体清单、BIS 未核实清单、欧盟限制名单等查询工具，合作方建档仅有营业执照、缺少制裁命中字段，未形成有效的多清单筛查机制。",
                "可能与受制裁主体发生交易，触发二级制裁与交易冻结风险。",
                "制定《制裁名单筛查作业指导书》，订阅 OFAC/BIS/欧盟清单查询工具，并在合作方建档中增加制裁命中字段。"));

        // 12. 供应链风险（中风险）
        items.add(new Item(
                "供应链风险 > 供应商准入管理 > 制裁与黑名单筛查 > 清单筛查覆盖率",
                26.28,
                Arrays.asList(
                        "ZZZ电子于2025-10-11出现制裁相关舆情后，公司仍继续执行PO-2025-1018、PO-2025-1106、PO-2025-1202三笔采购订单，合计约210万元；三笔订单审批单的“制裁筛查”栏均为空白，采购经理仍批注“老供应商，先交货”，说明供应商交易未被制裁筛查完整覆盖。"),
                "合作方出现制裁相关舆情后，公司仍继续执行三笔采购订单、合计约 210 万元，且订单审批单“制裁筛查”栏均为空白，供应商交易未被制裁清单筛查完整覆盖。",
                "在未完成制裁筛查的情况下继续交易，合规风险敞口扩大。",
                "暂停与舆情合作方的交易直至完成筛查，落实订单审批单制裁筛查栏必填与二次复核。"));

        return Collections.unmodifiableList(items);
    }

    /**
     * 单条「知识库风险指标路径 -> 风险点」映射。
     *
     * <p>维度与指标名均由完整指标路径推导：维度取第一级，指标名取最后一级（三级指标），
     * 风险点名称与三级指标同名，保证三者始终一致。</p>
     */
    private static final class Item {

        /** 指标路径分隔符，与知识库中的书写格式一致。 */
        private static final String SEPARATOR = " > ";

        /** 完整指标路径：维度 &gt; 二级指标 &gt; 三级指标 &gt; 三级指标项。 */
        private final String path;

        private final double score;

        /** 该指标命中的行为证据，按报告原文逐条列出，一条事实一项。 */
        private final List<String> behaviors;

        private final String judgement;
        private final String impactScope;
        private final String countermeasures;

        private Item(String path, double score, List<String> behaviors, String judgement,
                     String impactScope, String countermeasures) {
            this.path = path;
            this.score = score;
            this.behaviors = behaviors;
            this.judgement = judgement;
            this.impactScope = impactScope;
            this.countermeasures = countermeasures;
        }

        /** 维度取路径第一级，与 {@code ReportRiskDimensions} 的对外展示名一致。 */
        private String dimension() {
            return path.substring(0, path.indexOf(SEPARATOR));
        }

        /** 指标名取路径最后一级，即三级指标。 */
        private String indicatorName() {
            return path.substring(path.lastIndexOf(SEPARATOR) + SEPARATOR.length());
        }

        /** 风险点名称与三级指标同名。 */
        private String riskName() {
            return indicatorName();
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
