# Script copy files từ project cũ sang project mới
# Chạy script này trong PowerShell: .\COPY_FILES.ps1

$oldProject = "C:\Users\ADMIN\Downloads\PenaltyShootoutClient - Sao chép (2)"
$newProject = "C:\Users\ADMIN\Downloads\PenaltyShootout"

Write-Host "Copying files from old project to new project..." -ForegroundColor Green

# Tạo thư mục nếu chưa có
$javaDir = Join-Path $newProject "src\main\java"
$resourcesDir = Join-Path $newProject "src\main\resources"

New-Item -ItemType Directory -Path $javaDir -Force | Out-Null
New-Item -ItemType Directory -Path $resourcesDir -Force | Out-Null

# Copy Java source files
Write-Host "Copying Java source files..." -ForegroundColor Yellow
$srcClient = Join-Path $oldProject "src\client"
$srcServer = Join-Path $oldProject "src\server"
$srcCommon = Join-Path $oldProject "src\common"

if (Test-Path $srcClient) {
    Copy-Item -Path "$srcClient\*" -Destination "$javaDir\client" -Recurse -Force
    Write-Host "  ✓ Copied client/" -ForegroundColor Green
}

if (Test-Path $srcServer) {
    Copy-Item -Path "$srcServer\*" -Destination "$javaDir\server" -Recurse -Force
    Write-Host "  ✓ Copied server/" -ForegroundColor Green
}

if (Test-Path $srcCommon) {
    Copy-Item -Path "$srcCommon\*" -Destination "$javaDir\common" -Recurse -Force
    Write-Host "  ✓ Copied common/" -ForegroundColor Green
}

# Copy Resources
Write-Host "Copying resources..." -ForegroundColor Yellow
$srcResources = Join-Path $oldProject "src\resources"
if (Test-Path $srcResources) {
    Copy-Item -Path "$srcResources\*" -Destination $resourcesDir -Recurse -Force
    Write-Host "  ✓ Copied resources/" -ForegroundColor Green
}

# Copy Assets
Write-Host "Copying assets..." -ForegroundColor Yellow
$srcAssets = Join-Path $oldProject "src\assets"
$dstAssets = Join-Path $resourcesDir "assets"
if (Test-Path $srcAssets) {
    New-Item -ItemType Directory -Path $dstAssets -Force | Out-Null
    Copy-Item -Path "$srcAssets\*" -Destination $dstAssets -Recurse -Force
    Write-Host "  ✓ Copied assets/" -ForegroundColor Green
}

# Copy Sounds
Write-Host "Copying sounds..." -ForegroundColor Yellow
$srcSound = Join-Path $oldProject "src\sound"
$dstSound = Join-Path $resourcesDir "sound"
if (Test-Path $srcSound) {
    New-Item -ItemType Directory -Path $dstSound -Force | Out-Null
    Copy-Item -Path "$srcSound\*" -Destination $dstSound -Recurse -Force
    Write-Host "  ✓ Copied sound/" -ForegroundColor Green
}

Write-Host "`nDone! All files copied successfully." -ForegroundColor Green
Write-Host "You can now open the project in IntelliJ IDEA." -ForegroundColor Cyan


