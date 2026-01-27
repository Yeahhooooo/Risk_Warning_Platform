"""
BERT向量化服务 + 分类服务
提供HTTP接口，将文本转换为768维向量，以及文本分类
"""
from flask import Flask, request, jsonify
from flask_cors import CORS
from transformers import AutoTokenizer, AutoModel
import torch
import logging
import os
import sys
from pathlib import Path

# 配置日志（先初始化，以便后续使用）
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# 添加classify-service目录到路径
classify_service_path = Path(__file__).parent.parent / "classify-service"
classify_service_path_str = str(classify_service_path)
if classify_service_path.exists():
    sys.path.insert(0, classify_service_path_str)
    logger.info(f"已添加分类服务路径: {classify_service_path_str}")
else:
    logger.warning(f"分类服务目录不存在: {classify_service_path_str}")

# 导入分类服务模块（如果导入失败，分类功能不可用，但不影响BERT服务）
ModelLoader = None
try:
    from model.model_loader import ModelLoader
    logger.info("分类服务模块导入成功")
except Exception as e:
    logger.warning(f"分类服务模块导入失败: {e}，分类功能将不可用")
    import traceback
    logger.debug(traceback.format_exc())

app = Flask(__name__)
CORS(app)  # 允许跨域

# 全局变量存储模型
tokenizer = None
model = None
classifier_loader = None

def load_model():
    """加载BERT模型"""
    global tokenizer, model
    try:
        model_name = os.getenv("BERT_MODEL_NAME", "bert-base-chinese")
        tokenizer = AutoTokenizer.from_pretrained(model_name)
        model = AutoModel.from_pretrained(model_name)
        model.eval()  # 设置为评估模式
    except Exception as e:
        raise

def load_classifier():
    """加载分类模型"""
    global classifier_loader
    if ModelLoader is None:
        logger.warning("分类服务模块未导入，分类功能不可用")
        classifier_loader = None
        return
    
    try:
        # 默认模型路径：classify-service/checkpoints/best_model.pt
        default_model_path = str(classify_service_path / "checkpoints" / "best_model.pt")
        model_path = os.getenv("CLASSIFIER_MODEL_PATH", default_model_path)
        model_name = os.getenv("CLASSIFIER_MODEL_NAME", "hfl/chinese-roberta-wwm-ext")
        device = os.getenv("DEVICE", "cpu")
        
        logger.info(f"Loading classifier model: {model_name}")
        logger.info(f"Classifier model path: {model_path}")
        
        classifier_loader = ModelLoader(
            model_path=model_path,
            model_name=model_name,
            device=device
        )
        
        logger.info("Classifier model loaded successfully")
    except Exception as e:
        logger.error(f"Failed to load classifier model: {e}", exc_info=True)
        # 分类模型加载失败不影响BERT服务运行
        classifier_loader = None

@app.route('/health', methods=['GET'])
def health():
    """健康检查"""
    return jsonify({
        "status": "ok",
        "bert_model_loaded": model is not None,
        "classifier_model_loaded": classifier_loader is not None
    })

@app.route('/encode', methods=['POST'])
def encode():
    try:
        if model is None or tokenizer is None:
            return jsonify({"error": "模型未加载"}), 500
        
        data = request.json
        if not data or 'texts' not in data:
            return jsonify({"error": "请求格式错误，需要 'texts' 字段"}), 400
        
        texts = data['texts']
        if not isinstance(texts, list) or len(texts) == 0:
            return jsonify({"error": "'texts' 必须是非空数组"}), 400
        
        vectors = []
        for i, text in enumerate(texts):
            if not isinstance(text, str):
                continue
            
            # Tokenize
            inputs = tokenizer(
                text,
                return_tensors="pt",
                padding=True,
                truncation=True,
                max_length=512
            )
            
            # 推理
            with torch.no_grad():
                outputs = model(**inputs)
            
            # 提取 [CLS] token 的向量（第一个token）
            cls_vector = outputs.last_hidden_state[0][0].cpu().numpy().tolist()
            vectors.append(cls_vector)

        return jsonify(vectors)
        
    except Exception as e:
        return jsonify({"error": str(e)}), 500

@app.route('/vectorize-single', methods=['POST'])
def vectorize_single():
    try:
        data = request.json
        if not data or 'text' not in data:
            return jsonify({"error": "请求格式错误，需要 'text' 字段"}), 400
        
        texts = [data['text']]
        result = encode()
        if isinstance(result, tuple):
            return result  # 返回错误
        
        vectors = result.get_json()
        if vectors and len(vectors) > 0:
            return jsonify({"vector": vectors[0], "dimension": len(vectors[0])})
        else:
            return jsonify({"error": "向量化失败"}), 500
            
    except Exception as e:
        return jsonify({"error": str(e)}), 500

# ==================== 分类服务路由 ====================
# 注意：更具体的路由（/classify/health）要放在更通用的路由（/classify）之前

@app.route('/classify/health', methods=['GET'], strict_slashes=False)
def classify_health():
    """分类服务健康检查"""
    return jsonify({
        "status": "ok",
        "model_loaded": classifier_loader is not None
    })

@app.route('/classify/batch', methods=['POST'], strict_slashes=False)
def classify_batch():
    """批量分类接口"""
    try:
        if classifier_loader is None:
            return jsonify({"error": "分类模型未加载"}), 500
        
        data = request.json
        if not data:
            return jsonify({"error": "请求体为空"}), 400
        
        items = data.get('items', [])
        if not isinstance(items, list) or len(items) == 0:
            return jsonify({"error": "items必须是非空数组"}), 400
        
        # 提取文本和类型
        texts = []
        input_types = []
        for item in items:
            text = item.get('text', '').strip()
            input_type = item.get('input_type', 'behavior').strip().lower()
            
            if not text:
                continue
            
            if input_type not in ['behavior', 'indicator', 'regulation']:
                continue
            
            texts.append(text)
            input_types.append(input_type)
        
        if not texts:
            return jsonify({"error": "没有有效的文本"}), 400
        
        # 批量分类
        results = classifier_loader.classify_batch(texts, input_types)
        
        return jsonify({"results": results})
        
    except Exception as e:
        logger.error(f"Batch classification error: {e}", exc_info=True)
        return jsonify({"error": str(e)}), 500

@app.route('/classify', methods=['POST'], strict_slashes=False)
def classify():
    """分类接口"""
    try:
        if classifier_loader is None:
            return jsonify({"error": "分类模型未加载"}), 500
        
        data = request.json
        if not data:
            return jsonify({"error": "请求体为空"}), 400
        
        text = data.get('text', '').strip()
        input_type = data.get('input_type', 'behavior').strip().lower()
        
        # 验证输入
        if not text:
            return jsonify({"error": "文本不能为空"}), 400
        
        if input_type not in ['behavior', 'indicator', 'regulation']:
            return jsonify({
                "error": f"input_type必须是 behavior、indicator 或 regulation，当前为: {input_type}"
            }), 400
        
        # 分类
        result = classifier_loader.classify(text, input_type)
        
        return jsonify(result)
        
    except Exception as e:
        logger.error(f"Classification error: {e}", exc_info=True)
        return jsonify({"error": str(e)}), 500

if __name__ == '__main__':
    # 加载BERT模型
    load_model()
    
    # 加载分类模型
    load_classifier()
    
    # 启动服务
    port = int(os.getenv("PORT", 8000))
    host = os.getenv("HOST", "0.0.0.0")
    logger.info(f"Starting service on {host}:{port}")
    app.run(host=host, port=port, debug=False)

