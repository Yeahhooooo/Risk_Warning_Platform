package com.riskwarning.bert.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;


@Slf4j
@Service
public class PythonServiceManager {

    @Value("${bert.python.path:python}")
    private String pythonPath;

    @Value("${bert.python.script:bert-service/bert_service.py}")
    private String pythonScript;

    @Value("${bert.service.port:8000}")
    private int servicePort;

    @Value("${bert.service.host:0.0.0.0}")
    private String serviceHost;

    @Value("${bert.model.name:bert-base-chinese}")
    private String modelName;

    @Value("${bert.service.enabled:true}")
    private boolean serviceEnabled;

    @Value("${bert.service.healthcheck.enabled:true}")
    private boolean healthcheckEnabled;

    @Value("${bert.service.healthcheck.interval:5000}")
    private long healthcheckInterval;

    @Value("${bert.service.healthcheck.timeout:30000}")
    private long healthcheckTimeout;

    private Process pythonProcess;
    private Thread healthcheckThread;
    private volatile boolean running = false;

    
    public void startService() {
        if (!serviceEnabled) {
            log.info("Python BERT服务已禁用，跳过启动");
            return;
        }

        try {
            // 获取Python脚本的绝对路径
            File scriptFile = getScriptFile();
            if (!scriptFile.exists()) {
                throw new RuntimeException("Python脚本不存在: " + scriptFile.getAbsolutePath());
            }

            // 构建命令
            List<String> command = buildCommand(scriptFile);

            // 启动进程
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.directory(scriptFile.getParentFile());
            processBuilder.redirectErrorStream(true);

            // 设置环境变量
            processBuilder.environment().put("PORT", String.valueOf(servicePort));
            processBuilder.environment().put("HOST", serviceHost);
            processBuilder.environment().put("BERT_MODEL_NAME", modelName);
            processBuilder.environment().put("PYTHONUNBUFFERED", "1");

            pythonProcess = processBuilder.start();
            running = true;

            // 启动日志输出线程
            startLogReader();

            // 等待服务启动
            waitForServiceReady();

            // 启动健康检查
            if (healthcheckEnabled) {
                startHealthcheck();
            }

            log.info("Python BERT服务启动成功，监听端口: {}", servicePort);

        } catch (Exception e) {
            log.error("启动Python BERT服务失败", e);
            throw new RuntimeException("启动Python BERT服务失败", e);
        }
    }

