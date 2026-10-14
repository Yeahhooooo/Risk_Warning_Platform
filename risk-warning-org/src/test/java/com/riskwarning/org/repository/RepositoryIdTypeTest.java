package com.riskwarning.org.repository;

import com.riskwarning.common.po.project.Project;
import com.riskwarning.common.po.report.Assessment;
import org.junit.jupiter.api.Test;
import org.springframework.core.GenericTypeResolver;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.Repository;

import javax.persistence.Id;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 守住「仓储泛型 ID 类型 == 实体 @Id 字段类型」这一约束。
 *
 * <p>背景：org 的 AssessmentRepository 曾把泛型写成 {@code JpaRepository<Assessment, Integer>}，
 * 而 {@link Assessment#getId()} 实际是 {@code Long}。编译期完全发现不了，
 * 直到运行时用继承的 {@code findById(Integer)} 去查 Long 主键，才抛
 * {@code Provided id of the wrong type for class Assessment. Expected: Long, got: Integer}。
 * 这个测试让同类问题在构建阶段就暴露。</p>
 */
class RepositoryIdTypeTest {

    @Test
    void assessmentRepositoryIdTypeMatchesEntityIdField() {
        assertIdTypeMatches(AssessmentRepository.class, Assessment.class);
    }

    @Test
    void projectRepositoryIdTypeMatchesEntityIdField() {
        assertIdTypeMatches(ProjectRepository.class, Project.class);
    }

    @Test
    void guardActuallyDetectsMismatch() {
        // 自检：故意用一个泛型 ID 类型写错的仓储接口，确认校验逻辑真的会判定为不一致。
        Class<?> wrongIdType = GenericTypeResolver
                .resolveTypeArguments(WrongIdTypeAssessmentRepository.class, Repository.class)[1];

        assertEquals(Integer.class, wrongIdType);
        assertNotEquals(findIdField(Assessment.class).getType(), wrongIdType,
                "守卫逻辑失效：没能识别出 Generic ID 与实体 @Id 类型不一致");
    }

    /** 校验仓储的第二个泛型参数（ID 类型）与实体被 {@code @Id} 标注的字段类型一致。 */
    private void assertIdTypeMatches(Class<?> repositoryInterface, Class<?> entityClass) {
        Class<?>[] typeArguments = GenericTypeResolver.resolveTypeArguments(repositoryInterface, Repository.class);
        assertTrue(typeArguments != null, "无法解析 " + repositoryInterface.getName() + " 的泛型参数");
        Class<?> idType = typeArguments[1];
        Field idField = findIdField(entityClass);

        assertEquals(idField.getType(), idType,
                repositoryInterface.getSimpleName() + " 的泛型 ID 类型与 " + entityClass.getSimpleName()
                        + "." + idField.getName() + " 的类型不一致："
                        + "泛型=" + idType.getName() + "，实体=" + idField.getType().getName()
                        + "（运行时会报 Provided id of the wrong type）");
    }

    private Field findIdField(Class<?> entityClass) {
        for (Class<?> current = entityClass; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (field.isAnnotationPresent(Id.class)) {
                    return field;
                }
            }
        }
        throw new AssertionError("未在 " + entityClass.getName() + " 上找到 @Id 字段");
    }

    /** 仅供自检测试使用：泛型 ID 故意写成 Integer，与 Assessment 的 Long 主键不符。 */
    private interface WrongIdTypeAssessmentRepository extends JpaRepository<Assessment, Integer> {
    }
}
