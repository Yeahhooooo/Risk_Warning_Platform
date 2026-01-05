"""
BERT向量化服务
提供HTTP接口，将文本转换为768维向量
"""
from flask import Flask, request, jsonify
from flask_cors import CORS
from transformers import AutoTokenizer, AutoModel
import torch
import logging
import os

# 配置日志
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = Flask(__name__)
CORS(app)  # 允许跨域

# 全局变量存储模型
tokenizer = None
model = None

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

@app.route('/health', methods=['GET'])
def health():
    """健康检查"""
    return jsonify({"status": "ok", "model_loaded": model is not None})

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

if __name__ == '__main__':
    # 加载模型
    load_model()
    
    # 启动服务
    port = int(os.getenv("PORT", 8000))
    host = os.getenv("HOST", "0.0.0.0")
    app.run(host=host, port=port, debug=False)

