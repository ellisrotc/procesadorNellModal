# Configuracion del endpoint
$uri = "http://localhost:8080/api/raynen/push"

# Datos de prueba (puedes cambiarlos aqui)
$body = @{
    "1" = "110"
    "2" = "300"
    "4" = "140-0"
    "9" = "300"
    "16" = "260"
    "21" = "300"
} | ConvertTo-Json

Write-Host "--- TEST DE AUTOMATIZACION RAYNEN ---" -ForegroundColor Cyan
Write-Host "Preparate: Tienes 5 segundos para poner la aplicacion en pantalla..." -ForegroundColor Yellow

# Cuenta regresiva
for ($i = 5; $i -gt 0; $i--) {
    Write-Host "$i..." -NoNewline
    Start-Sleep -Seconds 1
}
Write-Host "LANZANDO!" -ForegroundColor Green

# Enviar la peticion
try {
    $response = Invoke-RestMethod -Uri $uri -Method Post -Body $body -ContentType "application/json"
    Write-Host "Respuesta del servidor: " -NoNewline
    Write-Host ($response | ConvertTo-Json -Compress) -ForegroundColor Gray
    Write-Host "`nAutomatizacion en curso... NO muevas el mouse." -ForegroundColor Cyan
}
catch {
    Write-Host "ERROR: No se pudo conectar con el servidor. ¿Esta ejecutandose el proyecto?" -ForegroundColor Red
}