    @PreDestroy
    public void stopService() {
        if (healthcheckThread != null && healthcheckThread.isAlive()) {
            healthcheckThread.interrupt();
        }

        if (pythonProcess != null && pythonProcess.isAlive()) {
            running = false;

            try {
                // 优雅关闭
                pythonProcess.destroy();
                boolean terminated = pythonProcess.waitFor(10, TimeUnit.SECONDS);

                if (!terminated) {
                    pythonProcess.destroyForcibly();
                    pythonProcess.waitFor(5, TimeUnit.SECONDS);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                pythonProcess.destroyForcibly();
            }
        }
    }

  
    public boolean isServiceRunning() {
        return running && pythonProcess != null && pythonProcess.isAlive();
    }

 
    private File getScriptFile() {
        // 尝试多种路径
        String userDir = System.getProperty("user.dir");
        String scriptName = new File(pythonScript).getName();
        
        // 尝试通过类路径定位模块目录
        String moduleDir = getModuleDirectory();
        
        List<String> possiblePaths = new ArrayList<>();
        
        // 1. 直接使用配置的路径（相对路径）
        possiblePaths.add(pythonScript);
        
        // 2. 相对于当前工作目录
        possiblePaths.add(userDir + "/" + pythonScript);
        
        // 3. 如果找到了模块目录，使用模块目录
        if (moduleDir != null) {
            possiblePaths.add(moduleDir + "/bert-service/" + scriptName);
            possiblePaths.add(moduleDir + "/" + pythonScript);
        }
        
        // 4. 在 risk-warning-bert 模块下的 bert-service 目录（从项目根目录）
        possiblePaths.add(userDir + "/risk-warning-bert/bert-service/" + scriptName);
        
        // 5. 如果当前目录就是 risk-warning-bert，直接查找 bert-service
        possiblePaths.add(userDir + "/bert-service/" + scriptName);
        
        // 6. 如果从项目根目录运行，查找 risk-warning-bert 子目录
        possiblePaths.add(userDir + "/../risk-warning-bert/bert-service/" + scriptName);
        
        // 7. 尝试通过类路径定位（编译后的路径）
        possiblePaths.add(userDir + "/target/classes/" + pythonScript);
        
        // 8. Maven 资源路径（不太可能，但保留）
        possiblePaths.add("src/main/resources/" + pythonScript);

       
        if (moduleDir != null) {
            log.info("模块目录: {}", moduleDir);
        }

        for (String path : possiblePaths) {
            File file = new File(path);
            if (file.exists() && file.isFile()) {
                log.info("找到Python脚本: {}", file.getAbsolutePath());
                return file.getAbsoluteFile();
            }
        }

        // 如果都找不到，抛出异常
        throw new RuntimeException("找不到Python脚本: " + pythonScript + 
            "。已尝试路径: " + String.join(", ", possiblePaths) +
            "。当前工作目录: " + userDir);
    }
  
    private String getModuleDirectory() {
        try {
            // 获取当前类的路径
            String classPath = this.getClass().getProtectionDomain()
                .getCodeSource().getLocation().getPath();
            
            // 解码 URL 编码
            classPath = java.net.URLDecoder.decode(classPath, "UTF-8");
            
            // 如果是 Windows，去掉开头的 /
            if (classPath.startsWith("/") && classPath.length() > 1 && classPath.charAt(2) == ':') {
                classPath = classPath.substring(1);
            }
            
            File classFile = new File(classPath);
            
            // 如果是 target/classes，向上找到模块目录
            if (classFile.getAbsolutePath().contains("target/classes")) {
                File targetDir = classFile.getParentFile().getParentFile(); // target
                File moduleDir = targetDir.getParentFile(); // risk-warning-bert
                if (moduleDir.exists() && moduleDir.isDirectory()) {
                    return moduleDir.getAbsolutePath();
                }
            }
            
            // 如果是 jar 文件，尝试从 jar 路径推断
            if (classPath.endsWith(".jar")) {
                File jarFile = new File(classPath);
                File libDir = jarFile.getParentFile();
                if (libDir != null && libDir.getName().equals("lib")) {
                    File moduleDir = libDir.getParentFile();
                    if (moduleDir != null && moduleDir.exists()) {
                        return moduleDir.getAbsolutePath();
                    }
                }
            }
        } catch (Exception e) {
            log.debug("无法通过类路径定位模块目录: {}", e.getMessage());
        }
        
        return null;
    }

   
    private List<String> buildCommand(File scriptFile) {
        List<String> command = new ArrayList<>();
        command.add(pythonPath);
        command.add(scriptFile.getAbsolutePath());
        return command;
    }

   
    private void startLogReader() {
        Thread logThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(pythonProcess.getInputStream()))) {
                String line;
                while (running && (line = reader.readLine()) != null) {
                    log.info("[BERT-Service] {}", line);
                }
            } catch (Exception e) {
                if (running) {
                    log.error("读取Python服务日志失败", e);
                }
            }
        });
        logThread.setDaemon(true);
        logThread.setName("bert-service-log-reader");
        logThread.start();
    }

  
    private void waitForServiceReady() {
        int maxAttempts = 60; // 最多等待60秒
        int attempt = 0;

        while (attempt < maxAttempts && running) {
            try {
                if (checkHealth()) {
                    log.info("Python BERT服务已就绪");
                    return;
                }
                Thread.sleep(1000); // 等待1秒
                attempt++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("等待服务就绪时被中断", e);
            }
        }

        throw new RuntimeException("Python BERT服务启动超时，未能在60秒内就绪");
    }

  
    private boolean checkHealth() {
        try {
            java.net.URL url = new java.net.URL("http://localhost:" + servicePort + "/health");
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            int responseCode = conn.getResponseCode();
            return responseCode == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private void startHealthcheck() {
        healthcheckThread = new Thread(() -> {
            while (running && !Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(healthcheckInterval);
                    if (!checkHealth()) {
                        log.warn("Python BERT服务健康检查失败，但进程仍在运行");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("健康检查异常", e);
                }
            }
        });
        healthcheckThread.setDaemon(true);
        healthcheckThread.setName("bert-service-healthcheck");
        healthcheckThread.start();
    }
}

