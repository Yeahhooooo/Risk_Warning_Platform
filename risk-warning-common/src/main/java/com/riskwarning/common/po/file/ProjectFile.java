package com.riskwarning.common.po.file;

import com.riskwarning.common.utils.StringListJsonConverter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "t_project_file")
public class ProjectFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long projectId;

    private Long userId;


    @Convert(converter = StringListJsonConverter.class)
    private List<String> filePaths;

    @Column(name = "create_at", insertable = false, updatable = false)
    private LocalDateTime createAt;
}
