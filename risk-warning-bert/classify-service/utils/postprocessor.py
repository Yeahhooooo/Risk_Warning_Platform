import torch
from typing import List, Dict, Any
from .constants import (
    IDX_TO_TAG, IDX_TO_INDUSTRY, IDX_TO_DIMENSION, IDX_TO_TYPE,
    PREDEFINED_TAGS, PREDEFINED_INDUSTRIES
)


def get_top_k_tags(tags_logits: torch.Tensor, k: int = 3, threshold: float = 0.5) -> List[str]:
    """
    从tags logits中获取Top-K标签
    
    Args:
        tags_logits: [num_tags] 或 [batch, num_tags] 的tensor
        k: 返回的标签数量
        threshold: 概率阈值（可选）
    
    Returns:
        标签列表
    """
    if tags_logits.dim() == 2:
        tags_logits = tags_logits[0]  # 取第一个batch
    
    # 应用sigmoid得到概率
    probs = torch.sigmoid(tags_logits)
    
    # 获取Top-K
    top_k_values, top_k_indices = torch.topk(probs, k=min(k, len(probs)))
    
    # 过滤低于阈值的（可选）
    valid_indices = top_k_indices[top_k_values >= threshold]
    
    # 转换为标签
    tags = [IDX_TO_TAG[idx.item()] for idx in valid_indices]
    
    # 如果不够k个，补充到k个
    if len(tags) < k:
        remaining = k - len(tags)
        remaining_indices = top_k_indices[len(valid_indices):len(valid_indices) + remaining]
        tags.extend([IDX_TO_TAG[idx.item()] for idx in remaining_indices])
    
    return tags[:k]


def get_top_k_industry(industry_logits: torch.Tensor, k: int = 3, threshold: float = 0.5) -> List[str]:
    """
    从industry logits中获取Top-K行业
    
    Args:
        industry_logits: [num_industries] 或 [batch, num_industries] 的tensor
        k: 返回的行业数量（1-3）
        threshold: 概率阈值（可选）
    
    Returns:
        行业列表
    """
    if industry_logits.dim() == 2:
        industry_logits = industry_logits[0]
    
    # 应用sigmoid得到概率
    probs = torch.sigmoid(industry_logits)
    
    # 获取Top-K
    top_k_values, top_k_indices = torch.topk(probs, k=min(k, len(probs)))
    
    # 过滤低于阈值的
    valid_indices = top_k_indices[top_k_values >= threshold]
    
    # 转换为行业
    industries = [IDX_TO_INDUSTRY[idx.item()] for idx in valid_indices]
    
    # 如果不够k个，补充到k个
    if len(industries) < k:
        remaining = k - len(industries)
        remaining_indices = top_k_indices[len(valid_indices):len(valid_indices) + remaining]
        industries.extend([IDX_TO_INDUSTRY[idx.item()] for idx in remaining_indices])
    
    return industries[:k]


def get_top_type(type_logits: torch.Tensor) -> str:
    """
    从type logits中获取类型
    
    Args:
        type_logits: [2] 或 [batch, 2] 的tensor
    
    Returns:
        类型字符串
    """
    if type_logits.dim() == 2:
        type_logits = type_logits[0]
    
    # 应用softmax得到概率
    probs = torch.softmax(type_logits, dim=0)
    
    # 获取最大值的索引
    pred_idx = torch.argmax(probs).item()
    
    return IDX_TO_TYPE[pred_idx]


def get_top_dimension(dimension_logits: torch.Tensor) -> str:
    """
    从dimension logits中获取维度
    
    Args:
        dimension_logits: [num_dimensions] 或 [batch, num_dimensions] 的tensor
    
    Returns:
        维度字符串
    """
    if dimension_logits.dim() == 2:
        dimension_logits = dimension_logits[0]
    
    # 应用softmax得到概率
    probs = torch.softmax(dimension_logits, dim=0)
    
    # 获取最大值的索引
    pred_idx = torch.argmax(probs).item()
    
    return IDX_TO_DIMENSION[pred_idx]


def postprocess_output(
    tags_logits: torch.Tensor,
    type_logits: torch.Tensor,
    dimension_logits: torch.Tensor,
    industry_logits: torch.Tensor = None,
    input_type: str = "behavior"
) -> Dict[str, Any]:
    """
    后处理模型输出，转换为最终结果
    
    Args:
        tags_logits: tags的logits
        type_logits: type的logits
        dimension_logits: dimension的logits
        industry_logits: industry的logits（可选）
        input_type: 输入类型
    
    Returns:
        分类结果字典
    """
    result = {
        "tags": get_top_k_tags(tags_logits, k=3),
        "type": get_top_type(type_logits),
        "dimension": get_top_dimension(dimension_logits)
    }
    
    # 只有indicator和regulation需要industry
    if input_type in ["indicator", "regulation"] and industry_logits is not None:
        result["industry"] = get_top_k_industry(industry_logits, k=3)
    
    return result

