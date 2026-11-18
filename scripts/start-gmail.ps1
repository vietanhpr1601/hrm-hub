$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath (Split-Path -Parent $PSScriptRoot)
Write-Host 'HRM: Gmail sends one notification on payroll approval and one on payment.'
Write-Host 'Pending messages will be sent after Gmail is enabled. No password is saved to source.'
$gmailAddress = (Read-Host 'Gmail sender address').Trim()
if ($gmailAddress -notmatch '^[^\s@<>;,]+@[^\s@<>;,]+\.[^\s@<>;,]+$') { throw 'Enter a valid Gmail sender address.' }
$gmailSecret = Read-Host 'Google App Password (16 characters; hidden input)' -AsSecureString
try {
    $gmailCredential = New-Object System.Net.NetworkCredential('', $gmailSecret)
    $gmailPassword = $gmailCredential.Password -replace '\s', ''
    if ($gmailPassword.Length -ne 16) { throw 'Use the Google App Password, not your regular account password.' }
    $env:PAYROLL_MAIL_ENABLED = 'true'
    $env:MAIL_HOST = 'smtp.gmail.com'
    $env:MAIL_PORT = '587'
    $env:MAIL_USERNAME = $gmailAddress
    $env:MAIL_FROM = $gmailAddress
    $env:MAIL_PASSWORD = $gmailPassword
    $env:MAIL_SMTP_AUTH = 'true'
    $env:MAIL_STARTTLS = 'true'
    $env:MAIL_SSL = 'false'
    node scripts/local.mjs stop
    if ($LASTEXITCODE -ne 0) { throw 'Unable to stop the previous HRM processes.' }
    node scripts/local.mjs start
    if ($LASTEXITCODE -ne 0) { throw 'Unable to start HRM. Check .runtime logs.' }
} finally {
    Remove-Item Env:MAIL_PASSWORD -ErrorAction SilentlyContinue
    $gmailPassword = $null
    $gmailCredential = $null
    $gmailSecret.Dispose()
}
