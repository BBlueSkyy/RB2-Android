param([int]$Seconds = 60, [string]$Tag = 'bench')
# Measure the RUNNING game for $Seconds (start it in a battle first, e.g. CPU vs CPU, infinite time):
# frame pacing from frames.csv rows written during the window, GPU busy / CP time, process CPU
# cores, and system power + hottest CPU/GPU zone via THP8's power.ps1.
$adb = $(if ($env:ADB) { $env:ADB } else { 'adb' })
$pkg = 'org.rb2recomp.android'
$f = "/sdcard/Android/data/$pkg/files"
$runs = 'C:\rb2-work\android\runs'
function ProcTicks {
  for ($try = 0; $try -lt 5; $try++) {
    $p = ((& $adb shell pidof "$pkg`:game") -join '').Trim()
    $s = ((& $adb shell "cat /proc/$p/stat") -join '') -replace '.*\) ', ''
    $q = $s -split ' '
    if ($p -and $q.Count -gt 12) { return [int64]$q[11] + [int64]$q[12] }
    Start-Sleep -Milliseconds 300
  }
  throw 'could not read the game process CPU time'
}
$pre = [int]((& $adb shell "wc -l < $f/perf/frames.csv") -join '').Trim()
$t0 = Get-Date; $k0 = ProcTicks
$pw = & 'C:\thp8-work\android\scripts\power.ps1' -Seconds $Seconds
# NOT "(ProcTicks - $k0)": PowerShell parses that as ProcTicks called with the arguments '-' and $k0,
# i.e. lifetime ticks instead of a difference (the same line in THP8's soak2.ps1 inflated its cores).
$k1 = ProcTicks
$cores = ($k1 - $k0) / 100.0 / ((Get-Date) - $t0).TotalSeconds
$csv = Join-Path $runs "$Tag.csv"
cmd /c "`"$adb`" pull $f/perf/frames.csv `"$csv`" > nul 2>&1"
$rows = Import-Csv $csv | Select-Object -Skip ($pre - 1)
$ft = $rows | ForEach-Object { [double]$_.frame_time_us / 1000 } | Where-Object { $_ -gt 0 }
$s = $ft | Sort-Object; $c = $s.Count; $tot = ($ft | Measure-Object -Sum).Sum
function P($q) { [math]::Round($s[[int][math]::Floor(($c - 1) * $q)], 2) }
function Avg($col) { $v = $rows | ForEach-Object { [double]$_.$col } | Where-Object { $_ -gt 0 }; if ($v) { [math]::Round(($v | Measure-Object -Average).Average / 1000, 2) } else { 'n/a' } }
"== $Tag ($Seconds s): {0:N1} fps, p50 {1} p99 {2} max {3} ms, >18 ms {4:P1}, >35 ms {5:P1}" -f ($c / ($tot / 1000)), (P 0.5), (P 0.99), (P 1), (($ft | Where-Object { $_ -gt 18 }).Count / $c), (($ft | Where-Object { $_ -gt 35 }).Count / $c)
"   draws/frame {0:N0} (max {1}), GPU busy {2} ms, CP cpu {3} ms per frame, game process {4:N2} cores" -f (($rows | ForEach-Object { [double]$_.draw_calls } | Measure-Object -Average).Average), (($rows | ForEach-Object { [int]$_.draw_calls } | Measure-Object -Maximum).Maximum), (Avg 'gpu_busy_us'), (Avg 'cp_cpu_us'), $cores
"   $pw"
