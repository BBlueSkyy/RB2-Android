param([string]$Apk, [string[]]$Args2 = @('--show_debug_overlay=true'), [int]$Seconds = 40, [string]$Tag = 'try')
# Install (optional), launch with args.txt = $Args2, wait, then report: did the game exit, crash or
# keep running; last screenshot; slow-frame stats. Removes args.txt afterwards.
$adb = $(if ($env:ADB) { $env:ADB } else { 'adb' })
$pkg = 'org.rb2recomp.android'
$f = "/sdcard/Android/data/$pkg/files"
$runs = 'C:\rb2-work\android\runs'
if ($Apk) { cmd /c "`"$adb`" install -r `"$Apk`"" | Out-Null }
$tmp = Join-Path $env:TEMP 'args.txt'
[IO.File]::WriteAllText($tmp, (($Args2 -join "`n") + "`n"))
cmd /c "`"$adb`" push `"$tmp`" $f/args.txt > nul 2>&1"
# Clear the Thor's pixel-refresh screensaver (it covers the game and appears after idle time).
cmd /c "`"$adb`" shell input tap 1700 700" | Out-Null
Start-Sleep 1
& "$PSScriptRoot\run_game.ps1" -Seconds $Seconds -Tag $Tag | Out-Null
cmd /c "`"$adb`" shell rm -f $f/args.txt"
$base = (Get-ChildItem $runs -Filter "$Tag-*.logcat.txt" | Sort-Object LastWriteTime | Select-Object -Last 1).FullName -replace '\.logcat\.txt$', ''
$lc = Get-Content "$base.logcat.txt"
$alive = ((cmd /c "`"$adb`" shell pidof $pkg`:game") -join '').Trim()
"game process: " + ($(if ($alive) { "running ($alive)" } else { 'gone' }))
$lc | Where-Object { $_ -match 'XAPI RETURN|Execution complete|Fatal signal|Abort message|REX_FATAL|Unresolved' } | ForEach-Object { $_.Substring(6, [Math]::Min(200, $_.Length - 6)) }
if (Test-Path "$base.frames.csv") { & "$PSScriptRoot\stats_android.ps1" -Csv "$base.frames.csv" | Select-Object -First 3 }
"screenshot: $base.png"

