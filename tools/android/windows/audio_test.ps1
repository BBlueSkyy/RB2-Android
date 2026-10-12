param([string]$Apk = '', [string[]]$Extra = @(), [string]$Tag = 'a', [int]$TitleWait = 60, [switch]$Menus)
# Cold-start with the guest audio dump on (one file per render client), optionally walk into the
# menus, then pull the dumps + log to runs\audio_<Tag>\ and print per-5 s channel levels
# (tools: audio_levels.py next to this script). Removes args.txt afterwards.
$adb = $(if ($env:ADB) { $env:ADB } else { 'adb' }); $pkg = 'org.rb2recomp.android'
$f = "/sdcard/Android/data/$pkg/files"; $pad = (Join-Path $PSScriptRoot 'pad.ps1')
$out = "C:\rb2-work\android\runs\audio_$Tag"; New-Item -ItemType Directory -Force $out | Out-Null
if ($Apk) { & $adb install -r $Apk | Select-Object -Last 1 }
$lines = @('--show_debug_overlay=true', '--rb2_trace_cri=true', "--audio_dump_path=$f/logs/audio.raw") + $Extra
$tmp = Join-Path $env:TEMP 'args.txt'; [IO.File]::WriteAllText($tmp, (($lines -join "`n") + "`n"))
cmd /c "`"$adb`" push `"$tmp`" $f/args.txt > nul 2>&1"
& $adb shell "rm -f $f/logs/rb2.log $f/logs/audio.raw*"
& $adb shell input tap 1700 700; & $adb shell am force-stop $pkg
& $adb shell am start -n "$pkg/.LauncherActivity" --ez play true | Out-Null
Start-Sleep $TitleWait
if ($Menus) {
  & $pad -Keys START -Wait 8 | Out-Null
  & $pad -Keys A -Wait 8 | Out-Null
  & $pad -Keys A -Wait 15 | Out-Null
}
& $adb shell am force-stop $pkg
& $adb shell rm -f "$f/args.txt"
foreach ($i in 0..3) { cmd /c "`"$adb`" pull $f/logs/audio.raw.$i `"$out\client$i.raw`" > nul 2>&1" }
cmd /c "`"$adb`" pull $f/logs/rb2.log `"$out\rb2.log`" > nul 2>&1"
Select-String -Path "$out\rb2.log" -Pattern 'SpeakerConfig|RegisterRenderDriver|bgm_' | Select-Object -First 8 | ForEach-Object { $_.Line.Substring([Math]::Min(25, $_.Line.Length)) }
py -3 -I (Join-Path $PSScriptRoot 'audio_levels.py') (Get-ChildItem "$out\client*.raw" | ForEach-Object FullName)
