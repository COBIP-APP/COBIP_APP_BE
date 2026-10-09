$projectRoot = $PSScriptRoot
$envFile = Join-Path $projectRoot '.env'
$configFile = Join-Path $projectRoot 'config/application-local.yml'

if (-not (Test-Path -LiteralPath $envFile)) {
    Copy-Item -LiteralPath (Join-Path $projectRoot '.env.example') -Destination $envFile
}

if (-not (Test-Path -LiteralPath $configFile)) {
    $bytes = New-Object byte[] 32
    $generator = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $generator.GetBytes($bytes) } finally { $generator.Dispose() }
    $secret = [Convert]::ToBase64String($bytes)
    $template = [IO.File]::ReadAllText((Join-Path $projectRoot 'config/application-local.yml.example'))
    if (-not $template.Contains("secret: ''")) { throw 'JWT key placeholder is missing from the local config example.' }
    $config = $template.Replace("secret: ''", "secret: '$secret'")
    [IO.File]::WriteAllText($configFile, $config, [Text.UTF8Encoding]::new($false))
}

Write-Host 'Local files are ready. Set DB_PASSWORD in .env and SMTP settings in config/application-local.yml.'
