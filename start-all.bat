@echo off
echo ========================================================
echo            MuFeng 微服务一键启动 (免编译版)
echo ========================================================
echo.

echo 正在启动各微服务，请确保刚刚在 IDEA 或 Maven 中已经编译过...
echo.

echo 启动 MuFeng-Marketing (8081)...
start "Marketing-8081" cmd /c "cd MuFeng-Marketing && mvn spring-boot:run"
echo 等待 5 秒...
timeout /t 5 /nobreak > nul

echo 启动 MuFeng-Inventory (8082)...
start "Inventory-8082" cmd /c "cd MuFeng-Inventory && mvn spring-boot:run"
echo 等待 5 秒...
timeout /t 5 /nobreak > nul

echo 启动 MuFeng-Trade (8083)...
start "Trade-8083" cmd /c "cd MuFeng-Trade && mvn spring-boot:run"
echo 等待 5 秒...
timeout /t 5 /nobreak > nul

echo 启动 MuFeng-Settlement (8084)...
start "Settlement-8084" cmd /c "cd MuFeng-Settlement && mvn spring-boot:run"
echo 等待 5 秒...
timeout /t 5 /nobreak > nul

echo 启动 MuFeng-Gateway (8080)...spring-boot:run"

start "Gateway-8080" cmd /c "cd MuFeng-Gateway && mvn 
echo.
echo 所有服务启动命令已下发！
pause