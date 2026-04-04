#!/bin/bash

# 启动Python BERT服务
cd bert-service
python3 bert_service.py &

# 回到根目录
cd ..

# 启动Java服务
java -jar app.jar