import json

input_file = "/Users/huayecai/Desktop/bach_01/Risk_Warning_Platform/documents/data/regulation.json"      # 你的原始 JSON
output_file = "bulk.json"     # 生成的 ES Bulk 文件
index_name = "t_regulation"     # 替换为你的索引名

with open(input_file, "r", encoding="utf-8") as f:
    items = json.load(f)

with open(output_file, "w", encoding="utf-8") as out:
    for item in items:
        out.write(json.dumps({"index": {"_index": index_name}}, ensure_ascii=False) + "\n")
        out.write(json.dumps(item, ensure_ascii=False) + "\n")

print("bulk.json 创建成功！")
