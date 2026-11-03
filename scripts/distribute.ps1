<#
Compila la release firmada y la sube a Firebase App Distribution.
Uso:  .\scripts\distribute.ps1 [-Notes "texto"] [-Group hehe] [-Yes] [-NoBump]

  -Yes     no pregunta antes de subir (para sesiones remotas: la aprobación la das tú en el chat).
  -NoBump  no sube el versionCode (úsalo en la primera subida o al reintentar la misma versión).

Por defecto sube el versionCode de app/build.gradle.kts en 1 antes de compilar; si la subida se cancela o falla,
lo deja como estaba. Requisitos: `firebase login` hecho y las propiedades MYFINANCE_* en ~/.gradle/gradle.properties.
El APK sale de esta máquina hacia los servidores de Firebase.
#>
param(
    [string]$Notes = "",
    [string]$Group = "hehe",
    [switch]$Yes,
    [switch]$NoBump
)

$ErrorActionPreference = "Stop"
$project = "my-finance-ce12a"
$appId = "1:1009303505667:android:c8127115775bbdad6d3736"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$gradleFile = Join-Path $root "app\build.gradle.kts"
$utf8 = New-Object System.Text.UTF8Encoding($false)
$original = [System.IO.File]::ReadAllText($gradleFile, $utf8)
$pattern = 'versionCode\s*=\s*(\d+)'
$current = [int]([regex]::Match($original, $pattern).Groups[1].Value)
$next = if ($NoBump) { $current } else { $current + 1 }
$bumped = $false
$uploaded = $false

try {
    if (-not $NoBump) {
        $updated = [regex]::Replace($original, $pattern, "versionCode = $next", 1)
        [System.IO.File]::WriteAllText($gradleFile, $updated, $utf8)
        $bumped = $true
    }
    Write-Host "versionCode: $next"

    & .\gradlew.bat :app:assembleRelease --console=plain -q
    if ($LASTEXITCODE -ne 0) { throw "assembleRelease falló" }

    $apk = Get-ChildItem "app\build\outputs\apk\release\*.apk" | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if (-not $apk) { throw "No hay APK de release" }
    if ($apk.Name -like "*unsigned*") { throw "El APK no está firmado: faltan las propiedades MYFINANCE_* en ~/.gradle/gradle.properties" }

    Write-Host "APK: $($apk.FullName) ($([math]::Round($apk.Length / 1MB, 1)) MB)"
    Write-Host "Se subirá a Firebase App Distribution (proyecto $project, grupo '$Group')."
    if (-not $Yes -and (Read-Host "¿Subir? (s/N)") -ne "s") { Write-Host "Cancelado."; return }

    firebase appdistribution:distribute $apk.FullName --app $appId --project $project --groups $Group --release-notes $Notes
    if ($LASTEXITCODE -ne 0) { throw "La subida falló" }
    $uploaded = $true
    Write-Host "Subida la versión $next."
}
finally {
    if ($bumped -and -not $uploaded) {
        [System.IO.File]::WriteAllText($gradleFile, $original, $utf8)
        Write-Host "versionCode restaurado a $current."
    }
}
