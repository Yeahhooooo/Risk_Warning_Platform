package com.riskwarning.org.repository;

import com.riskwarning.common.po.report.Assessment;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.query.parser.PartTree;
import java.lang.reflect.Method;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class LatestAssessmentOrderingTest {
    @Test
    void latestAssessmentQueryUsesLargestIdAndLimitsToOne() {
        Method query = Arrays.stream(AssessmentRepository.class.getDeclaredMethods())
                .filter(method -> method.getName().startsWith("findFirstByProjectIdIn"))
                .findFirst().orElseThrow(AssertionError::new);
        PartTree tree = new PartTree(query.getName(), Assessment.class);
        assertEquals(Sort.by(Sort.Direction.DESC, "id"), tree.getSort());
        assertEquals(Integer.valueOf(1), tree.getMaxResults());
    }
}
