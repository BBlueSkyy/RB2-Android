param([string[]]$Args2 = @('--show_debug_overlay=true'), [int]$MaxSeconds = 200, [switch]$NoLaunch)
# Cold-start Raging Blast 2 with args.txt = $Args2 and drive the menus to a CPU vs CPU battle with no
# time limit (Main Menu > Battle > Single Battle > CPU vs. CPU, infinite > Goku vs Vegeta > Wasteland).
# Screens are recognised by comparing a 32x18 thumbnail with references in runs\refs (taken from a
# manual walk-through). Leaves the battle running and args.txt in place (remove it when done).
$adb = $(if ($env:ADB) { $env:ADB } else { 'adb' })
$pkg = 'org.rb2recomp.android'
$refs = 'C:\rb2-work\android\runs\refs'
$padScript = (Join-Path $PSScriptRoot 'pad.ps1')
Add-Type -AssemblyName System.Drawing

function Thumb($path) {
  $b = [Drawing.Bitmap]::FromFile($path); $t = New-Object Drawing.Bitmap 32, 18
  $g = [Drawing.Graphics]::FromImage($t); $g.DrawImage($b, 0, 0, 32, 18); $g.Dispose(); $b.Dispose()
  $v = New-Object 'double[]' (32 * 18 * 3); $i = 0
  for ($y = 0; $y -lt 18; $y++) { for ($x = 0; $x -lt 32; $x++) { $c = $t.GetPixel($x, $y); $v[$i++] = $c.R; $v[$i++] = $c.G; $v[$i++] = $c.B } }
  $t.Dispose(); return ,$v
}
$ref = @{}
foreach ($f in Get-ChildItem $refs -Filter *.png) { $ref[$f.BaseName] = Thumb $f.FullName }
function Classify() {
  $shot = Join-Path $env:TEMP 'tb_shot.png'
  cmd /c "`"$adb`" exec-out screencap -p > `"$shot`""
  $v = Thumb $shot; $best = 'unknown'; $bd = 1e18
  foreach ($k in $ref.Keys) { $r = $ref[$k]; $d = 0.0; for ($i = 0; $i -lt $v.Length; $i++) { $d += [math]::Abs($v[$i] - $r[$i]) }; $d /= $v.Length; if ($d -lt $bd) { $bd = $d; $best = $k } }
  if ($bd -gt 40) { $best = 'unknown' }
  return @($best, [math]::Round($bd, 1))
}
function Pad($keys, $wait = 1.0) { & $padScript -Keys $keys -Wait $wait | Out-Null }

if (-not $NoLaunch) {
  $tmp = Join-Path $env:TEMP 'args.txt'
  [IO.File]::WriteAllText($tmp, (($Args2 -join "`n") + "`n"))
  cmd /c "`"$adb`" push `"$tmp`" /sdcard/Android/data/$pkg/files/args.txt > nul 2>&1"
  cmd /c "`"$adb`" shell input tap 1700 700" | Out-Null
  cmd /c "`"$adb`" shell am force-stop $pkg"
  cmd /c "`"$adb`" shell rm -f /sdcard/Android/data/$pkg/files/perf/frames.csv"
  cmd /c "`"$adb`" shell am start -n $pkg/.LauncherActivity --ez play true" | Out-Null
  Start-Sleep 20
}
$t0 = Get-Date
while (((Get-Date) - $t0).TotalSeconds -lt $MaxSeconds) {
  $s, $d = Classify
  "{0,5:N0}s {1} ({2})" -f ((Get-Date) - $t0).TotalSeconds, $s, $d
  if ($s -eq 'battle') { 'in battle'; return }
  elseif ($s -eq 'mainmenu') { Pad @('DOWN', 'A') 2.0 }
  elseif ($s -eq 'battlemenu') { Pad @('A') 2.5 }
  elseif ($s -eq 'settings') { Pad @('DOWN', 'RIGHT', 'RIGHT', 'DOWN', 'LEFT', 'UP', 'UP', 'A') 0.8; Start-Sleep 2 }
  elseif ($s -eq 'charselect') { Pad @('A', 'A') 1.5; Start-Sleep 1 }
  elseif ($s -eq 'stage') { Pad @('A') 2.0; Start-Sleep 8 }
  elseif ($s -eq 'loading') { Start-Sleep 3 }
  elseif ($s -in 'dialog', 'dialog2') { Pad @('A') 1.5 }
  else { Pad @('START') 1.0; Start-Sleep 2 }  # notices, logos, intro, title
}
'gave up'
