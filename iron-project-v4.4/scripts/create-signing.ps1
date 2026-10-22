param([string]$Destination = '.private')
$ErrorActionPreference='Stop'
if (!(Get-Command keytool -ErrorAction SilentlyContinue)) { throw 'Install a JDK 17 first; keytool must be on PATH.' }
$folder=[IO.Path]::GetFullPath($Destination)
New-Item -ItemType Directory -Path $folder -Force | Out-Null
$keystore=Join-Path $folder 'iron.jks'
if (Test-Path -LiteralPath $keystore) { throw 'Existing keystore must be reused; this script will not replace it.' }
$secret=Read-Host 'Signing password (at least 8 characters; save it securely)' -AsSecureString
$ptr=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($secret)
try {
    $env:IRON_NEW_KEY_PASSWORD=[Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
    if ($env:IRON_NEW_KEY_PASSWORD.Length -lt 8) { throw 'Password is too short.' }
    keytool -genkeypair -keystore $keystore -storetype JKS -alias iron -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=Iron Project Personal' -storepass:env IRON_NEW_KEY_PASSWORD -keypass:env IRON_NEW_KEY_PASSWORD
    if ($LASTEXITCODE -ne 0) { throw 'Key generation failed.' }
    [IO.File]::WriteAllText((Join-Path $folder 'IRON_KEYSTORE_BASE64.txt'),[Convert]::ToBase64String([IO.File]::ReadAllBytes($keystore)))
    Write-Host 'Created a private key and BASE64 file. Keep both outside Git. Key alias: iron. Store/key passwords are the password you entered.'
} finally {
    Remove-Item Env:\IRON_NEW_KEY_PASSWORD -ErrorAction SilentlyContinue
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)
}
