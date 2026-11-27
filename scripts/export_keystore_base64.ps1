# Xuất file enzii-release.jks sang chuỗi Base64 và copy vào Clipboard (không ghi ra file, không in lên terminal)
$ErrorActionPreference = "Stop"

$keystorePath = Join-Path $PSScriptRoot "..\enzii-release.jks"
if (-not (Test-Path $keystorePath)) {
    Write-Error "Không tìm thấy file enzii-release.jks tại $keystorePath"
    exit 1
}

$bytes = [System.IO.File]::ReadAllBytes($keystorePath)
$base64 = [Convert]::ToBase64String($bytes)
Set-Clipboard -Value $base64

Write-Host "Đã copy chuỗi Base64 của enzii-release.jks ($($base64.Length) ký tự) vào Clipboard!" -ForegroundColor Green
Write-Host ""
Write-Host "Các bước tiếp theo trên GitHub:"
Write-Host "1. Mở repo GitHub -> Settings -> Secrets and variables -> Actions -> New repository secret"
Write-Host "2. Thêm 4 secrets sau:"
Write-Host "   - ENZII_KEYSTORE_BASE64     : Dán (Ctrl+V) nội dung vừa copy từ Clipboard"
Write-Host "   - ENZII_KEYSTORE_PASSWORD   : Mật khẩu store của enzii-release.jks"
Write-Host "   - ENZII_KEY_ALIAS           : Tên alias (mặc định: enzii_key)"
Write-Host "   - ENZII_KEY_PASSWORD        : Mật khẩu key của alias"
