param([string[]]$Keys = @(), [string]$Name = 'step', [double]$Wait = 1.2, [double]$Before = 0)
# Press pad buttons (pad.ps1 names: A B X Y START SELECT UP DOWN LEFT RIGHT), then save a 960x540
# screenshot to runs\steps\<Name>.png for a quick look.
$adb = $(if ($env:ADB) { $env:ADB } else { 'adb' })
$dir = 'C:\rb2-work\android\runs\steps'
New-Item -ItemType Directory -Force $dir | Out-Null
if ($Before -gt 0) { Start-Sleep -Milliseconds ([int]($Before * 1000)) }
if ($Keys.Count) { & (Join-Path $PSScriptRoot 'pad.ps1') -Keys $Keys -Wait $Wait | Out-Null }
$full = Join-Path $env:TEMP 'step_full.png'
cmd /c "`"$adb`" exec-out screencap -p > `"$full`""
Add-Type -AssemblyName System.Drawing
$b = [Drawing.Bitmap]::FromFile($full); $t = New-Object Drawing.Bitmap 960, 540
$g = [Drawing.Graphics]::FromImage($t); $g.DrawImage($b, 0, 0, 960, 540)
$t.Save((Join-Path $dir "$Name.png")); $g.Dispose(); $b.Dispose(); $t.Dispose()
Join-Path $dir "$Name.png"
