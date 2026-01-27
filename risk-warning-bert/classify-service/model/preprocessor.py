from transformers import AutoTokenizer
from typing import Dict, List
import torch
import os


class TextPreprocessor:
    
    
    def __init__(self, model_name: str = "hfl/chinese-roberta-wwm-ext"):
        try:
            # 先尝试从本地缓存加载
            self.tokenizer = AutoTokenizer.from_pretrained(
                model_name,
                local_files_only=True
            )
        except Exception:
            try:
                self.tokenizer = AutoTokenizer.from_pretrained(model_name)
            except Exception as e:
                raise RuntimeError(f"无法加载tokenizer: {e}")
    
    def format_text_with_type(self, text: str, input_type: str) -> str:
        type_markers = {
            "behavior": "[TYPE]行为[/TYPE]",
            "indicator": "[TYPE]指标[/TYPE]",
            "regulation": "[TYPE]法规[/TYPE]"
        }
        marker = type_markers.get(input_type, "")
        return f"{marker} {text}" if marker else text
    
    def preprocess(self, text: str, input_type: str = "behavior") -> Dict[str, torch.Tensor]:
        formatted_text = self.format_text_with_type(text, input_type)
        
        encoded = self.tokenizer(
            formatted_text,
            return_tensors="pt",
            padding=True,
            truncation=True,
            max_length=512
        )
        
        return {
            "input_ids": encoded["input_ids"],
            "attention_mask": encoded["attention_mask"]
        }
    
    def preprocess_batch(self, texts: List[str], input_types: List[str]) -> Dict[str, torch.Tensor]:
        formatted_texts = [
            self.format_text_with_type(text, input_type)
            for text, input_type in zip(texts, input_types)
        ]
        
        encoded = self.tokenizer(
            formatted_texts,
            return_tensors="pt",
            padding=True,
            truncation=True,
            max_length=512
        )
        
        return {
            "input_ids": encoded["input_ids"],
            "attention_mask": encoded["attention_mask"]
        }

