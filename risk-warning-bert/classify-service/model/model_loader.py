import torch
import os
import sys
from pathlib import Path
from typing import Optional

# 添加父目录到路径
sys.path.insert(0, str(Path(__file__).parent.parent))

from model.multi_task_classifier import MultiTaskClassifier
from model.preprocessor import TextPreprocessor
from utils.postprocessor import postprocess_output


class ModelLoader:
    
    def __init__(
        self,
        model_path: Optional[str] = None,
        model_name: str = "hfl/chinese-roberta-wwm-ext",
        device: str = "cpu"
    ):
        self.model_path = model_path
        self.model_name = model_name
        self.device = torch.device(device)
        self.model = None
        self.preprocessor = None
        self._load_model()
        self._load_preprocessor()
    
    def _load_model(self):
        """加载模型"""
        # 初始化模型
        self.model = MultiTaskClassifier(model_name=self.model_name)
        
        # 如果提供了训练好的模型路径，加载权重
        if self.model_path and os.path.exists(self.model_path):
            print(f"Loading trained model from {self.model_path}")
            state_dict = torch.load(self.model_path, map_location=self.device)
            self.model.load_state_dict(state_dict)
        else:
            print(f"Using pretrained model: {self.model_name}")
            print("Note: Model weights are randomly initialized. Train the model first.")
        
        # 移动到设备
        self.model.to(self.device)
        self.model.eval()
    
    def _load_preprocessor(self):
        """加载预处理器"""
        self.preprocessor = TextPreprocessor(model_name=self.model_name)
    
    def classify(
        self,
        text: str,
        input_type: str = "behavior"
    ) -> dict:
        # 预处理
        inputs = self.preprocessor.preprocess(text, input_type)
        input_ids = inputs["input_ids"].to(self.device)
        attention_mask = inputs["attention_mask"].to(self.device)
        
        # 推理
        with torch.no_grad():
            tags_logits, type_logits, dimension_logits, industry_logits = self.model(
                input_ids=input_ids,
                attention_mask=attention_mask,
                input_type=input_type
            )
        
        # 后处理
        result = postprocess_output(
            tags_logits=tags_logits[0],  # 取第一个batch
            type_logits=type_logits[0],
            dimension_logits=dimension_logits[0],
            industry_logits=industry_logits[0] if input_type in ["indicator", "regulation"] else None,
            input_type=input_type
        )
        
        return result
    
    def classify_batch(
        self,
        texts: list,
        input_types: list
    ) -> list:
        results = []
        for text, input_type in zip(texts, input_types):
            result = self.classify(text, input_type)
            results.append(result)
        return results

