import torch
import torch.nn as nn
from transformers import AutoModel
from utils.constants import MODEL_CONFIG


class MultiTaskClassifier(nn.Module):
    
    def __init__(self, model_name: str = "hfl/chinese-roberta-wwm-ext", dropout: float = 0.1):
        super(MultiTaskClassifier, self).__init__()
        
        # 加载预训练BERT模型
        try:
            self.bert = AutoModel.from_pretrained(model_name, local_files_only=True)
        except Exception:
            try:
                self.bert = AutoModel.from_pretrained(model_name)
            except Exception as e:
                raise RuntimeError(f"无法加载模型: {e}")
        
        hidden_size = MODEL_CONFIG["hidden_size"]
        
        # Dropout层
        self.dropout = nn.Dropout(dropout)
        
        # 分类头
        self.tags_head = nn.Linear(hidden_size, MODEL_CONFIG["num_tags"])
        self.type_head = nn.Linear(hidden_size, MODEL_CONFIG["num_types"])
        self.dimension_head = nn.Linear(hidden_size, MODEL_CONFIG["num_dimensions"])
        self.industry_head = nn.Linear(hidden_size, MODEL_CONFIG["num_industries"])
    
    def forward(
        self,
        input_ids: torch.Tensor,
        attention_mask: torch.Tensor,
        input_type: str = None
    ):
        # BERT编码
        outputs = self.bert(input_ids=input_ids, attention_mask=attention_mask)
        
        # 获取[CLS] token的嵌入
        cls_embedding = outputs.last_hidden_state[:, 0, :]
        
        # Dropout
        cls_embedding = self.dropout(cls_embedding)
        
        # 通过各个分类头
        tags_logits = self.tags_head(cls_embedding)
        type_logits = self.type_head(cls_embedding)
        dimension_logits = self.dimension_head(cls_embedding)
        industry_logits = self.industry_head(cls_embedding)
        
        return tags_logits, type_logits, dimension_logits, industry_logits

